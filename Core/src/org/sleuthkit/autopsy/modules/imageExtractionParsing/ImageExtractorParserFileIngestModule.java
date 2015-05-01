/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.imageExtractionParsing;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.apache.tika.Tika;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Ingest module to parse image Exif metadata. Currently only supports JPEG
 * files. Ingests an image file and, if available, adds it's date, latitude,
 * longitude, altitude, device model, and device make to a blackboard artifact.
 */
public final class ImageExtractorParserFileIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(ImageExtractorParserFileIngestModule.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private final AtomicInteger filesProcessed = new AtomicInteger(0);
    private volatile boolean filesToFire = false;
    private long jobId;
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private ImageExtractor imageExtractor;
    private MetadataExtractor metadataExtractor;
    private Tika tika;
    private SupportedExtractionFormats abstractFileExtractionFormat;
    private SupportedParsingFormats abstractFileParsingFormat;
    private static final int BUFFER_SIZE = 64 * 1024;
    private final byte buffer[] = new byte[BUFFER_SIZE];
        
    ImageExtractorParserFileIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {    
        this.jobId = context.getJobId();
        refCounter.incrementAndGet(jobId);
        this.imageExtractor = new ImageExtractor(context);
        this.metadataExtractor = new MetadataExtractor();
        this.tika = new Tika();
    }

    
    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        if (abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return ProcessResult.OK;
        }

        if (abstractFile.getKnown().equals(TskData.FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }
        
        if (abstractFile.isFile() == false) {
            return ProcessResult.OK;
        }
        
        byte buf[] = readFileHeader(abstractFile);
        String mimeType = tika.detect(buf, abstractFile.getName());
        
        // module data event fired for every file from which one or more image/s
        // have been extracted.
        if(isExtractionSupported(mimeType)) {
            try {
                if(!abstractFile.hasChildren()) {
                    imageExtractor.extractImage(abstractFileExtractionFormat, abstractFile);
                }
            } catch(TskCoreException ex) {
                logger.log(Level.INFO, "Error determining if the file - {0} - has been processed.", abstractFile.getName()); //NON-NLS
                return ProcessResult.OK;
            }
        }
        
        // module data event fired for every 100 files that have been parsed for metadata.
        if(isParsingSupported(mimeType)) {
            final int filesProcessedValue = filesProcessed.incrementAndGet();
            filesToFire = metadataExtractor.extractMetadata(abstractFileParsingFormat, abstractFile);
            if ((filesToFire) && (filesProcessedValue % 1000 == 0)) {
                services.fireModuleDataEvent(new ModuleDataEvent(ImageExtractorParserModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF));
                filesToFire = false;
            }
        }
        return ProcessResult.OK; 
    }
    
    /**
     * This method returns true if the file format is currently supported. Else
     * it returns false.
     *
     * @param mimeType name of the file format as determined by tika.detect()
     * @return This method returns true if the file format is currently
     * supported. Else it returns false.
     */
    private boolean isExtractionSupported(String mimeType) {
        for (SupportedExtractionFormats s : SupportedExtractionFormats.values()) {
            if (s.toString().equals(mimeType)) {
                this.abstractFileExtractionFormat = s;
                return true;
            }
        }
        return false;
    }
    
    /**
     * This method returns true if the file format is currently supported for
     * metadata parsing. Else it returns false.
     * @param mimeType name of the file format as determined by tika.detect()
     * @return 
     */
    private boolean isParsingSupported(String mimeType) {
        for (SupportedParsingFormats s : SupportedParsingFormats.values()) {
            if (s.toString().equals(mimeType)) {
                this.abstractFileParsingFormat = s;
                return true;
            }
        }
        return false;
    }
    
    
    /**
     * Reads first 64 KB of the file content.
     * @param abstractFile the file whose content is to e read.
     * @return the first 64KB (or less) bytes of the file.
     */
    private byte[] readFileHeader(AbstractFile abstractFile) {
        byte buf[];
        try {
            int len = abstractFile.read(buffer, 0, BUFFER_SIZE);
            if (len < BUFFER_SIZE) {
                buf = new byte[len];
                System.arraycopy(buffer, 0, buf, 0, len);
            } else {
                buf = buffer;
            }
            return buf;
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Could not read the file header for " + abstractFile.getName(), ex);
            return null;
        }
    }

    @Override
    public void shutDown() {
        // We only need to check for this final event on the last module per job
        if (refCounter.decrementAndGet(jobId) == 0) {
            if (filesToFire) {
                //send the final new data event
                services.fireModuleDataEvent(new ModuleDataEvent(ImageExtractorParserModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF));
            }
        }
    }
    
    /**
     * Enum of formats from which images can be extracted
     */
    enum SupportedExtractionFormats {

        DOC("application/msword"),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        PPT("application/vnd.ms-powerpoint"),
        PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        XLS("application/vnd.ms-excel"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        private final String mimeType;

        SupportedExtractionFormats(final String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public String toString() {
            return this.mimeType;
        }
        // TODO Expand to support more formats
    }

    /**
     * Enum of formats from which exif/metadata can be extracted.
     */
    enum SupportedParsingFormats {

        JPEG("image/jpeg");

        private final String mimeType;

        SupportedParsingFormats(final String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public String toString() {
            return this.mimeType;
        }
        // TODO Expand to support more formats
    }
}