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
import java.io.FileNotFoundException;
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
import javax.xml.transform.TransformerException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.modules.filetypeid.FileType.Signature;
import org.xml.sax.SAXException;

/**
 * Manages user-defined file types characterized by MIME type, signature, and
 * optional membership in an interesting files set.
 * <p>
 * Note that this class exposes a very simple get/set API that operates on the
 * user-defined file types as a complete set - there is no concept of adding,
 * editing or deleting file types singly. This works because this class is not
 * exposed outside of this ingest module package and is ONLY used in a very
 * specific paradigm where there is a single modal writer of file types in the
 * form of a global settings panel that disables itself when ingest is running
 * so that multiple readers in the form of file ingest modules get a consistent
 * set of file type definitions. Moreover, there is no enforcement of compliance
 * with this paradigm by this class - it merely participates in the scheme.
 * <p>
 * Thread-safe.
 */
final class UserDefinedFileTypesManager {

    private static final Logger logger = Logger.getLogger(UserDefinedFileTypesManager.class.getName());
    private static final String FILE_TYPE_DEFINITIONS_SCHEMA_FILE = "FileTypeDefinitions.xsd"; //NON-NLS
    private static final String USER_DEFINED_TYPE_DEFINITIONS_FILE = "UserFileTypeDefinitions.xml"; //NON-NLS
    private static final String FILE_TYPES_TAG_NAME = "FileTypes"; //NON-NLS
    private static final String FILE_TYPE_TAG_NAME = "FileType"; //NON-NLS
    private static final String MIME_TYPE_TAG_NAME = "MimeType"; //NON-NLS
    private static final String SIGNATURE_TAG_NAME = "Signature"; //NON-NLS
    private static final String SIGNATURE_TYPE_ATTRIBUTE = "type"; //NON-NLS
    private static final String BYTES_TAG_NAME = "Bytes"; //NON-NLS
    private static final String OFFSET_TAG_NAME = "Offset"; //NON-NLS
    private static final String INTERESTING_FILES_SET_TAG_NAME = "InterestingFileSset"; //NON-NLS
    private static final String ALERT_ATTRIBUTE = "alert"; //NON-NLS
    private static final String ENCODING_FOR_XML_FILE = "UTF-8"; //NON-NLS
    private static final String ASCII_ENCODING = "US-ASCII"; //NON-NLS
    private static UserDefinedFileTypesManager instance;

    /**
     * Predefined file types are stored in this mapping of MIME types to file
     * types. Access to this map is guarded by the intrinsic lock of the
     * user-defined file types manager for thread-safety.
     */
    private final Map<String, FileType> predefinedFileTypes = new HashMap<>();

    /**
     * File types to be persisted to the user-defined file type definitions file
     * are stored in this mapping of MIME types to file types. Access to this
     * map is guarded by the intrinsic lock of the user-defined file types
     * manager for thread-safety.
     */
    private final Map<String, FileType> userDefinedFileTypes = new HashMap<>();

    /**
     * The combined set of user-defined file types and file types predefined by
     * Autopsy are stored in this mapping of MIME types to file types. This is
     * the current working set of file types. Access to this map is guarded by
     * the intrinsic lock of the user-defined file types manager for
     * thread-safety.
     */
    private final Map<String, FileType> fileTypes = new HashMap<>();

    /**
     * Gets the manager of user-defined file types characterized by MIME type,
     * signature, and optional membership in an interesting files set.
     *
     * @return The user-defined file types manager singleton.
     */
    synchronized static UserDefinedFileTypesManager getInstance() {
        if (UserDefinedFileTypesManager.instance == null) {
            UserDefinedFileTypesManager.instance = new UserDefinedFileTypesManager();
        }
        return UserDefinedFileTypesManager.instance;
    }

    /**
     * Creates a manager of user-defined file types characterized by MIME type,
     * signature, and optional membership in an interesting files set.
     */
    private UserDefinedFileTypesManager() {
    }

    /**
     * Adds the predefined file types to the in-memory mappings of MIME types to
     * file types.
     */
    private void loadPredefinedFileTypes() {
        // RJCTODO: Remove test file type.
        /**
         * Create a file type that should match $MBR in Small2 image.
         */
        FileType fileType = new FileType("predefinedRAW", new Signature(new byte[]{(byte) 0x66, (byte) 0x73, (byte) 0x00}, 8L, FileType.Signature.Type.RAW), "Suspicious", true);
        this.addPredefinedFileType(fileType);

        /**
         * Create a file type that should match test.txt in the Small2 image.
         */
        // RJCTODO: Remove test file type.
        try {
            fileType = new FileType("predefinedASCII", new Signature("hello".getBytes(UserDefinedFileTypesManager.ASCII_ENCODING), 0L, FileType.Signature.Type.ASCII), "Benign", true);
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
     * Adds a predefined file type to the in-memory mappings of MIME types to
     * file types.
     *
     * @param fileType The file type to add.
     */
    private void addPredefinedFileType(FileType fileType) {
        this.predefinedFileTypes.put(fileType.getMimeType(), fileType);
        this.fileTypes.put(fileType.getMimeType(), fileType);
    }

    /**
     * Adds the user-defined file types to the in-memory mappings of MIME types
     * to file types.
     */
    private void loadUserDefinedFileTypes() {
        try {
            String filePath = getFileTypeDefinitionsFilePath(UserDefinedFileTypesManager.USER_DEFINED_TYPE_DEFINITIONS_FILE);
            File file = new File(filePath);
            if (file.exists() && file.canRead()) {
                for (FileType fileType : XmlReader.readFileTypes(filePath)) {
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
     * Adds a user-defined file type to the in-memory mappings of MIME types to
     * file types.
     *
     * @param fileType The file type to add.
     */
    private void addUserDefinedFileType(FileType fileType) {
        this.userDefinedFileTypes.put(fileType.getMimeType(), fileType);
        this.fileTypes.put(fileType.getMimeType(), fileType);
    }

    /**
     * Gets both the predefined and the user-defined file types.
     *
     * @return A mapping of file type names to file types, possibly empty.
     */
    synchronized Map<String, FileType> getFileTypes() {
        /**
         * Load the predefined types first so that they can be overwritten by
         * any user-defined types with the same names.
         */
        loadPredefinedFileTypes();
        loadUserDefinedFileTypes();
                
        /**
         * It is safe to return references to the internal file type objects
         * because they are immutable.
         */
        return new HashMap<>(this.fileTypes);
    }

    /**
     * Gets the user-defined file types.
     *
     * @return A mapping of file type names to file types, possibly empty.
     */
    synchronized Map<String, FileType> getUserDefinedFileTypes() {
        /**
         * Load the predefined types first so that they can be overwritten by
         * any user-defined types with the same names.
         */
        loadPredefinedFileTypes();
        loadUserDefinedFileTypes();
                
        /**
         * It is safe to return references to the internal file type objects
         * because they are immutable.
         */
        return new HashMap<>(this.userDefinedFileTypes);
    }

    /**
     * Sets the user-defined file types.
     *
     * @param newFileTypes A mapping of file type names to user-defined file
     * types.
     */
    synchronized void setUserDefinedFileTypes(Map<String, FileType> newFileTypes) throws UserDefinedFileTypesManager.UserDefinedFileTypesException {
        try {
            String filePath = UserDefinedFileTypesManager.getFileTypeDefinitionsFilePath(UserDefinedFileTypesManager.USER_DEFINED_TYPE_DEFINITIONS_FILE);
            UserDefinedFileTypesManager.XmlWriter.writeFileTypes(newFileTypes.values(), filePath);

        } catch (ParserConfigurationException | FileNotFoundException | UnsupportedEncodingException | TransformerException ex) {
            UserDefinedFileTypesManager.logger.log(Level.SEVERE, "Failed to write file types file", ex);
            throw new UserDefinedFileTypesManager.UserDefinedFileTypesException(ex.getLocalizedMessage());
        } catch (IOException ex) {
            UserDefinedFileTypesManager.logger.log(Level.SEVERE, "Failed to write file types file", ex);
            throw new UserDefinedFileTypesManager.UserDefinedFileTypesException(ex.getLocalizedMessage());
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
    private static class XmlWriter {

        /**
         * Writes a set of file type definitions to an XML file.
         *
         * @param fileTypes A collection of file types.
         * @param filePath The path to the destination file.
         * @throws ParserConfigurationException
         * @throws IOException
         * @throws FileNotFoundException
         * @throws UnsupportedEncodingException
         * @throws TransformerException
         */
        private static void writeFileTypes(Collection<FileType> fileTypes, String filePath) throws ParserConfigurationException, IOException, FileNotFoundException, UnsupportedEncodingException, TransformerException {
            Document doc = XMLUtil.createDocument();
            Element fileTypesElem = doc.createElement(UserDefinedFileTypesManager.FILE_TYPES_TAG_NAME);
            doc.appendChild(fileTypesElem);
            for (FileType fileType : fileTypes) {
                Element fileTypeElem = UserDefinedFileTypesManager.XmlWriter.createFileTypeElement(fileType, doc);
                fileTypesElem.appendChild(fileTypeElem);
            }
            XMLUtil.saveDocument(doc, UserDefinedFileTypesManager.ENCODING_FOR_XML_FILE, filePath);
        }

        /**
         * Creates an XML representation of a file type.
         *
         * @param fileType The file type object.
         * @param doc The WC3 DOM object to use to create the XML.
         * @return An XML element.
         */
        private static Element createFileTypeElement(FileType fileType, Document doc) {
            Element fileTypeElem = doc.createElement(UserDefinedFileTypesManager.FILE_TYPE_TAG_NAME);
            XmlWriter.addMimeTypeElement(fileType, fileTypeElem, doc);
            XmlWriter.addSignatureElement(fileType, fileTypeElem, doc);
            XmlWriter.addInterestingFilesSetElement(fileType, fileTypeElem, doc);
            XmlWriter.addAlertAttribute(fileType, fileTypeElem);
            return fileTypeElem;
        }

        /**
         * Add a MIME type child element to a file type XML element.
         *
         * @param fileType The file type to use as a content source.
         * @param fileTypeElem The parent file type element.
         * @param doc The WC3 DOM object to use to create the XML.
         */
        private static void addMimeTypeElement(FileType fileType, Element fileTypeElem, Document doc) {
            Element typeNameElem = doc.createElement(UserDefinedFileTypesManager.MIME_TYPE_TAG_NAME);
            typeNameElem.setTextContent(fileType.getMimeType());
            fileTypeElem.appendChild(typeNameElem);
        }

        /**
         * Add a signature child element to a file type XML element.
         *
         * @param fileType The file type to use as a content source.
         * @param fileTypeElem The parent file type element.
         * @param doc The WC3 DOM object to use to create the XML.
         */
        private static void addSignatureElement(FileType fileType, Element fileTypeElem, Document doc) {
            Signature signature = fileType.getSignature();
            Element signatureElem = doc.createElement(UserDefinedFileTypesManager.SIGNATURE_TAG_NAME);

            Element bytesElem = doc.createElement(UserDefinedFileTypesManager.BYTES_TAG_NAME);
            bytesElem.setTextContent(DatatypeConverter.printHexBinary(signature.getSignatureBytes()));
            signatureElem.appendChild(bytesElem);

            Element offsetElem = doc.createElement(UserDefinedFileTypesManager.OFFSET_TAG_NAME);
            offsetElem.setTextContent(DatatypeConverter.printLong(signature.getOffset()));
            signatureElem.appendChild(offsetElem);

            signatureElem.setAttribute(UserDefinedFileTypesManager.SIGNATURE_TYPE_ATTRIBUTE, signature.getType().toString());
            fileTypeElem.appendChild(signatureElem);
        }

        /**
         * Add an interesting files set element to a file type XML element.
         *
         * @param fileType The file type to use as a content source.
         * @param fileTypeElem The parent file type element.
         * @param doc The WC3 DOM object to use to create the XML.
         */
        private static void addInterestingFilesSetElement(FileType fileType, Element fileTypeElem, Document doc) {
            Element filesSetElem = doc.createElement(UserDefinedFileTypesManager.INTERESTING_FILES_SET_TAG_NAME);
            filesSetElem.setTextContent(fileType.getFilesSetName());
            fileTypeElem.appendChild(filesSetElem);
        }

        /**
         * Add an alert attribute to a file type XML element.
         *
         * @param fileType The file type to use as a content source.
         * @param fileTypeElem The parent file type element.
         */
        private static void addAlertAttribute(FileType fileType, Element fileTypeElem) {
            fileTypeElem.setAttribute(UserDefinedFileTypesManager.ALERT_ATTRIBUTE, Boolean.toString(fileType.alertOnMatch()));
        }

    }

    /**
     * Provides a mechanism for reading a set of file type definitions from an
     * XML file.
     */
    private static class XmlReader {

        /**
         * Reads a set of file type definitions from an XML file.
         *
         * @param filePath The path to the XML file.
         * @return A collection of file types read from the XML file.
         */
        private static List<FileType> readFileTypes(String filePath) throws IOException, ParserConfigurationException, SAXException {
            List<FileType> fileTypes = new ArrayList<>();
            Document doc = XMLUtil.loadDocument(filePath, UserDefinedFileTypesManager.class, UserDefinedFileTypesManager.FILE_TYPE_DEFINITIONS_SCHEMA_FILE);
//            Document doc = XMLUtil.loadDocument(filePath);
            if (doc != null) {
                Element fileTypesElem = doc.getDocumentElement();
                if (fileTypesElem != null && fileTypesElem.getNodeName().equals(UserDefinedFileTypesManager.FILE_TYPES_TAG_NAME)) {
                    NodeList fileTypeElems = fileTypesElem.getElementsByTagName(UserDefinedFileTypesManager.FILE_TYPE_TAG_NAME);
                    for (int i = 0; i < fileTypeElems.getLength(); ++i) {
                        Element fileTypeElem = (Element) fileTypeElems.item(i);
                        FileType fileType = XmlReader.parseFileType(fileTypeElem);
                        fileTypes.add(fileType);
                    }
                }
            }
            return fileTypes;
        }

        /**
         * Gets a file type definition from a file type XML element.
         *
         * @param fileTypeElem The XML element.
         * @return A file type object.
         * @throws IllegalArgumentException
         * @throws NumberFormatException
         */
        private static FileType parseFileType(Element fileTypeElem) throws IllegalArgumentException, NumberFormatException {
            String mimeType = XmlReader.parseMimeType(fileTypeElem);
            Signature signature = XmlReader.parseSignature(fileTypeElem);
            String filesSetName = XmlReader.parseInterestingFilesSet(fileTypeElem);
            boolean alert = XmlReader.parseAlert(fileTypeElem);
            return new FileType(mimeType, signature, filesSetName, alert);
        }

        /**
         * Gets the MIME type from a file type XML element.
         *
         * @param fileTypeElem The element
         * @return A MIME type string.
         */
        private static String parseMimeType(Element fileTypeElem) {
            return getChildElementTextContent(fileTypeElem, UserDefinedFileTypesManager.MIME_TYPE_TAG_NAME);
        }

        /**
         * Gets the signature from a file type XML element.
         *
         * @param fileTypeElem The XML element.
         * @return The signature.
         */
        private static Signature parseSignature(Element fileTypeElem) throws IllegalArgumentException, NumberFormatException {
            NodeList signatureElems = fileTypeElem.getElementsByTagName(UserDefinedFileTypesManager.SIGNATURE_TAG_NAME);
            Element signatureElem = (Element) signatureElems.item(0);

            String sigTypeAttribute = signatureElem.getAttribute(UserDefinedFileTypesManager.SIGNATURE_TYPE_ATTRIBUTE);
            Signature.Type signatureType = Signature.Type.valueOf(sigTypeAttribute);

            String sigBytesString = getChildElementTextContent(signatureElem, UserDefinedFileTypesManager.BYTES_TAG_NAME);
            byte[] signatureBytes = DatatypeConverter.parseHexBinary(sigBytesString);

            String offsetString = getChildElementTextContent(signatureElem, UserDefinedFileTypesManager.OFFSET_TAG_NAME);
            long offset = DatatypeConverter.parseLong(offsetString);

            return new Signature(signatureBytes, offset, signatureType);
        }

        /**
         * Gets the interesting files set name from a file type XML element.
         *
         * @param fileTypeElem The XML element.
         * @return The files set name, possibly empty.
         */
        private static String parseInterestingFilesSet(Element fileTypeElem) {
            String filesSetName = "";
            NodeList filesSetElems = fileTypeElem.getElementsByTagName(UserDefinedFileTypesManager.INTERESTING_FILES_SET_TAG_NAME);
            if (filesSetElems.getLength() > 0) {
                Element filesSetElem = (Element) filesSetElems.item(0);
                filesSetName = filesSetElem.getTextContent();
            }
            return filesSetName;
        }

        /**
         * Gets the alert attribute from a file type XML element.
         *
         * @param fileTypeElem The XML element.
         * @return True or false;
         */
        private static boolean parseAlert(Element fileTypeElem) {
            String alertAttribute = fileTypeElem.getAttribute(UserDefinedFileTypesManager.ALERT_ATTRIBUTE);
            return Boolean.parseBoolean(alertAttribute);
        }

        /**
         * Gets the text content of a single child element.
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

    }

    /**
     * Used to translate more implementation-details-specific exceptions (which
     * are logged by this class) into more generic exceptions for propagation to
     * clients of the user-defined file types manager.
     */
    static class UserDefinedFileTypesException extends Exception {

        UserDefinedFileTypesException(String message) {
            super(message);
        }
    }

}
