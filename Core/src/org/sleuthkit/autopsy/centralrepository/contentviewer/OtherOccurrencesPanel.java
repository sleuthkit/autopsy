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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.sleuthkit.autopsy.centralrepository.application.NodeData;
import org.sleuthkit.autopsy.centralrepository.application.UniquePathKey;
import org.sleuthkit.autopsy.centralrepository.application.OtherOccurrences;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.DEFAULT_OPTION;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.PLAIN_MESSAGE;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.contentviewer.OtherOccurrencesNodeWorker.OtherOccurrencesData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Panel for displaying other occurrences results.
 */
@NbBundle.Messages({
    "OtherOccurrencesPanel.table.noArtifacts=Item has no attributes with which to search.",
    "OtherOccurrencesPanel.table.noResultsFound=No results found.",
    "OtherOccurrencesPanel_table_loadingResults=Loading results"
})
public final class OtherOccurrencesPanel extends javax.swing.JPanel {

    private static final CorrelationCaseWrapper NO_ARTIFACTS_CASE = new CorrelationCaseWrapper(Bundle.OtherOccurrencesPanel_table_noArtifacts());
    private static final CorrelationCaseWrapper NO_RESULTS_CASE = new CorrelationCaseWrapper(Bundle.OtherOccurrencesPanel_table_noResultsFound());
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

    private SwingWorker<?, ?> worker;

    // Initializing the JFileChooser in a thread to prevent a block on the EDT
    // see https://stackoverflow.com/questions/49792375/jfilechooser-is-very-slow-when-using-windows-look-and-feel
    private final FutureTask<JFileChooser> futureFileChooser = new FutureTask<>(JFileChooser::new);
    private JFileChooser CSVFileChooser;

    /**
     * Creates new form OtherOccurrencesPanel
     */
    public OtherOccurrencesPanel() {
        this.filesTableModel = new OtherOccurrencesFilesTableModel();
        this.casesTableModel = new OtherOccurrencesCasesTableModel();
        this.dataSourcesTableModel = new OtherOccurrencesDataSourcesTableModel();
        this.correlationAttributes = new ArrayList<>();
        initComponents();
        customizeComponents();

        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("JFileChooser-background-thread-OtherOccurrencesPanel").build());
        executor.execute(futureFileChooser);
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
            JOptionPane.showConfirmDialog(OtherOccurrencesPanel.this,
                    Bundle.OtherOccurrencesPanel_correlatedArtifacts_isEmpty(),
                    Bundle.OtherOccurrencesPanel_correlatedArtifacts_title(),
                    DEFAULT_OPTION, PLAIN_MESSAGE);
        } else {
            StringBuilder msg = new StringBuilder(correlationAttributes.size());
            int percentage;
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                // Leaving these calls on the EDT but adding wait cursor
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
                this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                JOptionPane.showConfirmDialog(OtherOccurrencesPanel.this,
                        msg.toString(),
                        Bundle.OtherOccurrencesPanel_correlatedArtifacts_title(),
                        DEFAULT_OPTION, PLAIN_MESSAGE);
            } catch (CentralRepoException ex) {
                this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                logger.log(Level.SEVERE, "Error getting commonality details.", ex);
                JOptionPane.showConfirmDialog(OtherOccurrencesPanel.this,
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
            if (-1 != selectedRowViewIdx && filesTableModel.getRowCount() > 0) {
                CentralRepository dbManager = CentralRepository.getInstance();
                int selectedRowModelIdx = filesTable.convertRowIndexToModel(selectedRowViewIdx);
                List<NodeData> rowList = filesTableModel.getListOfNodesForFile(selectedRowModelIdx);
                if (!rowList.isEmpty()) {
                    CorrelationCase eamCasePartial = rowList.get(0).getCorrelationAttributeInstance().getCorrelationCase();
                    caseDisplayName = eamCasePartial.getDisplayName();
                    // query case details
                    CorrelationCase eamCase = dbManager.getCaseByUUID(eamCasePartial.getCaseUUID());
                    if (eamCase != null) {
                        details = eamCase.getCaseDetailsOptionsPaneDialog();
                    } else {
                        details = Bundle.OtherOccurrencesPanel_caseDetailsDialog_noDetails();
                    }
                } else {
                    details = Bundle.OtherOccurrencesPanel_caseDetailsDialog_noDetailsReference();
                }
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error loading case details", ex);
        } finally {
            JOptionPane.showConfirmDialog(OtherOccurrencesPanel.this,
                    details,
                    caseDisplayName,
                    DEFAULT_OPTION, PLAIN_MESSAGE);
        }
    }

    private void saveToCSV() throws NoCurrentCaseException {
        if (casesTableModel.getRowCount() > 0) {

            if (CSVFileChooser == null) {
                try {
                    CSVFileChooser = futureFileChooser.get();
                } catch (InterruptedException | ExecutionException ex) {
                    // If something happened with the thread try and 
                    // initalized the chooser now
                    logger.log(Level.WARNING, "A failure occurred in the JFileChooser background thread");
                    CSVFileChooser = new JFileChooser();
                }
            }

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
                CSVWorker csvWorker = new CSVWorker(selectedFile, dataSourceName, deviceId, Collections.unmodifiableCollection(correlationAttributes));
                csvWorker.execute();
            }
        }
    }

    @NbBundle.Messages({"OtherOccurrencesPanel_earliestCaseNotAvailable=Not Available."})
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
     * This method contains its own SwingWorker togather data.
     *
     * @param aType The correlation attribute type to display other occurrences
     *              for.
     * @param value The value being correlated on.
     */
    public void populateTableForOneType(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException {
        if (worker != null) {
            worker.cancel(true);
            worker = null;
        }

        casesTableModel.addCorrelationCase(NO_ARTIFACTS_CASE);

        worker = new OtherOccurrenceOneTypeWorker(aType, value, file, deviceId, dataSourceName) {
            @Override
            public void done() {
                try {
                    if (isCancelled()) {
                        return;
                    }

                    casesTableModel.clearTable();

                    OtherOccurrenceOneTypeWorker.OneTypeData data = get();
                    correlationAttributes.addAll(data.getCorrelationAttributesToAdd());
                    for (CorrelationCase corCase : data.getCaseNames().values()) {
                        casesTableModel.addCorrelationCase(new CorrelationCaseWrapper(corCase));
                    }
                    int caseCount = casesTableModel.getRowCount();
                    if (correlationAttributes.isEmpty()) {
                        casesTableModel.addCorrelationCase(NO_ARTIFACTS_CASE);
                    } else if (caseCount == 0) {
                        casesTableModel.addCorrelationCase(NO_RESULTS_CASE);
                    } else {
                        String earliestDate = data.getEarliestCaseDate();
                        earliestCaseDate.setText(earliestDate.isEmpty() ? Bundle.OtherOccurrencesPanel_earliestCaseNotAvailable() : earliestDate);
                        foundInLabel.setText(String.format(Bundle.OtherOccurrencesPanel_foundIn_text(), data.getTotalCount(), caseCount, data.getDataSourceCount()));
                        if (caseCount > 0) {
                            casesTable.setRowSelectionInterval(0, 0);
                        }
                    }

                } catch (InterruptedException | ExecutionException ex) {
                    logger.log(Level.SEVERE, "Failed to update OtherOccurrence panel", ex);
                }
            }
        };

        worker.execute();
    }

    /**
     * Makes a loading message appear in the case table.
     */
    void showPanelLoadingMessage() {
        casesTableModel.addCorrelationCase(NO_ARTIFACTS_CASE);
    }

    /**
     * Load the correlatable data into the table model. If there is no data
     * available display the message on the status panel.
     *
     * @param data A data wrapper object.
     */
    @NbBundle.Messages({
        "OtherOccurrencesPanel.foundIn.text=Found %d instances in %d cases and %d data sources."
    })
    void populateTable(OtherOccurrencesData data) {
        this.file = data.getFile();
        this.dataSourceName = data.getDataSourceName();
        this.deviceId = data.getDeviceId();

        casesTableModel.clearTable();

        correlationAttributes.addAll(data.getCorrelationAttributes());

        for (CorrelationCase corCase : data.getCaseMap().values()) {
            casesTableModel.addCorrelationCase(new CorrelationCaseWrapper(corCase));
        }
        int caseCount = casesTableModel.getRowCount();
        if (correlationAttributes.isEmpty()) {
            casesTableModel.addCorrelationCase(NO_ARTIFACTS_CASE);
        } else if (caseCount == 0) {
            casesTableModel.addCorrelationCase(NO_RESULTS_CASE);
        } else {
            String earliestDate = data.getEarliestCaseDate();
            earliestCaseDate.setText(earliestDate.isEmpty() ? Bundle.OtherOccurrencesPanel_earliestCaseNotAvailable() : earliestDate);
            foundInLabel.setText(String.format(Bundle.OtherOccurrencesPanel_foundIn_text(), data.getInstanceDataCount(), caseCount, data.getDataSourceCount()));
            if (caseCount > 0) {
                casesTable.setRowSelectionInterval(0, 0);
            }
        }
    }

    /**
     * Updates displayed information to be correct for the current case
     * selection
     */
    private void updateOnCaseSelection() {
        if (worker != null) {
            worker.cancel(true);
            worker = null;
        }

        final int[] selectedCaseIndexes = casesTable.getSelectedRows();
        dataSourcesTableModel.clearTable();
        filesTableModel.clearTable();

        if (selectedCaseIndexes.length == 0) {
            //special case when no cases are selected
            occurrencePanel = new OccurrencePanel();
            occurrencePanel.getPreferredSize();
            detailsPanelScrollPane.setViewportView(occurrencePanel);

            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        worker = new SelectionWorker(correlationAttributes, deviceId, dataSourceName) {
            @Override
            public void done() {
                if (isCancelled()) {
                    return;
                }

                try {
                    Map<UniquePathKey, NodeData> correlatedNodeDataMap = get();

                    String currentCaseName;
                    try {
                        currentCaseName = Case.getCurrentCaseThrows().getName();
                    } catch (NoCurrentCaseException ex) {
                        currentCaseName = null;
                        logger.log(Level.WARNING, "Unable to get current case for other occurrences content viewer", ex);
                    }
                    if (casesTableModel.getRowCount() > 0) {
                        for (NodeData nodeData : correlatedNodeDataMap.values()) {
                            for (int selectedRow : selectedCaseIndexes) {
                                try {
                                    if (casesTableModel.getCorrelationCase(casesTable.convertRowIndexToModel(selectedRow)) != null
                                            && casesTableModel.getCorrelationCase(casesTable.convertRowIndexToModel(selectedRow)).getCaseUUID().equals(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID())) {
                                        dataSourcesTableModel.addNodeData(nodeData);
                                    }
                                } catch (CentralRepoException ex) {
                                    logger.log(Level.WARNING, "Unable to get correlation attribute instance from OtherOccurrenceNodeInstanceData for case " + nodeData.getCaseName(), ex);
                                }
                            }
                        }
                    }
                    if (dataSourcesTableModel.getRowCount() > 0) {
                        dataSourcesTable.setRowSelectionInterval(0, 0);
                    }

                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

                } catch (InterruptedException | ExecutionException ex) {
                    logger.log(Level.SEVERE, "Failed to update OtherOccurrencesPanel on data source selection", ex);
                }
            }
        };

        worker.execute();
    }

    /**
     * Updates displayed information to be correct for the current data source
     * selection
     */
    private void updateOnDataSourceSelection() {
        if (worker != null) {
            worker.cancel(true);
            worker = null;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        final int[] selectedDataSources = dataSourcesTable.getSelectedRows();
        filesTableModel.clearTable();

        worker = new SelectionWorker(correlationAttributes, deviceId, dataSourceName) {
            @Override
            public void done() {
                if (isCancelled()) {
                    return;
                }

                try {
                    Map<UniquePathKey, NodeData> correlatedNodeDataMap = get();
                    if (dataSourcesTableModel.getRowCount() > 0) {
                        for (NodeData nodeData : correlatedNodeDataMap.values()) {
                            for (int selectedDataSourceRow : selectedDataSources) {
                                int rowModelIndex = dataSourcesTable.convertRowIndexToModel(selectedDataSourceRow);
                                try {
                                    if (dataSourcesTableModel.getCaseUUIDForRow(rowModelIndex).equals(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID())
                                            && dataSourcesTableModel.getDeviceIdForRow(rowModelIndex).equals(nodeData.getDeviceID())) {
                                        filesTableModel.addNodeData(nodeData);
                                    }
                                } catch (CentralRepoException ex) {
                                    logger.log(Level.WARNING, "Unable to get correlation attribute instance from OtherOccurrenceNodeInstanceData for case " + nodeData.getCaseName(), ex);
                                }
                            }
                        }
                    }

                    if (filesTableModel.getRowCount() > 0) {
                        filesTable.setRowSelectionInterval(0, 0);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    logger.log(Level.SEVERE, "Failed to update OtherOccurrencesPanel on case selection", ex);
                } finally {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        };

        worker.execute();
    }

    /**
     * Update the data displayed in the details section to be correct for the
     * currently selected File
     */
    private void updateOnFileSelection() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            if (filesTableModel.getRowCount() > 0 && filesTable.getSelectedRowCount() == 1) {
                //if there is one file selected update the deatils to show the data for that file
                occurrencePanel = new OccurrencePanel(filesTableModel.getListOfNodesForFile(filesTable.convertRowIndexToModel(filesTable.getSelectedRow())));
            } else if (dataSourcesTableModel.getRowCount() > 0 && dataSourcesTable.getSelectedRowCount() == 1) {
                //if no files were selected and only one data source is selected update the information to reflect the data source
                String caseName = dataSourcesTableModel.getCaseNameForRow(dataSourcesTable.convertRowIndexToModel(dataSourcesTable.getSelectedRow()));
                String dsName = dataSourcesTableModel.getValueAt(dataSourcesTable.convertRowIndexToModel(dataSourcesTable.getSelectedRow()), 0).toString();
                String caseCreatedDate = "";
                if (casesTableModel.getRowCount() > 0) {
                    for (int row : casesTable.getSelectedRows()) {
                        if (casesTableModel.getValueAt(casesTable.convertRowIndexToModel(row), 0).toString().equals(caseName)) {
                            caseCreatedDate = getCaseCreatedDate(row);
                            break;
                        }
                    }
                }
                occurrencePanel = new OccurrencePanel(caseName, caseCreatedDate, dsName);
            } else if (casesTable.getSelectedRowCount() == 1) {
                //if no files were selected and a number of data source other than 1 are selected
                //update the information to reflect the case
                String createdDate;
                String caseName = "";
                if (casesTableModel.getRowCount() > 0) {
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
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
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
            if (CentralRepository.isEnabled() && casesTableModel.getRowCount() > 0) {
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
     * SwingWorker used by the case and data source selection handler.
     */
    private class SelectionWorker extends SwingWorker<Map<UniquePathKey, NodeData>, Void> {

        private final Collection<CorrelationAttributeInstance> coAtInstances;
        private final String deviceIdStr;
        private final String dataSourceNameStr;

        /**
         * Construct a new SelectionWorker.
         *
         * @param coAtInstances
         * @param abstractFile
         * @param deviceIdStr
         * @param dataSourceNameStr
         */
        SelectionWorker(Collection<CorrelationAttributeInstance> coAtInstances, String deviceIdStr, String dataSourceNameStr) {
            this.coAtInstances = coAtInstances;
            this.dataSourceNameStr = dataSourceNameStr;
            this.deviceIdStr = deviceIdStr;
        }

        @Override
        protected Map<UniquePathKey, NodeData> doInBackground() throws Exception {
            Map<UniquePathKey, NodeData> correlatedNodeDataMap = new HashMap<>();
            for (CorrelationAttributeInstance corAttr : coAtInstances) {
                correlatedNodeDataMap.putAll(OtherOccurrences.getCorrelatedInstances(deviceIdStr, dataSourceNameStr, corAttr));

                if (isCancelled()) {
                    return new HashMap<>();
                }
            }

            return correlatedNodeDataMap;
        }
    }

    /**
     * SwingWorker for creating the CSV dump file.
     */
    private class CSVWorker extends SwingWorker<Void, Void> {

        private final Collection<CorrelationAttributeInstance> correlationAttList;
        private final String dataSourceName;
        private final String deviceId;
        private final File destFile;

        /**
         * Construct a CSVWorker
         *
         * @param destFile           Output file.
         * @param sourceFile         Input file.
         * @param dataSourceName     Name of current dataSource.
         * @param deviceId           Id of the selected device.
         * @param correlationAttList
         */
        CSVWorker(File destFile, String dataSourceName, String deviceId, Collection<CorrelationAttributeInstance> correlationAttList) {
            this.destFile = destFile;
            this.dataSourceName = dataSourceName;
            this.deviceId = deviceId;
            this.correlationAttList = correlationAttList;
        }

        @Override
        protected Void doInBackground() throws Exception {
            OtherOccurrences.writeOtherOccurrencesToFileAsCSV(this.destFile, this.correlationAttList, this.dataSourceName, this.deviceId);
            return null;
        }

        @Override
        public void done() {
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                JOptionPane.showMessageDialog(OtherOccurrencesPanel.this,
                        "Failed to create csv file for Other Occurrences at\n" + destFile.getAbsolutePath(),
                        "Error Creating CSV",
                        JOptionPane.ERROR_MESSAGE);

                logger.log(Level.SEVERE, "Error writing selected rows to from OtherOccurrencePanel to " + destFile.getAbsolutePath(), ex);
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
        java.awt.GridBagConstraints gridBagConstraints;

        rightClickPopupMenu = new javax.swing.JPopupMenu();
        exportToCSVMenuItem = new javax.swing.JMenuItem();
        showCaseDetailsMenuItem = new javax.swing.JMenuItem();
        showCommonalityMenuItem = new javax.swing.JMenuItem();
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
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {
            }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {
            }
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {
                rightClickPopupMenuPopupMenuWillBecomeVisible(evt);
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
            List<NodeData> selectedFile = filesTableModel.getListOfNodesForFile(rowIndex);
            if (!selectedFile.isEmpty() && selectedFile.get(0) instanceof NodeData) {
                enableCentralRepoActions = true;
            }
        }
        showCaseDetailsMenuItem.setVisible(enableCentralRepoActions);
        showCommonalityMenuItem.setVisible(enableCentralRepoActions);
    }//GEN-LAST:event_rightClickPopupMenuPopupMenuWillBecomeVisible


    // Variables declaration - do not modify//GEN-BEGIN:variables
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
