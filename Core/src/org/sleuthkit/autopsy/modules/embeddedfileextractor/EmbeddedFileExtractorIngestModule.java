/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.ImageExtractor.SupportedImageExtractionFormats;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;

/**
 * Embedded File Extractor ingest module extracts embedded files from supported
 * archives and documents, adds extracted embedded DerivedFiles, reschedules
 * extracted DerivedFiles for ingest.
 */
@NbBundle.Messages({
    "CannotCreateOutputFolder=Unable to create output folder.",
    "CannotRunFileTypeDetection=Unable to run file type detection.",
    "UnableToInitializeLibraries=Unable to initialize 7Zip libraries."
})
public final class EmbeddedFileExtractorIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(EmbeddedFileExtractorIngestModule.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    static final String[] SUPPORTED_EXTENSIONS = {"zip", "rar", "arj", "7z", "7zip", "gzip", "gz", "bzip2", "tar", "tgz",}; // "iso"}; NON-NLS

    private IngestJobContext context;
    private long jobId;
    private final static IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();

    private String moduleDirRelative;
    private String moduleDirAbsolute;

    private boolean archivextraction;
    private boolean imageExtraction;
    private ImageExtractor imageExtractor;
    private SevenZipExtractor archiveExtractor;
    SupportedImageExtractionFormats abstractFileExtractionFormat;
    FileTypeDetector fileTypeDetector;

    EmbeddedFileExtractorIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        jobId = context.getJobId();

        final Case currentCase = Case.getCurrentCase();

        moduleDirRelative = currentCase.getModuleOutputDirectoryRelativePath() + File.separator + EmbeddedFileExtractorModuleFactory.getModuleName(); //relative to the case, to store in db
        moduleDirAbsolute = currentCase.getModuleDirectory() + File.separator + EmbeddedFileExtractorModuleFactory.getModuleName(); //absolute, to extract to

        // initialize the folder where the embedded files are extracted.
        File extractionDirectory = new File(moduleDirAbsolute);
        if (!extractionDirectory.exists()) {
            try {
                extractionDirectory.mkdirs();
            } catch (SecurityException ex) {
                throw new IngestModuleException(Bundle.CannotCreateOutputFolder(), ex);
            }
        }

        // initialize the filetypedetector
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.CannotRunFileTypeDetection(), ex);
        }

        // initialize the extraction modules.
        try {
            this.archiveExtractor = new SevenZipExtractor(context, fileTypeDetector, moduleDirRelative, moduleDirAbsolute);
        } catch (SevenZipNativeInitializationException ex) {
            throw new IngestModuleException(Bundle.UnableToInitializeLibraries(), ex);
        }

        this.imageExtractor = new ImageExtractor(context, fileTypeDetector, moduleDirRelative, moduleDirAbsolute);
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        // skip the unallocated blocks
        if ((abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) ||
                (abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK))) {
            return ProcessResult.OK;
        }

        // skip known files
        if (abstractFile.getKnown().equals(TskData.FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }

        // check if the file is supported by either of the two embedded file extractors.
        this.archivextraction = archiveExtractor.isSevenZipExtractionSupported(abstractFile);
        this.imageExtraction = imageExtractor.isImageExtractionSupported(abstractFile);

        if (!abstractFile.isFile() && (!this.archivextraction || !this.imageExtraction)) {
            return ProcessResult.OK;
        }

        // call the archive extractor if archiveExtraction flag is set.
        if (this.archivextraction) {
            archiveExtractor.unpack(abstractFile);
        }

        // calling the image extractor if imageExtraction flag set.
        if (this.imageExtraction) {
            imageExtractor.extractImage(abstractFile);
        }

        return ProcessResult.OK;
    }

    @Override
    public void shutDown() {
        // We don't need the value, but for cleanliness and consistency
        refCounter.decrementAndGet(jobId);
    }

    /**
     * Get local relative path to the unpacked archive root
     *
     * @param archiveFile
     *
     * @return
     */
    static String getUniqueName(AbstractFile archiveFile) {
        return archiveFile.getName() + "_" + archiveFile.getId();
    }
}
