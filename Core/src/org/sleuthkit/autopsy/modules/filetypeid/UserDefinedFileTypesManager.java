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
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
 * Manages user-defined file types characterized by type names (e.g., MIME type)
 * and signatures.
 */
final class UserDefinedFileTypesManager {

    private static final Logger logger = Logger.getLogger(UserDefinedFileTypesManager.class.getName());
    private static final String FILE_TYPE_DEFINITIONS_SCHEMA_FILE = "FileTypeDefinitions.xsd"; //NON-NLS
    private static final String USER_DEFINED_TYPE_DEFINITIONS_FILE = "UserFileTypeDefinitions.xml"; //NON-NLS
    private static final String FILE_TYPES_TAG_NAME = "filetypes"; //NON-NLS
    private static final String FILE_TYPE_TAG_NAME = "filetype"; //NON-NLS
    private static final String ALERT_ATTRIBUTE = "alert"; //NON-NLS
    private static final String TYPE_NAME_TAG_NAME = "typename"; //NON-NLS
    private static final String SIGNATURE_TAG_NAME = "signature"; //NON-NLS
    private static final String SIGNATURE_TYPE_ATTRIBUTE = "type"; //NON-NLS
    private static final String BYTES_TAG_NAME = "bytes"; //NON-NLS
    private static final String OFFSET_TAG_NAME = "offset"; //NON-NLS
    private static final String ENCODING_FOR_XML_FILE = "UTF-8"; //NON-NLS
    private static final String ASCII_ENCODING = "US-ASCII"; //NON-NLS
    private static UserDefinedFileTypesManager instance;

    /**
     * User-defined file types to be persisted to the user-defined file type
     * definitions file are stored in this mapping of file type names to file
     * types. Access to this map is guarded by the intrinsic lock of the
     * user-defined file types manager for thread-safety.
     */
    private final Map<String, FileType> userDefinedFileTypes = new HashMap<>();

    /**
     * The combined set of user-defined file types and file types predefined by
     * Autopsy are stored in this mapping of file type names to file types. This
     * is the current working set of file types. Access to this map is guarded
     * by the intrinsic lock of the user-defined file types manager for
     * thread-safety.
     */
    private final Map<String, FileType> fileTypes = new HashMap<>();

    /**
     * Gets the user-defined file types manager.
     *
     * @return A singleton user-defined file types manager.
     */
    synchronized static UserDefinedFileTypesManager getInstance() {
        if (UserDefinedFileTypesManager.instance == null) {
            UserDefinedFileTypesManager.instance = new UserDefinedFileTypesManager();
        }
        return UserDefinedFileTypesManager.instance;
    }

    /**
     * Creates a manager of user-defined file types characterized by type names
     * (e.g., MIME type) and signatures.
     */
    private UserDefinedFileTypesManager() {
        loadPredefinedFileTypes();
        loadUserDefinedFileTypes();
    }

    /**
     * Adds standard predefined file types to the in-memory mapping of file type
     * names to predefined file types.
     */
    private void loadPredefinedFileTypes() {
        // RJCTODO: Remove test type
        /**
         * Create a file type that should match $MBR in Small2 image.
         */
        this.fileTypes.put("predefinedRAW", new FileType("predefinedRAW", new Signature(new byte[]{(byte) 0x66, (byte) 0x73, (byte) 0x00}, 8L, FileType.Signature.Type.RAW), true));

        /**
         * Create a file type that should match test.txt in the Small2 image.
         */
        // RJCTODO: Remove test type
        try {
            this.fileTypes.put("predefinedASCII", new FileType("predefinedASCII", new Signature("hello".getBytes(UserDefinedFileTypesManager.ASCII_ENCODING), 0L, FileType.Signature.Type.ASCII), true));
        } catch (UnsupportedEncodingException ex) {
            UserDefinedFileTypesManager.logger.log(Level.SEVERE, "Unable to create 'predefinedASCII' predefined file type definition", ex); //NON-NLS
        }

        try {
            // RJCTODO: Remove this code from TikaFileTypeDetector.java
//        try {
//            byte buf[];
//            int len = abstractFile.read(buffer, 0, BUFFER_SIZE);
//            if (len < BUFFER_SIZE) {
//                buf = new byte[len];
//                System.arraycopy(buffer, 0, buf, 0, len);
//            } else {
//                buf = buffer;
//            }
//            
//            // the xml detection in Tika tries to parse the entire file and throws exceptions
//            // for files that are not valid XML
//            try {
//                String tagHeader = new String(buf, 0, 5);
//                if (tagHeader.equals("<?xml")) { //NON-NLS    
//                    return "text/xml"; //NON-NLS
//                }
//            }
//            catch (IndexOutOfBoundsException e) {
//                // do nothing
//            }
            this.fileTypes.put("text/xml", new FileType("text/xml", new Signature("<?xml".getBytes(UserDefinedFileTypesManager.ASCII_ENCODING), 0L, FileType.Signature.Type.ASCII), false));
        } catch (UnsupportedEncodingException ex) {
            /**
             * Using an all-or-none strategy.
             */
            UserDefinedFileTypesManager.logger.log(Level.SEVERE, "Unable to create predefined file type definitions", ex); //NON-NLS
            this.fileTypes.clear();
        }
    }

    /**
     * Reads user-defined file types into an in-memory mapping of file type
     * names to file types.
     */
    private void loadUserDefinedFileTypes() {
        try {
            String filePath = getFileTypeDefinitionsFilePath(UserDefinedFileTypesManager.USER_DEFINED_TYPE_DEFINITIONS_FILE);
            File file = new File(filePath);
            if (file.exists() && file.canRead()) {
                for (FileType fileType : XMLReader.readFileTypes(filePath)) {
                    this.userDefinedFileTypes.put(fileType.getTypeName(), fileType);
                    this.fileTypes.put(fileType.getTypeName(), fileType);
                }
            }

        } catch (UserDefinedFileTypesManager.InvalidXMLException ex) {
            /**
             * Using an all-or-none strategy.
             */
            UserDefinedFileTypesManager.logger.log(Level.SEVERE, "Unable to load user-defined types", ex); //NON-NLS
            this.fileTypes.clear();
            this.userDefinedFileTypes.clear();
        }
    }

    /**
     * Gets the user-defined file types.
     *
     * @return A mapping of file type names to file types, possibly empty.
     */
    synchronized List<FileType> getFileTypes() {
        /**
         * It is safe to return references to the internal file type objects
         * because they are immutable.
         */
        return new ArrayList<>(this.fileTypes.values());
    }

    /**
     * Adds a new user-defined file type, overwriting any existing file type
     * with the same type name.
     *
     * @param fileType The file type to add.
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    synchronized void addFileType(FileType fileType) throws UserDefinedFileTypesException {
        this.addFileTypes(Collections.singletonList(fileType));
    }

    /**
     * Adds a collection of new user-defined file types, overwriting any
     * existing file types with the same type names.
     *
     * @param newFileTypes The file types to add.
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    synchronized void addFileTypes(Collection<FileType> newFileTypes) throws UserDefinedFileTypesException {
        /**
         * It is safe to hold references to client-constructed file type objects
         * because they are immutable.
         */
        for (FileType fileType : newFileTypes) {
            this.userDefinedFileTypes.put(fileType.getTypeName(), fileType);
            this.fileTypes.put(fileType.getTypeName(), fileType);
        }
        this.saveUserDefinedTypes();
    }

    /**
     * Deletes a user-defined file type.
     *
     * @param fileType The file type to delete.
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    synchronized void deleteFileType(FileType fileType) throws UserDefinedFileTypesException {
        this.deleteFileTypes(Collections.singletonList(fileType));
    }

    /**
     * Deletes a set of user-defined file types.
     *
     * @param deletedFileTypes The file types to delete.
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    synchronized void deleteFileTypes(Collection<FileType> deletedFileTypes) throws UserDefinedFileTypesException {
        for (FileType fileType : deletedFileTypes) {
            this.userDefinedFileTypes.remove(fileType.getTypeName(), fileType);
            this.fileTypes.remove(fileType.getTypeName(), fileType);
        }
        this.saveUserDefinedTypes();
    }

    /**
     * Persists the user-defined file type definitions.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    private void saveUserDefinedTypes() throws UserDefinedFileTypesException {
        String filePath = UserDefinedFileTypesManager.getFileTypeDefinitionsFilePath(UserDefinedFileTypesManager.USER_DEFINED_TYPE_DEFINITIONS_FILE);
        UserDefinedFileTypesManager.writeFileTypes(this.userDefinedFileTypes.values(), filePath);
    }

    /**
     * Writes a set of file type definitions to a given file.
     *
     * @param fileTypes The file types.
     * @param filePath The destination file.
     * @throws UserDefinedFileTypesException
     */
    private static void writeFileTypes(Collection<FileType> fileTypes, String filePath) throws UserDefinedFileTypesException {
        try {
            UserDefinedFileTypesManager.XMLWriter.writeFileTypes(fileTypes, filePath);
        } catch (ParserConfigurationException | IOException ex) {
            UserDefinedFileTypesManager.logger.log(Level.SEVERE, "Failed to write file types file", ex);
            throw new UserDefinedFileTypesManager.UserDefinedFileTypesException(ex.getLocalizedMessage()); // RJCTODO: Create a bundled message
        }
    }

    /**
     * Gets the absolute path of a file type definitions file.
     *
     * @param fileName The name of the file.
     * @return The absolute path to the file.
     */
    private static String getFileTypeDefinitionsFilePath(String fileName) {
        Path filePath = Paths.get(PlatformUtil.getUserConfigDirectory(), fileName);
        return filePath.toAbsolutePath().toString();
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
        private static void writeFileTypes(Collection<FileType> fileTypes, String filePath) throws ParserConfigurationException, IOException {
            Document doc = XMLUtil.createDoc();
            Element fileTypesElem = doc.createElement(UserDefinedFileTypesManager.FILE_TYPES_TAG_NAME);
            doc.appendChild(fileTypesElem);
            for (FileType fileType : fileTypes) {
                Element fileTypeElem = UserDefinedFileTypesManager.XMLWriter.createFileTypeElement(fileType, doc);
                fileTypesElem.appendChild(fileTypeElem);
            }
            if (!XMLUtil.saveDoc(HashDbManager.class, filePath, UserDefinedFileTypesManager.ENCODING_FOR_XML_FILE, doc)) {
                // RJCTODO: If time permits add XMLUtil that properly throws and deprecate this one
                throw new IOException("Error saving user defined file types, see log for details"); //NON-NLS
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
            Element fileTypeElem = doc.createElement(UserDefinedFileTypesManager.FILE_TYPE_TAG_NAME);
            fileTypeElem.setAttribute(UserDefinedFileTypesManager.ALERT_ATTRIBUTE, Boolean.toString(fileType.alertOnMatch()));

            /**
             * Add a type name child element.
             */
            Element typeNameElem = doc.createElement(UserDefinedFileTypesManager.TYPE_NAME_TAG_NAME);
            typeNameElem.setTextContent(fileType.getTypeName());
            fileTypeElem.appendChild(typeNameElem);

            /**
             * Add a signature child element with a type attribute.
             */
            Signature signature = fileType.getSignature();
            Element signatureElem = doc.createElement(UserDefinedFileTypesManager.SIGNATURE_TAG_NAME);
            signatureElem.setAttribute(UserDefinedFileTypesManager.SIGNATURE_TYPE_ATTRIBUTE, signature.getType().toString());
            fileTypeElem.appendChild(signatureElem);

            /**
             * Add a bytes child element to the signature element.
             */
            Element bytesElem = doc.createElement(UserDefinedFileTypesManager.BYTES_TAG_NAME);
            bytesElem.setTextContent(DatatypeConverter.printHexBinary(signature.getSignatureBytes()));
            signatureElem.appendChild(bytesElem);

            /**
             * Add an offset child element to the signature element.
             */
            Element offsetElem = doc.createElement(UserDefinedFileTypesManager.OFFSET_TAG_NAME);
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
        private static List<FileType> readFileTypes(String filePath) throws InvalidXMLException {
            List<FileType> fileTypes = new ArrayList<>();
            Path xsdPath = Paths.get(PlatformUtil.getUserConfigDirectory(), UserDefinedFileTypesManager.FILE_TYPE_DEFINITIONS_SCHEMA_FILE);
            String xsdPathString = xsdPath.toAbsolutePath().toString();
            File file = new File(xsdPathString);
            if (file.exists() && file.canRead()) {
                Document doc = XMLUtil.loadDoc(UserDefinedFileTypesManager.XMLReader.class, filePath, xsdPathString);
                if (doc != null) {
                    Element fileTypesElem = doc.getDocumentElement();
                    if (fileTypesElem != null && fileTypesElem.getNodeName().equals(UserDefinedFileTypesManager.FILE_TYPES_TAG_NAME)) {
                        NodeList fileTypeElems = fileTypesElem.getElementsByTagName(UserDefinedFileTypesManager.FILE_TYPE_TAG_NAME);
                        for (int i = 0; i < fileTypeElems.getLength(); ++i) {
                            Element fileTypeElem = (Element) fileTypeElems.item(i);
                            FileType fileType = UserDefinedFileTypesManager.XMLReader.parseFileType(fileTypeElem);
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
        private static FileType parseFileType(Element fileTypeElem) throws InvalidXMLException {
            /**
             * Get the alert attribute.
             */
            String alertAttribute = fileTypeElem.getAttribute(UserDefinedFileTypesManager.ALERT_ATTRIBUTE);
            boolean alert = Boolean.parseBoolean(alertAttribute);

            /**
             * Get the type name child element.
             */
            String typeName = UserDefinedFileTypesManager.getChildElementTextContent(fileTypeElem, UserDefinedFileTypesManager.TYPE_NAME_TAG_NAME);

            /**
             * Get the signature child element.
             */
            Element signatureElem;
            NodeList signatureElems = fileTypeElem.getElementsByTagName(UserDefinedFileTypesManager.SIGNATURE_TAG_NAME);
            signatureElem = (Element) signatureElems.item(0);

            /**
             * Get the signature (interpretation) type attribute from the
             * signature child element.
             */
            String sigTypeAttribute = signatureElem.getAttribute(UserDefinedFileTypesManager.SIGNATURE_TYPE_ATTRIBUTE);
            Signature.Type signatureType = Signature.Type.valueOf(sigTypeAttribute);

            /**
             * Get the signature bytes.
             */
            String sigBytesString = UserDefinedFileTypesManager.getChildElementTextContent(signatureElem, UserDefinedFileTypesManager.TYPE_NAME_TAG_NAME);
            byte[] signatureBytes = DatatypeConverter.parseHexBinary(sigBytesString);

            /**
             * Get the offset.
             */
            String offsetString = UserDefinedFileTypesManager.getChildElementTextContent(signatureElem, UserDefinedFileTypesManager.OFFSET_TAG_NAME);
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
    private static String getChildElementTextContent(Element elem, String tagName) throws InvalidXMLException {
        /**
         * The checks here are essentially "sanity checks" since the XML was
         * already validated using the XSD file.
         */
        NodeList childElems = elem.getElementsByTagName(tagName);
        if (childElems.getLength() > 0) {
            Element childElem = (Element) childElems.item(0);
            String textContent = childElem.getTextContent();
            if (!textContent.isEmpty()) {
                return textContent;
            } else {
                throw new InvalidXMLException("File type " + tagName + " child element missing text content"); //NON-NLS
            }
        } else {
            throw new InvalidXMLException("File type element missing " + tagName + " child element"); //NON-NLS
        }
    }

    /**
     * Used for exceptions when parsing user-defined types XML elements and
     * attributes.
     */
    private static class InvalidXMLException extends Exception {

        InvalidXMLException(String message) {
            super(message);
        }
    }

    /**
     * Used to translate more implementation-details-specific exceptions (which
     * are logged by this class) into more generic exceptions.
     */
    static class UserDefinedFileTypesException extends Exception {

        UserDefinedFileTypesException(String message) {
            super(message);
        }
    }

}
