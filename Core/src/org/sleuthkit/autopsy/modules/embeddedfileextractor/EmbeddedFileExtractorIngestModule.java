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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.spi.IIORegistry;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.threadutils.TaskRetryUtil;
import org.sleuthkit.autopsy.threadutils.TaskRetryUtil.TaskAttempt;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.SevenZipExtractor.Archive;

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

    private static final Logger logger = Logger.getLogger(EmbeddedFileExtractorIngestModule.class.getName());    
    
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
    public void startUp(IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();

        /*
         * Construct absolute and relative paths to the output directory. The
         * output directory is a subdirectory of the ModuleOutput folder in the
         * case directory and is named for the module.
         *
         * The relative path is relative to the case folder, and will be used in
         * the case database for extracted (derived) file paths.
         *
         * The absolute path is used to write the extracted (derived) files to
         * local storage.
         */
        Case currentCase = Case.getCurrentCase();
        final String moduleDirRelative = Paths.get(currentCase.getModuleOutputDirectoryRelativePath(), EmbeddedFileExtractorModuleFactory.getModuleName()).toString();
        final String moduleDirAbsolute = Paths.get(currentCase.getModuleDirectory(), EmbeddedFileExtractorModuleFactory.getModuleName()).toString();

        /*
         * Do tasks that only need to be done by the first module instance for
         * an ingest job.
         */
        if (refCounter.incrementAndGet(jobId) == 1) {
            /*
             * Create the output directory, if it was not already created for
             * another job.
             *
             * RC NOTE: Retries are employed here due to observed issues with
             * hangs for a certain type of network file system. The problem was
             * that calls to File.exists() and File.mkdirs() were never
             * returning and auto ingest nodes were getting stuck indefinitely
             * (see Jira-6735). There is a severe downside to this approach,
             * where attempts can be abandoned after a timeout: threads stuck in
             * infinite loops (?) may accumulate. However, this was deemed
             * better than having an auto ingest node hang for nineteen days, as
             * in the Jira story.
             */
            Callable<Boolean> createOutptuDirTask = () -> {
                File extractionDirectory = new File(moduleDirAbsolute);
                if (extractionDirectory.exists()) {
                    return Boolean.TRUE;
                } else if (extractionDirectory.mkdirs()) {
                    return Boolean.TRUE;                    
                } else {
                    return null;
                }
            };
            List<TaskAttempt> attempts = new ArrayList<>(); // RJCTODO: Adjust
            attempts.add(new TaskAttempt(0L, TimeUnit.SECONDS, 5L, TimeUnit.SECONDS));
            attempts.add(new TaskAttempt(1L, TimeUnit.SECONDS, 5L, TimeUnit.SECONDS));
            attempts.add(new TaskAttempt(1L, TimeUnit.SECONDS, 5L, TimeUnit.SECONDS));
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(3, new ThreadFactoryBuilder().setNameFormat("").build()); // RJCTODO
            try {
                Boolean success = TaskRetryUtil.attemptTask(createOutptuDirTask, attempts, executor, logger, String.format("Creating %s if it does not exist", moduleDirAbsolute));
                if (success == null) {
                    throw new IngestModuleException(Bundle.CannotCreateOutputFolder());
                }
            } catch (InterruptedException ex) {
                throw new IngestModuleException(Bundle.CannotCreateOutputFolder(), ex); // RJCTODO
            } finally {
                executor.shutdownNow();
            }

            /*
             * Construct a hash map to keep track of depth in archives while
             * processing archive files.
             *
             * RC: A ConcurrentHashMap is almost certainly the wrong data
             * structure here. It is intended to efficiently provide snapshots
             * to multiple threads. A thread may not see the current state. See
             * Jira-7119.
             */
            mapOfDepthTrees.put(jobId, new ConcurrentHashMap<>());
        }

        /*
         * Construct a file type detector.
         */
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.CannotRunFileTypeDetection(), ex);
        }

        /*
         * Construct an archive file extractor that uses the 7Zip Java bindings.
         */
        try {
            this.archiveExtractor = new SevenZipExtractor(context, fileTypeDetector, moduleDirRelative, moduleDirAbsolute);
        } catch (SevenZipNativeInitializationException ex) {
            throw new IngestModuleException(Bundle.UnableToInitializeLibraries(), ex);
        }

        /*
         * Construct an embedded content extractor for processing Microsoft
         * Office documents and PDF documents.
         */
        try {
            this.documentExtractor = new DocumentEmbeddedContentExtractor(context, fileTypeDetector, moduleDirRelative, moduleDirAbsolute);
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.EmbeddedFileExtractorIngestModule_UnableToGetMSOfficeExtractor_errMsg(), ex);
        }

    }

    /**
     * Sorts all ImageIO SPI providers by their class name.
     */
    private <T> void sortPluginsInCategory(IIORegistry pluginRegistry, Class<T> category) {
        Iterator<T> serviceProviderIter = pluginRegistry.getServiceProviders(category, false);
        ArrayList<T> providers = new ArrayList<>();
        while (serviceProviderIter.hasNext()) {
            providers.add(serviceProviderIter.next());
        }
        Collections.sort(providers, (first, second) -> {
            return first.getClass().getCanonicalName().compareToIgnoreCase(second.getClass().getCanonicalName());
        });
        for (int i = 0; i < providers.size() - 1; i++) {
            for (int j = i + 1; j < providers.size(); j++) {
                // The registry only accepts pairwise orderings. To guarantee a 
                // total order, all pairs need to be exhausted.
                pluginRegistry.setOrdering(category, providers.get(i),
                        providers.get(j));
            }
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
        }
    }

    /**
     * Creates a unique name for a file by concatentating the file name and the
     * file object id.
     *
     * @param file The file.
     *
     * @return The unique file name.
     */
    static String getUniqueName(AbstractFile file) {
        return file.getName() + "_" + file.getId();
    }

}
