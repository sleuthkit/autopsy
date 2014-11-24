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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import javax.xml.bind.DatatypeConverter;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager;

/**
 * RJCTODO
 */
public class UserDefinedFileTypes {

    private static final String USER_DEFINED_TYPE_DEFINITIONS_FILE = "UserFileTypeDefinitions.xml"; // NON-NLS
    private static final String ROOT_FILE_TYPES_ELEMENT = "fileTypes"; // NON-NLS
    private static final String FILE_TYPE_ELEMENT = "fileType"; // NON-NLS
    private static final String MIME_TYPE_ELEMENT = "mimeType"; // NON-NLS
    private static final String SIGNATURE_ELEMENT = "signature"; // NON-NLS
    private static final String OFFSET_ELEMENT = "offset";
    private static final String ENCODING = "UTF-8"; //NON-NLS // RJCTODO: Is this right?

    /**
     * Reads the user-defined file type definitions from the user-defined file
     * types file.
     *
     * @return A list of file type signature
     */
    synchronized static List<FileTypeSignature> getUserDefinedFileTypeSignatures() {
        Path filePath = Paths.get(PlatformUtil.getUserConfigDirectory(), UserDefinedFileTypes.USER_DEFINED_TYPE_DEFINITIONS_FILE);
        String filePathString = filePath.toAbsolutePath().toString();
        File file = new File(filePathString);
        if (file.exists() && file.canRead()) {
            return UserDefinedFileTypes.Reader.readFileTypes(filePathString);
        }
        return Collections.emptyList();
    }

    /**
     * An association between a MIME type and a signature within a file.
     */
    static interface FileTypeSignature {

        /**
         * Gets the MIME type associated with this signature.
         *
         * @return The MIME type string.
         */
        String getMimeType();

        /**
         * RJCTODO
         *
         * @return
         */
        byte[] getSignatureBytes();

        /**
         * RJCTODO
         *
         * @return
         */
        long getOffset();

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
    static class ByteSignature implements FileTypeSignature {

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
        public byte[] getSignatureBytes() {
            return this.signatureBytes;
        }

        /**
         * @inheritDoc
         */
        @Override
        public long getOffset() {
            return this.offset;
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
     * RJCTODO
     */
    static class AsciiStringSignature implements FileTypeSignature {

        /**
         * @inheritDoc
         */
        @Override
        public String getMimeType() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public byte[] getSignatureBytes() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public long getOffset() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        /**
         * @inheritDoc
         */
        @Override
        public boolean containedIn(AbstractFile file) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        static void writeFileTypes(List<FileTypeSignature> signatures, String filePath) {
            try {
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document doc = builder.newDocument();
                Element rootElem = doc.createElement(UserDefinedFileTypes.ROOT_FILE_TYPES_ELEMENT);
                doc.appendChild(rootElem);
                for (FileTypeSignature signature : signatures) {
                    UserDefinedFileTypes.Writer.writeFileType(signature, rootElem, doc);
                }
                if (!XMLUtil.saveDoc(HashDbManager.class, filePath, UserDefinedFileTypes.ENCODING, doc)) {
                    // RJCTODO
                }
            } catch (ParserConfigurationException ex) {
            }

        }

        static void writeFileType(FileTypeSignature signature, Element rootElem, Document doc) {
            Element mimeTypeElem = doc.createElement(UserDefinedFileTypes.MIME_TYPE_ELEMENT);
            mimeTypeElem.setTextContent(signature.getMimeType());
            Element sigElem = doc.createElement(UserDefinedFileTypes.SIGNATURE_ELEMENT);
            sigElem.setTextContent(DatatypeConverter.printHexBinary(signature.getSignatureBytes()));
            Element offsetElem = doc.createElement(UserDefinedFileTypes.OFFSET_ELEMENT);
            offsetElem.setTextContent(DatatypeConverter.printLong(signature.getOffset()));
            Element fileTypeElem = doc.createElement(UserDefinedFileTypes.FILE_TYPE_ELEMENT);
            fileTypeElem.appendChild(mimeTypeElem);
            fileTypeElem.appendChild(sigElem);
            fileTypeElem.appendChild(offsetElem);
            rootElem.appendChild(fileTypeElem);
            // RJCTODO: Surely there are some exceptions that could be thrown here?
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
        static List<FileTypeSignature> readFileTypes(String filePath) {
            try {
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document doc = builder.parse(new FileInputStream(filePath));
                if (doc != null) {
                    Element rootElem = doc.getDocumentElement();
                    if (rootElem != null && rootElem.getNodeName().equals(UserDefinedFileTypes.ROOT_FILE_TYPES_ELEMENT)) {
                        return UserDefinedFileTypes.Reader.parseFileTypes(rootElem);
                    }
                }
            } catch (ParserConfigurationException | SAXException | IOException e) {
                // RJCTODO:
            }
            return Collections.emptyList();
        }

        /**
         * RJCTODO
         *
         * @param rootElem
         * @return
         */
        private static List<FileTypeSignature> parseFileTypes(Element rootElem) {
            List<FileTypeSignature> signatures = new ArrayList<>();
            NodeList fileTypeElems = rootElem.getElementsByTagName(UserDefinedFileTypes.FILE_TYPE_ELEMENT);
            for (int i = 0; i < fileTypeElems.getLength(); ++i) {
                Element fileTypeElem = (Element) fileTypeElems.item(i);
                UserDefinedFileTypes.Reader.tryParseSignature(fileTypeElem, signatures);
            }
            return signatures;
        }

        /**
         * RJCTODO
         *
         * @param fileTypeElem
         * @param signatures
         */
        private static void tryParseSignature(Element fileTypeElem, List<FileTypeSignature> signatures) {
            try {
                String mimeType = "";
                byte[] signature = null;
                long offset = -1;
                NodeList childElems = fileTypeElem.getElementsByTagName(UserDefinedFileTypes.FILE_TYPE_ELEMENT);
                for (int i = 0; i < childElems.getLength(); ++i) {
                    Element childElem = (Element) childElems.item(i);
                    switch (childElem.getTagName()) {
                        case MIME_TYPE_ELEMENT:
                            mimeType = childElem.getTextContent();
                            break;
                        case SIGNATURE_ELEMENT:
                            signature = DatatypeConverter.parseHexBinary(childElem.getTextContent());
                            break;
                        case OFFSET_ELEMENT:
                            offset = DatatypeConverter.parseLong(childElem.getTextContent());
                            break;
                        default:
                            break;
                    }
                }
                if (!mimeType.isEmpty() && null != signature && signature.length > 0 && offset >= 0) {
                    signatures.add(new ByteSignature(mimeType, signature, offset));
                }
            } catch (NumberFormatException ex) {
                // RJCTODO: Log error
            } catch (IllegalArgumentException ex) {
                // RJCTODO: Log error                
            }
        }

    }

}
