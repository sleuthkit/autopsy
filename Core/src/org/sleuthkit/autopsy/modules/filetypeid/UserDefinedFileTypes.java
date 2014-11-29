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
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.bind.DatatypeConverter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.modules.filetypeid.FileType.Signature;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager;

/**
 * Allows a user to define named file types characterized by file signatures.
 */
final class UserDefinedFileTypes {

    private static final Logger logger = Logger.getLogger(UserDefinedFileTypes.class.getName());
    private static final String FILE_TYPE_DEFINITIONS_SCHEMA_FILE = "FileTypeDefinitions.xsd"; // NON-NLS
    private static final String USER_DEFINED_TYPE_DEFINITIONS_FILE = "UserFileTypeDefinitions.xml"; // NON-NLS
    private static final String AUTOPSY_TYPE_DEFINITIONS_FILE = "AutopsyFileTypeDefinitions.xml"; // NON-NLS
    private static final String FILE_TYPES_TAG_NAME = "filetypes"; // NON-NLS
    private static final String FILE_TYPE_TAG_NAME = "filetype"; // NON-NLS
    private static final String ALERT_ATTRIBUTE = "alert"; // NON-NLS
    private static final String TYPE_NAME_TAG_NAME = "typename"; // NON-NLS
    private static final String SIGNATURE_TAG_NAME = "signature"; // NON-NLS
    private static final String SIGNATURE_TYPE_ATTRIBUTE = "type"; // NON-NLS
    private static final String BYTES_TAG_NAME = "bytes"; // NON-NLS
    private static final String OFFSET_TAG_NAME = "offset"; // NON-NLS
    private static final String ENCODING = "UTF-8"; //NON-NLS // RJCTODO: Is this right?

    static {
        UserDefinedFileTypes.writePredefinedTypes();
    }

    /**
     * Writes the predefined file types definition file.
     */
    private static void writePredefinedTypes() {
        try {
            Path filePath = Paths.get(PlatformUtil.getUserConfigDirectory(), UserDefinedFileTypes.USER_DEFINED_TYPE_DEFINITIONS_FILE);
            String filePathString = filePath.toAbsolutePath().toString();
            List<FileType> fileTypes = new ArrayList<>(); // RJCTODO
            UserDefinedFileTypes.writeFileTypes(fileTypes, filePathString);
        } catch (UserDefinedFileTypesException ex) {
            // RJCTODO
        }
    }

    /**
     * Gets the user-defined file types.
     *
     * @return A list of file types, possibly empty
     */
    synchronized static List<FileType> getFileTypes() throws UserDefinedFileTypesException {
        Map<String, FileType> fileTypes = new HashMap<>();
        UserDefinedFileTypes.readFileTypes(fileTypes, UserDefinedFileTypes.AUTOPSY_TYPE_DEFINITIONS_FILE);
        UserDefinedFileTypes.readFileTypes(fileTypes, UserDefinedFileTypes.USER_DEFINED_TYPE_DEFINITIONS_FILE);
        return new ArrayList(fileTypes.values());
    }

    /**
     * Reads file type definitions from a file into a map of type names to file
     * types. File types already loaded into the map will be overwritten by file
     * types from the file if the type name is the same.
     *
     * @param fileTypes The map of type names to file type objects.
     * @param fileName The file from which to read the file type definitions.
     */
    private static void readFileTypes(Map<String, FileType> fileTypes, String fileName) throws UserDefinedFileTypesException {
        Path filePath = Paths.get(PlatformUtil.getUserConfigDirectory(), fileName);
        String filePathString = filePath.toAbsolutePath().toString();
        File file = new File(filePathString);
        if (file.exists() && file.canRead()) {
            for (FileType fileType : UserDefinedFileTypes.XMLReader.readFileTypes(filePathString)) {
                fileTypes.put(fileType.getTypeName(), fileType);
            }
        }
    }

    /**
     * Sets the user-defined file types.
     *
     * @param fileTypes The file type definitions.
     * @throws UserDefinedFileTypesException
     */
    synchronized static void setFileTypes(List<FileType> fileTypes) throws UserDefinedFileTypesException {
        Path filePath = Paths.get(PlatformUtil.getUserConfigDirectory(), UserDefinedFileTypes.USER_DEFINED_TYPE_DEFINITIONS_FILE);
        String filePathString = filePath.toAbsolutePath().toString();
        UserDefinedFileTypes.writeFileTypes(fileTypes, filePathString);
    }

    /**
     * Writes a set of file type definitions to a given file.
     *
     * @param fileTypes The file types.
     * @param filePaht The destination file.
     * @throws UserDefinedFileTypesException
     */
    private static void writeFileTypes(List<FileType> fileTypes, String filePath) throws UserDefinedFileTypesException {
        try {
            UserDefinedFileTypes.XMLWriter.writeFileTypes(fileTypes, filePath);
        } catch (ParserConfigurationException | IOException ex) {
            UserDefinedFileTypes.logger.log(Level.SEVERE, "Failed to write file types file", ex);
            throw new UserDefinedFileTypesException(ex.getLocalizedMessage()); // RJCTODO: Create a bundled message
        }
    }

    /**
     * Provides a mechanism for writing a set of file type definitions to an XML
     * file.
     */
    private static class XMLWriter {

        /**
         * Writes a set of file type definitions to an XML file.
         *
         * @param signatures A collection of file types.
         * @param filePath The path to the destination file.
         */
        private static void writeFileTypes(List<FileType> fileTypes, String filePath) throws ParserConfigurationException, IOException {
            Document doc = XMLUtil.createDoc();
            Element fileTypesElem = doc.createElement(UserDefinedFileTypes.FILE_TYPES_TAG_NAME);
            doc.appendChild(fileTypesElem);
            for (FileType fileType : fileTypes) {
                Element fileTypeElem = UserDefinedFileTypes.XMLWriter.createFileTypeElement(fileType, doc);
                fileTypesElem.appendChild(fileTypeElem);
            }
            if (!XMLUtil.saveDoc(HashDbManager.class, filePath, UserDefinedFileTypes.ENCODING, doc)) {
                throw new IOException("Could not save user defined file types");
            }
        }

        /**
         * Creates an XML representation of a file type.
         *
         * @param fileType The file type object.
         * @param doc The DOM document to use to create the XML.
         * @return An XML element.
         */
        private static Element createFileTypeElement(FileType fileType, Document doc) {
            /**
             * Create a file type element with an alert attribute.
             */
            Element fileTypeElem = doc.createElement(UserDefinedFileTypes.FILE_TYPE_TAG_NAME);
            fileTypeElem.setAttribute(UserDefinedFileTypes.ALERT_ATTRIBUTE, Boolean.toString(fileType.alertOnMatch()));

            /**
             * Add a type name child element.
             */
            Element typeNameElem = doc.createElement(UserDefinedFileTypes.TYPE_NAME_TAG_NAME);
            typeNameElem.setTextContent(fileType.getTypeName());
            fileTypeElem.appendChild(typeNameElem);

            /**
             * Add a signature child element with a type attribute.
             */
            Signature signature = fileType.getSignature();
            Element signatureElem = doc.createElement(UserDefinedFileTypes.SIGNATURE_TAG_NAME);
            signatureElem.setAttribute(UserDefinedFileTypes.SIGNATURE_TYPE_ATTRIBUTE, signature.getType().toString());
            fileTypeElem.appendChild(signatureElem);

            /**
             * Add a bytes child element to the signature element.
             */
            Element bytesElem = doc.createElement(UserDefinedFileTypes.BYTES_TAG_NAME);
            bytesElem.setTextContent(DatatypeConverter.printHexBinary(signature.getSignatureBytes()));
            signatureElem.appendChild(bytesElem);

            /**
             * Add an offset child element to the signature element.
             */
            Element offsetElem = doc.createElement(UserDefinedFileTypes.OFFSET_TAG_NAME);
            offsetElem.setTextContent(DatatypeConverter.printLong(signature.getOffset()));
            signatureElem.appendChild(offsetElem);

            return fileTypeElem;
        }
    }

    /**
     * Provides a mechanism for reading a set of file type definitions from an
     * XML file.
     */
    private static class XMLReader {

        /**
         * Reads a set of file type definitions from an XML file.
         *
         * @param filePath The path to the file.
         * @return A collection of file types.
         */
        private static List<FileType> readFileTypes(String filePath) throws UserDefinedFileTypesException {
            List<FileType> fileTypes = new ArrayList<>();
            Path xsdPath = Paths.get(PlatformUtil.getUserConfigDirectory(), UserDefinedFileTypes.FILE_TYPE_DEFINITIONS_SCHEMA_FILE);
            String xsdPathString = xsdPath.toAbsolutePath().toString();
            File file = new File(xsdPathString);
            if (file.exists() && file.canRead()) {
                Document doc = XMLUtil.loadDoc(UserDefinedFileTypes.XMLReader.class, filePath, xsdPathString);
                if (doc != null) {
                    Element fileTypesElem = doc.getDocumentElement();
                    if (fileTypesElem != null && fileTypesElem.getNodeName().equals(UserDefinedFileTypes.FILE_TYPES_TAG_NAME)) {
                        NodeList fileTypeElems = fileTypesElem.getElementsByTagName(UserDefinedFileTypes.FILE_TYPE_TAG_NAME);
                        for (int i = 0; i < fileTypeElems.getLength(); ++i) {
                            Element fileTypeElem = (Element) fileTypeElems.item(i);
                            FileType fileType = UserDefinedFileTypes.XMLReader.parseFileType(fileTypeElem);
                            fileTypes.add(fileType);
                        }
                    }
                }
            }
            return fileTypes;
        }

        /**
         * Parse a file type definition from a file type XML element.
         *
         * @param fileTypeElem The XML element.
         * @return A file type object.
         * @throws UserDefinedFileTypesException
         */
        private static FileType parseFileType(Element fileTypeElem) throws UserDefinedFileTypesException {
            /**
             * Get the alert attribute.
             */
            String alertAttribute = fileTypeElem.getAttribute(UserDefinedFileTypes.ALERT_ATTRIBUTE);
            boolean alert = Boolean.parseBoolean(alertAttribute);

            /**
             * Get the type name child element.
             */
            String typeName = UserDefinedFileTypes.getChildElementTextContent(fileTypeElem, UserDefinedFileTypes.TYPE_NAME_TAG_NAME);

            /**
             * Get the signature child element.
             */
            Element signatureElem;
            NodeList signatureElems = fileTypeElem.getElementsByTagName(UserDefinedFileTypes.SIGNATURE_TAG_NAME);
            signatureElem = (Element) signatureElems.item(0);

            /**
             * Get the signature type attribute from the signature child
             * element.
             */
            String sigTypeAttribute = signatureElem.getAttribute(typeName);
            Signature.Type signatureType = Signature.Type.valueOf(sigTypeAttribute);

            /**
             * Get the signature bytes.
             */
            String sigBytesString = UserDefinedFileTypes.getChildElementTextContent(signatureElem, UserDefinedFileTypes.TYPE_NAME_TAG_NAME);
            byte[] signatureBytes = DatatypeConverter.parseHexBinary(sigBytesString);

            /**
             * Get the offset.
             */
            String offsetString = UserDefinedFileTypes.getChildElementTextContent(signatureElem, UserDefinedFileTypes.OFFSET_TAG_NAME);
            long offset = DatatypeConverter.parseLong(offsetString);

            /**
             * Put it all together.
             */
            Signature signature = new Signature(signatureBytes, offset, signatureType);
            return new FileType(typeName, signature, alert);
        }
    }

    /**
     * Gets the text content of a single child element.
     *
     * @param elem The parent element.
     * @param tagName The tag name of the child element.
     * @return The text content.
     * @throws UserDefinedFileTypesException
     */
    private static String getChildElementTextContent(Element elem, String tagName) throws UserDefinedFileTypesException {
        /**
         * The checks here are essentially "sanity checks" since the XML was
         * already validated using the XSD file.
         */
        String textContent = "";
        NodeList childElems = elem.getElementsByTagName(tagName);
        if (childElems.getLength() > 0) {
            Element childElem = (Element) childElems.item(0);
            textContent = childElem.getTextContent();
            if (textContent.isEmpty()) {
                UserDefinedFileTypes.logger.log(Level.SEVERE, "File type {0} child element missing text content", tagName); // NON-NLS                
            }
        } else {
            UserDefinedFileTypes.logger.log(Level.SEVERE, "File type element missing {0} child element", tagName); // NON-NLS
        }
        if (textContent.isEmpty()) {
            throw new UserDefinedFileTypes.UserDefinedFileTypesException("Error reading file type definitions file, see log for details"); // RJCTODO: Bundle
        }
        return textContent;
    }

    /**
     * An exception type to conflate more implementation-details-specific
     * exception types (which are logged by this class) into a single generic
     * type.
     */
    static class UserDefinedFileTypesException extends Exception {

        UserDefinedFileTypesException(String message) {
            super(message);
        }
    }

}
