/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss (z)");

    //fields from schema version 1
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

    //fields from schema version 2
    private static final String SCHEMA_VERSION_TWO = "2.0";
    private final static String AUTOPSY_CREATED_BY_ELEMENT_NAME = "CreatedByAutopsyVersion"; //NON-NLS
    private final static String CASE_DATABASE_ABSOLUTE_PATH_ELEMENT_NAME = "Database"; //NON-NLS
    private final static String TEXT_INDEX_ELEMENT = "TextIndex"; //NON-NLS

    //fields from schema version 3
    private static final String SCHEMA_VERSION_THREE = "3.0";
    private final static String CASE_DISPLAY_NAME_ELEMENT_NAME = "DisplayName"; //NON-NLS
    private final static String CASE_DATABASE_NAME_RELATIVE_ELEMENT_NAME = "CaseDatabase"; //NON-NLS

    //unread fields, these are regenerated on save
    private final static String MODIFIED_DATE_ELEMENT_NAME = "ModifiedDate"; //NON-NLS
    private final static String AUTOPSY_SAVED_BY_ELEMENT_NAME = "SavedByAutopsyVersion"; //NON-NLS

    private static final String CURRENT_SCHEMA_VERSION = SCHEMA_VERSION_THREE;

    private final Path metadataFilePath;
    private Case.CaseType caseType;
    private String caseName;
    private String caseDisplayName;
    private String caseNumber;
    private String examiner;
    private String caseDatabaseName;
    private String caseDatabasePath;
    private String textIndexName;
    private String createdDate;
    private String createdByVersion;

    /**
     * Gets the file extension used for case metadata files.
     *
     * @return The file extension.
     */
    public static String getFileExtension() {
        return FILE_EXTENSION;
    }

    /**
     * Constructs an object that provides access to the case metadata stored in
     * a new case metadata file that is created using the supplied metadata.
     *
     * @param caseDirectory   The case directory.
     * @param caseType        The type of case.
     * @param caseName        The immutable name of the case.
     * @param caseDisplayName The display name of the case, can be changed by a
     *                        user.
     * @param caseNumber      The case number.
     * @param examiner        The name of the case examiner.
     * @param caseDatabase    For a single-user case, the full path to the case
     *                        database file. For a multi-user case, the case
     *                        database name.
     *
     * @throws CaseMetadataException If the new case metadata file cannot be
     *                               created.
     */
    CaseMetadata(String caseDirectory, Case.CaseType caseType, String caseName, String caseDisplayName, String caseNumber, String examiner, String caseDatabase) throws CaseMetadataException {
        metadataFilePath = Paths.get(caseDirectory, caseName + FILE_EXTENSION);
        this.caseType = caseType;
        this.caseName = caseName;
        this.caseDisplayName = caseDisplayName;
        this.caseNumber = caseNumber;
        this.examiner = examiner;
        this.caseDatabaseName = caseDatabase;
        createdByVersion = Version.getVersion();
        createdDate = CaseMetadata.DATE_FORMAT.format(new Date());
        writeToFile();
    }

    /**
     * Constructs an object that provides access to the case metadata stored in
     * an existing case metadata file.
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
     * Gets the immutable case name, set at case creation.
     *
     * @return The case display name.
     */
    public String getCaseName() {
        return caseName;
    }

    /**
     * Gets the case display name.
     *
     * @return The case display name.
     */
    public String getCaseDisplayName() {
        return this.caseDisplayName;
    }

    /**
     * Sets the case display name. This does not change the name of the case
     * directory, the case database, or the text index name.
     *
     * @param caseName A case display name.
     */
    void setCaseDisplayName(String caseName) throws CaseMetadataException {
        String oldCaseName = caseName;
        this.caseDisplayName = caseName;
        try {
            writeToFile();
        } catch (CaseMetadataException ex) {
            this.caseDisplayName = oldCaseName;
            throw ex;
        }
    }

    /**
     * Gets the case number.
     *
     * @return The case number, may be empty.
     */
    public String getCaseNumber() {
        return caseNumber;
    }

    /**
     * Gets the examiner.
     *
     * @return The examiner, may be empty.
     */
    public String getExaminer() {
        return examiner;
    }

    /**
     * Gets the name of the case database.
     *
     * @return The case database name.
     */
    public String getCaseDatabaseName() {
        return caseDatabaseName;
    }

    /**
     * Sets the text index name.
     *
     * @param caseTextIndexName The text index name.
     */
    void setTextIndexName(String caseTextIndexName) throws CaseMetadataException {
        String oldIndexName = caseTextIndexName;
        this.textIndexName = caseTextIndexName;
        try {
            writeToFile();
        } catch (CaseMetadataException ex) {
            this.textIndexName = oldIndexName;
            throw ex;
        }
    }

    /**
     * Gets the text index name.
     *
     * @return The name of the text index for the case.
     */
    public String getTextIndexName() {
        return textIndexName;
    }

    /**
     * Gets the date the case was created.
     *
     * @return The date this case was created as a string
     */
    String getCreatedDate() {
        return createdDate;
    }

    /**
     * Sets the date the case was created. Used for preserving the case creation
     * date during single-user to multi-user case conversion.
     *
     * @param createdDate The date the case was created as a string.
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
     * @param buildVersion An build version identifier.
     */
    void setCreatedByVersion(String buildVersion) throws CaseMetadataException {
        String oldCreatedByVersion = this.createdByVersion;
        this.createdByVersion = buildVersion;
        try {
            this.writeToFile();
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
    private void writeToFile() throws CaseMetadataException {
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
             * Write the DOM to the metadata file.
             */
            try (BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(metadataFilePath.toFile())))) {
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
        createChildElement(doc, caseElement, CASE_NAME_ELEMENT_NAME, caseName);
        createChildElement(doc, caseElement, CASE_DISPLAY_NAME_ELEMENT_NAME, caseDisplayName);
        createChildElement(doc, caseElement, CASE_NUMBER_ELEMENT_NAME, caseNumber);
        createChildElement(doc, caseElement, EXAMINER_ELEMENT_NAME, examiner);
        createChildElement(doc, caseElement, CASE_TYPE_ELEMENT_NAME, caseType.toString());
        createChildElement(doc, caseElement, CASE_DATABASE_ABSOLUTE_PATH_ELEMENT_NAME, caseDatabasePath);
        createChildElement(doc, caseElement, CASE_DATABASE_NAME_RELATIVE_ELEMENT_NAME, caseDatabaseName);
        createChildElement(doc, caseElement, TEXT_INDEX_ELEMENT, textIndexName);
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
            if (schemaVersion.equals(SCHEMA_VERSION_ONE) || schemaVersion.equals(SCHEMA_VERSION_TWO)) {
                this.caseDisplayName = caseName;
            } else {
                this.caseDisplayName = getElementTextContent(caseElement, CASE_DISPLAY_NAME_ELEMENT_NAME, true);
            }
            this.caseNumber = getElementTextContent(caseElement, CASE_NUMBER_ELEMENT_NAME, false);
            this.examiner = getElementTextContent(caseElement, EXAMINER_ELEMENT_NAME, false);
            this.caseType = Case.CaseType.fromString(getElementTextContent(caseElement, CASE_TYPE_ELEMENT_NAME, true));
            if (null == this.caseType) {
                throw new CaseMetadataException("Case metadata file corrupted");
            }
            if (schemaVersion.equals(SCHEMA_VERSION_ONE)) {
                this.caseDatabaseName = getElementTextContent(caseElement, CASE_DATABASE_NAME_ELEMENT_NAME, true);
                this.textIndexName = getElementTextContent(caseElement, TEXT_INDEX_NAME_ELEMENT, true);
            } else if (schemaVersion.equals(SCHEMA_VERSION_TWO)) {
                this.caseDatabaseName = getElementTextContent(caseElement, CASE_DATABASE_ABSOLUTE_PATH_ELEMENT_NAME, true);
                this.textIndexName = getElementTextContent(caseElement, TEXT_INDEX_ELEMENT, false);
            } else {
                this.caseDatabaseName = getElementTextContent(caseElement, CASE_DATABASE_NAME_RELATIVE_ELEMENT_NAME, true);
                this.textIndexName = getElementTextContent(caseElement, TEXT_INDEX_ELEMENT, false);
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
     * @return The text content, may be empty if not required.
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
     * @deprecated
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
