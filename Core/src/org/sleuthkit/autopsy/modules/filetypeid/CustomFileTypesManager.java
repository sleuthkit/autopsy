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
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
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
final class CustomFileTypesManager {

    private static final String SERIALIZED_SETTINGS_FILE = "UserFileTypeDefinitions.settings"; //NON-NLS
    private static final String XML_SETTINGS_FILE = "UserFileTypeDefinitions.xml"; //NON-NLS
    private static final String FILE_TYPES_TAG_NAME = "FileTypes"; //NON-NLS
    private static final String FILE_TYPE_TAG_NAME = "FileType"; //NON-NLS
    private static final String MIME_TYPE_TAG_NAME = "MimeType"; //NON-NLS
    private static final String SIGNATURE_TAG_NAME = "Signature"; //NON-NLS
    private static final String SIGNATURE_TYPE_ATTRIBUTE = "type"; //NON-NLS
    private static final String BYTES_TAG_NAME = "Bytes"; //NON-NLS
    private static final String OFFSET_TAG_NAME = "Offset"; //NON-NLS
    private static final String RELATIVE_ATTRIBUTE = "RelativeToStart"; //NON-NLS
    private static CustomFileTypesManager instance;
    private final List<FileType> autopsyDefinedFileTypes = new ArrayList<>();
    private List<FileType> userDefinedFileTypes = new ArrayList<>();

    /**
     * Gets the singleton manager of the custom file types defined by Autopsy
     * and by users.
     *
     * @return The custom file types manager singleton.
     *
     * @throws CustomFileTypesException if there is a problem loading the custom
     *                                  file types.
     */
    synchronized static CustomFileTypesManager getInstance() throws CustomFileTypesException {
        if (null == instance) {
            instance = new CustomFileTypesManager();
            try {
                instance.loadUserDefinedFileTypes();
                instance.createAutopsyDefinedFileTypes();
            } catch (CustomFileTypesException ex) {
                instance = null;
                throw ex;
            }
        }
        return instance;
    }

    /**
     * Constructs a manager for the custom file types defined by Autopsy and by
     * users.
     */
    private CustomFileTypesManager() {
    }

    /**
     * Gets the custom file types defined by Autopsy and by users.
     *
     * @return A list of custom file types, possibly empty.
     */
    synchronized List<FileType> getFileTypes() {
        /**
         * It is safe to return references instead of copies in this snapshot
         * because FileType objects are immutable.
         */
        List<FileType> customTypes = new ArrayList<>(userDefinedFileTypes);
        customTypes.addAll(autopsyDefinedFileTypes);
        return customTypes;
    }

    /**
     * Gets the custom file types defined by Autopsy.
     *
     * @return A list of custom file types, possibly empty.
     */
    synchronized List<FileType> getAutopsyDefinedFileTypes() {
        /**
         * It is safe to return references instead of copies in this snapshot
         * because FileType objects are immutable.
         */
        return new ArrayList<>(autopsyDefinedFileTypes);
    }

    /**
     * Gets the user-defined custom file types.
     *
     * @return A list of file types, possibly empty.
     */
    synchronized List<FileType> getUserDefinedFileTypes() {
        /**
         * It is safe to return references instead of copies in this snapshot
         * because FileType objects are immutable.
         */
        return new ArrayList<>(userDefinedFileTypes);
    }

    /**
     * Sets the user-defined custom file types.
     *
     * @param newFileTypes A list of user-defined file types.
     *
     * @throws CustomFileTypesException if there is a problem setting the file
     *                                  types.
     */
    synchronized void setUserDefinedFileTypes(List<FileType> newFileTypes) throws CustomFileTypesException {
        String filePath = getFileTypeDefinitionsFilePath(SERIALIZED_SETTINGS_FILE);
        writeSerializedFileTypes(newFileTypes, filePath);
        userDefinedFileTypes = newFileTypes;
    }

    /**
     * Creates the custom file types defined by Autopsy.
     *
     * @throws CustomFileTypesException if there is a problem creating the file
     *                                  types.
     */
    private void createAutopsyDefinedFileTypes() throws CustomFileTypesException {
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
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for gzip.
             */
            byteArray = Hex.decodeHex("1F8B");  //NON-NLS  
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("application/x-gzip", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for wk1.
             */
            byteArray = Hex.decodeHex("0000020006040600080000000000"); //NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("application/x-123", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for Radiance images.
             */
            byteArray = Hex.decodeHex("233F52414449414E43450A");//NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("image/vnd.radiance", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for dcx images.
             */
            byteArray = Hex.decodeHex("B168DE3A"); //NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("image/x-dcx", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for ics images.
             */
            signatureList.clear();
            signatureList.add(new Signature("icns", 0L)); //NON-NLS
            fileType = new FileType("image/x-icns", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for pict images.
             */
            byteArray = Hex.decodeHex("001102FF"); //NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 522L));
            fileType = new FileType("image/x-pict", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * NOTE: see JIRA-4269. This MIME type seems to match a lot of
             * random file types, including ZIP archives. As a result those
             * files get assigned this MIME type instead of having their MIME
             * type detected by Tika. byteArray =
             * DatatypeConverter.parseHexBinary("1100"); //NON-NLS
             * signatureList.clear(); signatureList.add(new Signature(byteArray,
             * 522L)); fileType = new FileType("image/x-pict", signatureList);
             * //NON-NLS
            autopsyDefinedFileTypes.add(fileType);
             */

 /*
             * Add type for pam.
             */
            signatureList.clear();
            signatureList.add(new Signature("P7", 0L)); //NON-NLS
            fileType = new FileType("image/x-portable-arbitrarymap", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for pfm.
             */
            signatureList.clear();
            signatureList.add(new Signature("PF", 0L)); //NON-NLS
            fileType = new FileType("image/x-portable-floatmap", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);
            signatureList.clear();
            signatureList.add(new Signature("Pf", 0L)); //NON-NLS
            fileType = new FileType("image/x-portable-floatmap", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for tga.
             */
            byteArray = Hex.decodeHex("54525545564953494F4E2D5846494C452E00"); //NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 17, false));
            fileType = new FileType("image/x-tga", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for ilbm.
             */
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            signatureList.add(new Signature("ILBM", 8L)); //NON-NLS
            fileType = new FileType("image/x-ilbm", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            signatureList.add(new Signature("PBM", 8L)); //NON-NLS
            fileType = new FileType("image/x-ilbm", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for webp.
             */
            signatureList.clear();
            signatureList.add(new Signature("RIFF", 0L)); //NON-NLS
            signatureList.add(new Signature("WEBP", 8L)); //NON-NLS
            fileType = new FileType("image/webp", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for aiff.
             */
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            signatureList.add(new Signature("AIFF", 8L)); //NON-NLS
            fileType = new FileType("audio/aiff", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            signatureList.add(new Signature("AIFC", 8L)); //NON-NLS
            fileType = new FileType("audio/aiff", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            signatureList.add(new Signature("8SVX", 8L)); //NON-NLS
            fileType = new FileType("audio/aiff", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for iff.
             */
            signatureList.clear();
            signatureList.add(new Signature("FORM", 0L)); //NON-NLS
            fileType = new FileType("application/x-iff", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for .tec files with leading End Of Image marker (JFIF
             * JPEG)
             */
            byteArray = Hex.decodeHex("FFD9FFD8"); //NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("image/jpeg", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

            /*
             * Add type for Windows NT registry files with leading End Of Image marker (JFIF
             * JPEG)
             */
            byteArray = Hex.decodeHex("72656766"); //NON-NLS
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 0L));
            fileType = new FileType("application/x.windows-registry", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);
            
            /*
            * Add custom file type for hdb files that can be found in android os on the system volume
            * in the hdic folder
            */
            byteArray = DatatypeConverter.parseHexBinary("CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC");
            signatureList.clear();
            signatureList.add(new Signature(byteArray, 8L));
            fileType = new FileType("application/x.android-hdb", signatureList);
            autopsyDefinedFileTypes.add(fileType);
            
            /**
             * Add custom type for fixed-size VHDs.
             */
            signatureList.clear();
            signatureList.add(new Signature("conectix", 511L, false)); //NON-NLS
            fileType = new FileType("application/x-vhd", signatureList); //NON-NLS
            autopsyDefinedFileTypes.add(fileType);

        } catch (DecoderException ex) {
            /*
             * decodeHex() throws this if an odd number of characters or illegal
             * characters are supplied
             */
            throw new CustomFileTypesException("Error creating Autopsy defined custom file types", ex); //NON-NLS
        }
    }

    /**
     * Loads the custom file types defined by users.
     *
     * @throws CustomFileTypesException if there is a problem loading the file
     *                                  types.
     */
    private void loadUserDefinedFileTypes() throws CustomFileTypesException {
        userDefinedFileTypes.clear();
        String filePath = getFileTypeDefinitionsFilePath(SERIALIZED_SETTINGS_FILE);
        if (new File(filePath).exists()) {
            userDefinedFileTypes = readSerializedFileTypes(filePath);
        } else {
            filePath = getFileTypeDefinitionsFilePath(XML_SETTINGS_FILE);
            if (new File(filePath).exists()) {
                userDefinedFileTypes = readFileTypesXML(filePath);
            }
        }
    }

    /**
     * Writes serialized custom file types to a file.
     *
     * @param fileTypes A collection of file types.
     * @param filePath  The path to the file.
     *
     * @throws CustomFileTypesException if there is a problem writing the file
     *                                  types.
     */
    private static void writeSerializedFileTypes(List<FileType> fileTypes, String filePath) throws CustomFileTypesException {
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(filePath))) {
            UserDefinedFileTypesSettings settings = new UserDefinedFileTypesSettings(fileTypes);
            out.writeObject(settings);
        } catch (IOException ex) {
            throw new CustomFileTypesException(String.format("Failed to write settings to %s", filePath), ex); //NON-NLS
        }
    }

    /**
     * Reads serialized custom file types from a file.
     *
     * @param filePath The path to the file.
     *
     * @return The custom file types.
     *
     * @throws CustomFileTypesException if there is a problem reading the file
     *                                  types.
     */
    private static List<FileType> readSerializedFileTypes(String filePath) throws CustomFileTypesException {
        File serializedDefs = new File(filePath);
        try {
            try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(serializedDefs))) {
                UserDefinedFileTypesSettings filesSetsSettings = (UserDefinedFileTypesSettings) in.readObject();
                return filesSetsSettings.getUserDefinedFileTypes();
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new CustomFileTypesException(String.format("Failed to read settings from %s", filePath), ex); //NON-NLS
        }
    }

    /**
     * Reads custom file type definitions from an XML file.
     *
     * @param filePath The path to the file.
     *
     * @return A collection of custom file types read from the XML file.
     *
     * @throws IOException                  if there is problem reading the XML
     *                                      file.
     * @throws SAXException                 if there is a problem parsing the
     *                                      XML file.
     * @throws ParserConfigurationException if there is a problem parsing the
     *                                      XML file.
     */
    private static List<FileType> readFileTypesXML(String filePath) throws CustomFileTypesException {
        try {
            List<FileType> fileTypes = new ArrayList<>();
            Document doc = XMLUtil.loadDocument(filePath);
            if (doc != null) {
                Element fileTypesElem = doc.getDocumentElement();
                if (fileTypesElem != null && fileTypesElem.getNodeName().equals(FILE_TYPES_TAG_NAME)) {
                    NodeList fileTypeElems = fileTypesElem.getElementsByTagName(FILE_TYPE_TAG_NAME);
                    for (int i = 0; i < fileTypeElems.getLength(); ++i) {
                        Element fileTypeElem = (Element) fileTypeElems.item(i);
                        FileType fileType = parseFileType(fileTypeElem);
                        fileTypes.add(fileType);
                    }
                }
            }
            return fileTypes;
        } catch (IOException | ParserConfigurationException | SAXException | DecoderException ex) {
            throw new CustomFileTypesException(String.format("Failed to read ssettings from %s", filePath), ex); //NON-NLS
        }
    }

    /**
     * Gets a custom file type definition from a file type XML element.
     *
     * @param fileTypeElem The XML element.
     *
     * @return A file type object.
     *
     * @throws IllegalArgumentException if there is a problem parsing the file
     *                                  type.
     * @throws NumberFormatException    if there is a problem parsing the file
     *                                  type.
     */
    private static FileType parseFileType(Element fileTypeElem) throws DecoderException, NumberFormatException {
        String mimeType = parseMimeType(fileTypeElem);
        Signature signature = parseSignature(fileTypeElem);
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
    private static Signature parseSignature(Element fileTypeElem) throws DecoderException, NumberFormatException {
        NodeList signatureElems = fileTypeElem.getElementsByTagName(SIGNATURE_TAG_NAME);
        Element signatureElem = (Element) signatureElems.item(0);

        String sigTypeAttribute = signatureElem.getAttribute(SIGNATURE_TYPE_ATTRIBUTE);
        Signature.Type signatureType = Signature.Type.valueOf(sigTypeAttribute);

        String sigBytesString = getChildElementTextContent(signatureElem, BYTES_TAG_NAME);
        byte[] signatureBytes = Hex.decodeHex(sigBytesString);

        Element offsetElem = (Element) signatureElem.getElementsByTagName(OFFSET_TAG_NAME).item(0);
        String offsetString = offsetElem.getTextContent();
        long offset = Long.parseLong(offsetString);

        boolean isRelativeToStart;
        String relativeString = offsetElem.getAttribute(RELATIVE_ATTRIBUTE);
        if (null == relativeString || relativeString.equals("")) {
            isRelativeToStart = true;
        } else {
            isRelativeToStart = Boolean.parseBoolean(relativeString);
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
     * .An exception thrown by the custom file types manager.
     */
    static class CustomFileTypesException extends Exception {

        private static final long serialVersionUID = 1L;

        CustomFileTypesException(String message) {
            super(message);
        }

        CustomFileTypesException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }

}
