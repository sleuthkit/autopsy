/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.EncodedFileOutputStream;
import org.sleuthkit.datamodel.TskData;

/**
 * Facility for extracting and storing attachments from PDF documents.
 * Implementation specifics, however, are generic enough to be used on any
 * document with embedded resources. The current name reflects the only known
 * use case for this class.
 */
final class PDFAttachmentExtractor {

    private static final Logger logger = Logger.getLogger(PDFAttachmentExtractor.class.getName());
    private final AutoDetectParser parser;
    
    public PDFAttachmentExtractor() {
        parser = new AutoDetectParser();
    }
    
    public PDFAttachmentExtractor(AutoDetectParser parser) {
        this.parser = parser;
    }

    /**
     * Extracts PDF attachments from a given input and writes them to the supplied
     * output directory.
     * 
     * @param input Input PDF to extract attachments from
     * @param parentID ID for unique extraction names
     * @param outputDir Directory to write attachments
     * @return Map containing file name -> location on disk
     * @throws IOException
     * @throws SAXException
     * @throws TikaException 
     */
    public Map<String, NewResourceData> extract(InputStream input, long parentID, Path outputDir) throws IOException, SAXException, TikaException {
        ExtractionPreconditions.checkArgument(Files.exists(outputDir), 
                String.format("Output directory: %s, does not exist.", outputDir.toString())); //NON-NLS

        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, parser);

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);
        pdfConfig.setExtractUniqueInlineImagesOnly(true);

        parseContext.set(PDFParserConfig.class, pdfConfig);
        
        //Keep track of the attachment files as they are being extracted and written to disk.
        NewResourceWatcher watcher = new NewResourceWatcher();
        parseContext.set(EmbeddedDocumentExtractor.class, new EmbeddedAttachmentHandler(outputDir, parentID, watcher));

        //Parse input with default params, except for our ParseContext
        parser.parse(input, new BodyContentHandler(-1), new Metadata(), parseContext);

        return watcher.getSnapshot();
    }

    /**
     * Internal Tika class that is invoked upon encountering an embedded
     * resource.
     */
    static class EmbeddedAttachmentHandler implements EmbeddedDocumentExtractor {

        private final Path outputDirectory;
        private final NewResourceWatcher watcher;
        private final Long parentID;
        private Integer attachmentCount;

        public EmbeddedAttachmentHandler(Path outputDirectory, long parentID, NewResourceWatcher watcher) {
            this.outputDirectory = outputDirectory;
            this.watcher = watcher;
            this.parentID = parentID;
            attachmentCount = 0;
        }

        @Override
        public boolean shouldParseEmbedded(Metadata mtdt) {
            //Grab every available attachment
            return true;
        }

        @Override
        public void parseEmbedded(InputStream in, ContentHandler ch, Metadata mtdt, boolean bln) throws SAXException, IOException {
            //Resource naming scheme is used internally in autopsy, therefore we can guarentee uniqueness.
            String uniqueExtractedName = "extract_" + attachmentCount++; //NON-NLS
            
            String name = mtdt.get(Metadata.RESOURCE_NAME_KEY);
            String ext = FilenameUtils.getExtension(name);
            
            //Append the extension if we can.
            if(ext == null) {
                name = uniqueExtractedName;
            } else if(!ext.isEmpty()) {
                uniqueExtractedName += "." + ext;
            }
            
            Path outputFile = outputDirectory.resolve(uniqueExtractedName);

            try (EncodedFileOutputStream outputStream = new EncodedFileOutputStream(
                    new FileOutputStream(outputFile.toFile()), TskData.EncodingType.XOR1)){
                int bytesCopied = IOUtils.copy(in, outputStream);
                watcher.notify(name, outputFile, bytesCopied);
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Could not extract attachment %s into directory %s", //NON-NLS
                        uniqueExtractedName, outputFile), ex);
            }
        }
    }

    /**
     * Utility class to hold an extracted file's path and length.
     * Note that we can not use the length of the file on disk because
     * the XOR header has been added to it.
     */
    static class NewResourceData {
        private final Path path;
        private final int length;
        
        NewResourceData(Path path, int length) {
            this.path = path;
            this.length = length;
        }
        
        Path getPath() {
            return path;
        }
        
        int getLength() {
            return length;
        }
    }
    
    /**
     * Convenient wrapper for keeping track of new resource paths and the display
     * name for each of these resources.
     *
     * It is necessary to maintain a snapshot of only our changes when the
     * output directory is shared among other processes/threads.
     */
    static class NewResourceWatcher {

        private final Map<String, NewResourceData> newResourcePaths;

        public NewResourceWatcher() {
            newResourcePaths = new HashMap<>();
        }

        public void notify(String name, Path localPath, int length) {
            newResourcePaths.put(name, new NewResourceData(localPath, length));
        }

        public Map<String, NewResourceData> getSnapshot() {
            return newResourcePaths;
        }
    }
    
    /**
     * Static convenience methods that ensure the PDF extractor is being invoked
     * correctly.
     */
    static class ExtractionPreconditions {

        public static void checkArgument(boolean expression, String msg) throws IOException {
            if (!expression) {
                throw new IOException(msg);
            }
        }
        
        private ExtractionPreconditions(){
        }
    }
}
