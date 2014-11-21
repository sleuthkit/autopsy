/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.filetypeid;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.externalresults.ExternalResultsXMLParser;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * RJCTODO
 */
public class UserDefinedFileTypes {

    private static final String ROOT_FILE_TYPES_ELEMENT = "fileTypes"; // NON-NLS
    private static final String FILE_TYPE_ELEMENT = "fileType"; // NON-NLS
    private static final String MIME_TYPE_ELEMENT = "mimeType"; // NON-NLS
    private static final String SIGNATURE_ELEMENT = "signature"; // NON-NLS
    private static final String OFFSET_ELEMENT = "offset";

    /**
     * An association between a MIME type and a signature within a file.
     */
    static interface FileSignature {

        /**
         * Gets the MIME type associated with this signature.
         *
         * @return The MIME type string.
         */
        String getMimeType();

        /**
         * Determines whether or not a file contains the signature.
         *
         * @param file The file to test.
         * @return True or false.
         */
        boolean containedIn(AbstractFile file);

    }

    /**
     * An association between a MIME type and a byte signature at a specified
     * offset within a file.
     */
    static class ByteSignature implements FileSignature {

        private final String mimeType;
        private final byte[] signatureBytes;
        private final long offset;
        private final byte[] buffer;

        /**
         * Creates an association between a MIME type and a byte signature at a
         * specified offset.
         *
         * @param mimeType The MIME type string.
         * @param signatureBytes The bytes of the signature.
         * @param offset The offset of the signature within a file.
         */
        private ByteSignature(String mimeType, byte[] signatureBytes, long offset) {
            this.mimeType = mimeType;
            this.signatureBytes = signatureBytes;
            this.offset = offset;
            this.buffer = new byte[signatureBytes.length];
        }

        /**
         * @inheritDoc
         */
        @Override
        public String getMimeType() {
            return this.mimeType;
        }

        /**
         * @inheritDoc
         */
        @Override
        public boolean containedIn(AbstractFile file) {
            try {
                int bytesRead = file.read(this.buffer, offset, buffer.length);
                return bytesRead == buffer.length ? Arrays.equals(this.buffer, this.signatureBytes) : false;
            } catch (TskCoreException ex) {
                return false;
            }
        }

    }
        
    /**
     * Provides a mechanism for persisting user-defined file types.
     */
    static class Writer {

        /**
         * Reads user-defined file type definitions from a file.
         *
         * @param signatures A collection of file signatures.
         * @param filePath The path to the file.
         */
        static void writeFileTypes(List<FileSignature> signatures, String filePath) {

        }
        
    }

    /**
     * Provides a method for reading persisted user-defined file types.
     */
    static class Reader {

        /**
         * Reads user-defined file type definitions from a file.
         *
         * @param filePath The path to the file.
         * @return A collection of file signatures.
         */
        static List<FileSignature> readFileTypes(String filePath) {
            // RJCTODO:
        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document doc = builder.parse(new FileInputStream(filePath));
            final Document doc = XMLUtil.loadDoc(ExternalResultsXMLParser.class, this.resultsFilePath, XSD_FILE);
            if (doc != null) {
                final Element rootElem = doc.getDocumentElement();
                if (rootElem != null && rootElem.getNodeName().equals(ExternalResultsXMLParser.TagNames.ROOT_ELEM.toString())) {
            
        } catch (ParserConfigurationException e) {
        } catch (SAXException e) {
        } catch (IOException e) {
        }
            

            
            
            return Collections.emptyList();
        }

    }

}
