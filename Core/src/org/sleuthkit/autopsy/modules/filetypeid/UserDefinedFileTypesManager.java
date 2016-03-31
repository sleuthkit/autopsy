/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.persistence.PersistenceException;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.bind.DatatypeConverter;
import javax.xml.transform.TransformerException;
import org.openide.util.NbBundle;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.modules.filetypeid.FileType.Signature;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Node;
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
    private static final String USER_DEFINED_TYPES_XML_FILE = "UserFileTypeDefinitions.xml"; //NON-NLS
    private static final String USER_DEFINED_TYPES_SERIALIZATION_FILE = "UserFileTypeDefinitions.settings";
    private static final String FILE_TYPES_TAG_NAME = "FileTypes"; //NON-NLS
    private static final String FILE_TYPE_TAG_NAME = "FileType"; //NON-NLS
    private static final String MIME_TYPE_TAG_NAME = "MimeType"; //NON-NLS
    private static final String SIGNATURE_TAG_NAME = "Signature"; //NON-NLS
    private static final String SIGNATURE_TYPE_ATTRIBUTE = "type"; //NON-NLS
    private static final String BYTES_TAG_NAME = "Bytes"; //NON-NLS
    private static final String OFFSET_TAG_NAME = "Offset"; //NON-NLS
    private static final String RELATIVE_ATTRIBUTE = "RelativeToStart"; //NON-NLS
    private static final String INTERESTING_FILES_SET_TAG_NAME = "InterestingFileSset"; //NON-NLS
    private static final String ALERT_ATTRIBUTE = "alert"; //NON-NLS
    private static final String ENCODING_FOR_XML_FILE = "UTF-8"; //NON-NLS
    private static UserDefinedFileTypesManager instance;

    /**
     * File types to be persisted to the user-defined file type definitions file
     * are stored in this mapping of MIME types to file types. Access to this
     * map is guarded by the intrinsic lock of the user-defined file types
     * manager for thread-safety.
     */
    private final List<FileType> userDefinedFileTypes = new ArrayList<>();

    /**
     * The combined set of user-defined file types and file types predefined by
     * Autopsy are stored in this mapping of MIME types to file types. This is
     * the current working set of file types. Access to this map is guarded by
     * the intrinsic lock of the user-defined file types manager for
     * thread-safety.
     */
    private final List<FileType> fileTypes = new ArrayList<>();

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
    synchronized List<FileType> getFileTypes() throws UserDefinedFileTypesException {
        loadFileTypes();

        /**
         * It is safe to return references to the internal file type objects
         * because they are immutable. Note that
         * Collections.unmodifiableCollection() is not used here because this
         * view of the file types is a snapshot.
         */
        return new ArrayList<>(fileTypes);
    }

    /**
     * Gets the user-defined file types.
     *
     * @return A mapping of file type names to file types, possibly empty.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException
     */
    synchronized List<FileType> getUserDefinedFileTypes() throws UserDefinedFileTypesException {
        loadFileTypes();

        /**
         * It is safe to return references to the internal file type objects
         * because they are immutable. Note that
         * Collections.unmodifiableCollection() is not used here because this
         * view of the file types is a snapshot.
         */
        return new ArrayList<>(userDefinedFileTypes);
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
            List<Signature> signatureList;
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("<?xml", 0L));
            fileType = new FileType("text/xml", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);

            // Add rule for gzip
            byteArray = DatatypeConverter.parseHexBinary("1F8B");  //NON-NLS  
            signatureList = new ArrayList<>();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("application/x-gzip", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);

            // Add rule for .wk1
            byteArray = DatatypeConverter.parseHexBinary("0000020006040600080000000000"); //NON-NLS
            signatureList = new ArrayList<>();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("application/x-123", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);

            // Add rule for Radiance image
            byteArray = DatatypeConverter.parseHexBinary("233F52414449414E43450A");//NON-NLS
            signatureList = new ArrayList<>();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("image/vnd.radiance", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);

            // Add rule for .dcx image
            byteArray = DatatypeConverter.parseHexBinary("B168DE3A"); //NON-NLS
            signatureList = new ArrayList<>();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("image/x-dcx", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);

            // Add rule for .ics image
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("icns", 0L));
            fileType = new FileType("image/x-icns", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);

            // Add rule for .pict image
            byteArray = DatatypeConverter.parseHexBinary("001102FF"); //NON-NLS
            signatureList = new ArrayList<>();
            signatureList.add(new Signature(byteArray, 522L));
            fileType = new FileType("image/x-pict", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);
            byteArray = DatatypeConverter.parseHexBinary("1100"); //NON-NLS
            signatureList = new ArrayList<>();
            signatureList.add(new Signature(byteArray, 522L));
            fileType = new FileType("image/x-pict", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);

            // Add rule for .pam
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("P7", 0L));
            fileType = new FileType("image/x-portable-arbitrarymap", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);

            // Add rule for .pfm
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("PF", 0L));
            fileType = new FileType("image/x-portable-floatmap", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("Pf", 0L));
            fileType = new FileType("image/x-portable-floatmap", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);

            // Add rule for .tga
            byteArray = DatatypeConverter.parseHexBinary("54525545564953494F4E2D5846494C452E00"); //NON-NLS
            signatureList = new ArrayList<>();
            signatureList.add(new Signature(byteArray, 17, false));
            fileType = new FileType("image/x-tga", new ArrayList<>(signatureList), "", false); //NON-NLS
            fileTypes.add(fileType);

            // Add rule for .ilbm
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("FORM", 0L));
            signatureList.add(new Signature("ILBM", 8L));
            fileType = new FileType("image/x-ilbm", new ArrayList<>(signatureList), "", false);
            fileTypes.add(fileType);
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("FORM", 0L));
            signatureList.add(new Signature("PBM", 8L));
            fileType = new FileType("image/x-ilbm", new ArrayList<>(signatureList), "", false);
            fileTypes.add(fileType);

            // Add rule for .webp
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("RIFF", 0L));
            signatureList.add(new Signature("WEBP", 8L));
            fileType = new FileType("image/webp", new ArrayList<>(signatureList), "", false);
            fileTypes.add(fileType);

            // Add rule for .aiff
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("FORM", 0L));
            signatureList.add(new Signature("AIFF", 8L));
            fileType = new FileType("audio/aiff", new ArrayList<>(signatureList), "", false);
            fileTypes.add(fileType);
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("FORM", 0L));
            signatureList.add(new Signature("AIFC", 8L));
            fileType = new FileType("audio/aiff", new ArrayList<>(signatureList), "", false);
            fileTypes.add(fileType);
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("FORM", 0L));
            signatureList.add(new Signature("8SVX", 8L));
            fileType = new FileType("audio/aiff", new ArrayList<>(signatureList), "", false);
            fileTypes.add(fileType);
            
            // Add .iff
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("FORM", 0L));
            fileType = new FileType("application/x-iff", new ArrayList<>(signatureList), "", false);
            fileTypes.add(fileType);

        } // parseHexBinary() throws this if the argument passed in is not Hex
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
            File serialized = new File(getFileTypeDefinitionsFilePath(USER_DEFINED_TYPES_SERIALIZATION_FILE));
            if (serialized.exists()) {
                for (FileType fileType : readFileTypesSerialized()) {
                    addUserDefinedFileType(fileType);
                }
            } else {
                String filePath = getFileTypeDefinitionsFilePath(USER_DEFINED_TYPES_XML_FILE);
                File xmlFile = new File(filePath);
                if (xmlFile.exists()) {
                    for (FileType fileType : XMLDefinitionsReader.readFileTypes(filePath)) {
                        addUserDefinedFileType(fileType);
                    }
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
        userDefinedFileTypes.add(fileType);
        fileTypes.add(fileType);
    }

    /**
     * Sets the user-defined file types.
     *
     * @param newFileTypes A mapping of file type names to user-defined file
     *                     types.
     */
    synchronized void setUserDefinedFileTypes(List<FileType> newFileTypes) throws UserDefinedFileTypesException {
        String filePath = getFileTypeDefinitionsFilePath(USER_DEFINED_TYPES_SERIALIZATION_FILE);
        writeFileTypes(newFileTypes, filePath);
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
     * Writes a set of file types to a file.
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
    private static void writeFileTypes(List<FileType> fileTypes, String filePath) throws UserDefinedFileTypesException {
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(filePath))) {
            UserDefinedFileTypesSettings settings = new UserDefinedFileTypesSettings(fileTypes);
            out.writeObject(settings);
        } catch (IOException ex) {
            throw new UserDefinedFileTypesException(String.format("Failed to write settings to %s", filePath), ex);
        }
    }

    /**
     * Reads the file types
     *
     * @param filePath the file path where the file types are to be read
     *
     * @return the file types
     *
     * @throws ParserConfigurationException If the file cannot be read
     */
    private static List<FileType> readFileTypesSerialized() throws UserDefinedFileTypesException {
        File serializedDefs = new File(getFileTypeDefinitionsFilePath(USER_DEFINED_TYPES_SERIALIZATION_FILE));
        try {
            try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(serializedDefs))) {
                UserDefinedFileTypesSettings filesSetsSettings = (UserDefinedFileTypesSettings) in.readObject();
                return filesSetsSettings.getUserDefinedFileTypes();
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new UserDefinedFileTypesException("Couldn't read serialized settings.", ex);
        }
    }

    /**
     * Provides a mechanism for reading a set of file type definitions from an
     * XML file.
     */
    private static class XMLDefinitionsReader {

        /**
         * Reads a set of file type definitions from an XML file.
         *
         * @param filePath The path to the XML file.
         *
         * @return A collection of file types read from the XML file.
         */
        private static List<FileType> readFileTypes(String filePath) throws IOException, SAXException, ParserConfigurationException {
            List<FileType> fileTypes = new ArrayList<>();
            /*
             * RC: Commenting out the loadDocument overload that validates
             * against the XSD is a temp fix for a failure to provide an upgrade
             * path when the RelativeToStart attribute was added to the
             * Signature element. The upgrade path can be supplied, but the plan
             * is to replace the use of XML with object serialization for the
             * settings, so it may not be worth the effort.
             */
            // private static final String FILE_TYPE_DEFINITIONS_SCHEMA_FILE = "FileTypes.xsd"; //NON-NLS
            // Document doc = XMLUtil.loadDocument(filePath, UserDefinedFileTypesManager.class, FILE_TYPE_DEFINITIONS_SCHEMA_FILE);
            Document doc = XMLUtil.loadDocument(filePath);
            if (doc != null) {
                Element fileTypesElem = doc.getDocumentElement();
                if (fileTypesElem != null && fileTypesElem.getNodeName().equals(FILE_TYPES_TAG_NAME)) {
                    NodeList fileTypeElems = fileTypesElem.getElementsByTagName(FILE_TYPE_TAG_NAME);
                    for (int i = 0; i < fileTypeElems.getLength(); ++i) {
                        Element fileTypeElem = (Element) fileTypeElems.item(i);
                        FileType fileType = XMLDefinitionsReader.parseFileType(fileTypeElem);
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
            String mimeType = XMLDefinitionsReader.parseMimeType(fileTypeElem);
            Signature signature = XMLDefinitionsReader.parseSignature(fileTypeElem);
            String filesSetName = XMLDefinitionsReader.parseInterestingFilesSet(fileTypeElem);
            boolean alert = XMLDefinitionsReader.parseAlert(fileTypeElem);
            List<Signature> sigList = new ArrayList<>();
            sigList.add(signature);
            return new FileType(mimeType, sigList, filesSetName, alert);
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

            Element offsetElem = (Element) signatureElem.getElementsByTagName(OFFSET_TAG_NAME).item(0);
            String offsetString = offsetElem.getTextContent();
            long offset = DatatypeConverter.parseLong(offsetString);

            boolean isRelativeToStart;
            String relativeString = offsetElem.getAttribute(RELATIVE_ATTRIBUTE);
            if (null == relativeString || relativeString.equals("")) {
                isRelativeToStart = true;
            } else {
                isRelativeToStart = DatatypeConverter.parseBoolean(relativeString);
            }

            return new Signature(signatureBytes, offset, signatureType, isRelativeToStart);
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
         * @return The text content or null if the tag doesn't exist.
         */
        private static String getChildElementTextContent(Element elem, String tagName) {
            NodeList childElems = elem.getElementsByTagName(tagName);
            Node childNode = childElems.item(0);
            if (childNode == null) {
                return null;
            }
            Element childElem = (Element) childNode;
            return childElem.getTextContent();
        }

        /**
         * Private constructor suppresses creation of instanmces of this utility
         * class.
         */
        private XMLDefinitionsReader() {
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

        private static final long serialVersionUID = 1L;

        UserDefinedFileTypesException(String message) {
            super(message);
        }

        UserDefinedFileTypesException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }

}
