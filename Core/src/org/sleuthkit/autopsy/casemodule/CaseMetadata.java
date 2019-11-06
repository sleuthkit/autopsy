/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Provides access to the case metadata stored in the case metadata file.
 */
public final class CaseMetadata {

    private static final String FILE_EXTENSION = ".aut";
    private static final String DATE_FORMAT_STRING = "yyyy/MM/dd HH:mm:ss (z)";
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_STRING, Locale.US);

    /*
     * Fields from schema version 1
     */
    private static final String SCHEMA_VERSION_ONE = "1.0";
    private final static String ROOT_ELEMENT_NAME = "AutopsyCase"; //NON-NLS
    private final static String SCHEMA_VERSION_ELEMENT_NAME = "SchemaVersion"; //NON-NLS
    private final static String CREATED_DATE_ELEMENT_NAME = "CreatedDate"; //NON-NLS
    private final static String AUTOPSY_VERSION_ELEMENT_NAME = "AutopsyCreatedVersion"; //NON-NLS
    private final static String CASE_ELEMENT_NAME = "Case"; //NON-NLS
    private final static String CASE_NAME_ELEMENT_NAME = "Name"; //NON-NLS
    private final static String CASE_NUMBER_ELEMENT_NAME = "Number"; //NON-NLS
    private final static String EXAMINER_ELEMENT_NAME = "Examiner"; //NON-NLS
    private final static String CASE_TYPE_ELEMENT_NAME = "CaseType"; //NON-NLS
    private final static String CASE_DATABASE_NAME_ELEMENT_NAME = "DatabaseName"; //NON-NLS
    private final static String TEXT_INDEX_NAME_ELEMENT = "TextIndexName"; //NON-NLS

    /*
     * Fields from schema version 2
     */
    private static final String SCHEMA_VERSION_TWO = "2.0";
    private final static String AUTOPSY_CREATED_BY_ELEMENT_NAME = "CreatedByAutopsyVersion"; //NON-NLS
    private final static String CASE_DB_ABSOLUTE_PATH_ELEMENT_NAME = "Database"; //NON-NLS
    private final static String TEXT_INDEX_ELEMENT = "TextIndex"; //NON-NLS

    /*
     * Fields from schema version 3
     */
    private static final String SCHEMA_VERSION_THREE = "3.0";
    private final static String CASE_DISPLAY_NAME_ELEMENT_NAME = "DisplayName"; //NON-NLS
    private final static String CASE_DB_NAME_RELATIVE_ELEMENT_NAME = "CaseDatabase"; //NON-NLS

    /*
     * Fields from schema version 4
     */
    private static final String SCHEMA_VERSION_FOUR = "4.0";
    private final static String EXAMINER_ELEMENT_PHONE = "ExaminerPhone"; //NON-NLS  
    private final static String EXAMINER_ELEMENT_EMAIL = "ExaminerEmail"; //NON-NLS
    private final static String CASE_ELEMENT_NOTES = "CaseNotes"; //NON-NLS

    /*
     * Fields from schema version 5
     */
    private static final String SCHEMA_VERSION_FIVE = "5.0";
    private final static String ORIGINAL_CASE_ELEMENT_NAME = "OriginalCase"; //NON-NLS  

    /*
     * Unread fields, regenerated on save.
     */
    private final static String MODIFIED_DATE_ELEMENT_NAME = "ModifiedDate"; //NON-NLS
    private final static String AUTOPSY_SAVED_BY_ELEMENT_NAME = "SavedByAutopsyVersion"; //NON-NLS

    private final static String CURRENT_SCHEMA_VERSION = SCHEMA_VERSION_FIVE;

    private final Path metadataFilePath;
    private Case.CaseType caseType;
    private String caseName;
    private CaseDetails caseDetails;
    private String caseDatabaseName;
    private String caseDatabasePath; // Legacy
    private String textIndexName; // Legacy
    private String createdDate;
    private String createdByVersion;
    private CaseMetadata originalMetadata = null; // For portable cases

    /**
     * Gets the file extension used for case metadata files.
     *
     * @return The file extension.
     */
    public static String getFileExtension() {
        return FILE_EXTENSION;
    }

    /**
     * Gets the date format used for dates in case metadata.
     *
     * @return The date format.
     */
    public static DateFormat getDateFormat() {
        return new SimpleDateFormat(DATE_FORMAT_STRING, Locale.US);
    }

    /**
     * Constructs a CaseMetadata object for a new case. The metadata is not
     * persisted to the case metadata file until writeFile or a setX method is
     * called.
     *
     * @param caseType      The type of case.
     * @param caseDirectory The case directory.
     * @param caseName      The immutable name of the case.
     * @param caseDetails   The details for the case
     */
    CaseMetadata(Case.CaseType caseType, String caseDirectory, String caseName, CaseDetails caseDetails) {
        this(caseType, caseDirectory, caseName, caseDetails, null);
    }

    /**
     * Constructs a CaseMetadata object for a new case. The metadata is not
     * persisted to the case metadata file until writeFile or a setX method is
     * called.
     *
     * @param caseType         The type of case.
     * @param caseDirectory    The case directory.
     * @param caseName         The immutable name of the case.
     * @param caseDetails      The details for the case
     * @param originalMetadata The metadata object from the original case
     */
    CaseMetadata(Case.CaseType caseType, String caseDirectory, String caseName, CaseDetails caseDetails, CaseMetadata originalMetadata) {
        metadataFilePath = Paths.get(caseDirectory, caseDetails.getCaseDisplayName() + FILE_EXTENSION);
        this.caseType = caseType;
        this.caseName = caseName;
        this.caseDetails = caseDetails;
        caseDatabaseName = "";
        caseDatabasePath = "";
        textIndexName = "";
        createdByVersion = Version.getVersion();
        createdDate = CaseMetadata.DATE_FORMAT.format(new Date());
        this.originalMetadata = originalMetadata;
    }

    /**
     * Constructs a CaseMetadata object for an existing case. The metadata is
     * read from an existing case metadata file.
     *
     * @param metadataFilePath The full path to the case metadata file.
     *
     * @throws CaseMetadataException If the new case metadata file cannot be
     *                               read.
     */
    public CaseMetadata(Path metadataFilePath) throws CaseMetadataException {
        this.metadataFilePath = metadataFilePath;
        readFromFile();
    }

    /**
     * Locate the case meta data file in the supplied directory. If the file
     * does not exist, null is returned.
     *
     * @param directoryPath Directory path to search.
     *
     * @return Case metadata file path or null.
     */
    public static Path getCaseMetadataFilePath(Path directoryPath) {
        final File[] files = directoryPath.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                final String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(CaseMetadata.getFileExtension()) && file.isFile()) {
                    return file.toPath();
                }
            }
        }
        return null;
    }

    /**
     * Gets the full path to the case metadata file.
     *
     * @return The path to the metadata file
     */
    Path getFilePath() {
        return metadataFilePath;
    }

    /**
     * Gets the case directory.
     *
     * @return The case directory.
     */
    public String getCaseDirectory() {
        return metadataFilePath.getParent().toString();
    }

    /**
     * Gets the case type.
     *
     * @return The case type.
     */
    public Case.CaseType getCaseType() {
        return caseType;
    }

    /**
     * Gets the unique and immutable case name.
     *
     * @return The case display name.
     */
    public String getCaseName() {
        return caseName;
    }

    /**
     * Get current values for the case details which are user modifiable.
     *
     * @return the case details
     */
    public CaseDetails getCaseDetails() {
        return caseDetails;
    }

    /**
     * Gets the case display name.
     *
     * @return The case display name.
     */
    public String getCaseDisplayName() {
        return caseDetails.getCaseDisplayName();
    }

    void setCaseDetails(CaseDetails newCaseDetails) throws CaseMetadataException {
        CaseDetails oldCaseDetails = this.caseDetails;
        this.caseDetails = newCaseDetails;
        try {
            writeToFile();
        } catch (CaseMetadataException ex) {
            this.caseDetails = oldCaseDetails;
            throw ex;
        }
    }

    /**
     * Gets the case number.
     *
     * @return The case number, may be empty.
     */
    public String getCaseNumber() {
        return caseDetails.getCaseNumber();
    }

    /**
     * Gets the examiner.
     *
     * @return The examiner, may be empty.
     */
    public String getExaminer() {
        return caseDetails.getExaminerName();
    }

    public String getExaminerPhone() {
        return caseDetails.getExaminerPhone();
    }

    public String getExaminerEmail() {
        return caseDetails.getExaminerEmail();
    }

    public String getCaseNotes() {
        return caseDetails.getCaseNotes();
    }

    /**
     * Gets the name of the case database.
     *
     * @return The case database name, may be empty.
     */
    public String getCaseDatabaseName() {
        return caseDatabaseName;
    }

    /**
     * Sets the name of the case database.
     *
     * @param caseDatabaseName The case database name.
     *
     * @throws CaseMetadataException If the operation fails.
     */
    void setCaseDatabaseName(String caseDatabaseName) throws CaseMetadataException {
        String oldCaseDatabaseName = this.caseDatabaseName;
        this.caseDatabaseName = caseDatabaseName;
        try {
            writeToFile();
        } catch (CaseMetadataException ex) {
            this.caseDatabaseName = oldCaseDatabaseName;
            throw ex;
        }
    }

    /**
     * Gets the text index name. This is a legacy field and will be empty for
     * cases created with Autopsy 4.4.0 and above.
     *
     * @return The name of the text index for the case, may be empty.
     */
    public String getTextIndexName() {
        return textIndexName;
    }

    /**
     * Gets the date the case was created.
     *
     * @return The date this case was created, as a string.
     */
    public String getCreatedDate() {
        return createdDate;
    }

    /**
     * Sets the date the case was created. Used for preserving the case creation
     * date during single-user to multi-user case conversion.
     *
     * @param createdDate The date the case was created, as a string.
     *
     * @throws CaseMetadataException If the operation fails.
     */
    void setCreatedDate(String createdDate) throws CaseMetadataException {
        String oldCreatedDate = createdDate;
        this.createdDate = createdDate;
        try {
            writeToFile();
        } catch (CaseMetadataException ex) {
            this.createdDate = oldCreatedDate;
            throw ex;
        }
    }

    /**
     * Gets the Autopsy version that created the case.
     *
     * @return A build identifier.
     */
    String getCreatedByVersion() {
        return createdByVersion;
    }

    /**
     * Sets the Autopsy version that created the case. Used for preserving this
     * metadata during single-user to multi-user case conversion.
     *
     * @param buildVersion A build version identifier.
     *
     * @throws CaseMetadataException If the operation fails.
     */
    void setCreatedByVersion(String buildVersion) throws CaseMetadataException {
        String oldCreatedByVersion = this.createdByVersion;
        this.createdByVersion = buildVersion;
        try {
            writeToFile();
        } catch (CaseMetadataException ex) {
            this.createdByVersion = oldCreatedByVersion;
            throw ex;
        }
    }

    /**
     * Writes the case metadata to the metadata file.
     *
     * @throws CaseMetadataException If there is an error writing to the case
     *                               metadata file.
     */
    void writeToFile() throws CaseMetadataException {
        try {
            /*
             * Create the XML DOM.
             */
            Document doc = XMLUtil.createDocument();
            createXMLDOM(doc);
            doc.normalize();

            /*
             * Prepare the DOM for pretty printing to the metadata file.
             */
            Source source = new DOMSource(doc);
            StringWriter stringWriter = new StringWriter();
            Result streamResult = new StreamResult(stringWriter);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes"); //NON-NLS
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2"); //NON-NLS
            transformer.transform(source, streamResult);

            /*
             * Write the DOM to the metadata file.  Add UTF-8 Characterset so it writes to the file
             * correctly for non-latin characters
             */
            try (BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(metadataFilePath.toFile()), StandardCharsets.UTF_8))) {
                fileWriter.write(stringWriter.toString());
                fileWriter.flush();
            }

        } catch (ParserConfigurationException | TransformerException | IOException ex) {
            throw new CaseMetadataException(String.format("Error writing to case metadata file %s", metadataFilePath), ex);
        }
    }

    /*
     * Creates an XML DOM from the case metadata.
     */
    private void createXMLDOM(Document doc) {
        /*
         * Create the root element and its children.
         */
        Element rootElement = doc.createElement(ROOT_ELEMENT_NAME);
        doc.appendChild(rootElement);
        createChildElement(doc, rootElement, SCHEMA_VERSION_ELEMENT_NAME, CURRENT_SCHEMA_VERSION);
        createChildElement(doc, rootElement, CREATED_DATE_ELEMENT_NAME, createdDate);
        createChildElement(doc, rootElement, MODIFIED_DATE_ELEMENT_NAME, DATE_FORMAT.format(new Date()));
        createChildElement(doc, rootElement, AUTOPSY_CREATED_BY_ELEMENT_NAME, createdByVersion);
        createChildElement(doc, rootElement, AUTOPSY_SAVED_BY_ELEMENT_NAME, Version.getVersion());
        Element caseElement = doc.createElement(CASE_ELEMENT_NAME);
        rootElement.appendChild(caseElement);

        /*
         * Create the children of the case element.
         */
        createCaseElements(doc, caseElement, this);

        /*
         * Add original case element
         */
        Element originalCaseElement = doc.createElement(ORIGINAL_CASE_ELEMENT_NAME);
        rootElement.appendChild(originalCaseElement);
        if (originalMetadata != null) {
            createChildElement(doc, originalCaseElement, CREATED_DATE_ELEMENT_NAME, originalMetadata.createdDate);
            Element originalCaseDetailsElement = doc.createElement(CASE_ELEMENT_NAME);
            originalCaseElement.appendChild(originalCaseDetailsElement);
            createCaseElements(doc, originalCaseDetailsElement, originalMetadata);
        }

    }

    /**
     * Write the case element children for the given metadata object
     *
     * @param doc             The document.
     * @param caseElement     The case element parent
     * @param metadataToWrite The CaseMetadata object to read from
     */
    private void createCaseElements(Document doc, Element caseElement, CaseMetadata metadataToWrite) {
        CaseDetails caseDetailsToWrite = metadataToWrite.caseDetails;
        createChildElement(doc, caseElement, CASE_NAME_ELEMENT_NAME, metadataToWrite.caseName);
        createChildElement(doc, caseElement, CASE_DISPLAY_NAME_ELEMENT_NAME, caseDetailsToWrite.getCaseDisplayName());
        createChildElement(doc, caseElement, CASE_NUMBER_ELEMENT_NAME, caseDetailsToWrite.getCaseNumber());
        createChildElement(doc, caseElement, EXAMINER_ELEMENT_NAME, caseDetailsToWrite.getExaminerName());
        createChildElement(doc, caseElement, EXAMINER_ELEMENT_PHONE, caseDetailsToWrite.getExaminerPhone());
        createChildElement(doc, caseElement, EXAMINER_ELEMENT_EMAIL, caseDetailsToWrite.getExaminerEmail());
        createChildElement(doc, caseElement, CASE_ELEMENT_NOTES, caseDetailsToWrite.getCaseNotes());
        createChildElement(doc, caseElement, CASE_TYPE_ELEMENT_NAME, metadataToWrite.caseType.toString());
        createChildElement(doc, caseElement, CASE_DB_ABSOLUTE_PATH_ELEMENT_NAME, metadataToWrite.caseDatabasePath);
        createChildElement(doc, caseElement, CASE_DB_NAME_RELATIVE_ELEMENT_NAME, metadataToWrite.caseDatabaseName);
        createChildElement(doc, caseElement, TEXT_INDEX_ELEMENT, metadataToWrite.textIndexName);
    }

    /**
     * Creates an XML element for the case metadata XML DOM.
     *
     * @param doc            The document.
     * @param parentElement  The parent element of the element to be created.
     * @param elementName    The name of the element to be created.
     * @param elementContent The text content of the element to be created, may
     *                       be empty.
     */
    private void createChildElement(Document doc, Element parentElement, String elementName, String elementContent) {
        Element element = doc.createElement(elementName);
        element.appendChild(doc.createTextNode(elementContent));
        parentElement.appendChild(element);
    }

    /**
     * Reads the case metadata from the metadata file.
     *
     * @throws CaseMetadataException If there is an error reading from the case
     *                               metadata file.
     */
    private void readFromFile() throws CaseMetadataException {
        try {
            /*
             * Parse the file into an XML DOM and get the root element.
             */
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(this.getFilePath().toFile());
            doc.getDocumentElement().normalize();
            Element rootElement = doc.getDocumentElement();
            if (!rootElement.getNodeName().equals(ROOT_ELEMENT_NAME)) {
                throw new CaseMetadataException("Case metadata file corrupted");
            }

            /*
             * Get the content of the relevant children of the root element.
             */
            String schemaVersion = getElementTextContent(rootElement, SCHEMA_VERSION_ELEMENT_NAME, true);
            this.createdDate = getElementTextContent(rootElement, CREATED_DATE_ELEMENT_NAME, true);
            if (schemaVersion.equals(SCHEMA_VERSION_ONE)) {
                this.createdByVersion = getElementTextContent(rootElement, AUTOPSY_VERSION_ELEMENT_NAME, true);
            } else {
                this.createdByVersion = getElementTextContent(rootElement, AUTOPSY_CREATED_BY_ELEMENT_NAME, true);
            }

            /*
             * Get the content of the children of the case element.
             */
            NodeList caseElements = doc.getElementsByTagName(CASE_ELEMENT_NAME);
            if (caseElements.getLength() == 0) {
                throw new CaseMetadataException("Case metadata file corrupted");
            }
            Element caseElement = (Element) caseElements.item(0);
            this.caseName = getElementTextContent(caseElement, CASE_NAME_ELEMENT_NAME, true);
            String caseDisplayName;
            String caseNumber;
            if (schemaVersion.equals(SCHEMA_VERSION_ONE) || schemaVersion.equals(SCHEMA_VERSION_TWO)) {
                caseDisplayName = caseName;
            } else {
                caseDisplayName = getElementTextContent(caseElement, CASE_DISPLAY_NAME_ELEMENT_NAME, true);
            }
            caseNumber = getElementTextContent(caseElement, CASE_NUMBER_ELEMENT_NAME, false);
            String examinerName = getElementTextContent(caseElement, EXAMINER_ELEMENT_NAME, false);
            String examinerPhone;
            String examinerEmail;
            String caseNotes;
            if (schemaVersion.equals(SCHEMA_VERSION_ONE) || schemaVersion.equals(SCHEMA_VERSION_TWO) || schemaVersion.equals(SCHEMA_VERSION_THREE)) {
                examinerPhone = "";  //case had metadata file written before additional examiner details were included 
                examinerEmail = "";
                caseNotes = "";
            } else {
                examinerPhone = getElementTextContent(caseElement, EXAMINER_ELEMENT_PHONE, false);
                examinerEmail = getElementTextContent(caseElement, EXAMINER_ELEMENT_EMAIL, false);
                caseNotes = getElementTextContent(caseElement, CASE_ELEMENT_NOTES, false);
            }

            this.caseDetails = new CaseDetails(caseDisplayName, caseNumber, examinerName, examinerPhone, examinerEmail, caseNotes);
            this.caseType = Case.CaseType.fromString(getElementTextContent(caseElement, CASE_TYPE_ELEMENT_NAME, true));
            if (null == this.caseType) {
                throw new CaseMetadataException("Case metadata file corrupted");
            }
            switch (schemaVersion) {
                case SCHEMA_VERSION_ONE:
                    this.caseDatabaseName = getElementTextContent(caseElement, CASE_DATABASE_NAME_ELEMENT_NAME, true);
                    this.textIndexName = getElementTextContent(caseElement, TEXT_INDEX_NAME_ELEMENT, true);
                    break;
                case SCHEMA_VERSION_TWO:
                    this.caseDatabaseName = getElementTextContent(caseElement, CASE_DB_ABSOLUTE_PATH_ELEMENT_NAME, true);
                    this.textIndexName = getElementTextContent(caseElement, TEXT_INDEX_ELEMENT, false);
                    break;
                default:
                    this.caseDatabaseName = getElementTextContent(caseElement, CASE_DB_NAME_RELATIVE_ELEMENT_NAME, true);
                    this.textIndexName = getElementTextContent(caseElement, TEXT_INDEX_ELEMENT, false);
                    break;
            }

            /*
             * Fix up the case database name due to a bug that for a time caused
             * the absolute paths of single-user case databases to be stored.
             * Derive the missing (absolute/relative) value from the one
             * present.
             */
            Path possibleAbsoluteCaseDbPath = Paths.get(this.caseDatabaseName);
            Path caseDirectoryPath = Paths.get(getCaseDirectory());
            if (possibleAbsoluteCaseDbPath.getNameCount() > 1) {
                this.caseDatabasePath = this.caseDatabaseName;
                this.caseDatabaseName = caseDirectoryPath.relativize(possibleAbsoluteCaseDbPath).toString();
            } else {
                this.caseDatabasePath = caseDirectoryPath.resolve(caseDatabaseName).toAbsolutePath().toString();
            }

        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new CaseMetadataException(String.format("Error reading from case metadata file %s", metadataFilePath), ex);
        }
    }

    /**
     * Gets the text content of an XML element.
     *
     * @param parentElement     The parent element.
     * @param elementName       The element name.
     * @param contentIsRequired Whether or not the content is required.
     *
     * @return The text content, may be empty If not required.
     *
     * @throws CaseMetadataException If the element is missing or content is
     *                               required and it is empty.
     */
    private String getElementTextContent(Element parentElement, String elementName, boolean contentIsRequired) throws CaseMetadataException {
        NodeList elementsList = parentElement.getElementsByTagName(elementName);
        if (elementsList.getLength() == 0) {
            throw new CaseMetadataException(String.format("Missing %s element from case metadata file %s", elementName, metadataFilePath));
        }
        String textContent = elementsList.item(0).getTextContent();
        if (textContent.isEmpty() && contentIsRequired) {
            throw new CaseMetadataException(String.format("Empty %s element in case metadata file %s", elementName, metadataFilePath));
        }
        return textContent;
    }

    /**
     * Exception thrown by the CaseMetadata class when there is a problem
     * accessing the metadata for a case.
     */
    public final static class CaseMetadataException extends Exception {

        private static final long serialVersionUID = 1L;

        private CaseMetadataException(String message) {
            super(message);
        }

        private CaseMetadataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Gets the full path to the case database file if the case is a single-user
     * case.
     *
     * @return The full path to the case database file for a single-user case.
     *
     * @throws UnsupportedOperationException If called for a multi-user case.
     * @deprecated Do not use.
     */
    @Deprecated
    public String getCaseDatabasePath() throws UnsupportedOperationException {
        if (Case.CaseType.SINGLE_USER_CASE == caseType) {
            return Paths.get(getCaseDirectory(), caseDatabaseName).toString();
        } else {
            throw new UnsupportedOperationException();
        }
    }

}
