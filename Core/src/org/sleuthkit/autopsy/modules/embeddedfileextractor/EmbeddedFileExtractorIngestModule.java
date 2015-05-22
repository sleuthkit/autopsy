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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;

/**
 * 7Zip ingest module extracts supported archives, adds extracted DerivedFiles,
 * reschedules extracted DerivedFiles for ingest.
 */
public final class EmbeddedFileExtractorIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(EmbeddedFileExtractorIngestModule.class.getName());
    private IngestServices services = IngestServices.getInstance();
    static final String[] SUPPORTED_EXTENSIONS = {"zip", "rar", "arj", "7z", "7zip", "gzip", "gz", "bzip2", "tar", "tgz",}; // "iso"}; NON-NLS

    //buffer for checking file headers and signatures
    private static final int readHeaderSize = 4;
    private static final byte[] fileHeaderBuffer = new byte[readHeaderSize];
    private static final int ZIP_SIGNATURE_BE = 0x504B0304;
    private IngestJobContext context;
    private long jobId;
    private final static IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();

    private static final Case currentCase = Case.getCurrentCase();
    protected static final String moduleDirRelative = Case.getModulesOutputDirRelPath() + File.separator + EmbeddedFileExtractorModuleFactory.getModuleName(); //relative to the case, to store in db
    protected static final String moduleDirAbsolute = currentCase.getModulesOutputDirAbsPath() + File.separator + EmbeddedFileExtractorModuleFactory.getModuleName(); //absolute, to extract to

    private boolean archivextraction;
    private boolean imageExtraction;
    private ImageExtractor imageExtractor;
    private ArchiveExtractor archiveExtractor;
    private SupportedImageExtractionFormats abstractFileExtractionFormat;

    /**
     * Enum of mimetypes which support image extraction
     */
    enum SupportedImageExtractionFormats {

        DOC("application/msword"),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        PPT("application/vnd.ms-powerpoint"),
        PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        XLS("application/vnd.ms-excel"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        private final String mimeType;

        SupportedImageExtractionFormats(final String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public String toString() {
            return this.mimeType;
        }
        // TODO Expand to support more formats
    }

    /**
     * Enum of mimetypes which support archive extraction
     */
    enum SupportedArchiveExtractionFormats {
        // TODO Add mimetypes.
    }

    EmbeddedFileExtractorIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        jobId = context.getJobId();
        
        File extractionDirectory = new File(EmbeddedFileExtractorIngestModule.moduleDirAbsolute);
        if (!extractionDirectory.exists()) {
            try {
                extractionDirectory.mkdirs();
            } catch (SecurityException ex) {
                logger.log(Level.SEVERE, "Error initializing output dir: " + EmbeddedFileExtractorIngestModule.moduleDirAbsolute, ex); //NON-NLS
                services.postMessage(IngestMessage.createErrorMessage(EmbeddedFileExtractorModuleFactory.getModuleName(), "Error initializing", "Error initializing output dir: " + EmbeddedFileExtractorIngestModule.moduleDirAbsolute)); //NON-NLS
                throw new RuntimeException(ex);
            }
        }
        this.archiveExtractor = new ArchiveExtractor(context);
        this.imageExtractor = new ImageExtractor(context);
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        if (abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return ProcessResult.OK;
        }

        if (abstractFile.getKnown().equals(TskData.FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }

        this.archivextraction = isArchiveExtractionSupported(abstractFile);
        try {
            this.imageExtraction = isImageExtractionSupported(new FileTypeDetector().detectAndPostToBlackboard(abstractFile));
        } catch (FileTypeDetector.FileTypeDetectorInitException | TskCoreException ex) {
            this.imageExtraction = false;
        }

        if (!abstractFile.isFile() && (!this.archivextraction || !this.imageExtraction)) {
            return ProcessResult.OK;
        }

        //check if already has derived files, skip
        try {
            if (abstractFile.hasChildren()) {
                //check if local unpacked dir exists
                final String uniqueFileName = getUniqueName(abstractFile);
                final String localRootAbsPath = getLocalRootAbsPath(uniqueFileName);
                if (new File(localRootAbsPath).exists()) {
                    logger.log(Level.INFO, "File already has been processed as it has children and local unpacked file, skipping: {0}", abstractFile.getName()); //NON-NLS
                    return ProcessResult.OK;
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.INFO, "Error checking if file already has been processed, skipping: {0}", abstractFile.getName()); //NON-NLS
            return ProcessResult.OK;
        }

        logger.log(Level.INFO, "Processing with embedded file extractor: {0}", abstractFile.getName()); //NON-NLS

        if (this.archivextraction) {
            List<AbstractFile> unpackedFiles = archiveExtractor.unpack(abstractFile);
            if (!unpackedFiles.isEmpty()) {
                //currently sending a single event for all new files
                services.fireModuleContentEvent(new ModuleContentEvent(abstractFile));

                context.addFilesToJob(unpackedFiles);
            }
        }

        // calling the image extractor if imageExtraction flag set.
        if (this.imageExtraction) {
            imageExtractor.extractImage(abstractFileExtractionFormat, abstractFile);
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
     * @return
     */
    protected static String getUniqueName(AbstractFile archiveFile) {
        return archiveFile.getName() + "_" + archiveFile.getId();
    }

    /**
     * Get local abs path to the unpacked archive root
     *
     * @param localRootRelPath relative path to archive, from getUniqueName()
     * @return
     */
    protected static String getLocalRootAbsPath(String localRootRelPath) {
        return moduleDirAbsolute + File.separator + localRootRelPath;
    }

    // TODO change implementation. Less extension dependent
    protected static boolean isArchiveExtractionSupported(AbstractFile file) {
        // see if it is on the list of extensions
        final String extension = file.getNameExtension();
        for (int i = 0; i < SUPPORTED_EXTENSIONS.length; ++i) {
            if (extension.equals(SUPPORTED_EXTENSIONS[i])) {
                return true;
            }
        }

        // if no extension match, check the blackboard for the file type
        boolean attributeFound = false;
        try {
            ArrayList<BlackboardAttribute> attributes = file.getGenInfoAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
            for (BlackboardAttribute attribute : attributes) {
                attributeFound = true;
                String fileType = attribute.getValueString();
                if (!fileType.isEmpty() && fileType.equals("application/zip")) { //NON-NLS
                    return true;
                }
            }
        } catch (TskCoreException ex) {
        }

        // if no blackboard entry for file type, do it manually for ZIP files:
        if (attributeFound) {
            return false;
        } else {
            return isZipFileHeader(file);
        }
    }

    /**
     * This method returns true if the file format is currently supported. Else
     * it returns false.
     *
     * @param mimeType name of the file format as determined by tika.detect()
     * @return This method returns true if the file format is currently
     * supported. Else it returns false.
     */
    private boolean isImageExtractionSupported(String mimeType) {
        for (SupportedImageExtractionFormats s : SupportedImageExtractionFormats.values()) {
            if (s.toString().equals(mimeType)) {
                this.abstractFileExtractionFormat = s;
                return true;
            }
        }
        return false;
    }

    /**
     * Check if is zip file based on header
     *
     * @param file
     * @return true if zip file, false otherwise
     */
    private static boolean isZipFileHeader(AbstractFile file) {
        if (file.getSize() < readHeaderSize) {
            return false;
        }

        try {
            int bytesRead = file.read(fileHeaderBuffer, 0, readHeaderSize);
            if (bytesRead != readHeaderSize) {
                return false;
            }
        } catch (TskCoreException ex) {
            //ignore if can't read the first few bytes, not a ZIP
            return false;
        }

        ByteBuffer bytes = ByteBuffer.wrap(fileHeaderBuffer);
        int signature = bytes.getInt();

        return signature == ZIP_SIGNATURE_BE;
    }
}
