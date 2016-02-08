/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Provides access to case metadata.
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

    final static String XSDFILE = "CaseSchema.xsd"; //NON-NLS
    final static String TOP_ROOT_NAME = "AutopsyCase"; //NON-NLS
    final static String CASE_ROOT_NAME = "Case"; //NON-NLS
    // general metadata about the case file
    final static String NAME = "Name"; //NON-NLS
    final static String NUMBER = "Number"; //NON-NLS
    final static String EXAMINER = "Examiner"; //NON-NLS
    final static String CREATED_DATE_NAME = "CreatedDate"; //NON-NLS
    final static String MODIFIED_DATE_NAME = "ModifiedDate"; //NON-NLS
    final static String SCHEMA_VERSION_NAME = "SchemaVersion"; //NON-NLS
    final static String AUTOPSY_CRVERSION_NAME = "AutopsyCreatedVersion"; //NON-NLS
    final static String AUTOPSY_MVERSION_NAME = "AutopsySavedVersion"; //NON-NLS
    final static String CASE_TEXT_INDEX_NAME = "TextIndexName"; //NON-NLS
    // folders inside case directory
    final static String LOG_FOLDER_NAME = "LogFolder"; //NON-NLS
    final static String LOG_FOLDER_RELPATH = "Log"; //NON-NLS
    final static String TEMP_FOLDER_NAME = "TempFolder"; //NON-NLS
    final static String TEMP_FOLDER_RELPATH = "Temp"; //NON-NLS
    final static String EXPORT_FOLDER_NAME = "ExportFolder"; //NON-NLS
    final static String EXPORT_FOLDER_RELPATH = "Export"; //NON-NLS
    final static String CACHE_FOLDER_NAME = "CacheFolder"; //NON-NLS
    final static String CACHE_FOLDER_RELPATH = "Cache"; //NON-NLS
    final static String CASE_TYPE = "CaseType"; //NON-NLS
    final static String DATABASE_NAME = "DatabaseName"; //NON-NLS
    // folders attribute
    final static String RELATIVE_NAME = "Relative";    // relevant path info NON-NLS
    // folder attr values
    final static String RELATIVE_TRUE = "true";     // if it's a relative path NON-NLS
    final static String RELATIVE_FALSE = "false";   // if it's not a relative path NON-NLS

    private final DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss (z)");
    private Case.CaseType caseType;
    private String caseName;
    private String caseNumber;
    private String examiner;
    private String caseDirectory;
    private String caseDatabaseName;
    private String caseTextIndexName;
    private String createdDate;
    private String schemaVersion;
    private String fileName;
    private JPanel caller;
    private final String className = this.getClass().toString();
    private static final Logger logger = Logger.getLogger(CaseMetadata.class.getName());

    private CaseMetadata(Case.CaseType caseType, String caseName, String caseDirectory, String caseDatabaseName, String caseTextIndexName) throws CaseMetadataException {
        this.caseType = caseType;
        this.caseName = caseName;
        this.caseNumber = "";
        this.examiner = "";
        this.createdDate = this.dateFormat.format(new Date());
        this.caseDirectory = caseDirectory;
        this.caseDatabaseName = caseDatabaseName;
        this.caseTextIndexName = caseTextIndexName;
        this.schemaVersion = "1.0";
        this.fileName = caseName;
        this.write();

    }

    /**
     * Constructs an object that provides access to case metadata.
     *
     * @param metadataFilePath Path to the metadata (.aut) file for a case.
     */
    public CaseMetadata(Path metadataFilePath) throws CaseMetadataException {
        this(metadataFilePath.toString());
    }

    private CaseMetadata(String metadataFilePath) throws CaseMetadataException {
        this.open(metadataFilePath);
    }

    public static CaseMetadata open(Path metadataFilePath) throws CaseMetadataException {
        CaseMetadata metadata = new CaseMetadata(metadataFilePath.toString());
        return metadata;
    }

    public static CaseMetadata create(Case.CaseType caseType, String caseName, String caseNumber, String examiner, String caseDirectory, String caseDatabaseName, String caseTextIndexName) throws CaseMetadataException {
        CaseMetadata metadata = new CaseMetadata(caseType, caseName, caseDirectory, caseDatabaseName, caseTextIndexName);
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
     * @param createdDate the createdDate to set
     */
    void setCreatedDate(String createdDate) throws CaseMetadataException {
        this.createdDate = createdDate;
        this.write();
    }

    /**
     * @param caseType the caseType to set
     */
    public void setCaseType(Case.CaseType caseType) throws CaseMetadataException {
        this.caseType = caseType;
        this.write();
    }

    /**
     * @param caseName the caseName to set
     */
    public void setCaseName(String caseName) throws CaseMetadataException {
        this.caseName = caseName;
        this.write();
    }

    /**
     * @param caseNumber the caseNumber to set
     */
    public void setCaseNumber(String caseNumber) throws CaseMetadataException {
        this.caseNumber = caseNumber;
        this.write();
    }

    /**
     * @param examiner the examiner to set
     */
    public void setCaseExaminer(String examiner) throws CaseMetadataException {
        this.examiner = examiner;
        this.write();
    }

    /**
     * @param caseDirectory the caseDirectory to set
     */
    public void setCaseDirectory(String caseDirectory) throws CaseMetadataException {
        this.caseDirectory = caseDirectory;
        this.write();
    }

    /**
     * @param caseDatabaseName the caseDatabaseName to set
     */
    public void setCaseDatabaseName(String caseDatabaseName) throws CaseMetadataException {
        this.caseDatabaseName = caseDatabaseName;
        this.write();
    }

    /**
     * @return the caseTextIndexName
     */
    public String getCaseTextIndexName() {
        return caseTextIndexName;
    }

    /**
     * @param caseTextIndexName the caseTextIndexName to set
     */
    public void setCaseTextIndexName(String caseTextIndexName) throws CaseMetadataException {
        this.caseTextIndexName = caseTextIndexName;
        this.write();
    }

    /**
     * @return the schemaVersion
     */
    public String getSchemaVersion() {
        return schemaVersion;
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
        mDateElement.appendChild(doc.createTextNode(dateFormat.format(new Date())));
        rootElement.appendChild(mDateElement);

        Element autVerElement = doc.createElement(AUTOPSY_CRVERSION_NAME); // <AutopsyVersion> ... </AutopsyVersion>
        autVerElement.appendChild(doc.createTextNode(System.getProperty("netbeans.buildnumber")));
        rootElement.appendChild(autVerElement);

        Element autSavedVerElement = doc.createElement(AUTOPSY_MVERSION_NAME); // <AutopsySavedVersion> ... </AutopsySavedVersion>
        autSavedVerElement.appendChild(doc.createTextNode(System.getProperty("netbeans.buildnumber")));
        rootElement.appendChild(autSavedVerElement);

        Element schVerElement = doc.createElement(SCHEMA_VERSION_NAME); // <SchemaVersion> ... </SchemaVersion>
        schVerElement.appendChild(doc.createTextNode(getSchemaVersion()));
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

        Element exportElement = doc.createElement(EXPORT_FOLDER_NAME); // <ExportFolder> ... </ExportFolder>
        exportElement.appendChild(doc.createTextNode(EXPORT_FOLDER_RELPATH));
        exportElement.setAttribute(RELATIVE_NAME, "true"); //NON-NLS
        caseElement.appendChild(exportElement);

        Element logElement = doc.createElement(LOG_FOLDER_NAME); // <LogFolder> ... </LogFolder>
        logElement.appendChild(doc.createTextNode(LOG_FOLDER_RELPATH));
        logElement.setAttribute(RELATIVE_NAME, "true"); //NON-NLS
        caseElement.appendChild(logElement);

        Element tempElement = doc.createElement(TEMP_FOLDER_NAME); // <TempFolder> ... </TempFolder>
        tempElement.appendChild(doc.createTextNode(TEMP_FOLDER_RELPATH));
        tempElement.setAttribute(RELATIVE_NAME, "true"); //NON-NLS
        caseElement.appendChild(tempElement);

        Element cacheElement = doc.createElement(CACHE_FOLDER_NAME); // <CacheFolder> ... </CacheFolder>
        cacheElement.appendChild(doc.createTextNode(CACHE_FOLDER_RELPATH));
        cacheElement.setAttribute(RELATIVE_NAME, "true"); //NON-NLS
        caseElement.appendChild(cacheElement);

        Element typeElement = doc.createElement(CASE_TYPE); // <CaseType> ... </CaseType>
        typeElement.appendChild(doc.createTextNode(getCaseType().toString()));
        caseElement.appendChild(typeElement);

        Element dbNameElement = doc.createElement(DATABASE_NAME); // <DatabaseName> ... </DatabaseName>
        dbNameElement.appendChild(doc.createTextNode(this.getCaseDatabaseName()));
        caseElement.appendChild(dbNameElement);

        Element indexNameElement = doc.createElement(CASE_TEXT_INDEX_NAME); // <TextIndexName> ... </TextIndexName>
        indexNameElement.appendChild(doc.createTextNode(this.getCaseTextIndexName()));
        caseElement.appendChild(indexNameElement);
        doc.normalize();
        this.writeFile(doc);
    }

    private void writeFile(Document doc) throws CaseMetadataException {
        if (doc == null || getCaseName().equals("")) {
            throw new CaseMetadataException(
                    NbBundle.getMessage(this.getClass(), "XMLCaseManagement.writeFile.exception.noCase.msg"));
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
            logger.log(Level.SEVERE, "Could not setup tranformer and write case file"); //NON-NLS
            throw new CaseMetadataException(
                    NbBundle.getMessage(this.getClass(), "XMLCaseManagement.writeFile.exception.errWriteToFile.msg"), ex);
        }

        //Setup indenting to "pretty print"
        xformer.setOutputProperty(OutputKeys.INDENT, "yes"); //NON-NLS
        xformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2"); //NON-NLS

        try {
            xformer.transform(source, result);
        } catch (TransformerException ex) {
            logger.log(Level.SEVERE, "Could not run tranformer and write case file"); //NON-NLS
            throw new CaseMetadataException(
                    NbBundle.getMessage(this.getClass(), "XMLCaseManagement.writeFile.exception.errWriteToFile.msg"), ex);
        }

        // preparing the output file
        String xmlString = sw.toString();
        File file = new File(this.getCaseDirectory() + File.separator + this.fileName + ".aut");

        // write the file
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
            bw.write(xmlString);
            bw.flush();
            bw.close();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error writing to case file"); //NON-NLS
            throw new CaseMetadataException(
                    NbBundle.getMessage(this.getClass(), "XMLCaseManagement.writeFile.exception.errWriteToFile.msg"), ex);
        }
    }

    /**
     * Opens the configuration file and load the document handler Note: this is
     * for the schema version 1.0
     *
     * @param conFilePath the path of the XML case configuration file path
     */
    private void open(String conFilePath) throws CaseMetadataException {
        File file = new File(conFilePath);
        Document doc;

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db;
        try {
            db = dbf.newDocumentBuilder();
            doc = db.parse(file);
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new CaseMetadataException(
                    NbBundle.getMessage(this.getClass(), "XMLCaseManagement.open.exception.errReadXMLFile.msg",
                            conFilePath), ex);
        }

        doc.getDocumentElement().normalize();
        doc.getDocumentElement().normalize();

        Element rootEl = doc.getDocumentElement();
        String rootName = rootEl.getNodeName();

        // check if it's the autopsy case, if not, throws an error
        if (!rootName.equals(TOP_ROOT_NAME)) {
            // throw an error ...
            if (RuntimeProperties.coreComponentsAreActive()) {

                JOptionPane.showMessageDialog(caller,
                        NbBundle.getMessage(this.getClass(),
                                "XMLCaseManagement.open.msgDlg.notAutCase.msg",
                                file.getName(), className),
                        NbBundle.getMessage(this.getClass(),
                                "XMLCaseManagement.open.msgDlg.notAutCase.title"),
                        JOptionPane.ERROR_MESSAGE);
            }
        } else {
            /*
             * Autopsy Created Version
             */
            Element rootElement = doc.getDocumentElement();

            String createdVersion = ((Element) rootElement.getElementsByTagName(AUTOPSY_CRVERSION_NAME).item(0)).getTextContent(); // get the created version

            // check if it has the same autopsy version as the current one
            if (!createdVersion.equals(System.getProperty("netbeans.buildnumber"))) {
                // if not the same version, update the saved version in the xml to the current version
                rootElement.getElementsByTagName(AUTOPSY_MVERSION_NAME).item(0).setTextContent(System.getProperty("netbeans.buildnumber"));
            }

            /*
             * Schema Version
             */
            String schemaVer = ((Element) rootElement.getElementsByTagName(SCHEMA_VERSION_NAME).item(0)).getTextContent();
            // check if it has the same schema version as the current one
            if (!schemaVer.equals(schemaVersion)) {
                // do something here if not the same version
                // ... @Override
            }

            try {
                String fullFileName = file.getName();
                String fileName = fullFileName.substring(0, fullFileName.lastIndexOf(".")); // remove the extension
                this.fileName = fileName;
                Element caseElement = (Element) doc.getElementsByTagName(CASE_ROOT_NAME).item(0);

                String caseTypeString = ((Element) caseElement.getElementsByTagName(CASE_TYPE).item(0)).getTextContent();
                this.caseType = caseTypeString.equals("") ? Case.CaseType.SINGLE_USER_CASE : Case.CaseType.fromString(caseTypeString);
                this.caseName = ((Element) caseElement.getElementsByTagName(NAME).item(0)).getTextContent();
                Element numberElement = caseElement.getElementsByTagName(NUMBER).getLength() > 0 ? (Element) caseElement.getElementsByTagName(NUMBER).item(0) : null;
                this.caseNumber = numberElement != null ? numberElement.getTextContent() : "";
                Element examinerElement = caseElement.getElementsByTagName(EXAMINER).getLength() > 0 ? (Element) caseElement.getElementsByTagName(EXAMINER).item(0) : null;
                this.caseNumber = numberElement != null ? examinerElement.getTextContent() : "";
                this.caseDirectory = conFilePath.substring(0, conFilePath.lastIndexOf("\\"));
                if (this.caseDirectory.isEmpty()) {
                    throw new CaseMetadataException("Case directory missing");
                }
                Element databaseNameElement = caseElement.getElementsByTagName(DATABASE_NAME).getLength() > 0 ? (Element) caseElement.getElementsByTagName(DATABASE_NAME).item(0) : null;
                this.caseDatabaseName = databaseNameElement != null ? databaseNameElement.getTextContent() : "";
                Element caseTextIndexNameElement = caseElement.getElementsByTagName(CASE_TEXT_INDEX_NAME).getLength() > 0 ? (Element) caseElement.getElementsByTagName(CASE_TEXT_INDEX_NAME).item(0) : null;
                this.caseTextIndexName = caseTextIndexNameElement != null ? caseTextIndexNameElement.getTextContent() : "";
                if (Case.CaseType.MULTI_USER_CASE == caseType && caseDatabaseName.isEmpty()) {
                    throw new CaseMetadataException("Case keyword search index name missing");
                }
                this.setCreatedDate(((Element) rootElement.getElementsByTagName(CREATED_DATE_NAME).item(0)).getTextContent());
                this.schemaVersion = ((Element) rootElement.getElementsByTagName(SCHEMA_VERSION_NAME).item(0)).getTextContent();
            } catch (NullPointerException ex) {
                throw new CaseMetadataException("Error setting case metadata when opening file.", ex);
            }
        }
    }

}
