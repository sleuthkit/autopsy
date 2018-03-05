/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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

    static final String[] SUPPORTED_EXTENSIONS = {"zip", "rar", "arj", "7z", "7zip", "gzip", "gz", "bzip2", "tar", "tgz",}; // "iso"}; NON-NLS
    private String moduleDirRelative;
    private String moduleDirAbsolute;
    private MSOfficeEmbeddedContentExtractor officeExtractor;
    private SevenZipExtractor archiveExtractor;
    private FileTypeDetector fileTypeDetector;

    /**
     * Constructs a file level ingest module that extracts embedded files from
     * supported archive and document formats.
     */
    EmbeddedFileExtractorIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        /*
         * Construct absolute and relative paths to the output directory. The
         * relative path is relative to the case folder, and will be used in the
         * case database for extracted (derived) file paths. The absolute path
         * is used to write the extracted (derived) files to local storage.
         */
        try {
        final Case currentCase = Case.getOpenCase();
        moduleDirRelative = Paths.get(currentCase.getModuleOutputDirectoryRelativePath(), EmbeddedFileExtractorModuleFactory.getModuleName()).toString();
        moduleDirAbsolute = Paths.get(currentCase.getModuleDirectory(), EmbeddedFileExtractorModuleFactory.getModuleName()).toString();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.EmbeddedFileExtractorIngestModule_NoOpenCase_errMsg(), ex);
        }
        /*
         * Create the output directory.
         */
        File extractionDirectory = new File(moduleDirAbsolute);
        if (!extractionDirectory.exists()) {
            try {
                extractionDirectory.mkdirs();
            } catch (SecurityException ex) {
                throw new IngestModuleException(Bundle.CannotCreateOutputFolder(), ex);
            }
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
         * Construct a 7Zip file extractor for processing archive files.
         */
        try {
            this.archiveExtractor = new SevenZipExtractor(context, fileTypeDetector, moduleDirRelative, moduleDirAbsolute);
        } catch (SevenZipNativeInitializationException ex) {
            throw new IngestModuleException(Bundle.UnableToInitializeLibraries(), ex);
        }

        /*
         * Construct an embedded content extractor for processing Microsoft
         * Office documents.
         */
        try {
            this.officeExtractor = new MSOfficeEmbeddedContentExtractor(context, fileTypeDetector, moduleDirRelative, moduleDirAbsolute);
        } catch (NoCurrentCaseException ex) {
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
            archiveExtractor.unpack(abstractFile);
        } else if (officeExtractor.isContentExtractionSupported(abstractFile)) {
            officeExtractor.extractEmbeddedContent(abstractFile);
        }
        return ProcessResult.OK;
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
