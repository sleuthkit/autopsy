/*
 * Central Repository
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.DEFAULT_OPTION;
import static javax.swing.JOptionPane.PLAIN_MESSAGE;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;

/**
 * View correlation results from other cases
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
@ServiceProvider(service = DataContentViewer.class, position = 9)
@Messages({"DataContentViewerOtherCases.title=Other Occurrences",
    "DataContentViewerOtherCases.toolTip=Displays instances of the selected file/artifact from other occurrences.",})
public class DataContentViewerOtherCases extends JPanel implements DataContentViewer {

    private static final long serialVersionUID = -1L;

    private static final Logger LOGGER = Logger.getLogger(DataContentViewerOtherCases.class.getName());

    private static final int DEFAULT_MIN_CELL_WIDTH = 15;
    private static final int CELL_TEXT_WIDTH_PADDING = 5;

    private final DataContentViewerOtherCasesTableModel tableModel;
    private final Collection<CorrelationAttributeInstance> correlationAttributes;
    /**
     * Could be null.
     */
    private AbstractFile file;

    /**
     * Creates new form DataContentViewerOtherCases
     */
    public DataContentViewerOtherCases() {
        this.tableModel = new DataContentViewerOtherCasesTableModel();
        this.correlationAttributes = new ArrayList<>();

        initComponents();
        customizeComponents();
        reset();
    }

    private void customizeComponents() {
        ActionListener actList = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem jmi = (JMenuItem) e.getSource();
                if (jmi.equals(selectAllMenuItem)) {
                    otherCasesTable.selectAll();
                } else if (jmi.equals(showCaseDetailsMenuItem)) {
                    showCaseDetails(otherCasesTable.getSelectedRow());
                } else if (jmi.equals(exportToCSVMenuItem)) {
                    try {
                        saveToCSV();
                    } catch (NoCurrentCaseException ex) {
                        LOGGER.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
                    }
                } else if (jmi.equals(showCommonalityMenuItem)) {
                    showCommonalityDetails();
                }
            }
        };

        exportToCSVMenuItem.addActionListener(actList);
        selectAllMenuItem.addActionListener(actList);
        showCaseDetailsMenuItem.addActionListener(actList);
        showCommonalityMenuItem.addActionListener(actList);

        // Set background of every nth row as light grey.
        TableCellRenderer renderer = new DataContentViewerOtherCasesTableCellRenderer();
        otherCasesTable.setDefaultRenderer(Object.class, renderer);

    }

    @Messages({"DataContentViewerOtherCases.correlatedArtifacts.isEmpty=There are no files or artifacts to correlate.",
        "# {0} - commonality percentage",
        "# {1} - correlation type",
        "# {2} - correlation value",
        "DataContentViewerOtherCases.correlatedArtifacts.byType={0}% of data sources have {2} (type: {1})\n",
        "DataContentViewerOtherCases.correlatedArtifacts.title=Attribute Frequency",
        "DataContentViewerOtherCases.correlatedArtifacts.failed=Failed to get frequency details."})
    /**
     * Show how common the selected correlationAttributes are with details
     * dialog.
     */
    private void showCommonalityDetails() {
        if (correlationAttributes.isEmpty()) {
            JOptionPane.showConfirmDialog(showCommonalityMenuItem,
                    Bundle.DataContentViewerOtherCases_correlatedArtifacts_isEmpty(),
                    Bundle.DataContentViewerOtherCases_correlatedArtifacts_title(),
                    DEFAULT_OPTION, PLAIN_MESSAGE);
        } else {
            StringBuilder msg = new StringBuilder(correlationAttributes.size());
            int percentage;
            try {
                EamDb dbManager = EamDb.getInstance();
                for (CorrelationAttributeInstance eamArtifact : correlationAttributes) {
                    try {
                        percentage = dbManager.getFrequencyPercentage(eamArtifact);
                        msg.append(Bundle.DataContentViewerOtherCases_correlatedArtifacts_byType(percentage,
                                eamArtifact.getCorrelationType().getDisplayName(),
                                eamArtifact.getCorrelationValue()));
                    } catch (CorrelationAttributeNormalizationException ex) {
                        LOGGER.log(Level.WARNING, String.format("Error getting commonality details for artifact with ID: %s.", eamArtifact.getID()), ex);
                    }
                }
                JOptionPane.showConfirmDialog(showCommonalityMenuItem,
                        msg.toString(),
                        Bundle.DataContentViewerOtherCases_correlatedArtifacts_title(),
                        DEFAULT_OPTION, PLAIN_MESSAGE);
            } catch (EamDbException ex) {
                LOGGER.log(Level.SEVERE, "Error getting commonality details.", ex);
                JOptionPane.showConfirmDialog(showCommonalityMenuItem,
                        Bundle.DataContentViewerOtherCases_correlatedArtifacts_failed(),
                        Bundle.DataContentViewerOtherCases_correlatedArtifacts_title(),
                        DEFAULT_OPTION, ERROR_MESSAGE);
            }
        }
    }

    @Messages({"DataContentViewerOtherCases.caseDetailsDialog.notSelected=No Row Selected",
        "DataContentViewerOtherCases.caseDetailsDialog.noDetails=No details for this case.",
        "DataContentViewerOtherCases.caseDetailsDialog.noDetailsReference=No case details for Global reference properties.",
        "DataContentViewerOtherCases.caseDetailsDialog.noCaseNameError=Error",
        "DataContentViewerOtherCases.noOpenCase.errMsg=No open case available."})
    private void showCaseDetails(int selectedRowViewIdx) {

        String caseDisplayName = Bundle.DataContentViewerOtherCases_caseDetailsDialog_noCaseNameError();
        try {
            if (-1 != selectedRowViewIdx) {
                EamDb dbManager = EamDb.getInstance();
                int selectedRowModelIdx = otherCasesTable.convertRowIndexToModel(selectedRowViewIdx);
                OtherOccurrenceNodeInstanceData nodeData = (OtherOccurrenceNodeInstanceData) tableModel.getRow(selectedRowModelIdx);
                CorrelationCase eamCasePartial = nodeData.getCorrelationAttributeInstance().getCorrelationCase();
                if (eamCasePartial == null) {
                    JOptionPane.showConfirmDialog(showCaseDetailsMenuItem,
                            Bundle.DataContentViewerOtherCases_caseDetailsDialog_noDetailsReference(),
                            caseDisplayName,
                            DEFAULT_OPTION, PLAIN_MESSAGE);
                    return;
                }
                caseDisplayName = eamCasePartial.getDisplayName();
                // query case details
                CorrelationCase eamCase = dbManager.getCaseByUUID(eamCasePartial.getCaseUUID());
                if (eamCase == null) {
                    JOptionPane.showConfirmDialog(showCaseDetailsMenuItem,
                            Bundle.DataContentViewerOtherCases_caseDetailsDialog_noDetails(),
                            caseDisplayName,
                            DEFAULT_OPTION, PLAIN_MESSAGE);
                    return;
                }

                // display case details
                JOptionPane.showConfirmDialog(showCaseDetailsMenuItem,
                        eamCase.getCaseDetailsOptionsPaneDialog(),
                        caseDisplayName,
                        DEFAULT_OPTION, PLAIN_MESSAGE);
            } else {
                JOptionPane.showConfirmDialog(showCaseDetailsMenuItem,
                        Bundle.DataContentViewerOtherCases_caseDetailsDialog_notSelected(),
                        caseDisplayName,
                        DEFAULT_OPTION, PLAIN_MESSAGE);
            }
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error loading case details", ex);
            JOptionPane.showConfirmDialog(showCaseDetailsMenuItem,
                    Bundle.DataContentViewerOtherCases_caseDetailsDialog_noDetails(),
                    caseDisplayName,
                    DEFAULT_OPTION, PLAIN_MESSAGE);
        }
    }

    private void saveToCSV() throws NoCurrentCaseException {
        if (0 != otherCasesTable.getSelectedRowCount()) {
            Calendar now = Calendar.getInstance();
            String fileName = String.format("%1$tY%1$tm%1$te%1$tI%1$tM%1$tS_other_data_sources.csv", now);
            CSVFileChooser.setCurrentDirectory(new File(Case.getCurrentCaseThrows().getExportDirectory()));
            CSVFileChooser.setSelectedFile(new File(fileName));
            CSVFileChooser.setFileFilter(new FileNameExtensionFilter("csv file", "csv"));

            int returnVal = CSVFileChooser.showSaveDialog(otherCasesTable);
            if (returnVal == JFileChooser.APPROVE_OPTION) {

                File selectedFile = CSVFileChooser.getSelectedFile();
                if (!selectedFile.getName().endsWith(".csv")) { // NON-NLS
                    selectedFile = new File(selectedFile.toString() + ".csv"); // NON-NLS
                }

                writeSelectedRowsToFileAsCSV(selectedFile);
            }
        }
    }

    private void writeSelectedRowsToFileAsCSV(File destFile) {
        StringBuilder content;
        int[] selectedRowViewIndices = otherCasesTable.getSelectedRows();
        int colCount = tableModel.getColumnCount();

        try (BufferedWriter writer = Files.newBufferedWriter(destFile.toPath())) {

            // write column names
            content = new StringBuilder("");
            for (int colIdx = 0; colIdx < colCount; colIdx++) {
                content.append('"').append(tableModel.getColumnName(colIdx)).append('"');
                if (colIdx < (colCount - 1)) {
                    content.append(",");
                }
            }

            content.append(System.getProperty("line.separator"));
            writer.write(content.toString());

            // write rows
            for (int rowViewIdx : selectedRowViewIndices) {
                content = new StringBuilder("");
                for (int colIdx = 0; colIdx < colCount; colIdx++) {
                    int rowModelIdx = otherCasesTable.convertRowIndexToModel(rowViewIdx);
                    content.append('"').append(tableModel.getValueAt(rowModelIdx, colIdx)).append('"');
                    if (colIdx < (colCount - 1)) {
                        content.append(",");
                    }
                }
                content.append(System.getProperty("line.separator"));
                writer.write(content.toString());
            }

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error writing selected rows to CSV.", ex);
        }
    }

    /**
     * Reset the UI and clear cached data.
     */
    private void reset() {
        // start with empty table
        tableModel.clearTable();
        correlationAttributes.clear();
        earliestCaseDate.setText(Bundle.DataContentViewerOtherCases_earliestCaseNotAvailable());
    }

    @Override
    public String getTitle() {
        return Bundle.DataContentViewerOtherCases_title();
    }

    @Override
    public String getToolTip() {
        return Bundle.DataContentViewerOtherCases_toolTip();
    }

    @Override
    public DataContentViewer createInstance() {
        return new DataContentViewerOtherCases();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        reset();
    }

    @Override
    public int isPreferred(Node node) {
        return 1;
    }

    /**
     * Get the associated BlackboardArtifact from a node, if it exists.
     *
     * @param node The node
     *
     * @return The associated BlackboardArtifact, or null
     */
    private BlackboardArtifact getBlackboardArtifactFromNode(Node node) {
        BlackboardArtifactTag nodeBbArtifactTag = node.getLookup().lookup(BlackboardArtifactTag.class);
        BlackboardArtifact nodeBbArtifact = node.getLookup().lookup(BlackboardArtifact.class);

        if (nodeBbArtifactTag != null) {
            return nodeBbArtifactTag.getArtifact();
        } else if (nodeBbArtifact != null) {
            return nodeBbArtifact;
        }

        return null;
    }

    /**
     * Get the associated AbstractFile from a node, if it exists.
     *
     * @param node The node
     *
     * @return The associated AbstractFile, or null
     */
    private AbstractFile getAbstractFileFromNode(Node node) {
        BlackboardArtifactTag nodeBbArtifactTag = node.getLookup().lookup(BlackboardArtifactTag.class);
        ContentTag nodeContentTag = node.getLookup().lookup(ContentTag.class);
        BlackboardArtifact nodeBbArtifact = node.getLookup().lookup(BlackboardArtifact.class);
        AbstractFile nodeAbstractFile = node.getLookup().lookup(AbstractFile.class);

        if (nodeBbArtifactTag != null) {
            Content content = nodeBbArtifactTag.getContent();
            if (content instanceof AbstractFile) {
                return (AbstractFile) content;
            }
        } else if (nodeContentTag != null) {
            Content content = nodeContentTag.getContent();
            if (content instanceof AbstractFile) {
                return (AbstractFile) content;
            }
        } else if (nodeBbArtifact != null) {
            Content content;
            try {
                content = nodeBbArtifact.getSleuthkitCase().getContentById(nodeBbArtifact.getObjectID());
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error retrieving blackboard artifact", ex); // NON-NLS
                return null;
            }

            if (content instanceof AbstractFile) {
                return (AbstractFile) content;
            }
        } else if (nodeAbstractFile != null) {
            return nodeAbstractFile;
        }

        return null;
    }

    /**
     * Determine what attributes can be used for correlation based on the node.
     * If EamDB is not enabled, get the default Files correlation.
     *
     * @param node The node to correlate
     *
     * @return A list of attributes that can be used for correlation
     */
    private Collection<CorrelationAttributeInstance> getCorrelationAttributesFromNode(Node node) {
        Collection<CorrelationAttributeInstance> ret = new ArrayList<>();

        // correlate on blackboard artifact attributes if they exist and supported
        BlackboardArtifact bbArtifact = getBlackboardArtifactFromNode(node);
        if (bbArtifact != null && EamDb.isEnabled()) {
            ret.addAll(EamArtifactUtil.makeInstancesFromBlackboardArtifact(bbArtifact, false));
        }

        // we can correlate based on the MD5 if it is enabled      
        if (this.file != null && EamDb.isEnabled() && this.file.getSize() > 0) {
            try {

                List<CorrelationAttributeInstance.Type> artifactTypes = EamDb.getInstance().getDefinedCorrelationTypes();
                String md5 = this.file.getMd5Hash();
                if (md5 != null && !md5.isEmpty() && null != artifactTypes && !artifactTypes.isEmpty()) {
                    for (CorrelationAttributeInstance.Type aType : artifactTypes) {
                        if (aType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                            CorrelationCase corCase = EamDb.getInstance().getCase(Case.getCurrentCase());
                            try {
                                ret.add(new CorrelationAttributeInstance(
                                        aType,
                                        md5,
                                        corCase,
                                        CorrelationDataSource.fromTSKDataSource(corCase, file.getDataSource()),
                                        file.getParentPath() + file.getName(),
                                        "",
                                        file.getKnown(), 
                                        file.getId()));
                            } catch (CorrelationAttributeNormalizationException ex) {
                                LOGGER.log(Level.INFO, String.format("Unable to check create CorrelationAttribtueInstance for value %s and type %s.", md5, aType.toString()), ex);
                            }
                            break;
                        }
                    }
                }
            } catch (EamDbException | TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error connecting to DB", ex); // NON-NLS
            }
            // If EamDb not enabled, get the Files default correlation type to allow Other Occurances to be enabled.  
        } else if (this.file != null && this.file.getSize() > 0) {
            String md5 = this.file.getMd5Hash();
            if (md5 != null && !md5.isEmpty()) {
                try {
                    final CorrelationAttributeInstance.Type fileAttributeType
                            = CorrelationAttributeInstance.getDefaultCorrelationTypes()
                                    .stream()
                                    .filter(attrType -> attrType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID)
                                    .findAny()
                                    .get();
                    //The Central Repository is not enabled
                    ret.add(new CorrelationAttributeInstance(fileAttributeType, md5, null, null, "", "", TskData.FileKnown.UNKNOWN, this.file.getId()));
                } catch (EamDbException ex) {
                    LOGGER.log(Level.SEVERE, "Error connecting to DB", ex); // NON-NLS
                } catch (CorrelationAttributeNormalizationException ex) {
                    LOGGER.log(Level.INFO, String.format("Unable to create CorrelationAttributeInstance for value %s", md5), ex); // NON-NLS
                }
            }
        }

        return ret;
    }

    @Messages({"DataContentViewerOtherCases.earliestCaseNotAvailable= Not Enabled."})
    /**
     * Gets the list of Eam Cases and determines the earliest case creation
     * date. Sets the label to display the earliest date string to the user.
     */
    private void setEarliestCaseDate() {
        String dateStringDisplay = Bundle.DataContentViewerOtherCases_earliestCaseNotAvailable();

        if (EamDb.isEnabled()) {
            LocalDateTime earliestDate = LocalDateTime.now(DateTimeZone.UTC);
            DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
            try {
                EamDb dbManager = EamDb.getInstance();
                List<CorrelationCase> cases = dbManager.getCases();
                for (CorrelationCase aCase : cases) {
                    LocalDateTime caseDate = LocalDateTime.fromDateFields(datetimeFormat.parse(aCase.getCreationDate()));

                    if (caseDate.isBefore(earliestDate)) {
                        earliestDate = caseDate;
                        dateStringDisplay = aCase.getCreationDate();
                    }

                }

            } catch (EamDbException ex) {
                LOGGER.log(Level.SEVERE, "Error getting list of cases from database.", ex); // NON-NLS
            } catch (ParseException ex) {
                LOGGER.log(Level.SEVERE, "Error parsing date of cases from database.", ex); // NON-NLS
            }

        }
        earliestCaseDate.setText(dateStringDisplay);
    }

    /**
     * Query the central repo database (if enabled) and the case database to
     * find all artifact instances correlated to the given central repository
     * artifact. If the central repo is not enabled, this will only return files
     * from the current case with matching MD5 hashes.
     *
     * @param corAttr        CorrelationAttribute to query for
     * @param dataSourceName Data source to filter results
     * @param deviceId       Device Id to filter results
     *
     * @return A collection of correlated artifact instances
     */
    private Map<UniquePathKey, OtherOccurrenceNodeInstanceData> getCorrelatedInstances(CorrelationAttributeInstance corAttr, String dataSourceName, String deviceId) {
        // @@@ Check exception
        try {
            final Case openCase = Case.getCurrentCase();
            String caseUUID = openCase.getName();

            HashMap<UniquePathKey, OtherOccurrenceNodeInstanceData> nodeDataMap = new HashMap<>();

            if (EamDb.isEnabled()) {
                List<CorrelationAttributeInstance> instances = EamDb.getInstance().getArtifactInstancesByTypeValue(corAttr.getCorrelationType(), corAttr.getCorrelationValue());

                for (CorrelationAttributeInstance artifactInstance : instances) {

                    // Only add the attribute if it isn't the object the user selected.
                    // We consider it to be a different object if at least one of the following is true:
                    // - the case UUID is different
                    // - the data source name is different
                    // - the data source device ID is different
                    // - the file path is different
                    if (!artifactInstance.getCorrelationCase().getCaseUUID().equals(caseUUID)
                            || !artifactInstance.getCorrelationDataSource().getName().equals(dataSourceName)
                            || !artifactInstance.getCorrelationDataSource().getDeviceID().equals(deviceId)
                            || !artifactInstance.getFilePath().equalsIgnoreCase(file.getParentPath() + file.getName())) {

                        OtherOccurrenceNodeInstanceData newNode = new OtherOccurrenceNodeInstanceData(artifactInstance, corAttr.getCorrelationType(), corAttr.getCorrelationValue());
                        UniquePathKey uniquePathKey = new UniquePathKey(newNode);
                        nodeDataMap.put(uniquePathKey, newNode);
                    }
                }
            }

            if (corAttr.getCorrelationType().getDisplayName().equals("Files")) {
                List<AbstractFile> caseDbFiles = getCaseDbMatches(corAttr, openCase);

                for (AbstractFile caseDbFile : caseDbFiles) {
                    addOrUpdateNodeData(openCase, nodeDataMap, caseDbFile);
                }
            }

            return nodeDataMap;
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting artifact instances from database.", ex); // NON-NLS
        } catch (CorrelationAttributeNormalizationException ex) {
            LOGGER.log(Level.INFO, "Error getting artifact instances from database.", ex); // NON-NLS
        } catch (NoCurrentCaseException ex) {
            LOGGER.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        } catch (TskCoreException ex) {
            // do nothing. 
            // @@@ Review this behavior
            LOGGER.log(Level.SEVERE, "Exception while querying open case.", ex); // NON-NLS
        }

        return new HashMap<>(0);
    }

    /**
     * Get all other abstract files in the current case with the same MD5 as the
     * selected node.
     *
     * @param corAttr  The CorrelationAttribute containing the MD5 to search for
     * @param openCase The current case
     *
     * @return List of matching AbstractFile objects
     *
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     * @throws EamDbException
     */
    private List<AbstractFile> getCaseDbMatches(CorrelationAttributeInstance corAttr, Case openCase) throws NoCurrentCaseException, TskCoreException, EamDbException {
        String md5 = corAttr.getCorrelationValue();
        SleuthkitCase tsk = openCase.getSleuthkitCase();
        List<AbstractFile> matches = tsk.findAllFilesWhere(String.format("md5 = '%s'", new Object[]{md5}));

        List<AbstractFile> caseDbArtifactInstances = new ArrayList<>();
        for (AbstractFile fileMatch : matches) {
            if (this.file.equals(fileMatch)) {
                continue; // If this is the file the user clicked on
            }
            caseDbArtifactInstances.add(fileMatch);
        }
        return caseDbArtifactInstances;

    }

    /**
     * Adds the file to the nodeDataMap map if it does not already exist
     *
     * @param autopsyCase
     * @param nodeDataMap
     * @param newFile
     *
     * @throws TskCoreException
     * @throws EamDbException
     */
    private void addOrUpdateNodeData(final Case autopsyCase, Map<UniquePathKey, OtherOccurrenceNodeInstanceData> nodeDataMap, AbstractFile newFile) throws TskCoreException, EamDbException {

        OtherOccurrenceNodeInstanceData newNode = new OtherOccurrenceNodeInstanceData(newFile, autopsyCase);

        // If the caseDB object has a notable tag associated with it, update
        // the known status to BAD
        if (newNode.getKnown() != TskData.FileKnown.BAD) {
            List<ContentTag> fileMatchTags = autopsyCase.getServices().getTagsManager().getContentTagsByContent(newFile);
            for (ContentTag tag : fileMatchTags) {
                TskData.FileKnown tagKnownStatus = tag.getName().getKnownStatus();
                if (tagKnownStatus.equals(TskData.FileKnown.BAD)) {
                    newNode.updateKnown(TskData.FileKnown.BAD);
                    break;
                }
            }
        }

        // Make a key to see if the file is already in the map
        UniquePathKey uniquePathKey = new UniquePathKey(newNode);

        // If this node is already in the list, the only thing we need to do is
        // update the known status to BAD if the caseDB version had known status BAD.
        // Otherwise this is a new node so add the new node to the map.
        if (nodeDataMap.containsKey(uniquePathKey)) {
            if (newNode.getKnown() == TskData.FileKnown.BAD) {
                OtherOccurrenceNodeInstanceData prevInstance = nodeDataMap.get(uniquePathKey);
                prevInstance.updateKnown(newNode.getKnown());
            }
        } else {
            nodeDataMap.put(uniquePathKey, newNode);
        }
    }

    @Override
    public boolean isSupported(Node node) {

        // Is supported if one of the following is true:
        // - The central repo is enabled and the node has correlatable content
        //   (either through the MD5 hash of the associated file or through a BlackboardArtifact)
        // - The central repo is disabled and the backing file has a valid MD5 hash
        this.file = this.getAbstractFileFromNode(node);
        if (EamDb.isEnabled()) {
            return !getCorrelationAttributesFromNode(node).isEmpty();
        } else {
           return this.file != null
                    && this.file.getSize() > 0
                    && ((this.file.getMd5Hash() != null) && (!this.file.getMd5Hash().isEmpty()));
        }
    }

    @Override
    @Messages({"DataContentViewerOtherCases.table.nodbconnection=Cannot connect to central repository database."})
    public void setNode(Node node) {

        reset(); // reset the table to empty.
        if (node == null) {
            return;
        }
        //could be null
        this.file = this.getAbstractFileFromNode(node);
        populateTable(node);
    }

    /**
     * Load the correlatable data into the table model. If there is no data
     * available display the message on the status panel.
     *
     * @param node The node being viewed.
     */
    @Messages({
        "DataContentViewerOtherCases.table.noArtifacts=Item has no attributes with which to search.",
        "DataContentViewerOtherCases.table.noResultsFound=No results found."
    })
    private void populateTable(Node node) {
        String dataSourceName = "";
        String deviceId = "";
        try {
            if (this.file != null) {
                Content dataSource = this.file.getDataSource();
                dataSourceName = dataSource.getName();
                deviceId = Case.getCurrentCaseThrows().getSleuthkitCase().getDataSource(dataSource.getId()).getDeviceId();
            }
        } catch (TskException | NoCurrentCaseException ex) {
            // do nothing. 
            // @@@ Review this behavior
        }

        // get the attributes we can correlate on
        correlationAttributes.addAll(getCorrelationAttributesFromNode(node));
        for (CorrelationAttributeInstance corAttr : correlationAttributes) {
            Map<UniquePathKey, OtherOccurrenceNodeInstanceData> correlatedNodeDataMap = new HashMap<>(0);

            // get correlation and reference set instances from DB
            correlatedNodeDataMap.putAll(getCorrelatedInstances(corAttr, dataSourceName, deviceId));

            correlatedNodeDataMap.values().forEach((nodeData) -> {
                tableModel.addNodeData(nodeData);
            });
        }

        if (correlationAttributes.isEmpty()) {
            tableModel.addNodeData(new OtherOccurrenceNodeMessageData(Bundle.DataContentViewerOtherCases_table_noArtifacts()));
            setColumnWidthToText(0, Bundle.DataContentViewerOtherCases_table_noArtifacts());
        } else if (0 == tableModel.getRowCount()) {
            tableModel.addNodeData(new OtherOccurrenceNodeMessageData(Bundle.DataContentViewerOtherCases_table_noResultsFound()));
            setColumnWidthToText(0, Bundle.DataContentViewerOtherCases_table_noResultsFound());
        } else {
            setColumnWidths();
        }
        setEarliestCaseDate();
    }

    /**
     * Adjust a given column for the text provided.
     *
     * @param columnIndex The index of the column to adjust.
     * @param text        The text whose length will be used to adjust the
     *                    column width.
     */
    private void setColumnWidthToText(int columnIndex, String text) {
        TableColumn column = otherCasesTable.getColumnModel().getColumn(columnIndex);
        FontMetrics fontMetrics = otherCasesTable.getFontMetrics(otherCasesTable.getFont());
        int stringWidth = fontMetrics.stringWidth(text);
        column.setMinWidth(stringWidth + CELL_TEXT_WIDTH_PADDING);
    }

    /**
     * Adjust column widths to their preferred values.
     */
    private void setColumnWidths() {
        for (int idx = 0; idx < tableModel.getColumnCount(); idx++) {
            TableColumn column = otherCasesTable.getColumnModel().getColumn(idx);
            column.setMinWidth(DEFAULT_MIN_CELL_WIDTH);
            int columnWidth = tableModel.getColumnPreferredWidth(idx);
            if (columnWidth > 0) {
                column.setPreferredWidth(columnWidth);
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        rightClickPopupMenu = new javax.swing.JPopupMenu();
        selectAllMenuItem = new javax.swing.JMenuItem();
        exportToCSVMenuItem = new javax.swing.JMenuItem();
        showCaseDetailsMenuItem = new javax.swing.JMenuItem();
        showCommonalityMenuItem = new javax.swing.JMenuItem();
        CSVFileChooser = new javax.swing.JFileChooser();
        otherCasesPanel = new javax.swing.JPanel();
        tableContainerPanel = new javax.swing.JPanel();
        tableScrollPane = new javax.swing.JScrollPane();
        otherCasesTable = new javax.swing.JTable();
        earliestCaseLabel = new javax.swing.JLabel();
        earliestCaseDate = new javax.swing.JLabel();
        tableStatusPanel = new javax.swing.JPanel();

        rightClickPopupMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {
            }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {
            }
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {
                rightClickPopupMenuPopupMenuWillBecomeVisible(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(selectAllMenuItem, org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.selectAllMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(selectAllMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(exportToCSVMenuItem, org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.exportToCSVMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(exportToCSVMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(showCaseDetailsMenuItem, org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.showCaseDetailsMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(showCaseDetailsMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(showCommonalityMenuItem, org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.showCommonalityMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(showCommonalityMenuItem);

        setMinimumSize(new java.awt.Dimension(1500, 10));
        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(1500, 44));

        otherCasesPanel.setPreferredSize(new java.awt.Dimension(1500, 144));

        tableContainerPanel.setPreferredSize(new java.awt.Dimension(1500, 63));

        tableScrollPane.setPreferredSize(new java.awt.Dimension(1500, 30));

        otherCasesTable.setAutoCreateRowSorter(true);
        otherCasesTable.setModel(tableModel);
        otherCasesTable.setToolTipText(org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.table.toolTip.text")); // NOI18N
        otherCasesTable.setComponentPopupMenu(rightClickPopupMenu);
        otherCasesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        tableScrollPane.setViewportView(otherCasesTable);

        org.openide.awt.Mnemonics.setLocalizedText(earliestCaseLabel, org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.earliestCaseLabel.text")); // NOI18N
        earliestCaseLabel.setToolTipText(org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.earliestCaseLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(earliestCaseDate, org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.earliestCaseDate.text")); // NOI18N

        tableStatusPanel.setPreferredSize(new java.awt.Dimension(1500, 16));

        javax.swing.GroupLayout tableStatusPanelLayout = new javax.swing.GroupLayout(tableStatusPanel);
        tableStatusPanel.setLayout(tableStatusPanelLayout);
        tableStatusPanelLayout.setHorizontalGroup(
            tableStatusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        tableStatusPanelLayout.setVerticalGroup(
            tableStatusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 16, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout tableContainerPanelLayout = new javax.swing.GroupLayout(tableContainerPanel);
        tableContainerPanel.setLayout(tableContainerPanelLayout);
        tableContainerPanelLayout.setHorizontalGroup(
            tableContainerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tableContainerPanelLayout.createSequentialGroup()
                .addComponent(tableStatusPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 1282, Short.MAX_VALUE)
                .addGap(218, 218, 218))
            .addComponent(tableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(tableContainerPanelLayout.createSequentialGroup()
                .addComponent(earliestCaseLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(earliestCaseDate)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        tableContainerPanelLayout.setVerticalGroup(
            tableContainerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tableContainerPanelLayout.createSequentialGroup()
                .addComponent(tableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 27, Short.MAX_VALUE)
                .addGap(2, 2, 2)
                .addGroup(tableContainerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(earliestCaseLabel)
                    .addComponent(earliestCaseDate))
                .addGap(0, 0, 0)
                .addComponent(tableStatusPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout otherCasesPanelLayout = new javax.swing.GroupLayout(otherCasesPanel);
        otherCasesPanel.setLayout(otherCasesPanelLayout);
        otherCasesPanelLayout.setHorizontalGroup(
            otherCasesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1500, Short.MAX_VALUE)
            .addGroup(otherCasesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(tableContainerPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        otherCasesPanelLayout.setVerticalGroup(
            otherCasesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 483, Short.MAX_VALUE)
            .addGroup(otherCasesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(otherCasesPanelLayout.createSequentialGroup()
                    .addComponent(tableContainerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 59, Short.MAX_VALUE)
                    .addGap(0, 0, 0)))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(otherCasesPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(otherCasesPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 59, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void rightClickPopupMenuPopupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {//GEN-FIRST:event_rightClickPopupMenuPopupMenuWillBecomeVisible
        boolean enableCentralRepoActions = false;

        if (EamDbUtil.useCentralRepo() && otherCasesTable.getSelectedRowCount() == 1) {
            int rowIndex = otherCasesTable.getSelectedRow();
            OtherOccurrenceNodeData selectedNode = (OtherOccurrenceNodeData) tableModel.getRow(rowIndex);
            if (selectedNode instanceof OtherOccurrenceNodeInstanceData) {
                OtherOccurrenceNodeInstanceData instanceData = (OtherOccurrenceNodeInstanceData) selectedNode;
                enableCentralRepoActions = instanceData.isCentralRepoNode();
            }
        }
        showCaseDetailsMenuItem.setVisible(enableCentralRepoActions);
        showCommonalityMenuItem.setVisible(enableCentralRepoActions);
    }//GEN-LAST:event_rightClickPopupMenuPopupMenuWillBecomeVisible

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JFileChooser CSVFileChooser;
    private javax.swing.JLabel earliestCaseDate;
    private javax.swing.JLabel earliestCaseLabel;
    private javax.swing.JMenuItem exportToCSVMenuItem;
    private javax.swing.JPanel otherCasesPanel;
    private javax.swing.JTable otherCasesTable;
    private javax.swing.JPopupMenu rightClickPopupMenu;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JMenuItem showCaseDetailsMenuItem;
    private javax.swing.JMenuItem showCommonalityMenuItem;
    private javax.swing.JPanel tableContainerPanel;
    private javax.swing.JScrollPane tableScrollPane;
    private javax.swing.JPanel tableStatusPanel;
    // End of variables declaration//GEN-END:variables

    /**
     * Used as a key to ensure we eliminate duplicates from the result set by
     * not overwriting CR correlation instances.
     */
    private static final class UniquePathKey {

        private final String dataSourceID;
        private final String filePath;
        private final String type;

        UniquePathKey(OtherOccurrenceNodeInstanceData nodeData) {
            super();
            dataSourceID = nodeData.getDeviceID();
            if (nodeData.getFilePath() != null) {
                filePath = nodeData.getFilePath().toLowerCase();
            } else {
                filePath = null;
            }
            type = nodeData.getType();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof UniquePathKey) {
                UniquePathKey otherKey = (UniquePathKey) (other);
                return (Objects.equals(otherKey.getDataSourceID(), this.getDataSourceID())
                        && Objects.equals(otherKey.getFilePath(), this.getFilePath())
                        && Objects.equals(otherKey.getType(), this.getType()));
            }
            return false;
        }

        @Override
        public int hashCode() {
            //int hash = 7;
            //hash = 67 * hash + this.dataSourceID.hashCode();
            //hash = 67 * hash + this.filePath.hashCode();
            return Objects.hash(getDataSourceID(), getFilePath(), getType());
        }

        /**
         * Get the type of this UniquePathKey.
         *
         * @return the type
         */
        String getType() {
            return type;
        }

        /**
         * Get the file path for the UniquePathKey.
         *
         * @return the filePath
         */
        String getFilePath() {
            return filePath;
        }

        /**
         * Get the data source id for the UniquePathKey.
         *
         * @return the dataSourceID
         */
        String getDataSourceID() {
            return dataSourceID;
        }
    }
}
