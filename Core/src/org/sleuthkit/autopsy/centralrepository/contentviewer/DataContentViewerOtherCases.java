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
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamGlobalFileInstance;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;

/**
 * View correlation results from other cases
 */
@ServiceProvider(service = DataContentViewer.class, position = 8)
@Messages({"DataContentViewerOtherCases.title=Other Occurrences",
    "DataContentViewerOtherCases.toolTip=Displays instances of the selected file/artifact from other occurrences.",})
public class DataContentViewerOtherCases extends javax.swing.JPanel implements DataContentViewer {

    private final static Logger LOGGER = Logger.getLogger(DataContentViewerOtherCases.class.getName());

    private final DataContentViewerOtherCasesTableModel tableModel;
    private final Collection<CorrelationAttribute> correlationAttributes;

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
        tableStatusPanelLabel.setVisible(false);
    }

    @Messages({"DataContentViewerOtherCases.correlatedArtifacts.isEmpty=There are no files or artifacts to correlate.",
        "# {0} - commonality percentage",
        "# {1} - correlation type",
        "# {2} - correlation value",
        "DataContentViewerOtherCases.correlatedArtifacts.byType={0}% of data sources have {2} (type: {1})\n",
        "DataContentViewerOtherCases.correlatedArtifacts.title=Attribute Frequency",
        "DataContentViewerOtherCases.correlatedArtifacts.failed=Failed to get frequency details."})
    /**
     * Show how common the selected 
     */
    private void showCommonalityDetails() {
        if (correlationAttributes.isEmpty()) {
            JOptionPane.showConfirmDialog(showCommonalityMenuItem,
                    Bundle.DataContentViewerOtherCases_correlatedArtifacts_isEmpty(),
                    Bundle.DataContentViewerOtherCases_correlatedArtifacts_title(),
                    DEFAULT_OPTION, PLAIN_MESSAGE);
        } else {
            StringBuilder msg = new StringBuilder();
            int percentage;
            try {
                EamDb dbManager = EamDb.getInstance();
                for (CorrelationAttribute eamArtifact : correlationAttributes) {
                    percentage = dbManager.getFrequencyPercentage(eamArtifact);
                    msg.append(Bundle.DataContentViewerOtherCases_correlatedArtifacts_byType(percentage,
                            eamArtifact.getCorrelationType().getDisplayName(),
                            eamArtifact.getCorrelationValue()));
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
        Case openCase;
        try {
            openCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            JOptionPane.showConfirmDialog(showCaseDetailsMenuItem,
                            Bundle.DataContentViewerOtherCases_noOpenCase_errMsg(),
                            Bundle.DataContentViewerOtherCases_noOpenCase_errMsg(),
                            DEFAULT_OPTION, PLAIN_MESSAGE);
            return;
        }
        String caseDisplayName = Bundle.DataContentViewerOtherCases_caseDetailsDialog_noCaseNameError();
        try {
            if (-1 != selectedRowViewIdx) {
                EamDb dbManager = EamDb.getInstance();
                int selectedRowModelIdx = otherCasesTable.convertRowIndexToModel(selectedRowViewIdx);
                CorrelationAttribute eamArtifact = (CorrelationAttribute) tableModel.getRow(selectedRowModelIdx);
                CorrelationCase eamCasePartial = eamArtifact.getInstances().get(0).getCorrelationCase();
                if (eamCasePartial == null) {
                    JOptionPane.showConfirmDialog(showCaseDetailsMenuItem,
                            Bundle.DataContentViewerOtherCases_caseDetailsDialog_noDetailsReference(),
                            caseDisplayName,
                            DEFAULT_OPTION, PLAIN_MESSAGE);
                    return;
                }
                caseDisplayName = eamCasePartial.getDisplayName();
                // query case details
                CorrelationCase eamCase = dbManager.getCase(openCase);
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

    private void saveToCSV() throws NoCurrentCaseException {
        if (0 != otherCasesTable.getSelectedRowCount()) {
            Calendar now = Calendar.getInstance();
            String fileName = String.format("%1$tY%1$tm%1$te%1$tI%1$tM%1$tS_other_data_sources.csv", now);
            CSVFileChooser.setCurrentDirectory(new File(Case.getOpenCase().getExportDirectory()));
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
     *
     * @param node The node to correlate
     *
     * @return A list of attributes that can be used for correlation 
     */
    private Collection<CorrelationAttribute> getCorrelationAttributesFromNode(Node node) {
        Collection<CorrelationAttribute> ret = new ArrayList<>();

        // correlate on blackboard artifact attributes if they exist and supported
        BlackboardArtifact bbArtifact = getBlackboardArtifactFromNode(node);   
        if (bbArtifact != null) {
            ret.addAll(EamArtifactUtil.getCorrelationAttributeFromBlackboardArtifact(bbArtifact, false, false));
        }
        
        // we can correlate based on the MD5 if it is enabled
        AbstractFile abstractFile = getAbstractFileFromNode(node);
        if (abstractFile != null) {
            try {
                List<CorrelationAttribute.Type> artifactTypes = EamDb.getInstance().getDefinedCorrelationTypes();
                String md5 = abstractFile.getMd5Hash();
                if (md5 != null && !md5.isEmpty() && null != artifactTypes && !artifactTypes.isEmpty()) {
                    for (CorrelationAttribute.Type aType : artifactTypes) {
                        if (aType.getId() == CorrelationAttribute.FILES_TYPE_ID) {
                            ret.add(new CorrelationAttribute(aType, md5));
                            break;
                        }
                    }
                }
            } catch (EamDbException ex) {
                LOGGER.log(Level.SEVERE, "Error connecting to DB", ex); // NON-NLS
            }
        }

        return ret;
    }



    /**
     * Query the db for artifact instances from other cases correlated to the
     * given central repository artifact.  Will not show instances from the same datasource / device
     *
     * @param corAttr CorrelationAttribute to query for
     * @param dataSourceName Data source to filter results
     * @param deviceId Device Id to filter results 
     *
     * @return A collection of correlated artifact instances from other cases
     */
    private Collection<CorrelationAttributeInstance> getCorrelatedInstances(CorrelationAttribute corAttr, String dataSourceName, String deviceId) {
        // @@@ Check exception
        try {
            String caseUUID = Case.getOpenCase().getName();
            EamDb dbManager = EamDb.getInstance();
            Collection<CorrelationAttributeInstance> artifactInstances = dbManager.getArtifactInstancesByTypeValue(corAttr.getCorrelationType(), corAttr.getCorrelationValue()).stream()
                    .filter(artifactInstance -> !artifactInstance.getCorrelationCase().getCaseUUID().equals(caseUUID)
                    || !artifactInstance.getCorrelationDataSource().getName().equals(dataSourceName)
                    || !artifactInstance.getCorrelationDataSource().getDeviceID().equals(deviceId))
                    .collect(Collectors.toList());
            return artifactInstances;
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting artifact instances from database.", ex); // NON-NLS
        } catch (NoCurrentCaseException ex) {
            LOGGER.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        }

        return Collections.emptyList();
    }

    @Override
    public boolean isSupported(Node node) {
        if (!EamDb.isEnabled()) {
            return false;
        }

        // Is supported if this node has correlatable content (File, BlackboardArtifact)
        return !getCorrelationAttributesFromNode(node).isEmpty();
    }

    @Override
    @Messages({"DataContentViewerOtherCases.table.nodbconnection=Cannot connect to central repository database."})
    public void setNode(Node node) {
        if (!EamDb.isEnabled()) {
            return;
        }

        reset(); // reset the table to empty.
        if (node == null) {
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
    @Messages({"DataContentViewerOtherCases.table.isempty=There are no associated artifacts or files from other occurrences to display.",
        "DataContentViewerOtherCases.table.noArtifacts=Correlation cannot be performed on the selected file."})
    private void populateTable(Node node) {
        AbstractFile af = getAbstractFileFromNode(node);
        String dataSourceName = "";
        String deviceId = "";
        try {
            if (af != null) {
                Content dataSource = af.getDataSource();
                dataSourceName = dataSource.getName();
                deviceId = Case.getOpenCase().getSleuthkitCase().getDataSource(dataSource.getId()).getDeviceId();
            }
        } catch (TskException | NoCurrentCaseException ex) {
            // do nothing. 
            // @@@ Review this behavior
        }
        
        // get the attributes we can correlate on
        correlationAttributes.addAll(getCorrelationAttributesFromNode(node));
        for (CorrelationAttribute corAttr : correlationAttributes) {
            Collection<CorrelationAttributeInstance> corAttrInstances = new ArrayList<>();
            
            // get correlation and reference set instances from DB
            corAttrInstances.addAll(getCorrelatedInstances(corAttr, dataSourceName, deviceId));

            corAttrInstances.forEach((corAttrInstance) -> {
                try {
                    CorrelationAttribute newCeArtifact = new CorrelationAttribute(
                            corAttr.getCorrelationType(),
                            corAttr.getCorrelationValue()
                    );
                    newCeArtifact.addInstance(corAttrInstance);
                    tableModel.addEamArtifact(newCeArtifact);
                } catch (EamDbException ex){
                    LOGGER.log(Level.SEVERE, "Error creating correlation attribute", ex);
                }
            });
        }

        if (correlationAttributes.isEmpty()) {
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

        tableStatusPanel.setPreferredSize(new java.awt.Dimension(1500, 16));

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
            .addComponent(tableScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(tableStatusPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        tableContainerPanelLayout.setVerticalGroup(
            tableContainerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tableContainerPanelLayout.createSequentialGroup()
                .addComponent(tableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tableStatusPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
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
            .addGap(0, 60, Short.MAX_VALUE)
            .addGroup(otherCasesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(otherCasesPanelLayout.createSequentialGroup()
                    .addComponent(tableContainerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 60, Short.MAX_VALUE)
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
            .addComponent(otherCasesPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 60, Short.MAX_VALUE)
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
