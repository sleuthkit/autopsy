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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.stream.Collectors;
import javax.swing.GroupLayout;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.DEFAULT_OPTION;
import static javax.swing.JOptionPane.PLAIN_MESSAGE;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.LayoutStyle;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.openide.awt.Mnemonics;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
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
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskDataException;

/**
 * View correlation results from other cases
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
@ServiceProvider(service = DataContentViewer.class, position = 8)
@Messages({"DataContentViewerOtherCases.title=Other Occurrences",
    "DataContentViewerOtherCases.toolTip=Displays instances of the selected file/artifact from other occurrences.",})
public class DataContentViewerOtherCases extends JPanel implements DataContentViewer {
    
    private static final long serialVersionUID = -1L;
    
    private final static Logger LOGGER = Logger.getLogger(DataContentViewerOtherCases.class.getName());

    private final DataContentViewerOtherCasesTableModel tableModel;
    private final Collection<CorrelationAttribute> correlationAttributes;
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
     * Show how common the selected correlationAttributes are with details dialog.
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
            openCase = Case.getCurrentCaseThrows();
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
    private Collection<CorrelationAttribute> getCorrelationAttributesFromNode(Node node) {
        Collection<CorrelationAttribute> ret = new ArrayList<>();

        // correlate on blackboard artifact attributes if they exist and supported
        BlackboardArtifact bbArtifact = getBlackboardArtifactFromNode(node);
        if (bbArtifact != null && EamDb.isEnabled()) {
            ret.addAll(EamArtifactUtil.getCorrelationAttributeFromBlackboardArtifact(bbArtifact, false, false));
        }

        // we can correlate based on the MD5 if it is enabled      
        if (this.file != null && EamDb.isEnabled()) {
            try {

                List<CorrelationAttribute.Type> artifactTypes = EamDb.getInstance().getDefinedCorrelationTypes();
                String md5 = this.file.getMd5Hash();
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

        } else {
            try {
                // If EamDb not enabled, get the Files default correlation type to allow Other Occurances to be enabled.   
                if(this.file != null) {
                    String md5 = this.file.getMd5Hash();
                    if(md5 != null && !md5.isEmpty()) {
                        ret.add(new CorrelationAttribute(CorrelationAttribute.getDefaultCorrelationTypes().get(0),md5));
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
     * given central repository artifact. Will not show instances from the same
     * datasource / device
     *
     * @param corAttr CorrelationAttribute to query for
     * @param dataSourceName Data source to filter results
     * @param deviceId Device Id to filter results
     *
     * @return A collection of correlated artifact instances from other cases
     */
    private Map<UniquePathKey,CorrelationAttributeInstance> getCorrelatedInstances(CorrelationAttribute corAttr, String dataSourceName, String deviceId) {
        // @@@ Check exception
        try {
            final Case openCase = Case.getCurrentCase();
            String caseUUID = openCase.getName();
            HashMap<UniquePathKey,CorrelationAttributeInstance> artifactInstances = new HashMap<>();

            if (EamDb.isEnabled()) {
                EamDb dbManager = EamDb.getInstance();
                artifactInstances.putAll(dbManager.getArtifactInstancesByTypeValue(corAttr.getCorrelationType(), corAttr.getCorrelationValue()).stream()
                        .filter(artifactInstance -> !artifactInstance.getCorrelationCase().getCaseUUID().equals(caseUUID)
                        || !artifactInstance.getCorrelationDataSource().getName().equals(dataSourceName)
                        || !artifactInstance.getCorrelationDataSource().getDeviceID().equals(deviceId))
                        .collect(Collectors.toMap(correlationAttr -> new UniquePathKey(correlationAttr.getCorrelationDataSource().getDeviceID(), correlationAttr.getFilePath()),
                                correlationAttr -> correlationAttr)));
            }

            if (corAttr.getCorrelationType().getDisplayName().equals("Files")) { 
                List<AbstractFile> caseDbFiles = addCaseDbMatches(corAttr, openCase);
                for (AbstractFile caseDbFile : caseDbFiles) {
                    addOrUpdateAttributeInstance(openCase, artifactInstances, caseDbFile);
                }
            }

            return artifactInstances;
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting artifact instances from database.", ex); // NON-NLS
        } catch (NoCurrentCaseException ex) {
            LOGGER.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        } catch (TskCoreException ex) {
            // do nothing. 
            // @@@ Review this behavior
            LOGGER.log(Level.SEVERE, "Exception while querying open case.", ex); // NON-NLS
        }

        return new HashMap<>(0);
    }

    private List<AbstractFile> addCaseDbMatches(CorrelationAttribute corAttr, Case openCase) throws NoCurrentCaseException, TskCoreException, EamDbException {
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
     * Adds the file to the artifactInstances map if it does not already exist
     * 
     * @param autopsyCase 
     * @param artifactInstances
     * @param newFile
     * @throws TskCoreException
     * @throws EamDbException 
     */
    private void addOrUpdateAttributeInstance(final Case autopsyCase, Map<UniquePathKey,CorrelationAttributeInstance> artifactInstances, AbstractFile newFile) throws TskCoreException, EamDbException {
        
        // figure out if the casedb file is known via either hash or tags
        TskData.FileKnown localKnown = newFile.getKnown();

        if (localKnown != TskData.FileKnown.BAD) {
            List<ContentTag> fileMatchTags = autopsyCase.getServices().getTagsManager().getContentTagsByContent(newFile);
            for (ContentTag tag : fileMatchTags) {
                TskData.FileKnown tagKnownStatus = tag.getName().getKnownStatus();
                if (tagKnownStatus.equals(TskData.FileKnown.BAD)) {
                    localKnown = TskData.FileKnown.BAD;
                    break;
                }
            }
        }

        // make a key to see if the file is already in the map
        String filePath = newFile.getParentPath() + newFile.getName();
        String deviceId;
        try {
            deviceId = autopsyCase.getSleuthkitCase().getDataSource(newFile.getDataSource().getId()).getDeviceId();
        } catch (TskDataException | TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Error getting data source info: " + ex);
            return;
        }
        UniquePathKey uniquePathKey = new UniquePathKey(deviceId, filePath);
        
        // double check that the CR version is BAD if the caseDB version is BAD.
        if (artifactInstances.containsKey(uniquePathKey)) {
            if (localKnown == TskData.FileKnown.BAD) {
                CorrelationAttributeInstance prevInstance = artifactInstances.get(uniquePathKey);
                prevInstance.setKnownStatus(localKnown);
            }
        }
        // add the data from the case DB by pushing data into CorrelationAttributeInstance class
        else {
            // NOTE: If we are in here, it is likely because CR is not enabled.  So, we cannot rely
            // on any of the methods that query the DB.
            CorrelationCase correlationCase = new CorrelationCase(autopsyCase.getName(), autopsyCase.getDisplayName());
            
            CorrelationDataSource correlationDataSource = CorrelationDataSource.fromTSKDataSource(correlationCase, newFile.getDataSource());
        
            CorrelationAttributeInstance caseDbInstance = new CorrelationAttributeInstance(correlationCase, correlationDataSource, filePath, "", localKnown);
            artifactInstances.put(uniquePathKey, caseDbInstance);
        }
    }

    @Override
    public boolean isSupported(Node node) {
        this.file = this.getAbstractFileFromNode(node);
        //  Is supported if this node
        //      has correlatable content (File, BlackboardArtifact) OR
        //      other common files across datasources.
        
        if(EamDb.isEnabled()){
            return this.file != null
                && this.file.getSize() > 0
                && !getCorrelationAttributesFromNode(node).isEmpty();
        } else{
            return this.file != null
                && this.file.getSize() > 0;
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
    @Messages({"DataContentViewerOtherCases.table.isempty=There are no associated artifacts or files from other occurrences to display.",
        "DataContentViewerOtherCases.table.noArtifacts=Correlation cannot be performed on the selected file."})
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
        for (CorrelationAttribute corAttr : correlationAttributes) {
            Map<UniquePathKey, CorrelationAttributeInstance> corAttrInstances = new HashMap<>(0);

            // get correlation and reference set instances from DB
            corAttrInstances.putAll(getCorrelatedInstances(corAttr, dataSourceName, deviceId));

            corAttrInstances.values().forEach((corAttrInstance) -> {
                try {
                    CorrelationAttribute newCeArtifact = new CorrelationAttribute(
                            corAttr.getCorrelationType(),
                            corAttr.getCorrelationValue()
                    );
                    newCeArtifact.addInstance(corAttrInstance);
                    tableModel.addEamArtifact(newCeArtifact);
                } catch (EamDbException ex) {
                    LOGGER.log(Level.SEVERE, "Error creating correlation attribute", ex);
                }
            });
        }

        if (correlationAttributes.isEmpty()) {
            // @@@ BC: We should have a more descriptive message than this.  Mention that the file didn't have a MD5, etc.
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

        rightClickPopupMenu = new JPopupMenu();
        selectAllMenuItem = new JMenuItem();
        exportToCSVMenuItem = new JMenuItem();
        showCaseDetailsMenuItem = new JMenuItem();
        showCommonalityMenuItem = new JMenuItem();
        CSVFileChooser = new JFileChooser();
        otherCasesPanel = new JPanel();
        tableContainerPanel = new JPanel();
        tableScrollPane = new JScrollPane();
        otherCasesTable = new JTable();
        tableStatusPanel = new JPanel();
        tableStatusPanelLabel = new JLabel();

        Mnemonics.setLocalizedText(selectAllMenuItem, NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.selectAllMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(selectAllMenuItem);

        Mnemonics.setLocalizedText(exportToCSVMenuItem, NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.exportToCSVMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(exportToCSVMenuItem);

        Mnemonics.setLocalizedText(showCaseDetailsMenuItem, NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.showCaseDetailsMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(showCaseDetailsMenuItem);

        Mnemonics.setLocalizedText(showCommonalityMenuItem, NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.showCommonalityMenuItem.text")); // NOI18N
        rightClickPopupMenu.add(showCommonalityMenuItem);

        setMinimumSize(new Dimension(1500, 10));
        setOpaque(false);
        setPreferredSize(new Dimension(1500, 44));

        otherCasesPanel.setPreferredSize(new Dimension(1500, 144));

        tableContainerPanel.setPreferredSize(new Dimension(1500, 63));

        tableScrollPane.setPreferredSize(new Dimension(1500, 30));

        otherCasesTable.setAutoCreateRowSorter(true);
        otherCasesTable.setModel(tableModel);
        otherCasesTable.setToolTipText(NbBundle.getMessage(DataContentViewerOtherCases.class, "DataContentViewerOtherCases.table.toolTip.text")); // NOI18N
        otherCasesTable.setComponentPopupMenu(rightClickPopupMenu);
        otherCasesTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        tableScrollPane.setViewportView(otherCasesTable);

        tableStatusPanel.setPreferredSize(new Dimension(1500, 16));

        tableStatusPanelLabel.setForeground(new Color(255, 0, 51));

        GroupLayout tableStatusPanelLayout = new GroupLayout(tableStatusPanel);
        tableStatusPanel.setLayout(tableStatusPanelLayout);
        tableStatusPanelLayout.setHorizontalGroup(tableStatusPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
            .addGroup(tableStatusPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(tableStatusPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(tableStatusPanelLabel, GroupLayout.DEFAULT_SIZE, 780, Short.MAX_VALUE)
                    .addContainerGap()))
        );
        tableStatusPanelLayout.setVerticalGroup(tableStatusPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 16, Short.MAX_VALUE)
            .addGroup(tableStatusPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(tableStatusPanelLayout.createSequentialGroup()
                    .addComponent(tableStatusPanelLabel, GroupLayout.PREFERRED_SIZE, 16, GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 0, Short.MAX_VALUE)))
        );

        GroupLayout tableContainerPanelLayout = new GroupLayout(tableContainerPanel);
        tableContainerPanel.setLayout(tableContainerPanelLayout);
        tableContainerPanelLayout.setHorizontalGroup(tableContainerPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(tableScrollPane, GroupLayout.Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(tableStatusPanel, GroupLayout.Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        tableContainerPanelLayout.setVerticalGroup(tableContainerPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(tableContainerPanelLayout.createSequentialGroup()
                .addComponent(tableScrollPane, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tableStatusPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        GroupLayout otherCasesPanelLayout = new GroupLayout(otherCasesPanel);
        otherCasesPanel.setLayout(otherCasesPanelLayout);
        otherCasesPanelLayout.setHorizontalGroup(otherCasesPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 1500, Short.MAX_VALUE)
            .addGroup(otherCasesPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(tableContainerPanel, GroupLayout.Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        otherCasesPanelLayout.setVerticalGroup(otherCasesPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 60, Short.MAX_VALUE)
            .addGroup(otherCasesPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(otherCasesPanelLayout.createSequentialGroup()
                    .addComponent(tableContainerPanel, GroupLayout.DEFAULT_SIZE, 60, Short.MAX_VALUE)
                    .addGap(0, 0, 0)))
        );

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(otherCasesPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(otherCasesPanel, GroupLayout.DEFAULT_SIZE, 60, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JFileChooser CSVFileChooser;
    private JMenuItem exportToCSVMenuItem;
    private JPanel otherCasesPanel;
    private JTable otherCasesTable;
    private JPopupMenu rightClickPopupMenu;
    private JMenuItem selectAllMenuItem;
    private JMenuItem showCaseDetailsMenuItem;
    private JMenuItem showCommonalityMenuItem;
    private JPanel tableContainerPanel;
    private JScrollPane tableScrollPane;
    private JPanel tableStatusPanel;
    private JLabel tableStatusPanelLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * Used as a key to ensure we eliminate duplicates from the result set by not overwriting CR correlation instances.
     */
    static final class UniquePathKey {

        private final String dataSourceID;
        private final String filePath;

        UniquePathKey(String theDataSource, String theFilePath) {
            super();
            dataSourceID = theDataSource;
            filePath = theFilePath.toLowerCase();
        }

        /**
         *
         * @return the dataSourceID device ID
         */
        String getDataSourceID() {
            return dataSourceID;
        }

        /**
         *
         * @return the filPath including the filename and extension.
         */
        String getFilePath() {
            return filePath;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof UniquePathKey) {
                return ((UniquePathKey) other).getDataSourceID().equals(dataSourceID) && ((UniquePathKey) other).getFilePath().equals(filePath);
            }
            return false;
        }

        @Override
        public int hashCode() {
            //int hash = 7;
            //hash = 67 * hash + this.dataSourceID.hashCode();
            //hash = 67 * hash + this.filePath.hashCode();
            return Objects.hash(dataSourceID, filePath);
        }
    }

}
