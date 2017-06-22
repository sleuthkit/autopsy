/*
 * Enterprise Artifacts Manager
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.contentviewer;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.stream.Collectors;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.DEFAULT_OPTION;
import static javax.swing.JOptionPane.PLAIN_MESSAGE;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.contentviewer.Bundle;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamArtifact;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamArtifactInstance;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamCase;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamDbException;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamGlobalFileInstance;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamDb;

/**
 * View correlation results from other cases
 */
@ServiceProvider(service = DataContentViewer.class, position = 8)
@Messages({"DataContentViewerOtherCases.title=Other Cases",
    "DataContentViewerOtherCases.toolTip=Displays instances of the selected file/artifact from other cases.",})
public class DataContentViewerOtherCases extends javax.swing.JPanel implements DataContentViewer {

    private final static Logger LOGGER = Logger.getLogger(DataContentViewerOtherCases.class.getName());

    private final DataContentViewerOtherCasesTableModel tableModel;
    private final Collection<EamArtifact> correlatedArtifacts;

    /**
     * Creates new form DataContentViewerOtherCases
     */
    public DataContentViewerOtherCases() {
        this.tableModel = new DataContentViewerOtherCasesTableModel();
        this.correlatedArtifacts = new ArrayList<>();

        initComponents();
        customizeComponents();
        readSettings();
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
                    saveToCSV();
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
        tableStatusPanelLabel.setVisible(false);
    }

    @Messages({"DataContentViewerOtherCases.correlatedArtifacts.isEmpty=There are no files or artifacts to correlate.",
        "# {0} - commonality percentage",
        "# {1} - artifact type",
        "# {2} - artifact value",
        "DataContentViewerOtherCases.correlatedArtifacts.byType={0}% for Artifact Type: {1} and Artifact Value: {2}.\n",
        "DataContentViewerOtherCases.correlatedArtifacts.title=Commonality Percentages",
        "DataContentViewerOtherCases.correlatedArtifacts.failed=Failed to get commonality details."})
    private void showCommonalityDetails() {
        if (correlatedArtifacts.isEmpty()) {
            JOptionPane.showConfirmDialog(showCommonalityMenuItem,
                    Bundle.DataContentViewerOtherCases_correlatedArtifacts_isEmpty(),
                    Bundle.DataContentViewerOtherCases_correlatedArtifacts_title(),
                    DEFAULT_OPTION, PLAIN_MESSAGE);
        } else {
            StringBuilder msg = new StringBuilder();
            int percentage;
            try {
                EamDb dbManager = EamDb.getInstance();
                for (EamArtifact eamArtifact : correlatedArtifacts) {
                    percentage = dbManager.getCommonalityPercentageForTypeValue(eamArtifact);
                    msg.append(Bundle.DataContentViewerOtherCases_correlatedArtifacts_byType(percentage,
                            eamArtifact.getArtifactType().getName(),
                            eamArtifact.getArtifactValue()));
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
        "DataContentViewerOtherCases.caseDetailsDialog.noCaseNameError=Error"})
    private void showCaseDetails(int selectedRowViewIdx) {
        String caseDisplayName = Bundle.DataContentViewerOtherCases_caseDetailsDialog_noCaseNameError();
        try {
            if (-1 != selectedRowViewIdx) {
                EamDb dbManager = EamDb.getInstance();
                int selectedRowModelIdx = otherCasesTable.convertRowIndexToModel(selectedRowViewIdx);
                EamArtifact eamArtifact = (EamArtifact) tableModel.getRow(selectedRowModelIdx);
                EamCase eamCasePartial = eamArtifact.getInstances().get(0).getEamCase();
                caseDisplayName = eamCasePartial.getDisplayName();
                // query case details
                EamCase eamCase = dbManager.getCaseDetails(eamCasePartial.getCaseUUID());
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
            JOptionPane.showConfirmDialog(showCaseDetailsMenuItem,
                    Bundle.DataContentViewerOtherCases_caseDetailsDialog_noDetails(),
                    caseDisplayName,
                    DEFAULT_OPTION, PLAIN_MESSAGE);
        }
    }

    private void saveToCSV() {
        if (0 != otherCasesTable.getSelectedRowCount()) {
            Calendar now = Calendar.getInstance();
            String fileName = String.format("%1$tY%1$tm%1$te%1$tI%1$tM%1$tS_other_cases.csv", now);
            CSVFileChooser.setCurrentDirectory(new File(Case.getCurrentCase().getExportDirectory()));
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
     * Read the module settings from the config file and reset the table model.
     */
    private boolean readSettings() {
        // start with empty table
        tableModel.clearTable();
        correlatedArtifacts.clear();

        return true;
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
        readSettings();
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
     * Scan a Node for blackboard artifacts / content that we can correlate on
     * and create the corresponding enterprise artifacts manager artifacts for
     * display
     *
     * @param node The node to view
     *
     * @return A collection of enterprise artifacts manager artifacts to display
     */
    private Collection<EamArtifact> getArtifactsFromCorrelatableAttributes(Node node) {
        Collection<EamArtifact> ret = new ArrayList<>();

        /*
         * If the user selected a blackboard artifact or tag of a BB artifact,
         * correlate both the artifact and the associated file. If the user
         * selected a file, correlate only the file
         */
        BlackboardArtifact bbArtifact = getBlackboardArtifactFromNode(node);
        AbstractFile abstractFile = getAbstractFileFromNode(node);
        List<EamArtifact.Type> artifactTypes = null;
        try {
            EamDb dbManager = EamDb.getInstance();
            artifactTypes = dbManager.getCorrelationArtifactTypes();
            if (bbArtifact != null) {
                EamArtifact eamArtifact = EamArtifactUtil.fromBlackboardArtifact(bbArtifact, false, artifactTypes, false);
                if (eamArtifact != null) {
                    ret.add(eamArtifact);
                }
            }
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error retrieving correlation artifact types", ex); // NON-NLS
        }

        if (abstractFile != null) {
            String md5 = abstractFile.getMd5Hash();
            if (md5 != null && !md5.isEmpty() && null != artifactTypes && !artifactTypes.isEmpty()) {
                for (EamArtifact.Type aType : artifactTypes) {
                    if (aType.getName().equals("FILES")) {
                        ret.add(new EamArtifact(aType, md5));
                        break;
                    }
                }
            }
        }

        return ret;
    }

    /**
     * Given a node, return the associated data source
     *
     * @param node The node
     *
     * @return The name of the data source
     */
    private String getDataSourceNameFromNode(Node node) {
        AbstractFile af = getAbstractFileFromNode(node);
        try {
            if (af != null) {
                return af.getDataSource().getName();
            }
        } catch (TskException ex) {
            return "";
        }

        return "";
    }

    /**
     * Given a node, return the associated data source's device ID
     *
     * @param node The node
     *
     * @return The ID of the data source's device
     */
    private String getDeviceIdFromNode(Node node) {
        AbstractFile af = getAbstractFileFromNode(node);
        try {
            if (af != null) {
                return Case.getCurrentCase().getSleuthkitCase().getDataSource(af.getDataSource().getId()).getDeviceId();
            }
        } catch (TskException ex) {
            return "";
        }

        return "";
    }

    /**
     * Query the db for artifact instances from other cases correlated to the
     * given enterprise artifacts manager artifact.
     *
     * @param eamArtifact The artifact to correlate against
     *
     * @return A collection of correlated artifact instances from other cases
     */
    private Collection<EamArtifactInstance> getCorrelatedInstances(EamArtifact eamArtifact, String dataSourceName, String deviceId) {
        String caseUUID = Case.getCurrentCase().getName();
        try {
            EamDb dbManager = EamDb.getInstance();
            Collection<EamArtifactInstance> artifactInstances = dbManager.getArtifactInstancesByTypeValue(eamArtifact).stream()
                    .filter(artifactInstance -> !artifactInstance.getEamCase().getCaseUUID().equals(caseUUID)
                    || !artifactInstance.getEamDataSource().getName().equals(dataSourceName)
                    || !artifactInstance.getEamDataSource().getDeviceID().equals(deviceId))
                    .collect(Collectors.toList());
            return artifactInstances;
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting artifact instances from database.", ex); // NON-NLS
        }

        return Collections.emptyList();
    }

    /**
     * Get the Global File Instances matching the given eamArtifact and convert
     * them to Enterprise Artifacts Manager Artifact Instancess.
     *
     * @param eamArtifact Artifact to use for ArtifactTypeEnum matching
     *
     * @return List of Enterprise Artifacts Manager Artifact Instances, empty
     *         list if none found
     */
    public Collection<EamArtifactInstance> getGlobalFileInstancesAsArtifactInstances(EamArtifact eamArtifact) {
        Collection<EamArtifactInstance> eamArtifactInstances = new ArrayList<>();
        try {
            EamDb dbManager = EamDb.getInstance();
            if (dbManager.getCorrelationArtifactTypeByName("FILES").equals(eamArtifact.getArtifactType())) {
                try {
                    Collection<EamGlobalFileInstance> eamGlobalFileInstances = dbManager.getGlobalFileInstancesByHash(eamArtifact.getArtifactValue());
                    for (EamGlobalFileInstance eamGlobalFileInstance : eamGlobalFileInstances) {
                        eamArtifactInstances.add(new EamArtifactInstance(
                                null, null, "", eamGlobalFileInstance.getComment(), eamGlobalFileInstance.getKnownStatus(), EamArtifactInstance.GlobalStatus.GLOBAL
                        ));
                    }
                    return eamArtifactInstances;
                } catch (EamDbException ex) {
                    LOGGER.log(Level.SEVERE, "Error getting global file instances from database.", ex); // NON-NLS
                }
            }
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting correlation artifact type MD5 from database.", ex); // NON-NLS
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isSupported(Node node) {
        if (!EamDb.isEnabled()) {
            return false;
        }

        // Is supported if this node has correlatable content (File, BlackboardArtifact)
        return !getArtifactsFromCorrelatableAttributes(node).isEmpty();
    }

    @Override
    @Messages({"DataContentViewerOtherCases.table.nodbconnection=Cannot connect to enterprise artifacts manager database."})
    public void setNode(Node node) {
        if (!EamDb.isEnabled()) {
            return;
        }

        populateTable(node);
    }

    /**
     * Load the correlatable data into the table model. If there is no data
     * available display the message on the status panel.
     *
     * @param node The node being viewed.
     */
    @Messages({"DataContentViewerOtherCases.table.isempty=There are no associated artifacts or files from other cases to display.",
        "DataContentViewerOtherCases.table.noArtifacts=Correlation cannot be performed on the selected file; likely missing MD5 hash."})
    private void populateTable(Node node) {
        String dataSourceName = getDataSourceNameFromNode(node);
        String deviceId = getDeviceIdFromNode(node);
        correlatedArtifacts.addAll(getArtifactsFromCorrelatableAttributes(node));
        correlatedArtifacts.forEach((eamArtifact) -> {
            // get local instances
            Collection<EamArtifactInstance> eamArtifactInstances = getCorrelatedInstances(eamArtifact, dataSourceName, deviceId);
            // get global instances
            eamArtifactInstances.addAll(getGlobalFileInstancesAsArtifactInstances(eamArtifact));

            eamArtifactInstances.forEach((eamArtifactInstance) -> {
                EamArtifact newCeArtifact = new EamArtifact(
                        eamArtifact.getArtifactType(),
                        eamArtifact.getArtifactValue()
                );
                newCeArtifact.addInstance(eamArtifactInstance);
                tableModel.addEnterpriseArtifactManagerArtifact(newCeArtifact);
            });
        });

        if (correlatedArtifacts.isEmpty()) {
            displayMessageOnTableStatusPanel(Bundle.DataContentViewerOtherCases_table_noArtifacts());
        } else if (0 == tableModel.getRowCount()) {
            displayMessageOnTableStatusPanel(Bundle.DataContentViewerOtherCases_table_isempty());
        } else {
            clearMessageOnTableStatusPanel();
            setColumnWidths();
        }
    }

    private void setColumnWidths() {
        for (int idx = 0; idx < tableModel.getColumnCount(); idx++) {
            TableColumn column = otherCasesTable.getColumnModel().getColumn(idx);
            int colWidth = tableModel.getColumnPreferredWidth(idx);
            if (0 < colWidth) {
                column.setPreferredWidth(colWidth);
            }
        }
    }

    private void displayMessageOnTableStatusPanel(String message) {
        tableStatusPanelLabel.setText(message);
        tableStatusPanelLabel.setVisible(true);
    }

    private void clearMessageOnTableStatusPanel() {
        tableStatusPanelLabel.setVisible(false);
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
        tableStatusPanel = new javax.swing.JPanel();
        tableStatusPanelLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(selectAllMenuItem, org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.selectAllMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(selectAllMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(exportToCSVMenuItem, org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.exportToCSVMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(exportToCSVMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(showCaseDetailsMenuItem, org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.showCaseDetailsMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(showCaseDetailsMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(showCommonalityMenuItem, org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.showCommonalityMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(showCommonalityMenuItem);

        otherCasesTable.setAutoCreateRowSorter(true);
        otherCasesTable.setModel(tableModel);
        otherCasesTable.setToolTipText(org.openide.util.NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.table.toolTip.text")); // NOI18N
        otherCasesTable.setComponentPopupMenu(rightClickPopupMenu);
        otherCasesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        tableScrollPane.setViewportView(otherCasesTable);

        tableStatusPanelLabel.setForeground(new java.awt.Color(255, 0, 51));

        javax.swing.GroupLayout tableStatusPanelLayout = new javax.swing.GroupLayout(tableStatusPanel);
        tableStatusPanel.setLayout(tableStatusPanelLayout);
        tableStatusPanelLayout.setHorizontalGroup(
            tableStatusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
            .addGroup(tableStatusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(tableStatusPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(tableStatusPanelLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 780, Short.MAX_VALUE)
                    .addContainerGap()))
        );
        tableStatusPanelLayout.setVerticalGroup(
            tableStatusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 16, Short.MAX_VALUE)
            .addGroup(tableStatusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(tableStatusPanelLayout.createSequentialGroup()
                    .addComponent(tableStatusPanelLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 0, Short.MAX_VALUE)))
        );

        javax.swing.GroupLayout tableContainerPanelLayout = new javax.swing.GroupLayout(tableContainerPanel);
        tableContainerPanel.setLayout(tableContainerPanelLayout);
        tableContainerPanelLayout.setHorizontalGroup(
            tableContainerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tableScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 800, Short.MAX_VALUE)
            .addComponent(tableStatusPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        tableContainerPanelLayout.setVerticalGroup(
            tableContainerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tableContainerPanelLayout.createSequentialGroup()
                .addComponent(tableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 371, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tableStatusPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout otherCasesPanelLayout = new javax.swing.GroupLayout(otherCasesPanel);
        otherCasesPanel.setLayout(otherCasesPanelLayout);
        otherCasesPanelLayout.setHorizontalGroup(
            otherCasesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 800, Short.MAX_VALUE)
            .addGroup(otherCasesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(tableContainerPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        otherCasesPanelLayout.setVerticalGroup(
            otherCasesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 404, Short.MAX_VALUE)
            .addGroup(otherCasesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(otherCasesPanelLayout.createSequentialGroup()
                    .addComponent(tableContainerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
            .addComponent(otherCasesPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JFileChooser CSVFileChooser;
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
    private javax.swing.JLabel tableStatusPanelLabel;
    // End of variables declaration//GEN-END:variables
}
