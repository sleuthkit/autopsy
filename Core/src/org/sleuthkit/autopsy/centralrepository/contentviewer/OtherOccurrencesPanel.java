/*
 * Central Repository
 *
 * Copyright 2017-2021 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.DEFAULT_OPTION;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.PLAIN_MESSAGE;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Panel for displaying other occurrences results.
 */
@NbBundle.Messages({
    "OtherOccurrencesPanel.table.noArtifacts=Item has no attributes with which to search.",
    "OtherOccurrencesPanel.table.noResultsFound=No results found."})
public final class OtherOccurrencesPanel extends javax.swing.JPanel {

    private static final CorrelationCaseWrapper NO_ARTIFACTS_CASE = new CorrelationCaseWrapper(Bundle.OtherOccurrencesPanel_table_noArtifacts());
    private static final CorrelationCaseWrapper NO_RESULTS_CASE = new CorrelationCaseWrapper(Bundle.OtherOccurrencesPanel_table_noResultsFound());
    private static final String UUID_PLACEHOLDER_STRING = "NoCorrelationAttributeInstance";
    private static final Logger logger = Logger.getLogger(OtherOccurrencesPanel.class.getName());
    private static final long serialVersionUID = 1L;
    private final OtherOccurrencesFilesTableModel filesTableModel;
    private final OtherOccurrencesCasesTableModel casesTableModel;
    private final OtherOccurrencesDataSourcesTableModel dataSourcesTableModel;
    private OccurrencePanel occurrencePanel;
    private final Collection<CorrelationAttributeInstance> correlationAttributes;
    private String dataSourceName = "";  //the data source of the file which the content viewer is being populated for
    private String deviceId = ""; //the device id of the data source for the file which the content viewer is being populated for
    private AbstractFile file = null;

    /**
     * Creates new form OtherOccurrencesPanel
     */
    public OtherOccurrencesPanel() {
        this.filesTableModel = new OtherOccurrencesFilesTableModel();
        this.casesTableModel = new OtherOccurrencesCasesTableModel();
        this.dataSourcesTableModel = new OtherOccurrencesDataSourcesTableModel();
        this.correlationAttributes = new ArrayList<>();
        occurrencePanel = new OccurrencePanel();
        initComponents();
        customizeComponents();
    }

    /**
     * Get a placeholder string to use in place of case uuid when it isn't
     * available
     *
     * @return UUID_PLACEHOLDER_STRING
     */
    static String getPlaceholderUUID() {
        return UUID_PLACEHOLDER_STRING;
    }

    private void customizeComponents() {
        ActionListener actList = (ActionEvent e) -> {
            JMenuItem jmi = (JMenuItem) e.getSource();
            if (jmi.equals(showCaseDetailsMenuItem)) {
                showCaseDetails(filesTable.getSelectedRow());
            } else if (jmi.equals(exportToCSVMenuItem)) {
                try {
                    saveToCSV();
                } catch (NoCurrentCaseException ex) {
                    logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
                }
            } else if (jmi.equals(showCommonalityMenuItem)) {
                showCommonalityDetails();
            }
        };

        exportToCSVMenuItem.addActionListener(actList);
        showCaseDetailsMenuItem.addActionListener(actList);
        showCommonalityMenuItem.addActionListener(actList);
        filesTable.setComponentPopupMenu(rightClickPopupMenu);
        // Configure column sorting.
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(filesTable.getModel());
        filesTable.setRowSorter(sorter);

        //sort tables alphabetically initially
        casesTable.getRowSorter().toggleSortOrder(0);
        dataSourcesTable.getRowSorter().toggleSortOrder(0);
        filesTable.getRowSorter().toggleSortOrder(0);
        reset();
        casesTable.getSelectionModel().addListSelectionListener((e) -> {
            if (Case.isCaseOpen()) {
                updateOnCaseSelection();
            }
        });
        dataSourcesTable.getSelectionModel().addListSelectionListener((e) -> {
            if (Case.isCaseOpen()) {
                updateOnDataSourceSelection();
            }
        });

        //alows resizing of the 4th section
        filesTable.getSelectionModel().addListSelectionListener((e) -> {
            if (Case.isCaseOpen()) {
                occurrencePanel = new OccurrencePanel();
                updateOnFileSelection();
            }
        });
        detailsPanelScrollPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                //when its resized make sure the width of the panel resizes to match the scroll pane width to avoid a horizontal scroll bar
                occurrencePanel.setPreferredSize(new java.awt.Dimension(detailsPanelScrollPane.getPreferredSize().width, occurrencePanel.getPreferredSize().height));
                detailsPanelScrollPane.setViewportView(occurrencePanel);
            }
        });

    }

    @NbBundle.Messages({"OtherOccurrencesPanel.correlatedArtifacts.isEmpty=There are no files or artifacts to correlate.",
        "# {0} - commonality percentage",
        "# {1} - correlation type",
        "# {2} - correlation value",
        "OtherOccurrencesPanel.correlatedArtifacts.byType={0}% of data sources have {2} (type: {1})\n",
        "OtherOccurrencesPanel.correlatedArtifacts.title=Attribute Frequency",
        "OtherOccurrencesPanel.correlatedArtifacts.failed=Failed to get frequency details."})
    /**
     * Show how common the selected correlationAttributes are with details
     * dialog.
     */
    private void showCommonalityDetails() {
        if (correlationAttributes.isEmpty()) {
            JOptionPane.showConfirmDialog(showCommonalityMenuItem,
                    Bundle.OtherOccurrencesPanel_correlatedArtifacts_isEmpty(),
                    Bundle.OtherOccurrencesPanel_correlatedArtifacts_title(),
                    DEFAULT_OPTION, PLAIN_MESSAGE);
        } else {
            StringBuilder msg = new StringBuilder(correlationAttributes.size());
            int percentage;
            try {
                CentralRepository dbManager = CentralRepository.getInstance();
                for (CorrelationAttributeInstance eamArtifact : correlationAttributes) {
                    try {
                        percentage = dbManager.getFrequencyPercentage(eamArtifact);
                        msg.append(Bundle.OtherOccurrencesPanel_correlatedArtifacts_byType(percentage,
                                eamArtifact.getCorrelationType().getDisplayName(),
                                eamArtifact.getCorrelationValue()));
                    } catch (CorrelationAttributeNormalizationException ex) {
                        logger.log(Level.WARNING, String.format("Error getting commonality details for artifact with ID: %s.", eamArtifact.getID()), ex);
                    }
                }
                JOptionPane.showConfirmDialog(showCommonalityMenuItem,
                        msg.toString(),
                        Bundle.OtherOccurrencesPanel_correlatedArtifacts_title(),
                        DEFAULT_OPTION, PLAIN_MESSAGE);
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Error getting commonality details.", ex);
                JOptionPane.showConfirmDialog(showCommonalityMenuItem,
                        Bundle.OtherOccurrencesPanel_correlatedArtifacts_failed(),
                        Bundle.OtherOccurrencesPanel_correlatedArtifacts_title(),
                        DEFAULT_OPTION, ERROR_MESSAGE);
            }
        }
    }

    @NbBundle.Messages({"OtherOccurrencesPanel.caseDetailsDialog.notSelected=No Row Selected",
        "OtherOccurrencesPanel.caseDetailsDialog.noDetails=No details for this case.",
        "OtherOccurrencesPanel.caseDetailsDialog.noDetailsReference=No case details for Global reference properties.",
        "OtherOccurrencesPanel.caseDetailsDialog.noCaseNameError=Error",
        "OtherOccurrencesPanel.noOpenCase.errMsg=No open case available."})
    private void showCaseDetails(int selectedRowViewIdx) {
        String caseDisplayName = Bundle.OtherOccurrencesPanel_caseDetailsDialog_noCaseNameError();
        String details = Bundle.OtherOccurrencesPanel_caseDetailsDialog_noDetails();
        try {
            if (-1 != selectedRowViewIdx) {
                CentralRepository dbManager = CentralRepository.getInstance();
                int selectedRowModelIdx = filesTable.convertRowIndexToModel(selectedRowViewIdx);
                List<OtherOccurrenceNodeData> rowList = filesTableModel.getListOfNodesForFile(selectedRowModelIdx);
                if (!rowList.isEmpty()) {
                    if (rowList.get(0) instanceof OtherOccurrenceNodeInstanceData) {
                        CorrelationCase eamCasePartial = ((OtherOccurrenceNodeInstanceData) rowList.get(0)).getCorrelationAttributeInstance().getCorrelationCase();
                        caseDisplayName = eamCasePartial.getDisplayName();
                        // query case details
                        CorrelationCase eamCase = dbManager.getCaseByUUID(eamCasePartial.getCaseUUID());
                        if (eamCase != null) {
                            details = eamCase.getCaseDetailsOptionsPaneDialog();
                        } else {
                            details = Bundle.OtherOccurrencesPanel_caseDetailsDialog_noDetails();
                        }
                    } else {
                        details = Bundle.OtherOccurrencesPanel_caseDetailsDialog_notSelected();
                    }
                } else {
                    details = Bundle.OtherOccurrencesPanel_caseDetailsDialog_noDetailsReference();
                }
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error loading case details", ex);
        } finally {
            JOptionPane.showConfirmDialog(showCaseDetailsMenuItem,
                    details,
                    caseDisplayName,
                    DEFAULT_OPTION, PLAIN_MESSAGE);
        }
    }

    private void saveToCSV() throws NoCurrentCaseException {
        if (casesTableModel.getRowCount() > 0) {
            Calendar now = Calendar.getInstance();
            String fileName = String.format("%1$tY%1$tm%1$te%1$tI%1$tM%1$tS_other_data_sources.csv", now);
            CSVFileChooser.setCurrentDirectory(new File(Case.getCurrentCaseThrows().getExportDirectory()));
            CSVFileChooser.setSelectedFile(new File(fileName));
            CSVFileChooser.setFileFilter(new FileNameExtensionFilter("csv file", "csv"));

            int returnVal = CSVFileChooser.showSaveDialog(filesTable);
            if (returnVal == JFileChooser.APPROVE_OPTION) {

                File selectedFile = CSVFileChooser.getSelectedFile();
                if (!selectedFile.getName().endsWith(".csv")) { // NON-NLS
                    selectedFile = new File(selectedFile.toString() + ".csv"); // NON-NLS
                }
                writeOtherOccurrencesToFileAsCSV(selectedFile);
            }
        }
    }

    @NbBundle.Messages({
        "OtherOccurrencesPanel.csvHeader.case=Case",
        "OtherOccurrencesPanel.csvHeader.device=Device",
        "OtherOccurrencesPanel.csvHeader.dataSource=Data Source",
        "OtherOccurrencesPanel.csvHeader.attribute=Matched Attribute",
        "OtherOccurrencesPanel.csvHeader.value=Attribute Value",
        "OtherOccurrencesPanel.csvHeader.known=Known",
        "OtherOccurrencesPanel.csvHeader.path=Path",
        "OtherOccurrencesPanel.csvHeader.comment=Comment"
    })
    /**
     * Write data for all cases in the content viewer to a CSV file
     */
    private void writeOtherOccurrencesToFileAsCSV(File destFile) {
        try (BufferedWriter writer = Files.newBufferedWriter(destFile.toPath())) {
            //write headers 
            StringBuilder headers = new StringBuilder("\"");
            headers.append(Bundle.OtherOccurrencesPanel_csvHeader_case())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_dataSource())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_attribute())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_value())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_known())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_path())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_comment())
                    .append('"').append(System.getProperty("line.separator"));
            writer.write(headers.toString());
            //write content
            for (CorrelationAttributeInstance corAttr : correlationAttributes) {
                Map<UniquePathKey, OtherOccurrenceNodeInstanceData> correlatedNodeDataMap = new HashMap<>(0);
                // get correlation and reference set instances from DB
                correlatedNodeDataMap.putAll(getCorrelatedInstances(corAttr));
                for (OtherOccurrenceNodeInstanceData nodeData : correlatedNodeDataMap.values()) {
                    writer.write(nodeData.toCsvString());
                }
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error writing selected rows to CSV.", ex);
        }
    }

    @NbBundle.Messages({"OtherOccurrencesPanel.earliestCaseNotAvailable= Not Enabled."})
    /**
     * Gets the list of Eam Cases and determines the earliest case creation
     * date. Sets the label to display the earliest date string to the user.
     */
    private void setEarliestCaseDate() {
        String dateStringDisplay = Bundle.OtherOccurrencesPanel_earliestCaseNotAvailable();

        if (CentralRepository.isEnabled()) {
            LocalDateTime earliestDate = LocalDateTime.now(DateTimeZone.UTC);
            DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
            try {
                CentralRepository dbManager = CentralRepository.getInstance();
                List<CorrelationCase> cases = dbManager.getCases();
                for (CorrelationCase aCase : cases) {
                    LocalDateTime caseDate = LocalDateTime.fromDateFields(datetimeFormat.parse(aCase.getCreationDate()));

                    if (caseDate.isBefore(earliestDate)) {
                        earliestDate = caseDate;
                        dateStringDisplay = aCase.getCreationDate();
                    }

                }

            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Error getting list of cases from database.", ex); // NON-NLS
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, "Error parsing date of cases from database.", ex); // NON-NLS
            }

        }
        earliestCaseDate.setText(dateStringDisplay);
    }

    /**
     * Reset the UI and clear cached data.
     */
    public void reset() {
        // start with empty table
        casesTableModel.clearTable();
        dataSourcesTableModel.clearTable();
        filesTableModel.clearTable();
        correlationAttributes.clear();
        earliestCaseDate.setText(Bundle.OtherOccurrencesPanel_earliestCaseNotAvailable());
        foundInLabel.setText("");
        //calling getPreferredSize has a side effect of ensuring it has a preferred size which reflects the contents which are visible
        occurrencePanel = new OccurrencePanel();
        occurrencePanel.getPreferredSize();
        detailsPanelScrollPane.setViewportView(occurrencePanel);
    }

    /**
     * Populate the other occurrences table for one Correlation Attribute type
     * and value.
     *
     * @param aType The correlation attribute type to display other occurrences
     *              for.
     * @param value The value being correlated on.
     */
    public void populateTableForOneType(CorrelationAttributeInstance.Type aType, String value) {
        Map<String, CorrelationCase> caseNames = new HashMap<>();
        int totalCount = 0;
        Set<String> dataSources = new HashSet<>();
        if (CentralRepository.isEnabled()) {
            try {
                List<CorrelationAttributeInstance> instances;
                instances = CentralRepository.getInstance().getArtifactInstancesByTypeValue(aType, value);
                HashMap<UniquePathKey, OtherOccurrenceNodeInstanceData> nodeDataMap = new HashMap<>();
                String caseUUID = Case.getCurrentCase().getName();
                for (CorrelationAttributeInstance artifactInstance : instances) {

                    // Only add the attribute if it isn't the object the user selected.
                    // We consider it to be a different object if at least one of the following is true:
                    // - the case UUID is different
                    // - the data source name is different
                    // - the data source device ID is different
                    // - the file path is different
                    if (artifactInstance.getCorrelationCase().getCaseUUID().equals(caseUUID)
                            && (!StringUtils.isBlank(dataSourceName) && artifactInstance.getCorrelationDataSource().getName().equals(dataSourceName))
                            && (!StringUtils.isBlank(deviceId) && artifactInstance.getCorrelationDataSource().getDeviceID().equals(deviceId))
                            && (file != null && artifactInstance.getFilePath().equalsIgnoreCase(file.getParentPath() + file.getName()))) {
                        continue;
                    }
                    correlationAttributes.add(artifactInstance);
                    OtherOccurrenceNodeInstanceData newNode = new OtherOccurrenceNodeInstanceData(artifactInstance, aType, value);
                    UniquePathKey uniquePathKey = new UniquePathKey(newNode);
                    nodeDataMap.put(uniquePathKey, newNode);
                }
                for (OtherOccurrenceNodeInstanceData nodeData : nodeDataMap.values()) {
                    if (nodeData.isCentralRepoNode()) {
                        try {
                            dataSources.add(makeDataSourceString(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID(), nodeData.getDeviceID(), nodeData.getDataSourceName()));
                            caseNames.put(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID(), nodeData.getCorrelationAttributeInstance().getCorrelationCase());
                        } catch (CentralRepoException ex) {
                            logger.log(Level.WARNING, "Unable to get correlation case for displaying other occurrence for case: " + nodeData.getCaseName(), ex);
                        }
                    } else {
                        try {
                            dataSources.add(makeDataSourceString(Case.getCurrentCaseThrows().getName(), nodeData.getDeviceID(), nodeData.getDataSourceName()));
                            caseNames.put(Case.getCurrentCaseThrows().getName(), new CorrelationCase(Case.getCurrentCaseThrows().getName(), Case.getCurrentCaseThrows().getDisplayName()));
                        } catch (NoCurrentCaseException ex) {
                            logger.log(Level.WARNING, "No current case open for other occurrences", ex);
                        }
                    }
                    totalCount++;
                }
            } catch (CorrelationAttributeNormalizationException | CentralRepoException ex) {
                logger.log(Level.WARNING, "Error retrieving other occurrences for " + aType.getDisplayName() + ": " + value, ex);
            }
        }
        for (CorrelationCase corCase : caseNames.values()) {
            casesTableModel.addCorrelationCase(new CorrelationCaseWrapper(corCase));
        }
        int caseCount = casesTableModel.getRowCount();
        if (correlationAttributes.isEmpty()) {
            casesTableModel.addCorrelationCase(NO_ARTIFACTS_CASE);
        } else if (caseCount == 0) {
            casesTableModel.addCorrelationCase(NO_RESULTS_CASE);
        }
        setEarliestCaseDate();
        foundInLabel.setText(String.format(Bundle.OtherOccurrencesPanel_foundIn_text(), totalCount, caseCount, dataSources.size()));
        if (caseCount > 0) {
            casesTable.setRowSelectionInterval(0, 0);
        }
    }

    /**
     * Load the correlatable data into the table model. If there is no data
     * available display the message on the status panel.
     *
     * @param correlationAttrs The correlationAttributes to correlate on.
     * @param dataSourceName   The name of the dataSource to ignore results
     *                         from.
     * @param deviceId         The deviceId of the device to ignore results
     *                         from.
     * @param abstractFile     The abstract file to ignore files with the same
     *                         location as.
     */
    @NbBundle.Messages({
        "OtherOccurrencesPanel.foundIn.text=Found %d instances in %d cases and %d data sources."
    })
    void populateTable(Collection<CorrelationAttributeInstance> correlationAttrs, String dataSourceName, String deviceId, AbstractFile abstractFile) {
        this.file = abstractFile;
        this.dataSourceName = dataSourceName;
        this.deviceId = deviceId;

        // get the attributes we can correlate on
        correlationAttributes.addAll(correlationAttrs);
        Map<String, CorrelationCase> caseNames = new HashMap<>();
        int totalCount = 0;
        Set<String> dataSources = new HashSet<>();
        for (CorrelationAttributeInstance corAttr : correlationAttributes) {
            for (OtherOccurrenceNodeInstanceData nodeData : getCorrelatedInstances(corAttr).values()) {
                if (nodeData.isCentralRepoNode()) {
                    try {
                        dataSources.add(makeDataSourceString(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID(), nodeData.getDeviceID(), nodeData.getDataSourceName()));
                        caseNames.put(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID(), nodeData.getCorrelationAttributeInstance().getCorrelationCase());
                    } catch (CentralRepoException ex) {
                        logger.log(Level.WARNING, "Unable to get correlation case for displaying other occurrence for case: " + nodeData.getCaseName(), ex);
                    }
                } else {
                    try {
                        dataSources.add(makeDataSourceString(Case.getCurrentCaseThrows().getName(), nodeData.getDeviceID(), nodeData.getDataSourceName()));
                        caseNames.put(Case.getCurrentCaseThrows().getName(), new CorrelationCase(Case.getCurrentCaseThrows().getName(), Case.getCurrentCaseThrows().getDisplayName()));
                    } catch (NoCurrentCaseException ex) {
                        logger.log(Level.WARNING, "No current case open for other occurrences", ex);
                    }
                }
                totalCount++;
            }
        }
        for (CorrelationCase corCase : caseNames.values()) {
            casesTableModel.addCorrelationCase(new CorrelationCaseWrapper(corCase));
        }
        int caseCount = casesTableModel.getRowCount();
        if (correlationAttributes.isEmpty()) {
            casesTableModel.addCorrelationCase(NO_ARTIFACTS_CASE);
        } else if (caseCount == 0) {
            casesTableModel.addCorrelationCase(NO_RESULTS_CASE);
        }
        setEarliestCaseDate();
        foundInLabel.setText(String.format(Bundle.OtherOccurrencesPanel_foundIn_text(), totalCount, caseCount, dataSources.size()));
        if (caseCount > 0) {
            casesTable.setRowSelectionInterval(0, 0);
        }
    }

    /**
     * Query the central repo database (if enabled) and the case database to
     * find all artifact instances correlated to the given central repository
     * artifact. If the central repo is not enabled, this will only return files
     * from the current case with matching MD5 hashes.
     *
     * @param corAttr CorrelationAttribute to query for
     *
     * @return A collection of correlated artifact instances
     */
    private Map<UniquePathKey, OtherOccurrenceNodeInstanceData> getCorrelatedInstances(CorrelationAttributeInstance corAttr) {
        // @@@ Check exception
        try {
            final Case openCase = Case.getCurrentCaseThrows();
            String caseUUID = openCase.getName();
            HashMap<UniquePathKey, OtherOccurrenceNodeInstanceData> nodeDataMap = new HashMap<>();

            if (CentralRepository.isEnabled()) {
                List<CorrelationAttributeInstance> instances = CentralRepository.getInstance().getArtifactInstancesByTypeValue(corAttr.getCorrelationType(), corAttr.getCorrelationValue());

                for (CorrelationAttributeInstance artifactInstance : instances) {

                    // Only add the attribute if it isn't the object the user selected.
                    // We consider it to be a different object if at least one of the following is true:
                    // - the case UUID is different
                    // - the data source name is different
                    // - the data source device ID is different
                    // - the file path is different
                    if (artifactInstance.getCorrelationCase().getCaseUUID().equals(caseUUID)
                            && (!StringUtils.isBlank(dataSourceName) && artifactInstance.getCorrelationDataSource().getName().equals(dataSourceName))
                            && (!StringUtils.isBlank(deviceId) && artifactInstance.getCorrelationDataSource().getDeviceID().equals(deviceId))
                            && (file != null && artifactInstance.getFilePath().equalsIgnoreCase(file.getParentPath() + file.getName()))) {
                        continue;
                    }
                    OtherOccurrenceNodeInstanceData newNode = new OtherOccurrenceNodeInstanceData(artifactInstance, corAttr.getCorrelationType(), corAttr.getCorrelationValue());
                    UniquePathKey uniquePathKey = new UniquePathKey(newNode);
                    nodeDataMap.put(uniquePathKey, newNode);
                }
                if (file != null && corAttr.getCorrelationType().getDisplayName().equals("Files")) {
                    List<AbstractFile> caseDbFiles = getCaseDbMatches(corAttr, openCase, file);

                    for (AbstractFile caseDbFile : caseDbFiles) {
                        addOrUpdateNodeData(openCase, nodeDataMap, caseDbFile);
                    }
                }
            }
            return nodeDataMap;
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error getting artifact instances from database.", ex); // NON-NLS
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.INFO, "Error getting artifact instances from database.", ex); // NON-NLS
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        } catch (TskCoreException ex) {
            // do nothing. 
            // @@@ Review this behavior
            logger.log(Level.SEVERE, "Exception while querying open case.", ex); // NON-NLS
        }

        return new HashMap<>(
                0);
    }

    /**
     * Adds the file to the nodeDataMap map if it does not already exist
     *
     * @param autopsyCase
     * @param nodeDataMap
     * @param newFile
     *
     * @throws TskCoreException
     * @throws CentralRepoException
     */
    private void addOrUpdateNodeData(final Case autopsyCase, Map<UniquePathKey, OtherOccurrenceNodeInstanceData> nodeDataMap, AbstractFile newFile) throws TskCoreException, CentralRepoException {

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

    /**
     * Get all other abstract files in the current case with the same MD5 as the
     * selected node.
     *
     * @param corAttr  The CorrelationAttribute containing the MD5 to search for
     * @param openCase The current case
     * @param file     The current file.
     *
     * @return List of matching AbstractFile objects
     *
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     * @throws CentralRepoException
     */
    private List<AbstractFile> getCaseDbMatches(CorrelationAttributeInstance corAttr, Case openCase, AbstractFile file) throws NoCurrentCaseException, TskCoreException, CentralRepoException {
        List<AbstractFile> caseDbArtifactInstances = new ArrayList<>();
        if (file != null) {
            String md5 = corAttr.getCorrelationValue();
            SleuthkitCase tsk = openCase.getSleuthkitCase();
            List<AbstractFile> matches = tsk.findAllFilesWhere(String.format("md5 = '%s'", new Object[]{md5}));

            for (AbstractFile fileMatch : matches) {
                if (file.equals(fileMatch)) {
                    continue; // If this is the file the user clicked on
                }
                caseDbArtifactInstances.add(fileMatch);
            }
        }
        return caseDbArtifactInstances;

    }

    /**
     * Create a unique string to be used as a key for deduping data sources as
     * best as possible
     */
    private String makeDataSourceString(String caseUUID, String deviceId, String dataSourceName) {
        return caseUUID + deviceId + dataSourceName;
    }

    /**
     * Updates diplayed information to be correct for the current case selection
     */
    private void updateOnCaseSelection() {
        int[] selectedCaseIndexes = casesTable.getSelectedRows();
        dataSourcesTableModel.clearTable();
        filesTableModel.clearTable();
        if (selectedCaseIndexes.length == 0) {
            //special case when no cases are selected
            occurrencePanel = new OccurrencePanel();
            occurrencePanel.getPreferredSize();
            detailsPanelScrollPane.setViewportView(occurrencePanel);
        } else {
            String currentCaseName;
            try {
                currentCaseName = Case.getCurrentCaseThrows().getName();
            } catch (NoCurrentCaseException ex) {
                currentCaseName = null;
                logger.log(Level.WARNING, "Unable to get current case for other occurrences content viewer", ex);
            }
            for (CorrelationAttributeInstance corAttr : correlationAttributes) {
                Map<UniquePathKey, OtherOccurrenceNodeInstanceData> correlatedNodeDataMap = new HashMap<>(0);

                // get correlation and reference set instances from DB
                correlatedNodeDataMap.putAll(getCorrelatedInstances(corAttr));
                for (OtherOccurrenceNodeInstanceData nodeData : correlatedNodeDataMap.values()) {
                    for (int selectedRow : selectedCaseIndexes) {
                        try {
                            if (nodeData.isCentralRepoNode()) {
                                if (casesTableModel.getCorrelationCase(casesTable.convertRowIndexToModel(selectedRow)) != null
                                        && casesTableModel.getCorrelationCase(casesTable.convertRowIndexToModel(selectedRow)).getCaseUUID().equals(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID())) {
                                    dataSourcesTableModel.addNodeData(nodeData);
                                }
                            } else if (currentCaseName != null && (casesTableModel.getCorrelationCase(casesTable.convertRowIndexToModel(selectedRow)).getCaseUUID().equals(currentCaseName))) {
                                dataSourcesTableModel.addNodeData(nodeData);
                            }
                        } catch (CentralRepoException ex) {
                            logger.log(Level.WARNING, "Unable to get correlation attribute instance from OtherOccurrenceNodeInstanceData for case " + nodeData.getCaseName(), ex);
                        }
                    }
                }
            }
            if (dataSourcesTable.getRowCount() > 0) {
                dataSourcesTable.setRowSelectionInterval(0, 0);
            }
        }
    }

    /**
     * Updates diplayed information to be correct for the current data source
     * selection
     */
    private void updateOnDataSourceSelection() {
        int[] selectedDataSources = dataSourcesTable.getSelectedRows();
        filesTableModel.clearTable();
        for (CorrelationAttributeInstance corAttr : correlationAttributes) {
            Map<UniquePathKey, OtherOccurrenceNodeInstanceData> correlatedNodeDataMap = new HashMap<>(0);

            // get correlation and reference set instances from DB
            correlatedNodeDataMap.putAll(getCorrelatedInstances(corAttr));
            for (OtherOccurrenceNodeInstanceData nodeData : correlatedNodeDataMap.values()) {
                for (int selectedDataSourceRow : selectedDataSources) {
                    try {
                        if (nodeData.isCentralRepoNode()) {
                            if (dataSourcesTableModel.getCaseUUIDForRow(dataSourcesTable.convertRowIndexToModel(selectedDataSourceRow)).equals(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID())
                                    && dataSourcesTableModel.getDeviceIdForRow(dataSourcesTable.convertRowIndexToModel(selectedDataSourceRow)).equals(nodeData.getDeviceID())) {
                                filesTableModel.addNodeData(nodeData);
                            }
                        } else {
                            if (dataSourcesTableModel.getDeviceIdForRow(dataSourcesTable.convertRowIndexToModel(selectedDataSourceRow)).equals(nodeData.getDeviceID())) {
                                filesTableModel.addNodeData(nodeData);
                            }
                        }
                    } catch (CentralRepoException ex) {
                        logger.log(Level.WARNING, "Unable to get correlation attribute instance from OtherOccurrenceNodeInstanceData for case " + nodeData.getCaseName(), ex);
                    }
                }
            }
        }
        if (filesTable.getRowCount() > 0) {
            filesTable.setRowSelectionInterval(0, 0);
        }
    }

    /**
     * Update the data displayed in the details section to be correct for the
     * currently selected File
     */
    private void updateOnFileSelection() {
        if (filesTable.getSelectedRowCount() == 1) {
            //if there is one file selected update the deatils to show the data for that file
            occurrencePanel = new OccurrencePanel(filesTableModel.getListOfNodesForFile(filesTable.convertRowIndexToModel(filesTable.getSelectedRow())));
        } else if (dataSourcesTable.getSelectedRowCount() == 1) {
            //if no files were selected and only one data source is selected update the information to reflect the data source
            String caseName = dataSourcesTableModel.getCaseNameForRow(dataSourcesTable.convertRowIndexToModel(dataSourcesTable.getSelectedRow()));
            String dsName = dataSourcesTableModel.getValueAt(dataSourcesTable.convertRowIndexToModel(dataSourcesTable.getSelectedRow()), 0).toString();
            String caseCreatedDate = "";
            for (int row : casesTable.getSelectedRows()) {
                if (casesTableModel.getValueAt(casesTable.convertRowIndexToModel(row), 0).toString().equals(caseName)) {
                    caseCreatedDate = getCaseCreatedDate(row);
                    break;
                }
            }
            occurrencePanel = new OccurrencePanel(caseName, caseCreatedDate, dsName);
        } else if (casesTable.getSelectedRowCount() == 1) {
            //if no files were selected and a number of data source other than 1 are selected
            //update the information to reflect the case
            String createdDate;
            String caseName = "";
            if (casesTable.getRowCount() > 0) {
                caseName = casesTableModel.getValueAt(casesTable.convertRowIndexToModel(casesTable.getSelectedRow()), 0).toString();
            }
            if (caseName.isEmpty()) {
                occurrencePanel = new OccurrencePanel();
            } else {
                createdDate = getCaseCreatedDate(casesTable.getSelectedRow());
                occurrencePanel = new OccurrencePanel(caseName, createdDate);
            }
        } else {
            //else display an empty details area
            occurrencePanel = new OccurrencePanel();
        }
        //calling getPreferredSize has a side effect of ensuring it has a preferred size which reflects the contents which are visible
        occurrencePanel.getPreferredSize();
        detailsPanelScrollPane.setViewportView(occurrencePanel);
    }

    /**
     * Get the date a case was created
     *
     * @param caseTableRowIdx the row from the casesTable representing the case
     *
     * @return A string representing the date the case was created or an empty
     *         string if the date could not be determined
     */
    private String getCaseCreatedDate(int caseTableRowIdx) {
        try {
            if (CentralRepository.isEnabled()) {
                CorrelationCase partialCase;
                partialCase = casesTableModel.getCorrelationCase(casesTable.convertRowIndexToModel(caseTableRowIdx));
                if (partialCase == null) {
                    return "";
                }
                return CentralRepository.getInstance().getCaseByUUID(partialCase.getCaseUUID()).getCreationDate();
            } else {
                return Case.getCurrentCase().getCreatedDate();
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.WARNING, "Error getting case created date for row: " + caseTableRowIdx, ex);
        }
        return "";
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        rightClickPopupMenu = new javax.swing.JPopupMenu();
        exportToCSVMenuItem = new javax.swing.JMenuItem();
        showCaseDetailsMenuItem = new javax.swing.JMenuItem();
        showCommonalityMenuItem = new javax.swing.JMenuItem();
        CSVFileChooser = new javax.swing.JFileChooser();
        tableContainerPanel = new javax.swing.JPanel();
        tablesViewerSplitPane = new javax.swing.JSplitPane();
        caseDatasourceFileSplitPane = new javax.swing.JSplitPane();
        caseDatasourceSplitPane = new javax.swing.JSplitPane();
        caseScrollPane = new javax.swing.JScrollPane();
        casesTable = new javax.swing.JTable();
        dataSourceScrollPane = new javax.swing.JScrollPane();
        dataSourcesTable = new javax.swing.JTable();
        filesTableScrollPane = new javax.swing.JScrollPane();
        filesTable = new javax.swing.JTable();
        detailsPanelScrollPane = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        foundInLabel = new javax.swing.JLabel();
        earliestCaseDate = new javax.swing.JLabel();
        earliestCaseLabel = new javax.swing.JLabel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));

        rightClickPopupMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {
                rightClickPopupMenuPopupMenuWillBecomeVisible(evt);
            }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {
            }
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(exportToCSVMenuItem, org.openide.util.NbBundle.getMessage(OtherOccurrencesPanel.class, "OtherOccurrencesPanel.exportToCSVMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(exportToCSVMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(showCaseDetailsMenuItem, org.openide.util.NbBundle.getMessage(OtherOccurrencesPanel.class, "OtherOccurrencesPanel.showCaseDetailsMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(showCaseDetailsMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(showCommonalityMenuItem, org.openide.util.NbBundle.getMessage(OtherOccurrencesPanel.class, "OtherOccurrencesPanel.showCommonalityMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(showCommonalityMenuItem);

        tableContainerPanel.setPreferredSize(new java.awt.Dimension(600, 63));
        tableContainerPanel.setRequestFocusEnabled(false);

        tablesViewerSplitPane.setDividerLocation(450);
        tablesViewerSplitPane.setResizeWeight(0.75);

        caseDatasourceFileSplitPane.setDividerLocation(300);
        caseDatasourceFileSplitPane.setResizeWeight(0.66);
        caseDatasourceFileSplitPane.setToolTipText(org.openide.util.NbBundle.getMessage(OtherOccurrencesPanel.class, "OtherOccurrencesPanel.caseDatasourceFileSplitPane.toolTipText")); // NOI18N

        caseDatasourceSplitPane.setDividerLocation(150);
        caseDatasourceSplitPane.setResizeWeight(0.5);

        caseScrollPane.setPreferredSize(new java.awt.Dimension(150, 30));

        casesTable.setAutoCreateRowSorter(true);
        casesTable.setModel(casesTableModel);
        caseScrollPane.setViewportView(casesTable);

        caseDatasourceSplitPane.setLeftComponent(caseScrollPane);

        dataSourceScrollPane.setPreferredSize(new java.awt.Dimension(150, 30));

        dataSourcesTable.setAutoCreateRowSorter(true);
        dataSourcesTable.setModel(dataSourcesTableModel);
        dataSourceScrollPane.setViewportView(dataSourcesTable);

        caseDatasourceSplitPane.setRightComponent(dataSourceScrollPane);

        caseDatasourceFileSplitPane.setLeftComponent(caseDatasourceSplitPane);

        filesTableScrollPane.setPreferredSize(new java.awt.Dimension(150, 30));

        filesTable.setAutoCreateRowSorter(true);
        filesTable.setModel(filesTableModel);
        filesTable.setToolTipText(org.openide.util.NbBundle.getMessage(OtherOccurrencesPanel.class, "OtherOccurrencesPanel.filesTable.toolTipText")); // NOI18N
        filesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        filesTableScrollPane.setViewportView(filesTable);

        caseDatasourceFileSplitPane.setRightComponent(filesTableScrollPane);

        tablesViewerSplitPane.setLeftComponent(caseDatasourceFileSplitPane);

        detailsPanelScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        detailsPanelScrollPane.setPreferredSize(new java.awt.Dimension(300, 100));
        tablesViewerSplitPane.setRightComponent(detailsPanelScrollPane);

        jPanel1.setPreferredSize(new java.awt.Dimension(576, 22));
        jPanel1.setLayout(new java.awt.GridBagLayout());

        foundInLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        org.openide.awt.Mnemonics.setLocalizedText(foundInLabel, org.openide.util.NbBundle.getMessage(OtherOccurrencesPanel.class, "OtherOccurrencesPanel.foundInLabel.text")); // NOI18N
        foundInLabel.setPreferredSize(new java.awt.Dimension(400, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 0);
        jPanel1.add(foundInLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(earliestCaseDate, org.openide.util.NbBundle.getMessage(OtherOccurrencesPanel.class, "OtherOccurrencesPanel.earliestCaseDate.text")); // NOI18N
        earliestCaseDate.setMaximumSize(new java.awt.Dimension(200, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 7, 0, 0);
        jPanel1.add(earliestCaseDate, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(earliestCaseLabel, org.openide.util.NbBundle.getMessage(OtherOccurrencesPanel.class, "OtherOccurrencesPanel.earliestCaseLabel.text")); // NOI18N
        earliestCaseLabel.setToolTipText(org.openide.util.NbBundle.getMessage(OtherOccurrencesPanel.class, "OtherOccurrencesPanel.earliestCaseLabel.toolTipText")); // NOI18N
        earliestCaseLabel.setMaximumSize(new java.awt.Dimension(260, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        jPanel1.add(earliestCaseLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.1;
        jPanel1.add(filler1, gridBagConstraints);

        javax.swing.GroupLayout tableContainerPanelLayout = new javax.swing.GroupLayout(tableContainerPanel);
        tableContainerPanel.setLayout(tableContainerPanelLayout);
        tableContainerPanelLayout.setHorizontalGroup(
            tableContainerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tableContainerPanelLayout.createSequentialGroup()
                .addGroup(tableContainerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tablesViewerSplitPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 516, Short.MAX_VALUE))
                .addContainerGap())
        );
        tableContainerPanelLayout.setVerticalGroup(
            tableContainerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tableContainerPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(tablesViewerSplitPane)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tableContainerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 528, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tableContainerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 143, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void rightClickPopupMenuPopupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {//GEN-FIRST:event_rightClickPopupMenuPopupMenuWillBecomeVisible
        boolean enableCentralRepoActions = false;
        if (CentralRepository.isEnabled() && filesTable.getSelectedRowCount() == 1) {
            int rowIndex = filesTable.getSelectedRow();
            List<OtherOccurrenceNodeData> selectedFile = filesTableModel.getListOfNodesForFile(rowIndex);
            if (!selectedFile.isEmpty() && selectedFile.get(0) instanceof OtherOccurrenceNodeInstanceData) {
                OtherOccurrenceNodeInstanceData instanceData = (OtherOccurrenceNodeInstanceData) selectedFile.get(0);
                enableCentralRepoActions = instanceData.isCentralRepoNode();
            }
        }
        showCaseDetailsMenuItem.setVisible(enableCentralRepoActions);
        showCommonalityMenuItem.setVisible(enableCentralRepoActions);
    }//GEN-LAST:event_rightClickPopupMenuPopupMenuWillBecomeVisible


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JFileChooser CSVFileChooser;
    private javax.swing.JSplitPane caseDatasourceFileSplitPane;
    private javax.swing.JSplitPane caseDatasourceSplitPane;
    private javax.swing.JScrollPane caseScrollPane;
    private javax.swing.JTable casesTable;
    private javax.swing.JScrollPane dataSourceScrollPane;
    private javax.swing.JTable dataSourcesTable;
    private javax.swing.JScrollPane detailsPanelScrollPane;
    private javax.swing.JLabel earliestCaseDate;
    private javax.swing.JLabel earliestCaseLabel;
    private javax.swing.JMenuItem exportToCSVMenuItem;
    private javax.swing.JTable filesTable;
    private javax.swing.JScrollPane filesTableScrollPane;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JLabel foundInLabel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPopupMenu rightClickPopupMenu;
    private javax.swing.JMenuItem showCaseDetailsMenuItem;
    private javax.swing.JMenuItem showCommonalityMenuItem;
    private javax.swing.JPanel tableContainerPanel;
    private javax.swing.JSplitPane tablesViewerSplitPane;
    // End of variables declaration//GEN-END:variables
}
