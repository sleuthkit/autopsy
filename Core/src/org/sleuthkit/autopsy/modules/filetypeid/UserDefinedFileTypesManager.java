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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.ParserConfigurationException;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.modules.filetypeid.FileType.Signature;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * A singleton manager for the custom file types defined by Autopsy and by
 * users.
 */
final class UserDefinedFileTypesManager {

    private static final String XML_SETTINGS_FILE = "UserFileTypeDefinitions.xml"; //NON-NLS
    private static final String SERIALIZED_SETTINGS_FILE = "UserFileTypeDefinitions.settings"; //NON-NLS
    private static final String FILE_TYPES_TAG_NAME = "FileTypes"; //NON-NLS
    private static final String FILE_TYPE_TAG_NAME = "FileType"; //NON-NLS
    private static final String MIME_TYPE_TAG_NAME = "MimeType"; //NON-NLS
    private static final String SIGNATURE_TAG_NAME = "Signature"; //NON-NLS
    private static final String SIGNATURE_TYPE_ATTRIBUTE = "type"; //NON-NLS
    private static final String BYTES_TAG_NAME = "Bytes"; //NON-NLS
    private static final String OFFSET_TAG_NAME = "Offset"; //NON-NLS
    private static final String RELATIVE_ATTRIBUTE = "RelativeToStart"; //NON-NLS
    private static UserDefinedFileTypesManager instance;
    private final List<FileType> userDefinedFileTypes = new ArrayList<>();
    private final List<FileType> allFileTypes = new ArrayList<>();

    /**
     * Gets the singleton manager of the custom file types defined by Autopsy
     * and by users.
     *
     * @return The custom file types manager singleton.
     */
    synchronized static UserDefinedFileTypesManager getInstance() {
        if (instance == null) {
            instance = new UserDefinedFileTypesManager();
        }
        return instance;
    }

    /**
     * Constructs a manager for the custom file types defined by Autopsy and by
     * users.
     */
    private UserDefinedFileTypesManager() {
    }

    /**
     * Gets all of the custom file types defined by Autopsy and by users.
     *
     * @return A list of file types, possibly empty.
     *
     * @throws UserDefinedFileTypesException if there is a problem accessing the
     *                                       file types.
     */
    synchronized List<FileType> getFileTypes() throws UserDefinedFileTypesException {
        loadFileTypes();

        /**
         * It is safe to return references to the internal file type objects
         * because they are immutable. Note that
         * Collections.unmodifiableCollection() is not used here because this
         * view of the file types is a snapshot.
         */
        return new ArrayList<>(allFileTypes);
    }

    /**
     * Gets the custom file types defined by users.
     *
     * @return A list of file types, possibly empty.
     *
     * @throws UserDefinedFileTypesException if there is a problem accessing the
     *                                       file types.
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
     * Loads or re-loads the custom file types defined by Autopsy and by users.
     *
     * @throws UserDefinedFileTypesException if there is a problem loading the
     *                                       file types.
     */
    private void loadFileTypes() throws UserDefinedFileTypesException {
        allFileTypes.clear();
        userDefinedFileTypes.clear();

        /**
         * Load the predefined types first so that they can be overwritten by
         * any user-defined types with the same names.
         */
        loadPredefinedFileTypes();
        loadUserDefinedFileTypes();
    }

    /**
     * Loads or re-loads the custom file types defined by Autopsy.
     *
     * @throws UserDefinedFileTypesException if there is a problem loading the
     *                                       file types.
     */
    private void loadPredefinedFileTypes() throws UserDefinedFileTypesException {
        byte[] byteArray;
        FileType fileType;

        try {
            /*
             * Add type for xml.
             */
            List<Signature> signatureList;
            signatureList = new ArrayList<>();
            signatureList.add(new Signature("<?xml", 0L)); //NON-NLS
            fileType = new FileType("text/xml", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for gzip.
             */
            byteArray = DatatypeConverter.parseHexBinary("1F8B");  //NON-NLS  
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("application/x-gzip", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for wk1.
             */
            byteArray = DatatypeConverter.parseHexBinary("0000020006040600080000000000"); //NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("application/x-123", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for Radiance images.
             */
            byteArray = DatatypeConverter.parseHexBinary("233F52414449414E43450A");//NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("image/vnd.radiance", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for dcx images.
             */
            byteArray = DatatypeConverter.parseHexBinary("B168DE3A"); //NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("image/x-dcx", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for ics images.
             */
            signatureList.clear();
            signatureList.add(new Signature("icns", 0L)); //NON-NLS
            fileType = new FileType("image/x-icns", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for pict images.
             */
            byteArray = DatatypeConverter.parseHexBinary("001102FF"); //NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 522L));
            fileType = new FileType("image/x-pict", signatureList); //NON-NLS
            allFileTypes.add(fileType);
            byteArray = DatatypeConverter.parseHexBinary("1100"); //NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 522L));
            fileType = new FileType("image/x-pict", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for pam.
             */
            signatureList.clear();
            signatureList.add(new Signature("P7", 0L)); //NON-NLS
            fileType = new FileType("image/x-portable-arbitrarymap", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for pfm.
             */
            signatureList.clear();
            signatureList.add(new Signature("PF", 0L)); //NON-NLS
            fileType = new FileType("image/x-portable-floatmap", signatureList); //NON-NLS
            allFileTypes.add(fileType);
            signatureList.clear();
            signatureList.add(new Signature("Pf", 0L)); //NON-NLS
            fileType = new FileType("image/x-portable-floatmap", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for tga.
             */
            byteArray = DatatypeConverter.parseHexBinary("54525545564953494F4E2D5846494C452E00"); //NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 17, false));
            fileType = new FileType("image/x-tga", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for ilbm.
             */
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            signatureList.add(new Signature("ILBM", 8L)); //NON-NLS
            fileType = new FileType("image/x-ilbm", signatureList); //NON-NLS
            allFileTypes.add(fileType);
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            signatureList.add(new Signature("PBM", 8L)); //NON-NLS
            fileType = new FileType("image/x-ilbm", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for webp.
             */
            signatureList.clear();
            signatureList.add(new Signature("RIFF", 0L)); //NON-NLS
            signatureList.add(new Signature("WEBP", 8L)); //NON-NLS
            fileType = new FileType("image/webp", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for aiff.
             */
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            signatureList.add(new Signature("AIFF", 8L)); //NON-NLS
            fileType = new FileType("audio/aiff", signatureList); //NON-NLS
            allFileTypes.add(fileType);
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            signatureList.add(new Signature("AIFC", 8L)); //NON-NLS
            fileType = new FileType("audio/aiff", signatureList); //NON-NLS
            allFileTypes.add(fileType);
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            signatureList.add(new Signature("8SVX", 8L)); //NON-NLS
            fileType = new FileType("audio/aiff", signatureList); //NON-NLS
            allFileTypes.add(fileType);

            /*
             * Add type for iff.
             */
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            fileType = new FileType("application/x-iff", signatureList); //NON-NLS
            allFileTypes.add(fileType);

        } catch (IllegalArgumentException ex) {
            /*
             * parseHexBinary() throws this if the argument passed in is not hex
             */
            throw new UserDefinedFileTypesException("Error creating predefined file types", ex); //
        }
    }

    /**
     * Loads or re-loads the custom file types defined by users.
     *
     * @throws UserDefinedFileTypesException if there is a problem loading the
     *                                       file types.
     */
    private void loadUserDefinedFileTypes() throws UserDefinedFileTypesException {
        try {
            File serialized = new File(getFileTypeDefinitionsFilePath(SERIALIZED_SETTINGS_FILE));
            if (serialized.exists()) {
                for (FileType fileType : readSerializedFileTypes()) {
                    addUserDefinedFileType(fileType);
                }
            } else {
                String filePath = getFileTypeDefinitionsFilePath(XML_SETTINGS_FILE);
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
            allFileTypes.clear();
            userDefinedFileTypes.clear();
            throw new UserDefinedFileTypesException("UserDefinedFileTypesManager.loadFileTypes.errorMessage", ex);
        }
    }

    /**
     * Adds a custom file type to both internal file type lists.
     *
     * @param fileType The file type to add.
     */
    private void addUserDefinedFileType(FileType fileType) {
        userDefinedFileTypes.add(fileType);
        allFileTypes.add(fileType);
    }

    /**
     * Sets the user-defined custom file types.
     *
     * @param newFileTypes A list of user-defined file types.
     */
    synchronized void setUserDefinedFileTypes(List<FileType> newFileTypes) throws UserDefinedFileTypesException {
        String filePath = getFileTypeDefinitionsFilePath(SERIALIZED_SETTINGS_FILE);
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
     * Writes a collection of custom file types to disk.
     *
     * @param fileTypes A collection of file types.
     * @param filePath  The path to the destination file.
     *
     * @throws UserDefinedFileTypesException if there is a problem writing the
     *                                       file types.
     */
    private static void writeFileTypes(List<FileType> fileTypes, String filePath) throws UserDefinedFileTypesException {
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(filePath))) {
            UserDefinedFileTypesSettings settings = new UserDefinedFileTypesSettings(fileTypes);
            out.writeObject(settings);
        } catch (IOException ex) {
            throw new UserDefinedFileTypesException(String.format("Failed to write settings to %s", filePath), ex); //NON-NLS
        }
    }

    /**
     * Reads a collection of custom file types from disk.
     *
     * @param filePath The path of the file from which the custom file types are
     *                 to be read.
     *
     * @return The custom file types.
     *
     * @throws UserDefinedFileTypesException if there is a problem reading the
     *                                       file types.
     */
    private static List<FileType> readSerializedFileTypes() throws UserDefinedFileTypesException {
        File serializedDefs = new File(getFileTypeDefinitionsFilePath(SERIALIZED_SETTINGS_FILE));
        try {
            try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(serializedDefs))) {
                UserDefinedFileTypesSettings filesSetsSettings = (UserDefinedFileTypesSettings) in.readObject();
                return filesSetsSettings.getUserDefinedFileTypes();
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new UserDefinedFileTypesException("Couldn't read serialized settings.", ex); //NON-NLS
        }
    }

    /**
     * Provides a mechanism for reading a set of custom file type definitions
     * from an XML file.
     */
    private static class XMLDefinitionsReader {

        /**
         * Reads a set of custom file type definitions from an XML file.
         *
         * @param filePath The path to the XML file.
         *
         * @return A collection of custom file types read from the XML file.
         *
         * @throws IOException                  if there is problem reading the
         *                                      XML file.
         * @throws SAXException                 if there is a problem parsing
         *                                      the XML file.
         * @throws ParserConfigurationException if there is a problem parsing
         *                                      the XML file.
         */
        private static List<FileType> readFileTypes(String filePath) throws IOException, SAXException, ParserConfigurationException {
            List<FileType> fileTypes = new ArrayList<>();
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
         * Gets a custom file type definition from a file type XML element.
         *
         * @param fileTypeElem The XML element.
         *
         * @return A file type object.
         *
         * @throws IllegalArgumentException if there is a problem parsing the
         *                                  file type.
         * @throws NumberFormatException    if there is a problem parsing the
         *                                  file type.
         */
        private static FileType parseFileType(Element fileTypeElem) throws IllegalArgumentException, NumberFormatException {
            String mimeType = XMLDefinitionsReader.parseMimeType(fileTypeElem);
            Signature signature = XMLDefinitionsReader.parseSignature(fileTypeElem);
            // File type definitions in the XML file were written prior to the 
            // implementation of multiple signatures per type. 
            List<Signature> sigList = new ArrayList<>();
            sigList.add(signature);
            return new FileType(mimeType, sigList);
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
         * Private constructor suppresses creation of instances of this utility
         * class.
         */
        private XMLDefinitionsReader() {
        }

    }

    /**
     * .An exception thrown by the custom file types manager.
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
