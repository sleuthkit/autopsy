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
import org.openide.util.NbBundle;
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
 * specific paradigm. In this paradigm, there is a single modal writer of file
 * types in the form of a global settings panel that disables itself when ingest
 * is running so that multiple readers in the form of file ingest modules get a
 * consistent set of file type definitions.
 * <p>
 * Thread-safe.
 */
final class UserDefinedFileTypesManager {

    private static final Logger logger = Logger.getLogger(UserDefinedFileTypesManager.class.getName());
    private static final String FILE_TYPE_DEFINITIONS_SCHEMA_FILE = "FileTypes.xsd"; //NON-NLS
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
     * Gets the singleton manager of user-defined file types characterized by
     * MIME type, signature, and optional membership in an interesting files
     * set.
     *
     * @return The user-defined file types manager singleton.
     */
    synchronized static UserDefinedFileTypesManager getInstance() {
        if (instance == null) {
            instance = new UserDefinedFileTypesManager();
        }
        return instance;
    }

    /**
     * Creates a manager of user-defined file types characterized by MIME type,
     * signature, and optional membership in an interesting files set.
     */
    private UserDefinedFileTypesManager() {
    }

    /**
     * Gets both the predefined and the user-defined file types.
     *
     * @return A mapping of file type names to file types, possibly empty.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    synchronized Map<String, FileType> getFileTypes() throws UserDefinedFileTypesException {
        loadFileTypes();

        /**
         * It is safe to return references to the internal file type objects
         * because they are immutable. Note that
         * Collections.unmodifiableCollection() is not used here because this
         * view of the file types is a snapshot.
         */
        return new HashMap<>(fileTypes);
    }

    /**
     * Gets the user-defined file types.
     *
     * @return A mapping of file type names to file types, possibly empty.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    synchronized Map<String, FileType> getUserDefinedFileTypes() throws UserDefinedFileTypesException {
        loadFileTypes();

        /**
         * It is safe to return references to the internal file type objects
         * because they are immutable. Note that
         * Collections.unmodifiableCollection() is not used here because this
         * view of the file types is a snapshot.
         */
        return new HashMap<>(userDefinedFileTypes);
    }

    /**
     * Loads the MIME type to file type mappings with predefined and
     * user-defined types.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    private void loadFileTypes() throws UserDefinedFileTypesException {
        fileTypes.clear();
        userDefinedFileTypes.clear();
        /**
         * Load the predefined types first so that they can be overwritten by
         * any user-defined types with the same names.
         */
        loadPredefinedFileTypes();
        loadUserDefinedFileTypes();
    }

    /**
     * Adds the predefined file types to the in-memory mappings of MIME types to
     * file types.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    private void loadPredefinedFileTypes() throws UserDefinedFileTypesException {
        byte[] byteArray;
        FileType fileType;
        
        try {
            // Add rule for xml
            fileType = new FileType("text/xml", new Signature("<?xml", 0L), "", false); //NON-NLS
            fileTypes.put(fileType.getMimeType(), fileType);

            // Add rule for gzip
            byteArray = DatatypeConverter.parseHexBinary("1F8B");  //NON-NLS               
            fileType = new FileType("application/x-gzip", new Signature(byteArray, 0L), "", false); //NON-NLS
            fileTypes.put(fileType.getMimeType(), fileType);

            // Add rule for .wk1
            byteArray = DatatypeConverter.parseHexBinary("0000020006040600080000000000"); //NON-NLS
            fileType = new FileType("application/x-123", new Signature(byteArray, 0L), "", false); //NON-NLS
            fileTypes.put(fileType.getMimeType(), fileType);

            // Add rule for Radiance image
            byteArray = DatatypeConverter.parseHexBinary("233F52414449414E43450A");//NON-NLS
            fileType = new FileType("image/vnd.radiance", new Signature(byteArray, 0L), "", false); //NON-NLS
            fileTypes.put(fileType.getMimeType(), fileType);

            // Add rule for .dcx image
            byteArray = DatatypeConverter.parseHexBinary("B168DE3A"); //NON-NLS
            fileType = new FileType("image/x-dcx", new Signature(byteArray, 0L), "", false); //NON-NLS
            fileTypes.put(fileType.getMimeType(), fileType);

            // Add rule for .ics image
            fileType = new FileType("image/x-icns", new Signature("icns", 0L), "", false); //NON-NLS
            fileTypes.put(fileType.getMimeType(), fileType);

            // Add rule for .pict image
            byteArray = DatatypeConverter.parseHexBinary("001102FF"); //NON-NLS
            fileType = new FileType("image/x-pict", new Signature(byteArray, 522L), "", false); //NON-NLS
            fileTypes.put(fileType.getMimeType(), fileType);

            // Add rule for .pam
            fileType = new FileType("image/x-portable-arbitrarymap", new Signature("P7", 0L), "", false); //NON-NLS
            fileTypes.put(fileType.getMimeType(), fileType);

            // Add rule for .pfm
            fileType = new FileType("image/x-portable-floatmap", new Signature("PF", 0L), "", false); //NON-NLS
            fileTypes.put(fileType.getMimeType(), fileType);
            
            // Add rule for .tga
            byteArray = DatatypeConverter.parseHexBinary("54525545564953494F4E2D5846494C452E00");
            fileType = new FileType("image/x-tga", new Signature(byteArray, true), "", false); // NON-NLS
            fileTypes.put(fileType.getMimeType(), fileType);
            
        }
        // parseHexBinary() throws this if the argument passed in is not Hex
        catch (IllegalArgumentException e) {
            throw new UserDefinedFileTypesException("Error creating predefined file types", e); //
        }
    }

    /**
     * Adds the user-defined file types to the in-memory mappings of MIME types
     * to file types.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    private void loadUserDefinedFileTypes() throws UserDefinedFileTypesException {
        try {
            String filePath = getFileTypeDefinitionsFilePath(USER_DEFINED_TYPE_DEFINITIONS_FILE);
            File file = new File(filePath);
            if (file.exists() && file.canRead()) {
                for (FileType fileType : XmlReader.readFileTypes(filePath)) {
                    addUserDefinedFileType(fileType);
                }
            }

        } catch (IOException | ParserConfigurationException | SAXException ex) {
            /**
             * Using an all-or-none policy.
             */
            fileTypes.clear();
            userDefinedFileTypes.clear();
            throwUserDefinedFileTypesException(ex, "UserDefinedFileTypesManager.loadFileTypes.errorMessage");
        }
    }

    /**
     * Adds a user-defined file type to the in-memory mappings of MIME types to
     * file types.
     *
     * @param fileType The file type to add.
     */
    private void addUserDefinedFileType(FileType fileType) {
        userDefinedFileTypes.put(fileType.getMimeType(), fileType);
        fileTypes.put(fileType.getMimeType(), fileType);
    }

    /**
     * Sets the user-defined file types.
     *
     * @param newFileTypes A mapping of file type names to user-defined file
     *                     types.
     */
    synchronized void setUserDefinedFileTypes(Map<String, FileType> newFileTypes) throws UserDefinedFileTypesException {
        try {
            String filePath = getFileTypeDefinitionsFilePath(USER_DEFINED_TYPE_DEFINITIONS_FILE);
            XmlWriter.writeFileTypes(newFileTypes.values(), filePath);
        } catch (ParserConfigurationException | FileNotFoundException | UnsupportedEncodingException | TransformerException ex) {
            throwUserDefinedFileTypesException(ex, "UserDefinedFileTypesManager.saveFileTypes.errorMessage");
        } catch (IOException ex) {
            throwUserDefinedFileTypesException(ex, "UserDefinedFileTypesManager.saveFileTypes.errorMessage");
        }
    }

    /**
     * Gets the absolute path of a file type definitions file.
     *
     * @param fileName The name of the file.
     *
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
         * @param filePath  The path to the destination file.
         *
         * @throws ParserConfigurationException
         * @throws IOException
         * @throws FileNotFoundException
         * @throws UnsupportedEncodingException
         * @throws TransformerException
         */
        private static void writeFileTypes(Collection<FileType> fileTypes, String filePath) throws ParserConfigurationException, IOException, FileNotFoundException, UnsupportedEncodingException, TransformerException {
            Document doc = XMLUtil.createDocument();
            Element fileTypesElem = doc.createElement(FILE_TYPES_TAG_NAME);
            doc.appendChild(fileTypesElem);
            for (FileType fileType : fileTypes) {
                Element fileTypeElem = XmlWriter.createFileTypeElement(fileType, doc);
                fileTypesElem.appendChild(fileTypeElem);
            }
            XMLUtil.saveDocument(doc, ENCODING_FOR_XML_FILE, filePath);
        }

        /**
         * Creates an XML representation of a file type.
         *
         * @param fileType The file type object.
         * @param doc      The WC3 DOM object to use to create the XML.
         *
         * @return An XML element.
         */
        private static Element createFileTypeElement(FileType fileType, Document doc) {
            Element fileTypeElem = doc.createElement(FILE_TYPE_TAG_NAME);
            XmlWriter.addMimeTypeElement(fileType, fileTypeElem, doc);
            XmlWriter.addSignatureElement(fileType, fileTypeElem, doc);
            XmlWriter.addInterestingFilesSetElement(fileType, fileTypeElem, doc);
            XmlWriter.addAlertAttribute(fileType, fileTypeElem);
            return fileTypeElem;
        }

        /**
         * Add a MIME type child element to a file type XML element.
         *
         * @param fileType     The file type to use as a content source.
         * @param fileTypeElem The parent file type element.
         * @param doc          The WC3 DOM object to use to create the XML.
         */
        private static void addMimeTypeElement(FileType fileType, Element fileTypeElem, Document doc) {
            Element typeNameElem = doc.createElement(MIME_TYPE_TAG_NAME);
            typeNameElem.setTextContent(fileType.getMimeType());
            fileTypeElem.appendChild(typeNameElem);
        }

        /**
         * Add a signature child element to a file type XML element.
         *
         * @param fileType     The file type to use as a content source.
         * @param fileTypeElem The parent file type element.
         * @param doc          The WC3 DOM object to use to create the XML.
         */
        private static void addSignatureElement(FileType fileType, Element fileTypeElem, Document doc) {
            Signature signature = fileType.getSignature();
            Element signatureElem = doc.createElement(SIGNATURE_TAG_NAME);

            Element bytesElem = doc.createElement(BYTES_TAG_NAME);
            bytesElem.setTextContent(DatatypeConverter.printHexBinary(signature.getSignatureBytes()));
            signatureElem.appendChild(bytesElem);

            Element offsetElem = doc.createElement(OFFSET_TAG_NAME);
            offsetElem.setTextContent(DatatypeConverter.printLong(signature.getOffset()));
            signatureElem.appendChild(offsetElem);

            signatureElem.setAttribute(SIGNATURE_TYPE_ATTRIBUTE, signature.getType().toString());
            fileTypeElem.appendChild(signatureElem);
        }

        /**
         * Add an interesting files set element to a file type XML element.
         *
         * @param fileType     The file type to use as a content source.
         * @param fileTypeElem The parent file type element.
         * @param doc          The WC3 DOM object to use to create the XML.
         */
        private static void addInterestingFilesSetElement(FileType fileType, Element fileTypeElem, Document doc) {
            if (!fileType.getFilesSetName().isEmpty()) {
                Element filesSetElem = doc.createElement(INTERESTING_FILES_SET_TAG_NAME);
                filesSetElem.setTextContent(fileType.getFilesSetName());
                fileTypeElem.appendChild(filesSetElem);
            }
        }

        /**
         * Add an alert attribute to a file type XML element.
         *
         * @param fileType     The file type to use as a content source.
         * @param fileTypeElem The parent file type element.
         */
        private static void addAlertAttribute(FileType fileType, Element fileTypeElem) {
            fileTypeElem.setAttribute(ALERT_ATTRIBUTE, Boolean.toString(fileType.alertOnMatch()));
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
         *
         * @return A collection of file types read from the XML file.
         */
        private static List<FileType> readFileTypes(String filePath) throws IOException, ParserConfigurationException, SAXException {
            List<FileType> fileTypes = new ArrayList<>();
            Document doc = XMLUtil.loadDocument(filePath, UserDefinedFileTypesManager.class, FILE_TYPE_DEFINITIONS_SCHEMA_FILE);
            if (doc != null) {
                Element fileTypesElem = doc.getDocumentElement();
                if (fileTypesElem != null && fileTypesElem.getNodeName().equals(FILE_TYPES_TAG_NAME)) {
                    NodeList fileTypeElems = fileTypesElem.getElementsByTagName(FILE_TYPE_TAG_NAME);
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
         *
         * @return A file type object.
         *
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
         *
         * @return A MIME type string.
         */
        private static String parseMimeType(Element fileTypeElem) {
            return getChildElementTextContent(fileTypeElem, MIME_TYPE_TAG_NAME);
        }

        /**
         * Gets the signature from a file type XML element.
         *
         * @param fileTypeElem The XML element.
         *
         * @return The signature.
         */
        private static Signature parseSignature(Element fileTypeElem) throws IllegalArgumentException, NumberFormatException {
            NodeList signatureElems = fileTypeElem.getElementsByTagName(SIGNATURE_TAG_NAME);
            Element signatureElem = (Element) signatureElems.item(0);

            String sigTypeAttribute = signatureElem.getAttribute(SIGNATURE_TYPE_ATTRIBUTE);
            Signature.Type signatureType = Signature.Type.valueOf(sigTypeAttribute);

            String sigBytesString = getChildElementTextContent(signatureElem, BYTES_TAG_NAME);
            byte[] signatureBytes = DatatypeConverter.parseHexBinary(sigBytesString);

            String offsetString = getChildElementTextContent(signatureElem, OFFSET_TAG_NAME);
            long offset = DatatypeConverter.parseLong(offsetString);

            return new Signature(signatureBytes, offset, signatureType);
        }

        /**
         * Gets the interesting files set name from a file type XML element.
         *
         * @param fileTypeElem The XML element.
         *
         * @return The files set name, possibly empty.
         */
        private static String parseInterestingFilesSet(Element fileTypeElem) {
            String filesSetName = "";
            NodeList filesSetElems = fileTypeElem.getElementsByTagName(INTERESTING_FILES_SET_TAG_NAME);
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
         *
         * @return True or false;
         */
        private static boolean parseAlert(Element fileTypeElem) {
            String alertAttribute = fileTypeElem.getAttribute(ALERT_ATTRIBUTE);
            return Boolean.parseBoolean(alertAttribute);
        }

        /**
         * Gets the text content of a single child element.
         *
         * @param elem    The parent element.
         * @param tagName The tag name of the child element.
         *
         * @return The text content.
         */
        private static String getChildElementTextContent(Element elem, String tagName) {
            NodeList childElems = elem.getElementsByTagName(tagName);
            Element childElem = (Element) childElems.item(0);
            return childElem.getTextContent();
        }

    }

    /**
     * Logs an exception, bundles the exception with a simple message in a
     * uniform exception type, and throws the wrapper exception.
     *
     * @param ex         The exception to wrap.
     * @param messageKey A key into the bundle file that maps to the desired
     *                   message.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    private void throwUserDefinedFileTypesException(Exception ex, String messageKey) throws UserDefinedFileTypesException {
        String message = NbBundle.getMessage(UserDefinedFileTypesManager.class, messageKey);
        logger.log(Level.SEVERE, message, ex);
        throw new UserDefinedFileTypesException(message, ex);
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

        UserDefinedFileTypesException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }

}
