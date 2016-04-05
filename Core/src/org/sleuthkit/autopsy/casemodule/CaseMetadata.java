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
package org.sleuthkit.autopsy.casemodule;

import java.io.BufferedWriter;
import java.io.File;
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
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Provides access to the case metadata stored in the case metadata file.
 */
public final class CaseMetadata {

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

    private final static String XSDFILE = "CaseSchema.xsd"; //NON-NLS
    private final static String TOP_ROOT_NAME = "AutopsyCase"; //NON-NLS
    private final static String CASE_ROOT_NAME = "Case"; //NON-NLS
    private final static String NAME = "Name"; //NON-NLS
    private final static String NUMBER = "Number"; //NON-NLS
    private final static String EXAMINER = "Examiner"; //NON-NLS
    private final static String CREATED_DATE_NAME = "CreatedDate"; //NON-NLS
    private final static String MODIFIED_DATE_NAME = "ModifiedDate"; //NON-NLS
    private final static String SCHEMA_VERSION_NAME = "SchemaVersion"; //NON-NLS
    private final static String AUTOPSY_CRVERSION_NAME = "AutopsyCreatedVersion"; //NON-NLS
    private final static String AUTOPSY_MVERSION_NAME = "AutopsySavedVersion"; //NON-NLS
    private final static String CASE_TEXT_INDEX_NAME = "TextIndexName"; //NON-NLS
    private final static String CASE_TYPE = "CaseType"; //NON-NLS
    private final static String DATABASE_NAME = "DatabaseName"; //NON-NLS

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss (z)");
    private Case.CaseType caseType;
    private String caseName;
    private String caseNumber;
    private String examiner;
    private String caseDirectory;
    private String caseDatabaseName;
    private String caseTextIndexName;
    private String createdDate;
    private static final String SCHEMA_VERSION = "2.0";
    private String fileName;
    private String createdVersion;

    private CaseMetadata(Case.CaseType caseType, String caseName, String caseNumber, String examiner, String caseDirectory, String caseDatabaseName, String caseTextIndexName) throws CaseMetadataException {

        this.caseType = caseType;
        this.caseName = caseName;
        this.caseNumber = caseNumber;
        this.examiner = examiner;
        this.createdDate = this.DATE_FORMAT.format(new Date());
        this.caseDirectory = caseDirectory;
        this.caseDatabaseName = caseDatabaseName;
        this.caseTextIndexName = caseTextIndexName;
        this.fileName = caseName;
        this.createdVersion = System.getProperty("netbeans.buildnumber");
        this.write();

    }

    /**
     * Constructs an object that provides access to case metadata.
     *
     * @param metadataFilePath Path to the metadata (.aut) file for a case.
     *
     * @deprecated Use open instead
     */
    @Deprecated
    public CaseMetadata(Path metadataFilePath) throws CaseMetadataException {
        this(metadataFilePath.toString());
    }

    /**
     * Used to read a case at the given file path
     *
     * @param metadataFilePath the string file path for the case
     *
     * @throws
     * org.sleuthkit.autopsy.casemodule.CaseMetadata.CaseMetadataException
     */
    private CaseMetadata(String metadataFilePath) throws CaseMetadataException {
        this.read(metadataFilePath);
    }

    /**
     * Opens a case and gives the metadata object that corresponds
     *
     * @param metadataFilePath The file path of the metadata file
     *
     * @return The case metadata for the opened case
     *
     * @throws
     * org.sleuthkit.autopsy.casemodule.CaseMetadata.CaseMetadataException
     */
    public static CaseMetadata open(Path metadataFilePath) throws CaseMetadataException {
        CaseMetadata metadata = new CaseMetadata(metadataFilePath.toString());
        return metadata;
    }

    /**
     * Creates a metadata object for a new case
     *
     * @param caseType          The type of case
     * @param caseName          The name of the case
     * @param caseNumber        The case number
     * @param examiner          The name of the case examiner
     * @param caseDirectory     The directory of the case
     * @param caseDatabaseName  The name of the case db
     * @param caseTextIndexName The case index name
     *
     * @return The metadata for the new case
     *
     * @throws
     * org.sleuthkit.autopsy.casemodule.CaseMetadata.CaseMetadataException
     */
    public static CaseMetadata create(Case.CaseType caseType, String caseName, String caseNumber, String examiner, String caseDirectory, String caseDatabaseName, String caseTextIndexName) throws CaseMetadataException {
        CaseMetadata metadata = new CaseMetadata(caseType, caseName, caseNumber, examiner, caseDirectory, caseDatabaseName, caseTextIndexName);
        return metadata;
    }

    /**
     * Gets the case type.
     *
     * @return The case type.
     */
    public Case.CaseType getCaseType() {
        return this.caseType;
    }

    /**
     * Gets the case name.
     *
     * @return The case name.
     */
    public String getCaseName() {
        return caseName;
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
     * Gets the case directory.
     *
     * @return The case directory.
     */
    public String getCaseDirectory() {
        return caseDirectory;
    }

    /**
     * Gets the case database name.
     *
     * @return The case database name, will be empty for a single-user case.
     */
    public String getCaseDatabaseName() {
        return caseDatabaseName;
    }

    /**
     * Gets the date this case was created
     *
     * @return The date this case was created as a string
     */
    public String getCreatedDate() {
        return this.createdDate;
    }

    /**
     * @return the createdVersion
     */
    public String getCreatedVersion() {
        return createdVersion;
    }

    /**
     * Sets the created version of this case
     *
     * @param createdVersion the createdVersion to set
     */
    void setCreatedVersion(String createdVersion) throws CaseMetadataException {
        String oldCreatedVersion = this.createdVersion;
        this.createdVersion = createdVersion;
        try {
            this.write();
        } catch (CaseMetadataException ex) {
            this.createdDate = oldCreatedVersion;
            throw ex;
        }
    }

    /**
     * @param createdDate the createdDate to set
     */
    void setCreatedDate(String createdDate) throws CaseMetadataException {
        String oldCreatedDate = this.createdDate;
        this.createdDate = createdDate;
        try {
            this.write();
        } catch (CaseMetadataException ex) {
            this.createdDate = oldCreatedDate;
            throw ex;
        }
    }

    /**
     * @param caseName the caseName to set
     */
    void setCaseName(String caseName) throws CaseMetadataException {
        String oldCaseName = this.caseName;
        this.caseName = caseName;
        try {
            this.write();
        } catch (CaseMetadataException ex) {
            this.caseName = oldCaseName;
            throw ex;
        }
    }

    /**
     * @return the caseTextIndexName
     */
    public String getTextIndexName() {
        return caseTextIndexName;
    }

    private void write() throws CaseMetadataException {
        Document doc;
        try {
            doc = XMLUtil.createDocument();
        } catch (ParserConfigurationException ex) {
            throw new CaseMetadataException(
                    NbBundle.getMessage(this.getClass(), "XMLCaseManagement.create.exception.msg"), ex);
        }
        Element rootElement = doc.createElement(TOP_ROOT_NAME); // <AutopsyCase> ... </AutopsyCase>
        doc.appendChild(rootElement);

        Element crDateElement = doc.createElement(CREATED_DATE_NAME); // <CreatedDate> ... </CreatedDate>
        crDateElement.appendChild(doc.createTextNode(this.getCreatedDate()));
        rootElement.appendChild(crDateElement);

        Element mDateElement = doc.createElement(MODIFIED_DATE_NAME); // <ModifedDate> ... </ModifedDate>
        mDateElement.appendChild(doc.createTextNode(DATE_FORMAT.format(new Date())));
        rootElement.appendChild(mDateElement);

        Element autVerElement = doc.createElement(AUTOPSY_CRVERSION_NAME); // <AutopsyVersion> ... </AutopsyVersion>
        autVerElement.appendChild(doc.createTextNode(this.getCreatedVersion()));
        rootElement.appendChild(autVerElement);

        Element autSavedVerElement = doc.createElement(AUTOPSY_MVERSION_NAME); // <AutopsySavedVersion> ... </AutopsySavedVersion>
        autSavedVerElement.appendChild(doc.createTextNode(System.getProperty("netbeans.buildnumber")));
        rootElement.appendChild(autSavedVerElement);

        Element schVerElement = doc.createElement(SCHEMA_VERSION_NAME); // <SchemaVersion> ... </SchemaVersion>
        schVerElement.appendChild(doc.createTextNode(SCHEMA_VERSION));
        rootElement.appendChild(schVerElement);

        Element caseElement = doc.createElement(CASE_ROOT_NAME); // <Case> ... </Case>
        rootElement.appendChild(caseElement);

        Element nameElement = doc.createElement(NAME); // <Name> ... </Name>
        nameElement.appendChild(doc.createTextNode(getCaseName()));
        caseElement.appendChild(nameElement);

        Element numberElement = doc.createElement(NUMBER); // <Number> ... </Number>
        numberElement.appendChild(doc.createTextNode(String.valueOf(getCaseNumber())));
        caseElement.appendChild(numberElement);

        Element examinerElement = doc.createElement(EXAMINER); // <Examiner> ... </Examiner>
        examinerElement.appendChild(doc.createTextNode(getExaminer()));
        caseElement.appendChild(examinerElement);

        Element typeElement = doc.createElement(CASE_TYPE); // <CaseType> ... </CaseType>
        typeElement.appendChild(doc.createTextNode(getCaseType().toString()));
        caseElement.appendChild(typeElement);

        Element dbNameElement = doc.createElement(DATABASE_NAME); // <DatabaseName> ... </DatabaseName>
        dbNameElement.appendChild(doc.createTextNode(this.getCaseDatabaseName()));
        caseElement.appendChild(dbNameElement);

        Element indexNameElement = doc.createElement(CASE_TEXT_INDEX_NAME); // <TextIndexName> ... </TextIndexName>
        indexNameElement.appendChild(doc.createTextNode(this.getTextIndexName()));
        caseElement.appendChild(indexNameElement);
        doc.normalize();
        this.writeFile(doc);
    }

    private void writeFile(Document doc) throws CaseMetadataException {
        if (doc == null || getCaseName().equals("")) {
            throw new CaseMetadataException(
                    "No set case to write management file for."
            );
        }

        // Prepare the DOM document for writing
        Source source = new DOMSource(doc);

        // Prepare the data for the output file
        StringWriter sw = new StringWriter();
        Result result = new StreamResult(sw);

        // Write the DOM document to the file
        Transformer xformer;// = TransformerFactory.newInstance().newTransformer();
        TransformerFactory tfactory = TransformerFactory.newInstance();

        try {
            xformer = tfactory.newTransformer();
        } catch (TransformerConfigurationException ex) {
            throw new CaseMetadataException(
                    "Error writing to case file", ex);
        }

        //Setup indenting to "pretty print"
        xformer.setOutputProperty(OutputKeys.INDENT, "yes"); //NON-NLS
        xformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2"); //NON-NLS

        try {
            xformer.transform(source, result);
        } catch (TransformerException ex) {
            throw new CaseMetadataException(
                    "Error writing to case file", ex);
        }

        // preparing the output file
        String xmlString = sw.toString();
        File file = new File(Paths.get(this.getCaseDirectory(), this.fileName + ".aut").toString());

        // write the file
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
            bw.write(xmlString);
            bw.flush();
            bw.close();
        } catch (IOException ex) {
            throw new CaseMetadataException(
                    "Error writing to case file", ex);
        }
    }

    /**
     * Opens the configuration file and load the document handler Note: this is
     * for the schema version 1.0
     *
     * @param conFilePath the path of the XML case configuration file path
     */
    private void read(String conFilePath) throws CaseMetadataException {
        File file = new File(conFilePath);
        Document doc;

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db;
        try {
            db = dbf.newDocumentBuilder();
            doc = db.parse(file);
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new CaseMetadataException(
                    "Error reading case XML file\\:" + conFilePath, ex);
        }

        doc.getDocumentElement().normalize();

        Element rootEl = doc.getDocumentElement();
        String rootName = rootEl.getNodeName();

        if (!rootName.equals(TOP_ROOT_NAME)) {
            // throw an error because the xml is malformed
            if (RuntimeProperties.coreComponentsAreActive()) {
                throw new CaseMetadataException("Root element not recognized as autopsy case tag");
            }
        } else {
            /*
             * Autopsy Created Version
             */
            Element rootElement = doc.getDocumentElement();

            NodeList cversionList = rootElement.getElementsByTagName(AUTOPSY_CRVERSION_NAME);
            if (cversionList.getLength() == 0) {
                throw new CaseMetadataException("Could not find created version in metadata");
            }
            String createdVersion = cversionList.item(0).getTextContent(); // get the created version
            // check if it has the same autopsy version as the current one
            if (!createdVersion.equals(System.getProperty("netbeans.buildnumber"))) {
                // if not the same version, update the saved version in the xml to the current version
                rootElement.getElementsByTagName(AUTOPSY_MVERSION_NAME).item(0).setTextContent(System.getProperty("netbeans.buildnumber"));
            }

            String fullFileName = file.getName();
            String fileName = fullFileName.substring(0, fullFileName.lastIndexOf(".")); // remove the extension
            this.fileName = fileName;

            NodeList rootNameList = doc.getElementsByTagName(CASE_ROOT_NAME);
            if (rootNameList.getLength() == 0) {
                throw new CaseMetadataException("Couldn't get case root");
            }
            Element caseElement = (Element) rootNameList.item(0);

            NodeList caseTypeList = caseElement.getElementsByTagName(CASE_TYPE);
            if (caseTypeList.getLength() == 0) {
                throw new CaseMetadataException("Couldn't get case type");
            }
            String caseTypeString = caseTypeList.item(0).getTextContent();
            this.caseType = Case.CaseType.fromString(caseTypeString);
            if (this.caseType == null) {
                throw new CaseMetadataException("Invalid case type");
            }

            NodeList caseNameList = caseElement.getElementsByTagName(NAME);
            if (caseNameList.getLength() == 0) {
                throw new CaseMetadataException("Couldn't get case name");
            }
            this.caseName = caseNameList.item(0).getTextContent();
            if (this.caseName.isEmpty()) {
                throw new CaseMetadataException("Invalid case name, cannot be empty");
            }

            Element numberElement = caseElement.getElementsByTagName(NUMBER).getLength() > 0 ? (Element) caseElement.getElementsByTagName(NUMBER).item(0) : null;
            this.caseNumber = numberElement != null ? numberElement.getTextContent() : "";

            Element examinerElement = caseElement.getElementsByTagName(EXAMINER).getLength() > 0 ? (Element) caseElement.getElementsByTagName(EXAMINER).item(0) : null;
            this.examiner = examinerElement != null ? examinerElement.getTextContent() : "";

            this.caseDirectory = conFilePath.substring(0, conFilePath.lastIndexOf("\\"));
            if (this.caseDirectory.isEmpty()) {
                throw new CaseMetadataException("Could not get a valid case directory");
            }

            NodeList databaseNameList = caseElement.getElementsByTagName(DATABASE_NAME);
            if (databaseNameList.getLength() == 0) {
                throw new CaseMetadataException("Couldn't get database name");
            }
            Element databaseNameElement = (Element) databaseNameList.item(0);
            this.caseDatabaseName = databaseNameElement.getTextContent();
            if (Case.CaseType.MULTI_USER_CASE == caseType && caseDatabaseName.isEmpty()) {
                throw new CaseMetadataException("Case database name cannot be empty in multi user case.");
            }

            NodeList textIndexList = caseElement.getElementsByTagName(CASE_TEXT_INDEX_NAME);
            if (textIndexList.getLength() == 0) {
                throw new CaseMetadataException("Couldn't get text index");
            }
            Element caseTextIndexNameElement = (Element) textIndexList.item(0);
            this.caseTextIndexName = caseTextIndexNameElement.getTextContent();

            NodeList createdDateList = rootElement.getElementsByTagName(CREATED_DATE_NAME);
            if (createdDateList.getLength() == 0) {
                throw new CaseMetadataException("Couldn't get created date");
            }
            this.createdDate = createdDateList.item(0).getTextContent();

            NodeList createdVersionList = rootElement.getElementsByTagName(AUTOPSY_CRVERSION_NAME);
            if (createdVersionList.getLength() == 0) {
                throw new CaseMetadataException("Couldn't get created version");
            }
            this.createdVersion = createdVersionList.item(0).getTextContent();
        }
    }

}
