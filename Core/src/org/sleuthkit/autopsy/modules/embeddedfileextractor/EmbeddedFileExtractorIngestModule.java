/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.modules.embeddedfileextractor;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import org.sleuthkit.autopsy.apputils.ApplicationLoggers;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.SevenZipExtractor.Archive;
import org.sleuthkit.autopsy.threadutils.TaskRetryUtil;

/**
 * A file level ingest module that extracts embedded files from supported
 * archive and document formats.
 */
@NbBundle.Messages({
    "CannotCreateOutputFolder=Unable to create output folder.",
    "CannotRunFileTypeDetection=Unable to run file type detection.",
    "UnableToInitializeLibraries=Unable to initialize 7Zip libraries.",
    "EmbeddedFileExtractorIngestModule.NoOpenCase.errMsg=No open case available.",
    "EmbeddedFileExtractorIngestModule.UnableToGetMSOfficeExtractor.errMsg=Unable to get MSOfficeEmbeddedContentExtractor."
})
public final class EmbeddedFileExtractorIngestModule extends FileIngestModuleAdapter {

    private static final String TASK_RETRY_STATS_LOG_NAME = "task_retry_stats";
    private static final Logger taskStatsLogger = ApplicationLoggers.getLogger(TASK_RETRY_STATS_LOG_NAME);
    private static final Object execMapLock = new Object();
    @GuardedBy("execMapLock")
    private static final Map<Long, FileTaskExecutor> fileTaskExecsByJob = new HashMap<>();
    //Outer concurrent hashmap with keys of JobID, inner concurrentHashmap with keys of objectID
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Archive>> mapOfDepthTrees = new ConcurrentHashMap<>();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private DocumentEmbeddedContentExtractor documentExtractor;
    private SevenZipExtractor archiveExtractor;
    private FileTypeDetector fileTypeDetector;
    private long jobId;

    /**
     * Constructs a file level ingest module that extracts embedded files from
     * supported archive and document formats.
     */
    EmbeddedFileExtractorIngestModule() {
    }

    @Override
    @NbBundle.Messages({
        "EmbeddedFileExtractor_make_output_dir_err=Failed to create module output directory for Embedded File Extractor"
    })
    public void startUp(IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();

        /*
         * Construct absolute and relative paths to the output directory. The
         * output directory is a subdirectory of the ModuleOutput folder in the
         * case directory and is named for the module.
         *
         * The absolute path is used to write the extracted (derived) files to
         * local storage.
         *
         * The relative path is relative to the case folder and is used in the
         * case database for extracted (derived) file paths.
         *
         */
        Case currentCase = Case.getCurrentCase();
        String moduleDirAbsolute = Paths.get(currentCase.getModuleDirectory(), EmbeddedFileExtractorModuleFactory.getOutputFolderName()).toString();
        String moduleDirRelative = Paths.get(currentCase.getModuleOutputDirectoryRelativePath(), EmbeddedFileExtractorModuleFactory.getOutputFolderName()).toString();

        if (refCounter.incrementAndGet(jobId) == 1) {

            /*
             * Construct a per ingest job executor that will be used for calling
             * java.io.File methods as tasks with retries. Retries are employed
             * here due to observed issues with hangs when attempting these
             * operations on case directories stored on a certain type of
             * network file system. See the FileTaskExecutor class header docs
             * for more details.
             */
            FileTaskExecutor fileTaskExecutor = new FileTaskExecutor(context);
            synchronized (execMapLock) {
                fileTaskExecsByJob.put(jobId, fileTaskExecutor);
            }

            try {
                File extractionDirectory = new File(moduleDirAbsolute);
                if (!fileTaskExecutor.exists(extractionDirectory)) {
                    fileTaskExecutor.mkdirs(extractionDirectory);
                }
            } catch (FileTaskExecutor.FileTaskFailedException | InterruptedException ex) {
                /*
                 * The exception message is localized because ingest module
                 * start up exceptions are displayed to the user when running
                 * with the RCP GUI.
                 */
                throw new IngestModuleException(Bundle.EmbeddedFileExtractor_make_output_dir_err(), ex);
            }

            /*
             * Construct a hash map to keep track of depth in archives while
             * processing archive files.
             *
             * TODO (Jira-7119): A ConcurrentHashMap of ConcurrentHashMaps is
             * almost certainly the wrong data structure here. ConcurrentHashMap
             * is intended to efficiently provide snapshots to multiple threads.
             * A thread may not see the current state.
             */
            mapOfDepthTrees.put(jobId, new ConcurrentHashMap<>());
        }

        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.CannotRunFileTypeDetection(), ex);
        }

        try {
            archiveExtractor = new SevenZipExtractor(context, fileTypeDetector, moduleDirRelative, moduleDirAbsolute, fileTaskExecsByJob.get(jobId));
        } catch (SevenZipNativeInitializationException ex) {
            /*
             * The exception message is localized because ingest module start up
             * exceptions are displayed to the user when running with the RCP
             * GUI.
             */
            throw new IngestModuleException(Bundle.UnableToInitializeLibraries(), ex);
        }

        try {
            documentExtractor = new DocumentEmbeddedContentExtractor(context, fileTypeDetector, moduleDirRelative, moduleDirAbsolute, fileTaskExecsByJob.get(jobId));
        } catch (NoCurrentCaseException ex) {
            /*
             * The exception message is localized because ingest module start up
             * exceptions are displayed to the user when running with the RCP
             * GUI.
             */
            throw new IngestModuleException(Bundle.EmbeddedFileExtractorIngestModule_UnableToGetMSOfficeExtractor_errMsg(), ex);
        }
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        /*
         * Skip unallocated space files.
         */
        if ((abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS))
                || (abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK))) {
            return ProcessResult.OK;
        }

        /*
         * Skip known files.
         */
        if (abstractFile.getKnown().equals(TskData.FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }

        /*
         * Skip directories, etc.
         */
        if (!abstractFile.isFile()) {
            return ProcessResult.OK;
        }

        /*
         * Attempt embedded file extraction for the file if it is a supported
         * type/format.
         */
        if (archiveExtractor.isSevenZipExtractionSupported(abstractFile)) {
            archiveExtractor.unpack(abstractFile, mapOfDepthTrees.get(jobId));
        } else if (documentExtractor.isContentExtractionSupported(abstractFile)) {
            documentExtractor.extractEmbeddedContent(abstractFile);
        }
        return ProcessResult.OK;
    }

    @Override
    public void shutDown() {
        if (refCounter.decrementAndGet(jobId) == 0) {
            mapOfDepthTrees.remove(jobId);
            FileTaskExecutor fileTaskExecutor;
            synchronized (execMapLock) {
                fileTaskExecutor = fileTaskExecsByJob.remove(jobId);
            }
            fileTaskExecutor.shutDown();
            taskStatsLogger.log(Level.INFO, String.format("total tasks: %d, total task timeouts: %d, total task retries: %d, total task failures: %d (ingest job ID = %d)", TaskRetryUtil.getTotalTasksCount(), TaskRetryUtil.getTotalTaskAttemptTimeOutsCount(), TaskRetryUtil.getTotalTaskRetriesCount(), TaskRetryUtil.getTotalFailedTasksCount(), jobId));
        }
    }

    /**
     * Creates a unique name for a file.
     * Currently this is just the file object id to prevent long paths and illegal characters.
     *
     * @param file The file.
     *
     * @return The unique file name.
     */
    static String getUniqueName(AbstractFile file) {
        return Long.toString(file.getId());
    }

}
