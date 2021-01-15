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

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import static java.util.Locale.US;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.collections4.MapUtils;
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

    /**
     * Represents metadata for a particular column in a tsv file.
     */
    private static class TsvColumn {

        private final BlackboardAttribute.Type attributeType;
        private final String columnName;
        private final boolean required;

        /**
         * Main constructor.
         *
         * @param attributeType The BlackboardAttribute type or null if not
         * used. used.
         * @param columnName The name of the column in the tsv file.
         * @param required Whether or not this attribute is required to be
         * present.
         */
        TsvColumn(BlackboardAttribute.Type attributeType, String columnName, boolean required) {
            this.attributeType = attributeType;
            this.columnName = columnName;
            this.required = required;
        }

        /**
         * @return The BlackboardAttribute type or null if not used.
         */
        BlackboardAttribute.Type getAttributeType() {
            return attributeType;
        }

        /**
         * @return The name of the column in the tsv file.
         */
        String getColumnName() {
            return columnName;
        }

        /**
         * @return Whether or not this attribute is required to be present.
         */
        boolean isRequired() {
            return required;
        }
    }

    private static final Logger logger = Logger.getLogger(LeappFileProcessor.class.getName());
    private static final String MODULE_NAME = ILeappAnalyzerModuleFactory.getModuleName();

    private final String xmlFile; //NON-NLS

    private final Map<String, String> tsvFiles;
    private final Map<String, BlackboardArtifact.Type> tsvFileArtifacts;
    private final Map<String, String> tsvFileArtifactComments;
    private final Map<String, List<TsvColumn>> tsvFileAttributes;

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
        } catch (IngestModuleException ex) {
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
     * @param LeappImageFile Abstract file to create artifact for
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
                BlackboardArtifact.Type artifactType = null;
                try {
                    List<TsvColumn> attrList = tsvFileAttributes.get(fileName);
                    artifactType = tsvFileArtifacts.get(fileName);
                    processFile(LeappFile, attrList, fileName, artifactType, bbartifacts, LeappImageFile);
                } catch (TskCoreException ex) {
                    throw new IngestModuleException(String.format("Error getting Blackboard Artifact Type for %s", artifactType == null ? "<null>" : artifactType.toString()), ex);
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
     * @param dataSource The data source.
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void processLeappFiles(List<String> LeappFilesToProcess, Content dataSource) throws IngestModuleException {
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();

        for (String LeappFileName : LeappFilesToProcess) {
            String fileName = FilenameUtils.getName(LeappFileName);
            File LeappFile = new File(LeappFileName);
            if (tsvFileAttributes.containsKey(fileName)) {
                List<TsvColumn> attrList = tsvFileAttributes.get(fileName);
                BlackboardArtifact.Type artifactType = tsvFileArtifacts.get(fileName);

                try {
                    processFile(LeappFile, attrList, fileName, artifactType, bbartifacts, dataSource);
                } catch (TskCoreException | IOException ex) {
                    logger.log(Level.SEVERE, String.format("Error processing file at %s", LeappFile.toString()), ex);
                }
            }

        }

        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }

    }

    private void processFile(File LeappFile, List<TsvColumn> attrList, String fileName, BlackboardArtifact.Type artifactType,
            List<BlackboardArtifact> bbartifacts, Content dataSource) throws FileNotFoundException, IOException, IngestModuleException,
            TskCoreException {

        if (LeappFile == null || !LeappFile.exists() || fileName == null) {
            logger.log(Level.WARNING, String.format("Leap file: %s is null or does not exist", LeappFile == null ? LeappFile.toString() : "<null>"));
            return;
        } else if (attrList == null || artifactType == null || dataSource == null) {
            logger.log(Level.WARNING, String.format("attribute list, artifact type or dataSource not provided for %s", LeappFile == null ? LeappFile.toString() : "<null>"));
            return;
        }

        try (MappingIterator<Map<String, String>> iterator = new CsvMapper()
                .readerFor(Map.class)
                .with(CsvSchema.emptySchema().withHeader().withColumnSeparator('\t'))
                .readValues(LeappFile)) {

            int lineNum = 1;
            while (iterator.hasNext()) {
                Map<String, String> keyVals = iterator.next();

                Collection<BlackboardAttribute> bbattributes = processReadLine(keyVals, attrList, fileName, lineNum++);

                if (!bbattributes.isEmpty() && !blkBoard.artifactExists(dataSource, BlackboardArtifact.ARTIFACT_TYPE.fromID(artifactType.getTypeID()), bbattributes)) {
                    BlackboardArtifact bbartifact = createArtifactWithAttributes(artifactType.getTypeID(), dataSource, bbattributes);
                    if (bbartifact != null) {
                        bbartifacts.add(bbartifact);
                    }
                }
            }
        }
    }

    /**
     * Process the line read and create the necessary attributes for it.
     *
     * @param lineKeyValues A mapping of column names to values for the line.
     * @param attrList The list of attributes as specified for the schema of
     * this file.
     * @param fileName The name of the file being processed.
     * @param lineNum The line number in the file.
     * @return The collection of blackboard attributes for the artifact created
     * from this line.
     * @throws IngestModuleException
     */
    private Collection<BlackboardAttribute> processReadLine(Map<String, String> lineKeyValues, List<TsvColumn> attrList, String fileName, int lineNum) throws IngestModuleException {
        if (MapUtils.isEmpty(lineKeyValues)) {
            return Collections.emptyList();
        } else if (lineKeyValues == null) {
            logger.log(Level.WARNING, "Line is null.  Returning empty list for attributes.");
            return Collections.emptyList();
        }

        List<BlackboardAttribute> attrsToRet = new ArrayList<>();
        for (TsvColumn colAttr : attrList) {
            if (colAttr.getAttributeType() == null) {
                continue;
            }

            // TODO error handling
            String value = lineKeyValues.get(colAttr.getColumnName());
            if (value == null) {
                logger.log(Level.WARNING, String.format("No value found for column %s at line %d in file %s.", colAttr.getColumnName(), lineNum, fileName));
                continue;
            }

            BlackboardAttribute attr = (value == null) ? null : getAttribute(colAttr.getAttributeType(), value, fileName);
            if (value != null) {
                attrsToRet.add(attr);
            }
        }

        return attrsToRet;
    }

    /**
     * The format of time stamps in tsv.
     */
    private static final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-d HH:mm:ss", US);

    /**
     * Gets an appropriate attribute based on the attribute type and string
     * value.
     *
     * @param attrType The attribute type.
     * @param value The string value to be converted to the appropriate data
     * type for the attribute type.
     * @param fileName The file name that the value comes from.
     * @return The generated blackboard attribute.
     */
    private BlackboardAttribute getAttribute(BlackboardAttribute.Type attrType, String value, String fileName) {
        if (attrType == null || value == null) {
            logger.log(Level.WARNING, String.format("Unable to parse attribute type %s for value '%s' in fileName %s",
                    attrType == null ? "<null>" : attrType.toString(),
                    value == null ? "<null>" : value,
                    fileName == null ? "<null>" : fileName));
            return null;
        }

        String trimmed = value.trim();
        switch (attrType.getValueType()) {
            case JSON:
            case STRING:
                return parseAttrValue(trimmed, attrType, fileName, (v) -> new BlackboardAttribute(attrType, MODULE_NAME, v));
            case INTEGER:
                return parseAttrValue(trimmed, attrType, fileName, (v) -> new BlackboardAttribute(attrType, MODULE_NAME, (int) Double.valueOf(v).intValue()));
            case LONG:
                return parseAttrValue(trimmed, attrType, fileName, (v) -> new BlackboardAttribute(attrType, MODULE_NAME, (long) Double.valueOf(v).longValue()));
            case DOUBLE:
                return parseAttrValue(trimmed, attrType, fileName, (v) -> new BlackboardAttribute(attrType, MODULE_NAME, (double) Double.valueOf(v)));
            case BYTE:
                return parseAttrValue(trimmed, attrType, fileName, (v) -> new BlackboardAttribute(attrType, MODULE_NAME, new byte[]{Byte.valueOf(v)}));
            case DATETIME:
                return parseAttrValue(value, attrType, fileName, (v) -> new BlackboardAttribute(attrType, MODULE_NAME, TIMESTAMP_FORMAT.parse(v).getTime() / 1000));
            default:
                // Log this and continue on with processing
                logger.log(Level.WARNING, String.format("Attribute Type %s for file %s not defined.", attrType, fileName)); //NON-NLS                   
                return null;
        }
    }

    /**
     * Handles converting a string to a blackboard attribute.
     */
    private interface ParseExceptionFunction {

        /**
         * Handles converting a string value to a blackboard attribute.
         *
         * @param orig The original string value.
         * @return The generated blackboard attribute.
         * @throws ParseException
         * @throws NumberFormatException
         */
        BlackboardAttribute apply(String orig) throws ParseException, NumberFormatException;
    }

    /**
     * Runs parsing function on string value to convert to right data type and
     * generates a blackboard attribute for that converted data type.
     *
     * @param value The string value.
     * @param attrType The blackboard attribute type.
     * @param fileName The name of the file from which the value comes.
     * @param valueConverter The means of converting the string value to an
     * appropriate blackboard attribute.
     * @return The generated blackboard attribute or null if not determined.
     */
    private BlackboardAttribute parseAttrValue(String value, BlackboardAttribute.Type attrType, String fileName, ParseExceptionFunction valueConverter) {
        try {
            return valueConverter.apply(value);
        } catch (NumberFormatException | ParseException ex) {
            logger.log(Level.WARNING, String.format("Unable to format '%s' as value type %s while converting to attributes from %s.", value, attrType.getValueType().getLabel(), fileName), ex);
            return null;
        }
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

            BlackboardArtifact.Type foundArtifactType = null;
            try {
                foundArtifactType = Case.getCurrentCase().getSleuthkitCase().getArtifactType(artifactName);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("There was an issue that arose while trying to fetch artifact type for %s.", artifactName), ex);
            }

            if (foundArtifactType == null) {
                logger.log(Level.SEVERE, String.format("No known artifact mapping found for [artifact: %s, %s]",
                        artifactName, getXmlFileIdentifier(parentName)));
            } else {
                tsvFileArtifacts.put(parentName, foundArtifactType);
            }

            if (!comment.toLowerCase().matches("null")) {
                tsvFileArtifactComments.put(parentName, comment);
            }
        }

    }

    private String getXmlFileIdentifier(String fileName) {
        return String.format("file: %s, filename: %s",
                this.xmlFile == null ? "<null>" : this.xmlFile,
                fileName == null ? "<null>" : fileName);
    }

    private String getXmlAttrIdentifier(String fileName, String attributeName) {
        return String.format("attribute: %s %s",
                attributeName == null ? "<null>" : attributeName,
                getXmlFileIdentifier(fileName));
    }

    private void getAttributeNodes(Document xmlinput) {

        NodeList attributeNlist = xmlinput.getElementsByTagName("AttributeName"); //NON-NLS
        for (int k = 0; k < attributeNlist.getLength(); k++) {
            NamedNodeMap nnm = attributeNlist.item(k).getAttributes();
            String attributeName = nnm.getNamedItem("attributename").getNodeValue();

            if (!attributeName.toLowerCase().matches("null")) {
                String columnName = nnm.getNamedItem("columnName").getNodeValue();
                String required = nnm.getNamedItem("required").getNodeValue();
                String parentName = attributeNlist.item(k).getParentNode().getParentNode().getAttributes().getNamedItem("filename").getNodeValue();

                BlackboardAttribute.Type foundAttrType = null;
                try {
                    foundAttrType = Case.getCurrentCase().getSleuthkitCase().getAttributeType(attributeName.toUpperCase());
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("There was an issue that arose while trying to fetch attribute type for %s.", attributeName), ex);
                }

                if (foundAttrType == null) {
                    logger.log(Level.SEVERE, String.format("No known attribute mapping found for [%s]", getXmlAttrIdentifier(parentName, attributeName)));
                }

                if (required != null && required.compareToIgnoreCase("yes") != 0 && required.compareToIgnoreCase("no") != 0) {
                    logger.log(Level.SEVERE, String.format("Required value %s did not match 'yes' or 'no' for [%s]",
                            required, getXmlAttrIdentifier(parentName, attributeName)));
                }

                if (columnName == null) {
                    logger.log(Level.SEVERE, String.format("No column name provided for [%s]", getXmlAttrIdentifier(parentName, attributeName)));
                } else if (columnName.trim().length() != columnName.length()) {
                    logger.log(Level.SEVERE, String.format("Column name '%s' starts or ends with whitespace for [%s]", columnName, getXmlAttrIdentifier(parentName, attributeName)));
                } else if (columnName.matches("[^ \\S]")) {
                    logger.log(Level.SEVERE, String.format("Column name '%s' contains invalid characters [%s]", columnName, getXmlAttrIdentifier(parentName, attributeName)));
                }

                TsvColumn thisCol = new TsvColumn(
                        foundAttrType,
                        columnName.toLowerCase(),
                        "yes".compareToIgnoreCase(required) == 0);

                if (tsvFileAttributes.containsKey(parentName)) {
                    List<TsvColumn> attrList = tsvFileAttributes.get(parentName);
                    attrList.add(thisCol);
                    tsvFileAttributes.replace(parentName, attrList);
                } else {
                    List<TsvColumn> attrList = new ArrayList<>();
                    attrList.add(thisCol);
                    tsvFileAttributes.put(parentName, attrList);
                }
            }

        }
    }

    /**
     * Generic method for creating a blackboard artifact with attributes
     *
     * @param type is a blackboard.artifact_type enum to determine which type
     * the artifact should be
     * @param abstractFile is the AbstractFile object that needs to have the
     * artifact added for it
     * @param bbattributes is the collection of blackboard attributes that need
     * to be added to the artifact after the artifact has been created
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
     * @param type is a blackboard.artifact_type enum to determine which type
     * the artifact should be
     * @param dataSource is the Content object that needs to have the artifact
     * added for it
     * @param bbattributes is the collection of blackboard attributes that need
     * to be added to the artifact after the artifact has been created
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
     * function will return.
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
        PlatformUtil.extractResourceToUserConfigDir(LeappFileProcessor.class,
                xmlFile, true);
    }

}
