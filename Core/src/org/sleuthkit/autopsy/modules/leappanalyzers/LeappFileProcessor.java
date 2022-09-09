/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.collect.ImmutableMap;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import static java.util.Locale.US;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.getCurrentCase;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper.CallMediaType;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper.CommunicationDirection;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper.MessageReadStatus;
import org.sleuthkit.datamodel.blackboardutils.GeoArtifactsHelper;
import org.sleuthkit.datamodel.blackboardutils.attributes.GeoTrackPoints;
import org.sleuthkit.datamodel.blackboardutils.attributes.GeoTrackPoints.TrackPoint;
import org.sleuthkit.datamodel.blackboardutils.attributes.GeoWaypoints;
import org.sleuthkit.datamodel.blackboardutils.attributes.GeoWaypoints.Waypoint;
import org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments;
import org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments.FileAttachment;
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
         *                      used. used.
         * @param columnName    The name of the column in the tsv file.
         * @param required      Whether or not this attribute is required to be
         *                      present.
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
    private final String xmlFile; //NON-NLS
    private final String moduleName;
    private final IngestJobContext context;

    private final Map<String, String> tsvFiles;
    private final Map<String, BlackboardArtifact.Type> tsvFileArtifacts;
    private final Map<String, String> tsvFileArtifactComments;
    private final Map<String, List<TsvColumn>> tsvFileAttributes;

    private static final Map<String, String> CUSTOM_ARTIFACT_MAP = ImmutableMap.<String, String>builder()
            .put("TSK_IP_DHCP", "DHCP Information")
            .build();

    private static final Map<String, String> ACCOUNT_RELATIONSHIPS = ImmutableMap.<String, String>builder()
            .put("zapya.tsv", "message")
            .put("sms messages.tsv", "message")
            .put("mms messages.tsv", "message")
            .put("viber - messages.tsv", "message")
            .put("viber - contacts.tsv", "contact")
            .put("viber - call logs.tsv", "calllog")
            .put("xender file transfer - messages.tsv", "message")
            .put("xender file transfer - contacts.tsv", "contact")
            .put("whatsapp - contacts.tsv", "contact")
            .put("whatsapp - group call logs.tsv", "calllog")
            .put("whatsapp - single call logs.tsv", "calllog")
            .put("whatsapp - messages logs.tsv", "message")
            .put("shareit file transfer.tsv", "message")
            .put("tangomessages messages.tsv", "message")
            .put("contacts.tsv", "contact")
            .put("imo - accountid.tsv", "contact")
            .put("imo - messages.tsv", "message")
            .put("textnow - contacts.tsv", "contact")
            .put("textnow - messages.tsv", "message")
            .put("line - messages.tsv", "message")
            .put("line - contacts.tsv", "contact")
            .put("line - calllogs.tsv", "calllog")
            .put("skype - messages logs.tsv", "message")
            .put("skype - contacts.tsv", "contact")
            .put("skype - call logs.tsv", "calllog")
            .put("facebook messenger - chats.tsv", "message")
            .put("facebook messenger - contacts.tsv", "contact")
            .put("facebook messenger - calls.tsv", "calllog")
            .put("call logs2.tsv", "calllog")
            .put("call logs.tsv", "calllog")
            .put("oruxmaps tracks.tsv", "trackpoint")
            .put("google map locations.tsv", "route")
            .put("Contacts.tsv", "contact")
            .put("sms - imessage.tsv", "message")
            .put("call history.tsv", "calllog")
            .build();

    private final Blackboard blkBoard;

    public LeappFileProcessor(String xmlFile, String moduleName, IngestJobContext context) throws IOException, IngestModuleException, NoCurrentCaseException {
        this.tsvFiles = new HashMap<>();
        this.tsvFileArtifacts = new HashMap<>();
        this.tsvFileArtifactComments = new HashMap<>();
        this.tsvFileAttributes = new HashMap<>();
        this.xmlFile = xmlFile;
        this.moduleName = moduleName;
        this.context = context;

        blkBoard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();

        createCustomArtifacts(blkBoard);
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
        "LeappFileProcessor.findTsv=Finding all Leapp ouput",
        "LeappFileProcessor.error.reading.Leapp.directory=Error reading Leapp Output Directory"
    })
    public ProcessResult processFiles(Content dataSource, Path moduleOutputPath, AbstractFile LeappFile, DataSourceIngestModuleProgress progress) {
        try {
            if (checkCancelled()) {
                return ProcessResult.OK;
            }
            progress.switchToIndeterminate();
            progress.progress(Bundle.LeappFileProcessor_findTsv());
            List<String> LeappTsvOutputFiles = findTsvFiles(moduleOutputPath);
            processLeappFiles(LeappTsvOutputFiles, LeappFile, progress);
        } catch (IngestModuleException ex) {
            logger.log(Level.SEVERE, String.format("Error trying to process Leapp output files in directory %s. ", moduleOutputPath.toString()), ex); //NON-NLS
            return ProcessResult.ERROR;
        }

        return ProcessResult.OK;
    }

    public ProcessResult processFileSystem(Content dataSource, Path moduleOutputPath, DataSourceIngestModuleProgress progress) {
        try {
            if (checkCancelled()) {
                return ProcessResult.OK;
            }
            progress.switchToIndeterminate();
            progress.progress(Bundle.LeappFileProcessor_findTsv());
            List<String> LeappTsvOutputFiles = findTsvFiles(moduleOutputPath);
            processLeappFiles(LeappTsvOutputFiles, dataSource, progress);
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
        List<String> allTsvFiles;
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

    private boolean checkCancelled() {
        if (this.context.dataSourceIngestIsCancelled()) {
            logger.log(Level.INFO, "Leapp File processing module run was cancelled"); //NON-NLS
            return true;
        } else {
            return false;
        }
    }

    /**
     * Process the Leapp files that were found that match the xml mapping file
     *
     * @param LeappFilesToProcess List of files to process.
     * @param dataSource          The data source.
     * @param progress            Means of updating progress in UI.
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    @Messages({
        "# {0} - fileName",
        "LeappFileProcessor.tsvProcessed=Processing LEAPP output file: {0}"
    })
    private void processLeappFiles(List<String> LeappFilesToProcess, Content dataSource, DataSourceIngestModuleProgress progress) throws IngestModuleException {
        progress.switchToDeterminate(LeappFilesToProcess.size());

        for (int i = 0; i < LeappFilesToProcess.size(); i++) {
            if (checkCancelled()) {
                return;
            }

            String LeappFileName = LeappFilesToProcess.get(i);
            String fileName = FilenameUtils.getName(LeappFileName);
            progress.progress(Bundle.LeappFileProcessor_tsvProcessed(fileName), i);

            File LeappFile = new File(LeappFileName);
            if (tsvFileAttributes.containsKey(fileName)) {
                List<TsvColumn> attrList = tsvFileAttributes.get(fileName);
                BlackboardArtifact.Type artifactType = tsvFileArtifacts.get(fileName);

                try {
                    processFile(LeappFile, attrList, fileName, artifactType, dataSource);
                } catch (TskCoreException | IOException ex) {
                    logger.log(Level.SEVERE, String.format("Error processing file at %s", LeappFile.toString()), ex);
                }
            }

        }
    }

    private void processFile(File LeappFile, List<TsvColumn> attrList, String fileName,
            BlackboardArtifact.Type artifactType, Content dataSource) throws FileNotFoundException, IOException, IngestModuleException,
            TskCoreException {

        String trackpointSegmentName = null;
        GeoTrackPoints pointList = new GeoTrackPoints();
        AbstractFile geoAbstractFile = null;

        if (LeappFile == null || !LeappFile.exists() || fileName == null) {
            logger.log(Level.WARNING, String.format("Leap file: %s is null or does not exist", LeappFile != null ? LeappFile.toString() : "<null>"));
            return;
        } else if (attrList == null || artifactType == null || dataSource == null) {
            logger.log(Level.WARNING, String.format("attribute list, artifact type or dataSource not provided for %s", LeappFile.toString()));
            return;
        }

        List<BlackboardArtifact> bbartifacts = new ArrayList<>();

        // based on https://stackoverflow.com/questions/56921465/jackson-csv-schema-for-array
        try (MappingIterator<List<String>> iterator = new CsvMapper()
                .enable(CsvParser.Feature.WRAP_AS_ARRAY)
                .readerFor(List.class)
                .with(CsvSchema.emptySchema().withColumnSeparator('\t'))
                .readValues(LeappFile)) {

            if (iterator.hasNext()) {
                List<String> headerItems = iterator.next();
                Map<String, Integer> columnIndexes = IntStream.range(0, headerItems.size())
                        .mapToObj(idx -> idx)
                        .collect(Collectors.toMap(
                                idx -> headerItems.get(idx) == null ? null : headerItems.get(idx).trim().toLowerCase(),
                                idx -> idx,
                                (val1, val2) -> val1));

                int lineNum = 2;
                while (iterator.hasNext()) {
                    List<String> columnItems = iterator.next();
                    Collection<BlackboardAttribute> bbattributes = processReadLine(columnItems, columnIndexes, attrList, fileName, lineNum);

                    if (!bbattributes.isEmpty()) {
                        switch (ACCOUNT_RELATIONSHIPS.getOrDefault(fileName.toLowerCase(), "norelationship").toLowerCase()) {
                            case "message":
                                createMessageRelationship(bbattributes, dataSource, fileName);
                                break;
                            case "contact":
                                createContactRelationship(bbattributes, dataSource, fileName);
                                break;
                            case "calllog":
                                createCalllogRelationship(bbattributes, dataSource, fileName);
                                break;
                            case "route":
                                createRoute(bbattributes, dataSource, fileName);
                                break;
                            case "trackpoint":
                                geoAbstractFile = createTrackpoint(bbattributes, dataSource, fileName, trackpointSegmentName, pointList);
                                break;
                            default: // There is no relationship defined so just process the artifact normally
                                BlackboardArtifact bbartifact = createArtifactWithAttributes(artifactType, dataSource, bbattributes);
                                if (bbartifact != null) {
                                    bbartifacts.add(bbartifact);
                                }
                                break;
                        }
                    }

                    lineNum++;
                }
            }
        }

        try {
            if (ACCOUNT_RELATIONSHIPS.getOrDefault(fileName.toLowerCase(), "norelationship").toLowerCase().equals("trackpoint")) {
                (new GeoArtifactsHelper(Case.getCurrentCaseThrows().getSleuthkitCase(), moduleName, "", geoAbstractFile, context.getJobId())).addTrack(trackpointSegmentName, pointList, new ArrayList<>());
            }
        } catch (NoCurrentCaseException | TskCoreException | BlackboardException ex) {
            throw new IngestModuleException(Bundle.LeappFileProcessor_cannot_create_message_relationship() + ex.getLocalizedMessage(), ex); //NON-NLS
        }

        if (!bbartifacts.isEmpty()) {
            postArtifacts(bbartifacts);
        }
    }

    @NbBundle.Messages({
        "LeappFileProcessor.cannot.create.waypoint.relationship=Cannot create TSK_WAYPOINT artifact."
    })
    private void createRoute(Collection<BlackboardAttribute> bbattributes, Content dataSource, String fileName) throws IngestModuleException {

        Double startLatitude = Double.valueOf(0);
        Double startLongitude = Double.valueOf(0);
        Double endLatitude = Double.valueOf(0);
        Double endLongitude = Double.valueOf(0);
        Double zeroValue = Double.valueOf(0);
        String destinationName = "";
        String locationName = "";
        Long dateTime = Long.valueOf(0);
        Collection<BlackboardAttribute> otherAttributes = new ArrayList<>();
        String sourceFile = null;
        AbstractFile absFile;
        String comment = "";

        try {
            for (BlackboardAttribute bba : bbattributes) {
                switch (bba.getAttributeType().getTypeName()) {
                    case "TSK_GEO_LATITUDE_START":
                        startLatitude = bba.getValueDouble();
                        break;
                    case "TSK_GEO_LONGITUDE_START":
                        startLongitude = bba.getValueDouble();
                        break;
                    case "TSK_GEO_LATITUDE_END":
                        startLatitude = bba.getValueDouble();
                        break;
                    case "TSK_GEO_LONGITUDE_END":
                        startLongitude = bba.getValueDouble();
                        break;
                    case "TSK_DATETIME":
                        dateTime = bba.getValueLong();
                        break;
                    case "TSK_NAME":
                        destinationName = bba.getValueString();
                        break;
                    case "TSK_LOCATION":
                        locationName = bba.getValueString();
                        break;
                    case "TSK_TEXT_FILE":
                        sourceFile = bba.getValueString();
                        break;
                    case "TSK_COMMENT":
                        comment = bba.getValueString();
                        break;
                    default:
                        otherAttributes.add(bba);
                        break;
                }
            }
            absFile = findAbstractFile(dataSource, sourceFile);
            if (absFile == null) {
                absFile = (AbstractFile) dataSource;
            }
            GeoWaypoints waypointList = new GeoWaypoints();
            waypointList.addPoint(new Waypoint(startLatitude, startLongitude, zeroValue, ""));
            waypointList.addPoint(new Waypoint(endLatitude, endLongitude, zeroValue, locationName));
            (new GeoArtifactsHelper(Case.getCurrentCaseThrows().getSleuthkitCase(), moduleName, comment, absFile, context.getJobId())).addRoute(destinationName, dateTime, waypointList, new ArrayList<>());

        } catch (NoCurrentCaseException | TskCoreException | BlackboardException ex) {
            throw new IngestModuleException(Bundle.LeappFileProcessor_cannot_create_waypoint_relationship() + ex.getLocalizedMessage(), ex); //NON-NLS
        }

    }

    @NbBundle.Messages({
        "LeappFileProcessor.cannot.create.trackpoint.relationship=Cannot create TSK_TRACK_POINT artifact."
    })
    private AbstractFile createTrackpoint(Collection<BlackboardAttribute> bbattributes, Content dataSource, String fileName, String trackpointSegmentName, GeoTrackPoints pointList) throws IngestModuleException {

        Double latitude = Double.valueOf(0);
        Double longitude = Double.valueOf(0);
        Double altitude = Double.valueOf(0);
        Double zeroValue = Double.valueOf(0);
        String segmentName = null;
        Long dateTime = Long.valueOf(0);
        Collection<BlackboardAttribute> otherAttributes = new ArrayList<>();
        String sourceFile = null;
        String comment = null;
        AbstractFile absFile = null;

        try {
            for (BlackboardAttribute bba : bbattributes) {
                switch (bba.getAttributeType().getTypeName()) {
                    case "TSK_GEO_LATITUDE":
                        latitude = bba.getValueDouble();
                        break;
                    case "TSK_GEO_LONGITUDE":
                        longitude = bba.getValueDouble();
                        break;
                    case "TSK_GEO_ALTITUDE":
                        altitude = bba.getValueDouble();
                        break;
                    case "TSK_DATETIME":
                        dateTime = bba.getValueLong();
                        break;
                    case "TSK_NAME":
                        segmentName = bba.getValueString();
                        break;
                    case "TSK_TEXT_FILE":
                        sourceFile = bba.getValueString();
                        break;
                    case "TSK_COMMENT":
                        comment = bba.getValueString();
                        otherAttributes.add(bba);
                        break;
                    default:
                        otherAttributes.add(bba);
                        break;
                }
            }
            absFile = findAbstractFile(dataSource, sourceFile);
            if (absFile == null) {
                absFile = (AbstractFile) dataSource;
            }
            if ((trackpointSegmentName == null) || (trackpointSegmentName.equals(segmentName))) {
                pointList.addPoint(new TrackPoint(latitude, longitude, altitude, segmentName, zeroValue, zeroValue, zeroValue, dateTime));
            } else {
                (new GeoArtifactsHelper(Case.getCurrentCaseThrows().getSleuthkitCase(), moduleName, comment, absFile, context.getJobId())).addTrack(segmentName, pointList, new ArrayList<>());
                pointList.addPoint(new TrackPoint(latitude, longitude, altitude, segmentName, zeroValue, zeroValue, zeroValue, dateTime));

            }
        } catch (NoCurrentCaseException | TskCoreException | BlackboardException ex) {
            throw new IngestModuleException(Bundle.LeappFileProcessor_cannot_create_trackpoint_relationship() + ex.getLocalizedMessage(), ex); //NON-NLS
        }

        return absFile;

    }

    @NbBundle.Messages({
        "LeappFileProcessor.cannot.create.message.relationship=Cannot create TSK_MESSAGE Relationship."
    })
    private void createMessageRelationship(Collection<BlackboardAttribute> bbattributes, Content dataSource, String fileName) throws IngestModuleException {

        String messageType = null;
        String alternateId = null;
        CommunicationDirection communicationDirection = CommunicationDirection.UNKNOWN;
        String senderId = null;
        String receipentId = null;
        String[] receipentIdList = null;
        Long dateTime = Long.valueOf(0);
        MessageReadStatus messageStatus = MessageReadStatus.UNKNOWN;
        String subject = null;
        String messageText = null;
        String threadId = null;
        List<BlackboardAttribute> otherAttributes = new ArrayList<>();
        List<FileAttachment> fileAttachments = new ArrayList<>();
        String sourceFile = null;
        MessageAttachments messageAttachments;

        try {
            for (BlackboardAttribute bba : bbattributes) {
                switch (bba.getAttributeType().getTypeName()) {
                    case "TSK_DIRECTION":
                        if (bba.getValueString().toLowerCase().equals("outgoing")) {
                            communicationDirection = CommunicationDirection.OUTGOING;
                        } else if (bba.getValueString().toLowerCase().equals("incoming")) {
                            communicationDirection = CommunicationDirection.INCOMING;
                        }
                        break;
                    case "TSK_PHONE_NUMBER_FROM":
                        if (!bba.getValueString().isEmpty()) {
                            senderId = bba.getValueString();
                        }
                        break;
                    case "TSK_PHONE_NUMBER_TO":
                        if (!bba.getValueString().isEmpty()) {
                            receipentIdList = bba.getValueString().split(",", 0);
                        }
                        break;
                    case "TSK_DATETIME":
                        dateTime = bba.getValueLong();
                        break;
                    case "TSK_COMMENT":
                        messageType = bba.getValueString();
                        break;
                    case "TSK_ATTACHMENTS":
                        if (!bba.getValueString().isEmpty()) {
                            fileAttachments.add(new FileAttachment(Case.getCurrentCaseThrows().getSleuthkitCase(), dataSource, bba.getValueString()));
                        }
                        break;
                    case "TSK_TEXT_FILE":
                        sourceFile = bba.getValueString();
                        break;
                    case "TSK_READ_STATUS":
                        if (bba.getValueInt() == 1) {
                            messageStatus = MessageReadStatus.READ;
                        } else {
                            messageStatus = MessageReadStatus.UNREAD;
                        }
                        break;
                    case "TSK_TEXT":
                        messageText = bba.getValueString();
                        break;
                    case "TSK_SUBJECT":
                        subject = bba.getValueString();
                        break;
                    case "TSK_ID":
                        alternateId = bba.getValueString();
                        otherAttributes.add(bba);
                        break;
                    default:
                        otherAttributes.add(bba);
                        break;
                }
            }
            AbstractFile absFile = findAbstractFile(dataSource, sourceFile);
            if (absFile == null) {
                absFile = (AbstractFile) dataSource;
            }
            CommunicationArtifactsHelper accountHelper;
            Account.Type accountType = getAccountType(fileName);
            if (alternateId == null) {
                accountHelper = new CommunicationArtifactsHelper(Case.getCurrentCaseThrows().getSleuthkitCase(),
                        moduleName, absFile, accountType, context.getJobId());
            } else {
                accountHelper = new CommunicationArtifactsHelper(Case.getCurrentCaseThrows().getSleuthkitCase(),
                        moduleName, absFile, accountType, accountType, alternateId, context.getJobId());
            }
            BlackboardArtifact messageArtifact = accountHelper.addMessage(messageType, communicationDirection, senderId,
                    receipentId, dateTime, messageStatus, subject,
                    messageText, threadId, otherAttributes);
            if (!fileAttachments.isEmpty()) {
                messageAttachments = new MessageAttachments(fileAttachments, new ArrayList<>());
                accountHelper.addAttachments(messageArtifact, messageAttachments);
            }
        } catch (NoCurrentCaseException | TskCoreException | BlackboardException ex) {
            throw new IngestModuleException(Bundle.LeappFileProcessor_cannot_create_message_relationship() + ex.getLocalizedMessage(), ex); //NON-NLS
        }

    }

    @NbBundle.Messages({
        "LeappFileProcessor.cannot.create.contact.relationship=Cannot create TSK_CONTACT Relationship."
    })
    private void createContactRelationship(Collection<BlackboardAttribute> bbattributes, Content dataSource, String fileName) throws IngestModuleException {

        String alternateId = null;
        String contactName = null;
        String phoneNumber = null;
        String homePhoneNumber = null;
        String mobilePhoneNumber = null;
        String emailAddr = null;
        List<BlackboardAttribute> otherAttributes = new ArrayList<>();
        String sourceFile = null;

        try {
            for (BlackboardAttribute bba : bbattributes) {
                switch (bba.getAttributeType().getTypeName()) {
                    case "TSK_PHONE_NUMBER":
                        if (!bba.getValueString().isEmpty()) {
                            phoneNumber = bba.getValueString();
                        }
                        break;
                    case "TSK_NAME":
                        if (!bba.getValueString().isEmpty()) {
                            contactName = bba.getValueString();
                        }
                        break;
                    case "TSK_TEXT_FILE":
                        sourceFile = bba.getValueString();
                        break;
                    case "TSK_PHONE_NUMBER_HOME":
                        homePhoneNumber = bba.getValueString();
                        break;
                    case "TSK_PHONE_NUMBER_MOBILE":
                        mobilePhoneNumber = bba.getValueString();
                        break;
                    case "TSK_EMAIL":
                        emailAddr = bba.getValueString();
                        break;
                    case "TSK_ID":
                        alternateId = bba.getValueString();
                        otherAttributes.add(bba);
                        break;
                    default:
                        otherAttributes.add(bba);
                        break;
                }
            }
            AbstractFile absFile = findAbstractFile(dataSource, sourceFile);
            if (absFile == null) {
                absFile = (AbstractFile) dataSource;
            }
            Account.Type accountType = getAccountType(fileName);
            if (accountType != null) {

                CommunicationArtifactsHelper accountHelper;
                if (alternateId == null) {
                    accountHelper = new CommunicationArtifactsHelper(Case.getCurrentCaseThrows().getSleuthkitCase(),
                            moduleName, absFile, accountType, context.getJobId());
                } else {
                    accountHelper = new CommunicationArtifactsHelper(Case.getCurrentCaseThrows().getSleuthkitCase(),
                            moduleName, absFile, accountType, accountType, alternateId, context.getJobId());
                }
                BlackboardArtifact messageArtifact = accountHelper.addContact(contactName, phoneNumber, homePhoneNumber, mobilePhoneNumber, emailAddr, otherAttributes);
            }
        } catch (NoCurrentCaseException | TskCoreException | BlackboardException ex) {
            throw new IngestModuleException(Bundle.LeappFileProcessor_cannot_create_contact_relationship() + ex.getLocalizedMessage(), ex); //NON-NLS
        }
    }

    @NbBundle.Messages({
        "LeappFileProcessor.cannot.create.calllog.relationship=Cannot create TSK_CALLLOG Relationship."
    })
    private void createCalllogRelationship(Collection<BlackboardAttribute> bbattributes, Content dataSource, String fileName) throws IngestModuleException {

        String callerId = null;
        String alternateId = null;
        List<String> calleeId = Arrays.asList();
        CommunicationDirection communicationDirection = CommunicationDirection.UNKNOWN;
        Long startDateTime = Long.valueOf(0);
        Long endDateTime = Long.valueOf(0);
        CallMediaType mediaType = CallMediaType.UNKNOWN;
        List<BlackboardAttribute> otherAttributes = new ArrayList<>();
        String sourceFile = null;

        try {
            for (BlackboardAttribute bba : bbattributes) {
                switch (bba.getAttributeType().getTypeName()) {
                    case "TSK_TEXT_FILE":
                        sourceFile = bba.getValueString();
                        break;
                    case "TSK_DATETIME_START":
                        startDateTime = bba.getValueLong();
                        break;
                    case "TSK_DATETIME_END":
                        startDateTime = bba.getValueLong();
                        break;
                    case "TSK_DIRECTION":
                        if (bba.getValueString().toLowerCase().equals("outgoing")) {
                            communicationDirection = CommunicationDirection.OUTGOING;
                        } else if (bba.getValueString().toLowerCase().equals("incoming")) {
                            communicationDirection = CommunicationDirection.INCOMING;
                        }
                        break;
                    case "TSK_PHONE_NUMBER_FROM":
                        if (!bba.getValueString().isEmpty()) {
                            callerId = bba.getValueString();
                        }
                        break;
                    case "TSK_PHONE_NUMBER_TO":
                        if (!bba.getValueString().isEmpty()) {
                            String[] calleeTempList = bba.getValueString().split(",", 0);
                            calleeId = Arrays.asList(calleeTempList);
                        }
                        break;
                    case "TSK_ID":
                        alternateId = bba.getValueString();
                        otherAttributes.add(bba);
                        break;
                    default:
                        otherAttributes.add(bba);
                        break;
                }
            }

            if (calleeId.isEmpty() && communicationDirection == CommunicationDirection.OUTGOING && callerId != null) {
                String[] calleeTempList = callerId.split(",", 0);
                calleeId = Arrays.asList(calleeTempList);
                callerId = null;
            }
            AbstractFile absFile = findAbstractFile(dataSource, sourceFile);
            if (absFile == null) {
                absFile = (AbstractFile) dataSource;
            }
            Account.Type accountType = getAccountType(fileName);
            CommunicationArtifactsHelper accountHelper;
            if (accountType != null) {
                accountHelper = new CommunicationArtifactsHelper(Case.getCurrentCaseThrows().getSleuthkitCase(),
                        moduleName, absFile, accountType, context.getJobId());
            } else {
                accountHelper = new CommunicationArtifactsHelper(Case.getCurrentCaseThrows().getSleuthkitCase(),
                        moduleName, absFile, accountType, accountType, alternateId, context.getJobId());
            }
            accountHelper.addCalllog(communicationDirection, callerId, calleeId, startDateTime, endDateTime, mediaType, otherAttributes);
        } catch (NoCurrentCaseException | TskCoreException | BlackboardException ex) {
            throw new IngestModuleException(Bundle.LeappFileProcessor_cannot_create_calllog_relationship() + ex.getLocalizedMessage(), ex); //NON-NLS
        }

    }

    private Account.Type getAccountType(String AccountTypeName) {
        switch (AccountTypeName.toLowerCase()) {
            case "zapya.tsv":
                return Account.Type.ZAPYA;
            case "sms messages.tsv":
                return Account.Type.PHONE;
            case "contacts.tsv":
                return Account.Type.PHONE;
            case "imo - accountid.tsv":
                return Account.Type.IMO;
            case "imo - messages.tsv":
                return Account.Type.IMO;
            case "textnow - contacts.tsv":
                return Account.Type.TEXTNOW;
            case "textnow - messages.tsv":
                return Account.Type.TEXTNOW;
            case "mms messages.tsv":
                return Account.Type.PHONE;
            case "viber - call logs.tsv":
                return Account.Type.VIBER;
            case "viber - contacts.tsv":
                return Account.Type.VIBER;
            case "viber - messages.tsv":
                return Account.Type.VIBER;
            case "xender file transfer - messages.tsv":
                return Account.Type.XENDER;
            case "xender file transfer - contacts.tsv":
                return Account.Type.XENDER;
            case "whatsapp - single call logs.tsv":
                return Account.Type.WHATSAPP;
            case "whatsapp - messages logs.tsv":
                return Account.Type.WHATSAPP;
            case "whatsapp - group call logs.tsv":
                return Account.Type.WHATSAPP;
            case "whatsapp - contacts.tsv":
                return Account.Type.WHATSAPP;
            case "tangomessages messages.tsv":
                return Account.Type.TANGO;
            case "shareit file transfer.tsv":
                return Account.Type.SHAREIT;
            case "line - calllogs.tsv":
                return Account.Type.LINE;
            case "line - contacts.tsv":
                return Account.Type.LINE;
            case "line - messages.tsv":
                return Account.Type.LINE;
            case "skype - call logs.tsv":
                return Account.Type.SKYPE;
            case "skype - contacts.tsv":
                return Account.Type.SKYPE;
            case "skype - messages logs.tsv":
                return Account.Type.SKYPE;
            case "facebook messenger - calls.tsv":
                return Account.Type.FACEBOOK;
            case "facebook messenger - contacts.tsv":
                return Account.Type.FACEBOOK;
            case "facebook messenger - chats.tsv":
                return Account.Type.FACEBOOK;
            case "call logs2.tsv":
                return Account.Type.PHONE;
            case "call logs.tsv":
                return Account.Type.PHONE;
            case "sms - imessage.tsv":
                return Account.Type.PHONE;
            default:
                return Account.Type.PHONE;
        }
    }

    /**
     * Process the line read and create the necessary attributes for it.
     *
     * @param lineValues    List of column values.
     * @param columnIndexes Mapping of column headers (trimmed; to lower case)
     *                      to column index. All header columns and only all
     *                      header columns should be present.
     * @param attrList      The list of attributes as specified for the schema
     *                      of this file.
     * @param fileName      The name of the file being processed.
     * @param lineNum       The line number in the file.
     *
     * @return The collection of blackboard attributes for the artifact created
     *         from this line.
     *
     * @throws IngestModuleException
     */
    private Collection<BlackboardAttribute> processReadLine(List<String> lineValues, Map<String, Integer> columnIndexes,
            List<TsvColumn> attrList, String fileName, int lineNum) throws IngestModuleException {

        if (MapUtils.isEmpty(columnIndexes) || CollectionUtils.isEmpty(lineValues)
                || (lineValues.size() == 1 && StringUtils.isEmpty(lineValues.get(0)))) {
            return Collections.emptyList();
        } else if (lineValues.size() != columnIndexes.size()) {
            logger.log(Level.WARNING, String.format(
                    "Row at line number %d in file %s has %d columns when %d were expected based on the header row.",
                    lineNum, fileName, lineValues.size(), columnIndexes.size()));
            return Collections.emptyList();
        }

        List<BlackboardAttribute> attrsToRet = new ArrayList<>();
        for (TsvColumn colAttr : attrList) {
            if (colAttr.getAttributeType() == null) {
                // this handles columns that are currently ignored.
                continue;
            }

            Integer columnIdx = columnIndexes.get(colAttr.getColumnName());
            if (columnIdx == null) {
                logger.log(Level.WARNING, String.format("No column mapping found for %s in file %s.  Omitting column.", colAttr.getColumnName(), fileName));
                continue;
            }

            String value = (columnIdx >= lineValues.size() || columnIdx < 0) ? null : lineValues.get(columnIdx);
            if (value == null) {
                logger.log(Level.WARNING, String.format("No value found for column %s at line %d in file %s.  Omitting row.", colAttr.getColumnName(), lineNum, fileName));
                return Collections.emptyList();
            }

            String formattedValue = formatValueBasedOnAttrType(colAttr, value);

            BlackboardAttribute attr = getAttribute(colAttr.getAttributeType(), formattedValue, fileName);
            if (attr == null) {
                logger.log(Level.WARNING, String.format("Blackboard attribute could not be parsed column %s at line %d in file %s.  Omitting row.", colAttr.getColumnName(), lineNum, fileName));
                return Collections.emptyList();
            }
            attrsToRet.add(attr);
        }

        if (tsvFileArtifactComments.containsKey(fileName)) {
            attrsToRet.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COMMENT, moduleName, tsvFileArtifactComments.get(fileName)));
        }

        return attrsToRet;
    }

    /**
     * Check type of attribute and possibly format string based on it.
     *
     * @param colAttr Column Attribute information
     * @param value   string to be formatted
     *
     * @return formatted string based on attribute type if no attribute type
     *         found then return original string
     */
    private String formatValueBasedOnAttrType(TsvColumn colAttr, String value) {
        if (colAttr.getAttributeType().getTypeName().equals("TSK_DOMAIN")) {
            return NetworkUtils.extractDomain(value);
        }

        return value;
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
     * @param value    The string value to be converted to the appropriate data
     *                 type for the attribute type.
     * @param fileName The file name that the value comes from.
     *
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

        switch (attrType.getValueType()) {
            case JSON:
            case STRING:
                return parseAttrValue(value, attrType, fileName, false, false,
                        (v) -> new BlackboardAttribute(attrType, moduleName, v));
            case INTEGER:
                return parseAttrValue(value.trim(), attrType, fileName, true, false,
                        (v) -> new BlackboardAttribute(attrType, moduleName, Double.valueOf(v).intValue()));
            case LONG:
                return parseAttrValue(value.trim(), attrType, fileName, true, false,
                        (v) -> new BlackboardAttribute(attrType, moduleName, Double.valueOf(v).longValue()));
            case DOUBLE:
                return parseAttrValue(value.trim(), attrType, fileName, true, false,
                        (v) -> new BlackboardAttribute(attrType, moduleName, Double.valueOf(v)));
            case BYTE:
                return parseAttrValue(value.trim(), attrType, fileName, true, false,
                        (v) -> new BlackboardAttribute(attrType, moduleName, new byte[]{Byte.valueOf(v)}));
            case DATETIME:
                return parseAttrValue(value.trim(), attrType, fileName, true, true,
                        (v) -> new BlackboardAttribute(attrType, moduleName, TIMESTAMP_FORMAT.parse(v).getTime() / 1000));
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
         *
         * @return The generated blackboard attribute.
         *
         * @throws ParseException
         * @throws NumberFormatException
         */
        BlackboardAttribute apply(String orig) throws ParseException, NumberFormatException;
    }

    /**
     * Runs parsing function on string value to convert to right data type and
     * generates a blackboard attribute for that converted data type.
     *
     * @param value          The string value.
     * @param attrType       The blackboard attribute type.
     * @param fileName       The name of the file from which the value comes.
     * @param blankIsNull    If string is blank return null attribute.
     * @param zeroIsNull     If string is some version of 0, return null
     *                       attribute.
     * @param valueConverter The means of converting the string value to an
     *                       appropriate blackboard attribute.
     *
     * @return The generated blackboard attribute or null if not determined.
     */
    private BlackboardAttribute parseAttrValue(String value, BlackboardAttribute.Type attrType, String fileName, boolean blankIsNull, boolean zeroIsNull, ParseExceptionFunction valueConverter) {
        // remove non-printable characters from tsv input
        // https://stackoverflow.com/a/6199346
        String sanitizedValue = value.replaceAll("\\p{C}", "");

        if (blankIsNull && StringUtils.isBlank(sanitizedValue)) {
            return null;
        }

        if (zeroIsNull && sanitizedValue.matches("^\\s*[0\\.]*\\s*$")) {
            return null;
        }

        try {
            return valueConverter.apply(sanitizedValue);
        } catch (NumberFormatException | ParseException ex) {
            logger.log(Level.WARNING, String.format("Unable to format '%s' as value type %s while converting to attributes from %s.", sanitizedValue, attrType.getValueType().getLabel(), fileName), ex);
            return null;
        }
    }

    /**
     * Read the XML config file and load the mappings into maps
     */
    @NbBundle.Messages({
        "LeappFileProcessor.cannot.load.artifact.xml=Cannot load xml artifact file.",
        "LeappFileProcessor.cannotBuildXmlParser=Cannot buld an XML parser.",
        "LeappFileProcessor_cannotParseXml=Cannot Parse XML file.",
        "LeappFileProcessor.postartifacts_error=Error posting Blackboard Artifact",
        "LeappFileProcessor.error.creating.new.artifacts=Error creating new artifacts."
    })
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
                foundArtifactType = Case.getCurrentCase().getSleuthkitCase().getBlackboard().getArtifactType(artifactName);
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
                    foundAttrType = Case.getCurrentCase().getSleuthkitCase().getBlackboard().getAttributeType(attributeName.toUpperCase());
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
                    continue;
                } else if (columnName.trim().length() != columnName.length()) {
                    logger.log(Level.SEVERE, String.format("Column name '%s' starts or ends with whitespace for [%s]", columnName, getXmlAttrIdentifier(parentName, attributeName)));
                    continue;
                } else if (columnName.matches("[^ \\S]")) {
                    logger.log(Level.SEVERE, String.format("Column name '%s' contains invalid characters [%s]", columnName, getXmlAttrIdentifier(parentName, attributeName)));
                    continue;
                }

                TsvColumn thisCol = new TsvColumn(
                        foundAttrType,
                        columnName.trim().toLowerCase(),
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
     * @param artType      The artifact type.
     * @param dataSource   is the Content object that needs to have the artifact
     *                     added for it
     * @param bbattributes is the collection of blackboard attributes that need
     *                     to be added to the artifact after the artifact has
     *                     been created
     *
     * @return The newly-created artifact, or null on error
     */
    private BlackboardArtifact createArtifactWithAttributes(BlackboardArtifact.Type artType, Content dataSource, Collection<BlackboardAttribute> bbattributes) {
        try {
            switch (artType.getCategory()) {
                case DATA_ARTIFACT:
                    return dataSource.newDataArtifact(artType, bbattributes);
                case ANALYSIS_RESULT:
                    return dataSource.newAnalysisResult(artType, Score.SCORE_UNKNOWN, null, null, null, bbattributes).getAnalysisResult();
                default:
                    logger.log(Level.SEVERE, String.format("Unknown category type: %s", artType.getCategory().getDisplayName()));
                    return null;
            }
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
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifacts(artifacts, moduleName, context.getJobId());
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

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList("zip", "tar", "tgz"));

    /**
     * Find the files that will be processed by the iLeapp program
     *
     * @param dataSource
     *
     * @return List of abstract files to process.
     */
    static List<AbstractFile> findLeappFilesToProcess(Content dataSource) {

        List<AbstractFile> leappFiles = new ArrayList<>();

        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        // findFiles use the SQL wildcard % in the file name
        try {
            leappFiles = fileManager.findFiles(dataSource, "%", "/"); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "No files found to process"); //NON-NLS
            return leappFiles;
        }

        List<AbstractFile> leappFilesToProcess = new ArrayList<>();
        for (AbstractFile leappFile : leappFiles) {
            if (((leappFile.getLocalAbsPath() != null)
                    && !leappFile.isVirtual())
                    && leappFile.getNameExtension() != null
                    && ALLOWED_EXTENSIONS.contains(leappFile.getNameExtension().toLowerCase())) {
                leappFilesToProcess.add(leappFile);
            }
        }

        return leappFilesToProcess;
    }

    /**
     * Create custom artifacts that are defined in the xLeapp xml file(s).
     *
     */
    private void createCustomArtifacts(Blackboard blkBoard) {

        for (Map.Entry<String, String> customArtifact : CUSTOM_ARTIFACT_MAP.entrySet()) {
            String artifactName = customArtifact.getKey();
            String artifactDescription = customArtifact.getValue();

            try {
                BlackboardArtifact.Type customArtifactType = blkBoard.getOrAddArtifactType(artifactName, artifactDescription);
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.WARNING, String.format("Failed to create custom artifact type %s.", artifactName), ex);
            }

        }
    }

    private AbstractFile findAbstractFile(Content dataSource, String fileNamePath) {
        if (fileNamePath == null) {
            return null;
        }

        List<AbstractFile> files;

        String fileName = FilenameUtils.getName(fileNamePath);
        String filePath = FilenameUtils.normalize(FilenameUtils.getPath(fileNamePath), true);

        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        try {
            files = fileManager.findFiles(dataSource, fileName); //NON-NLS

        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to find prefetch files.", ex); //NON-NLS
            return null;  // No need to continue
        }

        for (AbstractFile pFile : files) {

            if (pFile.getParentPath().toLowerCase().endsWith(filePath.toLowerCase())) {
                return pFile;
            }
        }

        return null;

    }
}
