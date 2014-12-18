/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this schemaFile except in compliance with the License.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.bind.DatatypeConverter;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.modules.filetypeid.FileType.Signature;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager;
import org.xml.sax.SAXException;

/**
 * Manages user-defined schemaFile types characterized by MIME type, signature,
 * and optional membership in an interesting files set.
 */
final class UserDefinedFileTypesManager {

    private static final Logger logger = Logger.getLogger(UserDefinedFileTypesManager.class.getName());
    private static final String FILE_TYPE_DEFINITIONS_SCHEMA_FILE = "FileTypeDefinitions.xsd"; //NON-NLS
    private static final String USER_DEFINED_TYPE_DEFINITIONS_FILE = "UserFileTypeDefinitions.xml"; //NON-NLS
    private static final String FILE_TYPES_TAG_NAME = "filetypes"; //NON-NLS
    private static final String FILE_TYPE_TAG_NAME = "filetype"; //NON-NLS
    private static final String MIME_TYPE_TAG_NAME = "mimetype"; //NON-NLS
    private static final String SIGNATURE_TAG_NAME = "signature"; //NON-NLS
    private static final String SIGNATURE_TYPE_ATTRIBUTE = "type"; //NON-NLS
    private static final String BYTES_TAG_NAME = "bytes"; //NON-NLS
    private static final String OFFSET_TAG_NAME = "offset"; //NON-NLS
    private static final String INTERESTING_FILES_SET_TAG_NAME = "filesset"; //NON-NLS
    private static final String ALERT_ATTRIBUTE = "alert"; //NON-NLS
    private static final String ENCODING_FOR_XML_FILE = "UTF-8"; //NON-NLS
    private static final String ASCII_ENCODING = "US-ASCII"; //NON-NLS
    private static UserDefinedFileTypesManager instance;

    /**
     * Predefined schemaFile types are stored in this mapping of MIME types to
     * schemaFile types. Access to this map is guarded by the intrinsic lock of
     * the user-defined schemaFile types manager for thread-safety.
     */
    private final Map<String, FileType> predefinedFileTypes = new HashMap<>();

    /**
     * User-defined schemaFile types to be persisted to the user-defined
     * schemaFile type definitions schemaFile are stored in this mapping of
     * schemaFile type names to schemaFile types. Access to this map is guarded
     * by the intrinsic lock of the user-defined schemaFile types manager for
     * thread-safety.
     */
    private final Map<String, FileType> userDefinedFileTypes = new HashMap<>();

    /**
     * The combined set of user-defined schemaFile types and schemaFile types
     * predefined by Autopsy are stored in this mapping of MIME types to
     * schemaFile types. This is the current working set of schemaFile types.
     * Access to this map is guarded by the intrinsic lock of the user-defined
     * schemaFile types manager for thread-safety.
     */
    private final Map<String, FileType> fileTypes = new HashMap<>();

    /**
     * Gets the manager of user-defined schemaFile types characterized by MIME
     * type, signature, and optional membership in an interesting files set.
     *
     * @return The user-defined schemaFile types manager singleton.
     */
    synchronized static UserDefinedFileTypesManager getInstance() {
        if (UserDefinedFileTypesManager.instance == null) {
            UserDefinedFileTypesManager.instance = new UserDefinedFileTypesManager();
        }
        return UserDefinedFileTypesManager.instance;
    }

    /**
     * Creates a manager of user-defined schemaFile types characterized by MIME
     * type, signature, and optional membership in an interesting files set.
     */
    private UserDefinedFileTypesManager() {
        /**
         * Load the predefined types first so that they can be overwritten by
         * any user-defined types with the same names.
         */
        loadPredefinedFileTypes();
        loadUserDefinedFileTypes();
    }

    /**
     * Adds the predefined schemaFile types to the in-memory mappings of MIME
     * types to schemaFile types.
     */
    private void loadPredefinedFileTypes() {
        // RJCTODO: Remove test schemaFile type.
        /**
         * Create a file type that should match $MBR in Small2 image.
         */
        FileType fileType = new FileType("predefinedRAW", new Signature(new byte[]{(byte) 0x66, (byte) 0x73, (byte) 0x00}, 8L, FileType.Signature.Type.RAW), "predefinedRAW", true);
        this.addPredefinedFileType(fileType);

        /**
         * Create a file type that should match test.txt in the Small2 image.
         */
        // RJCTODO: Remove test schemaFile type.
        try {
            fileType = new FileType("predefinedASCII", new Signature("hello".getBytes(UserDefinedFileTypesManager.ASCII_ENCODING), 0L, FileType.Signature.Type.ASCII), "predefinedASCII", true);
            this.addPredefinedFileType(fileType);
        } catch (UnsupportedEncodingException ex) {
            UserDefinedFileTypesManager.logger.log(Level.SEVERE, "Unable to create 'predefinedASCII' predefined file type definition", ex); //NON-NLS
        }

        try {
            // RJCTODO: Remove this code from TikaFileTypeDetector.java.
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
//            // the xml detection in Tika tries to parse the entire schemaFile and throws exceptions
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
            fileType = new FileType("text/xml", new Signature("<?xml".getBytes(UserDefinedFileTypesManager.ASCII_ENCODING), 0L, FileType.Signature.Type.ASCII), "", false);
            this.addPredefinedFileType(fileType);
        } catch (UnsupportedEncodingException ex) {
            /**
             * Using an all-or-none policy.
             */
            UserDefinedFileTypesManager.logger.log(Level.SEVERE, "Unable to create predefined file type definitions", ex); //NON-NLS
            this.fileTypes.clear();
        }
    }

    /**
     * Adds a schemaFile type to the the predefined schemaFile types and
     * combined schemaFile types maps.
     *
     * @param fileType The schemaFile type to add.
     */
    private void addPredefinedFileType(FileType fileType) {
        this.predefinedFileTypes.put(fileType.getMimeType(), fileType);
        this.fileTypes.put(fileType.getMimeType(), fileType);
    }

    /**
     * Adds the user-defined schemaFile types to the in-memory mappings of MIME
     * types to schemaFile types.
     */
    private void loadUserDefinedFileTypes() {
        try {
            String filePath = getFileTypeDefinitionsFilePath(UserDefinedFileTypesManager.USER_DEFINED_TYPE_DEFINITIONS_FILE);
            File file = new File(filePath);
            if (file.exists() && file.canRead()) {
                for (FileType fileType : XMLReader.readFileTypes(filePath)) {
                    this.addUserDefinedFileType(fileType);
                }
            }

        } catch (IOException | ParserConfigurationException | SAXException ex) {
            /**
             * Using an all-or-none policy.
             */
            UserDefinedFileTypesManager.logger.log(Level.SEVERE, "Unable to load user-defined types", ex); //NON-NLS
            this.fileTypes.clear();
            this.userDefinedFileTypes.clear();
        } 
    }

    /**
     * Adds a schemaFile type to the the user-defined schemaFile types and
     * combined schemaFile types maps.
     *
     * @param fileType The schemaFile type to add.
     */
    private void addUserDefinedFileType(FileType fileType) {
        this.userDefinedFileTypes.put(fileType.getMimeType(), fileType);
        this.fileTypes.put(fileType.getMimeType(), fileType);
    }

    /**
     * Gets both the predefined and the user-defined schemaFile types.
     *
     * @return A mapping of schemaFile type names to schemaFile types, possibly
     * empty.
     */
    synchronized Map<String, FileType> getFileTypes() {
        /**
         * It is safe to return references to the internal file type objects
         * because they are immutable.
         */
        return new HashMap<>(this.fileTypes);
    }

    /**
     * Gets the user-defined schemaFile types.
     *
     * @return A mapping of schemaFile type names to schemaFile types, possibly
     * empty.
     */
    synchronized Map<String, FileType> getUserDefinedFileTypes() {
        /**
         * It is safe to return references to the internal file type objects
         * because they are immutable.
         */
        return new HashMap<>(this.userDefinedFileTypes);
    }

    /**
     * Sets the user-defined schemaFile types.
     *
     * @param newFileTypes A mapping of schemaFile type names to user-defined
     * schemaFile types.
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    synchronized void setUserDefinedFileTypes(Map<String, FileType> newFileTypes) throws UserDefinedFileTypesManager.UserDefinedFileTypesException {
        try {
            /**
             * Persist the user-defined file type definitions.
             */
            String filePath = UserDefinedFileTypesManager.getFileTypeDefinitionsFilePath(UserDefinedFileTypesManager.USER_DEFINED_TYPE_DEFINITIONS_FILE);
            UserDefinedFileTypesManager.XMLWriter.writeFileTypes(newFileTypes.values(), filePath);

        } catch (ParserConfigurationException | IOException ex) {
            UserDefinedFileTypesManager.logger.log(Level.SEVERE, "Failed to write file types file", ex);
            throw new UserDefinedFileTypesManager.UserDefinedFileTypesException(ex.getLocalizedMessage()); // RJCTODO: Create a bundled message
        }

        /**
         * Clear and reinitialize the user-defined file type map. It is safe to
         * hold references to file type objects obtained for the caller because
         * they are immutable.
         */
        this.userDefinedFileTypes.clear();
        this.userDefinedFileTypes.putAll(newFileTypes);

        /**
         * Clear and reinitialize the combined file type map, loading the
         * predefined types first so that they can be overwritten by any
         * user-defined types with the same names.
         */
        this.fileTypes.clear();
        this.fileTypes.putAll(this.predefinedFileTypes);
        this.fileTypes.putAll(this.userDefinedFileTypes);
    }

    /**
     * Gets the absolute path of a schemaFile type definitions schemaFile.
     *
     * @param fileName The name of the schemaFile.
     * @return The absolute path to the schemaFile.
     */
    private static String getFileTypeDefinitionsFilePath(String fileName) {
        Path filePath = Paths.get(PlatformUtil.getUserConfigDirectory(), fileName);
        return filePath.toAbsolutePath().toString();
    }

    /**
     * Provides a mechanism for writing a set of schemaFile type definitions to
     * an XML schemaFile.
     */
    private static class XMLWriter {

        /**
         * Writes a set of schemaFile type definitions to an XML schemaFile.
         *
         * @param signatures A collection of schemaFile types.
         * @param filePath The path to the destination schemaFile.
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
         * Creates an XML representation of a schemaFile type.
         *
         * @param fileType The schemaFile type object.
         * @param doc The DOM document to use to create the XML.
         * @return An XML element.
         */
        private static Element createFileTypeElement(FileType fileType, Document doc) {
            /**
             * Create a file type element.
             */
            Element fileTypeElem = doc.createElement(UserDefinedFileTypesManager.FILE_TYPE_TAG_NAME);

            /**
             * Add a MIME type name child element.
             */
            Element typeNameElem = doc.createElement(UserDefinedFileTypesManager.MIME_TYPE_TAG_NAME);
            typeNameElem.setTextContent(fileType.getMimeType());
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

            /**
             * Add a files set child element with an alert attribute.
             */
            Element filesSetElem = doc.createElement(UserDefinedFileTypesManager.INTERESTING_FILES_SET_TAG_NAME);
            filesSetElem.setTextContent(fileType.getFilesSetName());
            filesSetElem.setAttribute(UserDefinedFileTypesManager.ALERT_ATTRIBUTE, Boolean.toString(fileType.alertOnMatch()));
            fileTypeElem.appendChild(filesSetElem);

            return fileTypeElem;
        }
    }

    /**
     * Provides a mechanism for reading a set of schemaFile type definitions
     * from an XML schemaFile.
     */
    private static class XMLReader {

        /**
         * Reads a set of schemaFile type definitions from an XML schemaFile.
         *
         * @param filePath The path to the XML schemaFile.
         * @return A collection of schemaFile types read from the XML
         * schemaFile.
         */
        private static List<FileType> readFileTypes(String filePath) throws IOException, ParserConfigurationException, SAXException {
            List<FileType> fileTypes = new ArrayList<>();
            Path schemaFilePath = Paths.get(PlatformUtil.getUserConfigDirectory(), UserDefinedFileTypesManager.FILE_TYPE_DEFINITIONS_SCHEMA_FILE);
            Document doc = XMLUtil.loadAndValidateDoc(UserDefinedFileTypesManager.XMLReader.class, filePath, schemaFilePath.toAbsolutePath().toString());
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
            return fileTypes;
        }

        /**
         * Parse a schemaFile type definition from a schemaFile type XML
         * element.
         *
         * @param fileTypeElem The XML element.
         * @return A schemaFile type object.
         * @throws
         */
        private static FileType parseFileType(Element fileTypeElem) throws IllegalArgumentException, NumberFormatException {
            /**
             * Get the mime type child element.
             */
            String mimeType = UserDefinedFileTypesManager.getChildElementTextContent(fileTypeElem, UserDefinedFileTypesManager.MIME_TYPE_TAG_NAME);

            /**
             * Get the signature child element.
             */
            NodeList signatureElems = fileTypeElem.getElementsByTagName(UserDefinedFileTypesManager.SIGNATURE_TAG_NAME);
            Element signatureElem = (Element) signatureElems.item(0);

            /**
             * Get the signature (interpretation) type attribute from the
             * signature child element.
             */
            String sigTypeAttribute = signatureElem.getAttribute(UserDefinedFileTypesManager.SIGNATURE_TYPE_ATTRIBUTE);
            Signature.Type signatureType = Signature.Type.valueOf(sigTypeAttribute);

            /**
             * Get the signature bytes.
             */
            String sigBytesString = UserDefinedFileTypesManager.getChildElementTextContent(signatureElem, UserDefinedFileTypesManager.BYTES_TAG_NAME);
            byte[] signatureBytes = DatatypeConverter.parseHexBinary(sigBytesString);

            /**
             * Get the offset.
             */
            String offsetString = UserDefinedFileTypesManager.getChildElementTextContent(signatureElem, UserDefinedFileTypesManager.OFFSET_TAG_NAME);
            long offset = DatatypeConverter.parseLong(offsetString);

            /**
             * Get the interesting files set element.
             */
            NodeList filesSetElems = fileTypeElem.getElementsByTagName(UserDefinedFileTypesManager.INTERESTING_FILES_SET_TAG_NAME);
            Element filesSetElem = (Element) filesSetElems.item(0);
            String filesSetName = filesSetElem.getTextContent();

            /**
             * Get the alert attribute from the interesting files set element.
             */
            String alertAttribute = filesSetElem.getAttribute(UserDefinedFileTypesManager.ALERT_ATTRIBUTE);
            boolean alert = Boolean.parseBoolean(alertAttribute);

            /**
             * Put it all together.
             */
            Signature signature = new Signature(signatureBytes, offset, signatureType);
            return new FileType(mimeType, signature, filesSetName, alert);
        }
    }

    /**
     * Gets the text content of a single child element. Assumes the elements
     * have already been validated.
     *
     * @param elem The parent element.
     * @param tagName The tag name of the child element.
     * @return The text content.
     */
    private static String getChildElementTextContent(Element elem, String tagName) {
        NodeList childElems = elem.getElementsByTagName(tagName);
        Element childElem = (Element) childElems.item(0);
        return childElem.getTextContent();
    }

    /**
     * Used for throwing exceptions when parsing user-defined types XML elements
     * and attributes.
     */
    private static class InvalidXMLException extends Exception {

        InvalidXMLException(String message) {
            super(message);
        }
    }

    /**
     * Used to translate more implementation-details-specific exceptions (which
     * are logged by this class) into more generic exceptions for propagation to
     * clients of the user-defined schemaFile types manager.
     */
    static class UserDefinedFileTypesException extends Exception {

        UserDefinedFileTypesException(String message) {
            super(message);
        }
    }

}
