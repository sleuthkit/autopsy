/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.leappanalyzers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import static java.util.Locale.US;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Find and process output from Leapp program and bring into Autopsy
 */
public final class LeappFileProcessor {

    private static final Logger logger = Logger.getLogger(LeappFileProcessor.class.getName());
    private static final String MODULE_NAME = ILeappAnalyzerModuleFactory.getModuleName();

    private final String xmlFile; //NON-NLS

    private final Map<String, String> tsvFiles;
    private final Map<String, String> tsvFileArtifacts;
    private final Map<String, String> tsvFileArtifactComments;
    private final Map<String, List<List<String>>> tsvFileAttributes;

    Blackboard blkBoard;

    public LeappFileProcessor(String xmlFile) throws IOException, IngestModuleException, NoCurrentCaseException {
        this.tsvFiles = new HashMap<>();
        this.tsvFileArtifacts = new HashMap<>();
        this.tsvFileArtifactComments = new HashMap<>();
        this.tsvFileAttributes = new HashMap<>();
        this.xmlFile = xmlFile;

        blkBoard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();

        configExtractor();
        loadConfigFile();

    }

    @NbBundle.Messages({
        "LeappFileProcessor.error.running.Leapp=Error running Leapp, see log file.",
        "LeappFileProcessor.error.creating.output.dir=Error creating Leapp module output directory.",
        "LeappFileProcessor.starting.Leapp=Starting Leapp",
        "LeappFileProcessor.running.Leapp=Running Leapp",
        "LeappFileProcessor.has.run=Leapp",
        "LeappFileProcessor.Leapp.cancelled=Leapp run was canceled",
        "LeappFileProcessor.completed=Leapp Processing Completed",
        "LeappFileProcessor.error.reading.Leapp.directory=Error reading Leapp Output Directory"})

    public ProcessResult processFiles(Content dataSource, Path moduleOutputPath, AbstractFile LeappFile) {

        try {
            List<String> LeappTsvOutputFiles = findTsvFiles(moduleOutputPath);
            processLeappFiles(LeappTsvOutputFiles, LeappFile);
        } catch (IOException | IngestModuleException ex) {
            logger.log(Level.SEVERE, String.format("Error trying to process Leapp output files in directory %s. ", moduleOutputPath.toString()), ex); //NON-NLS
            return ProcessResult.ERROR;
        }

        return ProcessResult.OK;
    }

    public ProcessResult processFileSystem(Content dataSource, Path moduleOutputPath) {

        try {
            List<String> LeappTsvOutputFiles = findTsvFiles(moduleOutputPath);
            processLeappFiles(LeappTsvOutputFiles, dataSource);
        } catch (IOException | IngestModuleException ex) {
            logger.log(Level.SEVERE, String.format("Error trying to process Leapp output files in directory %s. ", moduleOutputPath.toString()), ex); //NON-NLS
            return ProcessResult.ERROR;
        }

        return ProcessResult.OK;
    }

    /**
     * Find the tsv files in the Leapp output directory and match them to files
     * we know we want to process and return the list to process those files.
     */
    private List<String> findTsvFiles(Path LeappOutputDir) throws IngestModuleException {
        List<String> allTsvFiles = new ArrayList<>();
        List<String> foundTsvFiles = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(LeappOutputDir)) {

            allTsvFiles = walk.map(x -> x.toString())
                    .filter(f -> f.toLowerCase().endsWith(".tsv")).collect(Collectors.toList());

            for (String tsvFile : allTsvFiles) {
                if (tsvFiles.containsKey(FilenameUtils.getName(tsvFile.toLowerCase()))) {
                    foundTsvFiles.add(tsvFile);
                }
            }

        } catch (IOException | UncheckedIOException e) {
            throw new IngestModuleException(Bundle.LeappFileProcessor_error_reading_Leapp_directory() + LeappOutputDir.toString(), e);
        }

        return foundTsvFiles;

    }

    /**
     * Process the Leapp files that were found that match the xml mapping file
     *
     * @param LeappFilesToProcess List of files to process
     * @param LeappImageFile      Abstract file to create artifact for
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void processLeappFiles(List<String> LeappFilesToProcess, AbstractFile LeappImageFile) throws FileNotFoundException, IOException, IngestModuleException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();

        for (String LeappFileName : LeappFilesToProcess) {
            String fileName = FilenameUtils.getName(LeappFileName);
            File LeappFile = new File(LeappFileName);
            if (tsvFileAttributes.containsKey(fileName)) {
                List<List<String>> attrList = tsvFileAttributes.get(fileName);
                try {
                    BlackboardArtifact.Type artifactType = Case.getCurrentCase().getSleuthkitCase().getArtifactType(tsvFileArtifacts.get(fileName));

                    processFile(LeappFile, attrList, fileName, artifactType, bbartifacts, LeappImageFile);

                } catch (TskCoreException ex) {
                    throw new IngestModuleException(String.format("Error getting Blackboard Artifact Type for %s", tsvFileArtifacts.get(fileName)), ex);
                }
            }

        }

        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }

    }

    /**
     * Process the Leapp files that were found that match the xml mapping file
     *
     * @param LeappFilesToProcess List of files to process
     * @param dataSource          The data source.
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void processLeappFiles(List<String> LeappFilesToProcess, Content dataSource) throws FileNotFoundException, IOException, IngestModuleException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();

        for (String LeappFileName : LeappFilesToProcess) {
            String fileName = FilenameUtils.getName(LeappFileName);
            File LeappFile = new File(LeappFileName);
            if (tsvFileAttributes.containsKey(fileName)) {
                List<List<String>> attrList = tsvFileAttributes.get(fileName);
                try {
                    BlackboardArtifact.Type artifactType = Case.getCurrentCase().getSleuthkitCase().getArtifactType(tsvFileArtifacts.get(fileName));

                    processFile(LeappFile, attrList, fileName, artifactType, bbartifacts, dataSource);

                } catch (TskCoreException ex) {
                    throw new IngestModuleException(String.format("Error getting Blackboard Artifact Type for %s", tsvFileArtifacts.get(fileName)), ex);
                }
            }

        }

        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }

    }

    private void processFile(File LeappFile, List<List<String>> attrList, String fileName, BlackboardArtifact.Type artifactType,
            List<BlackboardArtifact> bbartifacts, Content dataSource) throws FileNotFoundException, IOException, IngestModuleException,
            TskCoreException {
        try (BufferedReader reader = new BufferedReader(new FileReader(LeappFile))) {
            String line = reader.readLine();
            // Check first line, if it is null then no heading so nothing to match to, close and go to next file.
            if (line != null) {
                Map<Integer, String> columnNumberToProcess = findColumnsToProcess(line, attrList);
                line = reader.readLine();
                while (line != null) {
                    Collection<BlackboardAttribute> bbattributes = processReadLine(line, columnNumberToProcess, fileName);
                    if (artifactType == null) {
                        logger.log(Level.SEVERE, "Error trying to process Leapp output files in directory . "); //NON-NLS
                        
                    }
                    if (!bbattributes.isEmpty() && !blkBoard.artifactExists(dataSource, BlackboardArtifact.ARTIFACT_TYPE.fromID(artifactType.getTypeID()), bbattributes)) {
                        BlackboardArtifact bbartifact = createArtifactWithAttributes(artifactType.getTypeID(), dataSource, bbattributes);
                        if (bbartifact != null) {
                            bbartifacts.add(bbartifact);
                        }
                    }
                    line = reader.readLine();
                }
            }
        }

    }

    /**
     * Process the line read and create the necessary attributes for it
     *
     * @param line                  a tsv line to process that was read
     * @param columnNumberToProcess Which columns to process in the tsv line
     * @param fileName              name of file begin processed
     *
     * @return
     */
    private Collection<BlackboardAttribute> processReadLine(String line, Map<Integer, String> columnNumberToProcess, String fileName) throws IngestModuleException {
        String[] columnValues = line.split("\\t");

        Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();

        for (Map.Entry<Integer, String> columnToProcess : columnNumberToProcess.entrySet()) {
            Integer columnNumber = columnToProcess.getKey();
            String attributeName = columnToProcess.getValue();

            try {
                BlackboardAttribute.Type attributeType = Case.getCurrentCase().getSleuthkitCase().getAttributeType(attributeName.toUpperCase());
                if (attributeType == null) {
                    break;
                }
                String attrType = attributeType.getValueType().getLabel().toUpperCase();
                checkAttributeType(bbattributes, attrType, columnValues, columnNumber, attributeType, fileName);
            } catch (TskCoreException ex) {
                throw new IngestModuleException(String.format("Error getting Attribute type for Attribute Name %s", attributeName), ex); //NON-NLS
            }
        }

        if (tsvFileArtifactComments.containsKey(fileName)) {
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME, tsvFileArtifactComments.get(fileName)));
        }

        return bbattributes;

    }

    private void checkAttributeType(Collection<BlackboardAttribute> bbattributes, String attrType, String[] columnValues, Integer columnNumber, BlackboardAttribute.Type attributeType,
            String fileName) {
        if (attrType.matches("STRING")) {
            bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, columnValues[columnNumber]));
        } else if (attrType.matches("INTEGER")) {
            bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, Integer.valueOf(columnValues[columnNumber])));
        } else if (attrType.matches("LONG")) {
            bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, Long.valueOf(columnValues[columnNumber])));
        } else if (attrType.matches("DOUBLE")) {
            bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, Double.valueOf(columnValues[columnNumber])));
        } else if (attrType.matches("BYTE")) {
            bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, Byte.valueOf(columnValues[columnNumber])));
        } else if (attrType.matches("DATETIME")) {
            // format of data should be the same in all the data and the format is 2020-03-28 01:00:17
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-d HH:mm:ss", US);
            Long dateLong = Long.valueOf(0);
            try {
                Date newDate = dateFormat.parse(columnValues[columnNumber]);
                dateLong = newDate.getTime() / 1000;
                bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, dateLong));
            } catch (ParseException ex) {
                // catching error and displaying date that could not be parsed
                // we set the timestamp to 0 and continue on processing
                logger.log(Level.WARNING, String.format("Failed to parse date/time %s for attribute type %s in file %s.", columnValues[columnNumber], attributeType.getDisplayName(), fileName)); //NON-NLS
            }
        } else if (attrType.matches("JSON")) {

            bbattributes.add(new BlackboardAttribute(attributeType, MODULE_NAME, columnValues[columnNumber]));
        } else {
            // Log this and continue on with processing
            logger.log(Level.WARNING, String.format("Attribute Type %s not defined.", attrType)); //NON-NLS                   
        }

    }

    /**
     * Process the first line of the tsv file which has the headings. Match the
     * headings to the columns in the XML mapping file so we know which columns
     * to process.
     *
     * @param line     a tsv heading line of the columns in the file
     * @param attrList the list of headings we want to process
     *
     * @return the numbered column(s) and attribute(s) we want to use for the
     *         column(s)
     */
    private Map<Integer, String> findColumnsToProcess(String line, List<List<String>> attrList) {
        String[] columnNames = line.split("\\t");
        HashMap<Integer, String> columnsToProcess = new HashMap<>();

        Integer columnPosition = 0;
        for (String columnName : columnNames) {
            // for some reason the first column of the line has unprintable characters so removing them
            String cleanColumnName = columnName.replaceAll("[^\\n\\r\\t\\p{Print}]", "");
            for (List<String> atList : attrList) {
                if (atList.contains(cleanColumnName.toLowerCase())) {
                    columnsToProcess.put(columnPosition, atList.get(0));
                    break;
                }
            }
            columnPosition++;
        }

        return columnsToProcess;
    }

    @NbBundle.Messages({
        "LeappFileProcessor.cannot.load.artifact.xml=Cannor load xml artifact file.",
        "LeappFileProcessor.cannotBuildXmlParser=Cannot buld an XML parser.",
        "LeappFileProcessor_cannotParseXml=Cannot Parse XML file.",
        "LeappFileProcessor.postartifacts_error=Error posting Blackboard Artifact",
        "LeappFileProcessor.error.creating.new.artifacts=Error creating new artifacts."
    })

    /**
     * Read the XML config file and load the mappings into maps
     */
    private void loadConfigFile() throws IngestModuleException {
        Document xmlinput;
        try {
            String path = PlatformUtil.getUserConfigDirectory() + File.separator + xmlFile;
            File f = new File(path);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            xmlinput = db.parse(f);

        } catch (IOException e) {
            throw new IngestModuleException(Bundle.LeappFileProcessor_cannot_load_artifact_xml() + e.getLocalizedMessage(), e); //NON-NLS
        } catch (ParserConfigurationException pce) {
            throw new IngestModuleException(Bundle.LeappFileProcessor_cannotBuildXmlParser() + pce.getLocalizedMessage(), pce); //NON-NLS
        } catch (SAXException sxe) {
            throw new IngestModuleException(Bundle.LeappFileProcessor_cannotParseXml() + sxe.getLocalizedMessage(), sxe); //NON-NLS
        }

        getFileNode(xmlinput);
        getArtifactNode(xmlinput);
        getAttributeNodes(xmlinput);

    }

    private void getFileNode(Document xmlinput) {

        NodeList nlist = xmlinput.getElementsByTagName("FileName"); //NON-NLS

        for (int i = 0; i < nlist.getLength(); i++) {
            NamedNodeMap nnm = nlist.item(i).getAttributes();
            tsvFiles.put(nnm.getNamedItem("filename").getNodeValue().toLowerCase(), nnm.getNamedItem("description").getNodeValue());

        }

    }

    private void getArtifactNode(Document xmlinput) {

        NodeList artifactNlist = xmlinput.getElementsByTagName("ArtifactName"); //NON-NLS
        for (int k = 0; k < artifactNlist.getLength(); k++) {
            NamedNodeMap nnm = artifactNlist.item(k).getAttributes();
            String artifactName = nnm.getNamedItem("artifactname").getNodeValue();
            String comment = nnm.getNamedItem("comment").getNodeValue();
            String parentName = artifactNlist.item(k).getParentNode().getAttributes().getNamedItem("filename").getNodeValue();

            tsvFileArtifacts.put(parentName, artifactName);

            if (!comment.toLowerCase().matches("null")) {
                tsvFileArtifactComments.put(parentName, comment);
            }
        }

    }

    private void getAttributeNodes(Document xmlinput) {

        NodeList attributeNlist = xmlinput.getElementsByTagName("AttributeName"); //NON-NLS
        for (int k = 0; k < attributeNlist.getLength(); k++) {
            List<String> attributeList = new ArrayList<>();
            NamedNodeMap nnm = attributeNlist.item(k).getAttributes();
            String attributeName = nnm.getNamedItem("attributename").getNodeValue();
            if (!attributeName.toLowerCase().matches("null")) {
                String columnName = nnm.getNamedItem("columnName").getNodeValue();
                String required = nnm.getNamedItem("required").getNodeValue();
                String parentName = attributeNlist.item(k).getParentNode().getParentNode().getAttributes().getNamedItem("filename").getNodeValue();

                attributeList.add(attributeName.toLowerCase());
                attributeList.add(columnName.toLowerCase());
                attributeList.add(required.toLowerCase());

                if (tsvFileAttributes.containsKey(parentName)) {
                    List<List<String>> attrList = tsvFileAttributes.get(parentName);
                    attrList.add(attributeList);
                    tsvFileAttributes.replace(parentName, attrList);
                } else {
                    List<List<String>> attrList = new ArrayList<>();
                    attrList.add(attributeList);
                    tsvFileAttributes.put(parentName, attrList);
                }
            }

        }
    }

    /**
     * Generic method for creating a blackboard artifact with attributes
     *
     * @param type         is a blackboard.artifact_type enum to determine which
     *                     type the artifact should be
     * @param abstractFile is the AbstractFile object that needs to have the
     *                     artifact added for it
     * @param bbattributes is the collection of blackboard attributes that need
     *                     to be added to the artifact after the artifact has
     *                     been created
     *
     * @return The newly-created artifact, or null on error
     */
    private BlackboardArtifact createArtifactWithAttributes(int type, AbstractFile abstractFile, Collection<BlackboardAttribute> bbattributes) {
        try {
            BlackboardArtifact bbart = abstractFile.newArtifact(type);
            bbart.addAttributes(bbattributes);
            return bbart;
        } catch (TskException ex) {
            logger.log(Level.WARNING, Bundle.LeappFileProcessor_error_creating_new_artifacts(), ex); //NON-NLS
        }
        return null;
    }

    /**
     * Generic method for creating a blackboard artifact with attributes
     *
     * @param type         is a blackboard.artifact_type enum to determine which
     *                     type the artifact should be
     * @param dataSource   is the Content object that needs to have the artifact
     *                     added for it
     * @param bbattributes is the collection of blackboard attributes that need
     *                     to be added to the artifact after the artifact has
     *                     been created
     *
     * @return The newly-created artifact, or null on error
     */
    private BlackboardArtifact createArtifactWithAttributes(int type, Content dataSource, Collection<BlackboardAttribute> bbattributes) {
        try {
            BlackboardArtifact bbart = dataSource.newArtifact(type);
            bbart.addAttributes(bbattributes);
            return bbart;
        } catch (TskException ex) {
            logger.log(Level.WARNING, Bundle.LeappFileProcessor_error_creating_new_artifacts(), ex); //NON-NLS
        }
        return null;
    }

    /**
     * Method to post a list of BlackboardArtifacts to the blackboard.
     *
     * @param artifacts A list of artifacts. IF list is empty or null, the
     *                  function will return.
     */
    void postArtifacts(Collection<BlackboardArtifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }

        try {
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifacts(artifacts, MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, Bundle.LeappFileProcessor_postartifacts_error(), ex); //NON-NLS
        }
    }

    /**
     * Extract the Leapp config xml file to the user directory to process
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    private void configExtractor() throws IOException {
        PlatformUtil.extractResourceToUserConfigDir(LeappFileProcessor.class, xmlFile, true);
    }

}
