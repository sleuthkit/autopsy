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
import java.util.concurrent.ConcurrentHashMap;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
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

    //Outer concurrent hashmap with keys of JobID, inner concurrentHashmap with keys of objectID
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Archive>> mapOfDepthTrees = new ConcurrentHashMap<>();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private FileTaskExecutor fileIoTaskExecutor;
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
         * Construct a file type detector.
         */
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.CannotRunFileTypeDetection(), ex);
        }

        /*
         * Construct a file I/O tasks executor. See FileIoTaskExecutor class
         * header docs for an explanation of the use of this object.
         */
        fileIoTaskExecutor = new FileTaskExecutor(context);

        /*
         * Construct relative and absolute paths to the output directory. The
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
         * Construct an archive file extractor that uses the 7Zip Java bindings.
         */
        try {
            this.archiveExtractor = new SevenZipExtractor(context, fileTypeDetector, moduleDirRelative, moduleDirAbsolute, fileIoTaskExecutor);
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

        if (refCounter.incrementAndGet(jobId) == 1) {
            /*
             * Create the output directory, if it was not already created for
             * another job.
             */
            try {
                File extractionDirectory = new File(moduleDirAbsolute);
                if (!fileIoTaskExecutor.exists(extractionDirectory)) {
                    fileIoTaskExecutor.mkdirs(extractionDirectory);
                }
            } catch (FileTaskExecutor.FileTaskFailedException | InterruptedException ex) {
                fileIoTaskExecutor.shutDown();
                throw new IngestModuleException(Bundle.EmbeddedFileExtractor_make_output_dir_err(), ex);
            }

            /*
             * Construct a hash map to keep track of depth in archives while
             * processing archive files.
             *
             * TODO (Jira-7119): A ConcurrentHashMap is almost certainly the
             * wrong data structure here. It is intended to efficiently provide
             * snapshots to multiple threads. A thread may not see the current
             * state.
             */
            mapOfDepthTrees.put(jobId, new ConcurrentHashMap<>());
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
            fileIoTaskExecutor.shutDown();
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
