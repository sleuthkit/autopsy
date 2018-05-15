/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.ComboBoxModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.openide.explorer.ExplorerManager;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel used for common files search configuration and configuration business
 * logic. Nested within CommonFilesDialog.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class CommonFilesPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private static final Long NO_DATA_SOURCE_SELECTED = -1L;

    private ComboBoxModel<String> dataSourcesList = new DataSourceComboBoxModel();
    private Map<Long, String> dataSourceMap;

    private static final Logger LOGGER = Logger.getLogger(CommonFilesPanel.class.getName());
    private boolean singleDataSource = false;
    private String selectedDataSource = "";
    private boolean pictureViewCheckboxState;
    private boolean documentsCheckboxState;

    /**
     * Creates new form CommonFilesPanel
     */
    @NbBundle.Messages({
        "CommonFilesPanel.title=Common Files Panel",
        "CommonFilesPanel.exception=Unexpected Exception loading DataSources."})
    public CommonFilesPanel() {
        initComponents();

        this.setupDataSources();
        
        this.errorText.setVisible(false);
    }

    /**
     * Sets up the data sources dropdown and returns the data sources map for
     * future usage.
     *
     * @return a mapping of data source ids to data source names
     */
    @NbBundle.Messages({
        "CommonFilesPanel.buildDataSourceMap.done.tskCoreException=Unable to run query against DB.",
        "CommonFilesPanel.buildDataSourceMap.done.noCurrentCaseException=Unable to open case file.",
        "CommonFilesPanel.buildDataSourceMap.done.exception=Unexpected exception building data sources map.",
        "CommonFilesPanel.buildDataSourceMap.done.interupted=Something went wrong building the Common Files Search dialog box.",
        "CommonFilesPanel.buildDataSourceMap.done.sqlException=Unable to query db for data sources.",
        "CommonFilesPanel.buildDataSourcesMap.updateUi.noDataSources=No data sources were found."})
    private void setupDataSources() {

        new SwingWorker<Map<Long, String>, Void>() {

            private static final String SELECT_DATA_SOURCES_LOGICAL = "select obj_id, name from tsk_files where obj_id in (SELECT obj_id FROM tsk_objects WHERE obj_id in (select obj_id from data_source_info))";

            private static final String SELECT_DATA_SOURCES_IMAGE = "select obj_id, name from tsk_image_names where obj_id in (SELECT obj_id FROM tsk_objects WHERE obj_id in (select obj_id from data_source_info))";

            private void updateUi() {

                String[] dataSourcesNames = new String[CommonFilesPanel.this.dataSourceMap.size()];

                //only enable all this stuff if we actually have datasources
                if (dataSourcesNames.length > 0) {
                    dataSourcesNames = CommonFilesPanel.this.dataSourceMap.values().toArray(dataSourcesNames);
                    CommonFilesPanel.this.dataSourcesList = new DataSourceComboBoxModel(dataSourcesNames);
                    CommonFilesPanel.this.selectDataSourceComboBox.setModel(CommonFilesPanel.this.dataSourcesList);

                    boolean multipleDataSources = this.caseHasMultipleSources();
                    CommonFilesPanel.this.allDataSourcesRadioButton.setEnabled(multipleDataSources);
                    CommonFilesPanel.this.allDataSourcesRadioButton.setSelected(multipleDataSources);

                    if (!multipleDataSources) {
                        CommonFilesPanel.this.withinDataSourceRadioButton.setSelected(true);
                        withinDataSourceSelected(true);
                    }

                    CommonFilesPanel.this.searchButton.setEnabled(true);
                } else {
                    MessageNotifyUtil.Message.info(Bundle.CommonFilesPanel_buildDataSourcesMap_updateUi_noDataSources());
                    CommonFilesPanel.this.cancelButtonActionPerformed(null);
                }
            }

            private boolean caseHasMultipleSources() {
                return CommonFilesPanel.this.dataSourceMap.size() >= 2;
            }

            private void loadLogicalSources(SleuthkitCase tskDb, Map<Long, String> dataSouceMap) throws TskCoreException, SQLException {
                //try block releases resources - exceptions are handled in done()
                try (
                        CaseDbQuery query = tskDb.executeQuery(SELECT_DATA_SOURCES_LOGICAL);
                        ResultSet resultSet = query.getResultSet()) {
                    while (resultSet.next()) {
                        Long objectId = resultSet.getLong(1);
                        String dataSourceName = resultSet.getString(2);
                        dataSouceMap.put(objectId, dataSourceName);
                    }
                }
            }

            private void loadImageSources(SleuthkitCase tskDb, Map<Long, String> dataSouceMap) throws SQLException, TskCoreException {
                //try block releases resources - exceptions are handled in done()
                try (
                        CaseDbQuery query = tskDb.executeQuery(SELECT_DATA_SOURCES_IMAGE);
                        ResultSet resultSet = query.getResultSet()) {
                    
                    while (resultSet.next()) {
                        Long objectId = resultSet.getLong(1);
                        String dataSourceName = resultSet.getString(2);
                        File image = new File(dataSourceName);
                        String dataSourceNameTrimmed = image.getName();
                        dataSouceMap.put(objectId, dataSourceNameTrimmed);
                    }
                }
            }

            @Override
            protected Map<Long, String> doInBackground() throws NoCurrentCaseException, TskCoreException, SQLException {

                Map<Long, String> dataSouceMap = new HashMap<>();

                Case currentCase = Case.getCurrentCaseThrows();
                SleuthkitCase tskDb = currentCase.getSleuthkitCase();

                loadLogicalSources(tskDb, dataSouceMap);

                loadImageSources(tskDb, dataSouceMap);

                return dataSouceMap;
            }

            @Override
            protected void done() {

                try {
                    CommonFilesPanel.this.dataSourceMap = this.get();

                    updateUi();

                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, "Interrupted while building Common Files Search dialog.", ex);
                    MessageNotifyUtil.Message.error(Bundle.CommonFilesPanel_buildDataSourceMap_done_interupted());
                } catch (ExecutionException ex) {
                    String errorMessage;
                    Throwable inner = ex.getCause();
                    if (inner instanceof TskCoreException) {
                        LOGGER.log(Level.SEVERE, "Failed to load data sources from database.", ex);
                        errorMessage = Bundle.CommonFilesPanel_buildDataSourceMap_done_tskCoreException();
                    } else if (inner instanceof NoCurrentCaseException) {
                        LOGGER.log(Level.SEVERE, "Current case has been closed.", ex);
                        errorMessage = Bundle.CommonFilesPanel_buildDataSourceMap_done_noCurrentCaseException();
                    } else if (inner instanceof SQLException) {
                        LOGGER.log(Level.SEVERE, "Unable to query db for data sources.", ex);
                        errorMessage = Bundle.CommonFilesPanel_buildDataSourceMap_done_sqlException();
                    } else {
                        LOGGER.log(Level.SEVERE, "Unexpected exception while building Common Files Search dialog panel.", ex);
                        errorMessage = Bundle.CommonFilesPanel_buildDataSourceMap_done_exception();
                    }
                    MessageNotifyUtil.Message.error(errorMessage);
                }
            }
        }.execute();
    }

    @NbBundle.Messages({
        "CommonFilesPanel.search.results.titleAll=Common Files (All Data Sources)",
        "CommonFilesPanel.search.results.titleSingle=Common Files (Match Within Data Source: %s)",
        "CommonFilesPanel.search.results.pathText=Common Files Search Results",
        "CommonFilesPanel.search.done.tskCoreException=Unable to run query against DB.",
        "CommonFilesPanel.search.done.noCurrentCaseException=Unable to open case file.",
        "CommonFilesPanel.search.done.exception=Unexpected exception running Common Files Search.",
        "CommonFilesPanel.search.done.interupted=Something went wrong finding common files.",
        "CommonFilesPanel.search.done.sqlException=Unable to query db for files or data sources."})
    private void search() {
        String pathText = Bundle.CommonFilesPanel_search_results_pathText();

        new SwingWorker<CommonFilesMetadata, Void>() {

            private String tabTitle;

            private void setTitleForAllDataSources() {
                this.tabTitle = Bundle.CommonFilesPanel_search_results_titleAll();
            }

            private void setTitleForSingleSource(Long dataSourceId) {
                final String CommonFilesPanel_search_results_titleSingle = Bundle.CommonFilesPanel_search_results_titleSingle();
                final Object[] dataSourceName = new Object[]{dataSourceMap.get(dataSourceId)};

                this.tabTitle = String.format(CommonFilesPanel_search_results_titleSingle, dataSourceName);
            }

            private Long determineDataSourceId() {
                Long selectedObjId = CommonFilesPanel.NO_DATA_SOURCE_SELECTED;
                if (CommonFilesPanel.this.singleDataSource) {
                    for (Entry<Long, String> dataSource : CommonFilesPanel.this.dataSourceMap.entrySet()) {
                        if (dataSource.getValue().equals(CommonFilesPanel.this.selectedDataSource)) {
                            selectedObjId = dataSource.getKey();
                            break;
                        }
                    }
                }
                return selectedObjId;
            }

            @Override
            @SuppressWarnings({"BoxedValueEquality", "NumberEquality"})
            protected CommonFilesMetadata doInBackground() throws TskCoreException, NoCurrentCaseException, SQLException {
                Long dataSourceId = determineDataSourceId();

                CommonFilesMetadataBuilder builder;
                boolean filterByMedia = false;
                boolean filterByDocuments = false;
                if (selectedFileCategoriesButton.isSelected()) {
                    if (pictureVideoCheckbox.isSelected()) {
                        filterByMedia = true;
                    }
                    if (documentsCheckbox.isSelected()) {
                        filterByDocuments = true;
                    }
                }
                if (dataSourceId == CommonFilesPanel.NO_DATA_SOURCE_SELECTED) {
                    builder = new AllDataSourcesCommonFilesAlgorithm(CommonFilesPanel.this.dataSourceMap, filterByMedia, filterByDocuments);

                    setTitleForAllDataSources();
                } else {
                    builder = new SingleDataSource(dataSourceId, CommonFilesPanel.this.dataSourceMap, filterByMedia, filterByDocuments);

                    setTitleForSingleSource(dataSourceId);
                }// else if(false) { 
                    // TODO, is CR cases, add option chosen CorrelationCase ID lookup
                 //   builder = new AllDataSourcesEamDbCommonFilesAlgorithm(CommonFilesPanel.this.dataSourceMap, filterByMedia, filterByDocuments);    
                //}

                this.tabTitle = builder.buildTabTitle();

                CommonFilesMetadata metadata = builder.findCommonFiles();

                return metadata;
            }

            @Override
            protected void done() {
                try {
                    super.done();

                    CommonFilesMetadata metadata = get();

                    CommonFilesNode commonFilesNode = new CommonFilesNode(metadata);

                    DataResultFilterNode dataResultFilterNode = new DataResultFilterNode(commonFilesNode, ExplorerManager.find(CommonFilesPanel.this));

                    TableFilterNode tableFilterWithDescendantsNode = new TableFilterNode(dataResultFilterNode);

                    DataResultViewerTable table = new DataResultViewerTable();
                    
                    Collection<DataResultViewer> viewers = new ArrayList<>(1);
                    viewers.add(table);
                                        
                    DataResultTopComponent.createInstance(tabTitle, pathText, tableFilterWithDescendantsNode, metadata.size(), viewers);

                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, "Interrupted while loading Common Files", ex);
                    MessageNotifyUtil.Message.error(Bundle.CommonFilesPanel_search_done_interupted());
                } catch (ExecutionException ex) {
                    String errorMessage;
                    Throwable inner = ex.getCause();
                    if (inner instanceof TskCoreException) {
                        LOGGER.log(Level.SEVERE, "Failed to load files from database.", ex);
                        errorMessage = Bundle.CommonFilesPanel_search_done_tskCoreException();
                    } else if (inner instanceof NoCurrentCaseException) {
                        LOGGER.log(Level.SEVERE, "Current case has been closed.", ex);
                        errorMessage = Bundle.CommonFilesPanel_search_done_noCurrentCaseException();
                    } else if (inner instanceof SQLException) {
                        LOGGER.log(Level.SEVERE, "Unable to query db for files.", ex);
                        errorMessage = Bundle.CommonFilesPanel_search_done_sqlException();
                    } else {
                        LOGGER.log(Level.SEVERE, "Unexpected exception while running Common Files Search.", ex);
                        errorMessage = Bundle.CommonFilesPanel_search_done_exception();
                    }
                    MessageNotifyUtil.Message.error(errorMessage);
                }
            }
        }.execute();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        dataSourcesButtonGroup = new javax.swing.ButtonGroup();
        fileTypeFilterButtonGroup = new javax.swing.ButtonGroup();
        interIntraButtonGroup = new javax.swing.ButtonGroup();
        caseSelectionButtonGroup = new javax.swing.ButtonGroup();
        searchButton = new javax.swing.JButton();
        allDataSourcesRadioButton = new javax.swing.JRadioButton();
        withinDataSourceRadioButton = new javax.swing.JRadioButton();
        selectDataSourceComboBox = new javax.swing.JComboBox<>();
        cancelButton = new javax.swing.JButton();
        allFileCategoriesRadioButton = new javax.swing.JRadioButton();
        selectedFileCategoriesButton = new javax.swing.JRadioButton();
        pictureVideoCheckbox = new javax.swing.JCheckBox();
        documentsCheckbox = new javax.swing.JCheckBox();
        categoriesLabel = new javax.swing.JLabel();
        errorText = new javax.swing.JLabel();
        commonFilesSearchLabel1 = new javax.swing.JLabel();
        intraCaseRadio = new javax.swing.JRadioButton();
        interCaseRadio = new javax.swing.JRadioButton();
        anCentralRepoCaseRadio = new javax.swing.JRadioButton();
        specificCentralRepoCaseRadio = new javax.swing.JRadioButton();
        caseComboBox = new javax.swing.JComboBox<>();

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.searchButton.text")); // NOI18N
        searchButton.setEnabled(false);
        searchButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        dataSourcesButtonGroup.add(allDataSourcesRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(allDataSourcesRadioButton, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.allDataSourcesRadioButton.text")); // NOI18N
        allDataSourcesRadioButton.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        allDataSourcesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allDataSourcesRadioButtonActionPerformed(evt);
            }
        });

        dataSourcesButtonGroup.add(withinDataSourceRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(withinDataSourceRadioButton, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.withinDataSourceRadioButton.text")); // NOI18N
        withinDataSourceRadioButton.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        withinDataSourceRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                withinDataSourceRadioButtonActionPerformed(evt);
            }
        });

        selectDataSourceComboBox.setModel(dataSourcesList);
        selectDataSourceComboBox.setEnabled(false);
        selectDataSourceComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectDataSourceComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.cancelButton.text")); // NOI18N
        cancelButton.setActionCommand(org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.cancelButton.actionCommand")); // NOI18N
        cancelButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        fileTypeFilterButtonGroup.add(allFileCategoriesRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(allFileCategoriesRadioButton, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.allFileCategoriesRadioButton.text")); // NOI18N
        allFileCategoriesRadioButton.setToolTipText(org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.allFileCategoriesRadioButton.toolTipText")); // NOI18N
        allFileCategoriesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allFileCategoriesRadioButtonActionPerformed(evt);
            }
        });

        fileTypeFilterButtonGroup.add(selectedFileCategoriesButton);
        selectedFileCategoriesButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(selectedFileCategoriesButton, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.selectedFileCategoriesButton.text")); // NOI18N
        selectedFileCategoriesButton.setToolTipText(org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.selectedFileCategoriesButton.toolTipText")); // NOI18N
        selectedFileCategoriesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectedFileCategoriesButtonActionPerformed(evt);
            }
        });

        pictureVideoCheckbox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(pictureVideoCheckbox, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.pictureVideoCheckbox.text")); // NOI18N
        pictureVideoCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pictureVideoCheckboxActionPerformed(evt);
            }
        });

        documentsCheckbox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(documentsCheckbox, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.documentsCheckbox.text")); // NOI18N
        documentsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                documentsCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(categoriesLabel, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.categoriesLabel.text")); // NOI18N
        categoriesLabel.setName(""); // NOI18N

        errorText.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorText, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.errorText.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(commonFilesSearchLabel1, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.commonFilesSearchLabel1.text")); // NOI18N
        commonFilesSearchLabel1.setFocusable(false);

        interIntraButtonGroup.add(intraCaseRadio);
        intraCaseRadio.setSelected(true);
        intraCaseRadio.setLabel(org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.intraCaseRadio.label")); // NOI18N
        intraCaseRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                intraCaseRadioActionPerformed(evt);
            }
        });

        interIntraButtonGroup.add(interCaseRadio);
        org.openide.awt.Mnemonics.setLocalizedText(interCaseRadio, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.jRadioButton2.text")); // NOI18N
        interCaseRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                interCaseRadioActionPerformed(evt);
            }
        });

        caseSelectionButtonGroup.add(anCentralRepoCaseRadio);
        anCentralRepoCaseRadio.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(anCentralRepoCaseRadio, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.anCentralRepoCaseRadio.text_1")); // NOI18N
        anCentralRepoCaseRadio.setEnabled(false);

        caseSelectionButtonGroup.add(specificCentralRepoCaseRadio);
        org.openide.awt.Mnemonics.setLocalizedText(specificCentralRepoCaseRadio, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.specificCentralRepoCaseRadio.text_1")); // NOI18N
        specificCentralRepoCaseRadio.setEnabled(false);

        caseComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        caseComboBox.setEnabled(false);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(commonFilesSearchLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(categoriesLabel)
                                            .addGroup(layout.createSequentialGroup()
                                                .addGap(6, 6, 6)
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                    .addComponent(allFileCategoriesRadioButton)
                                                    .addComponent(selectedFileCategoriesButton)
                                                    .addGroup(layout.createSequentialGroup()
                                                        .addGap(21, 21, 21)
                                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                            .addComponent(pictureVideoCheckbox)
                                                            .addComponent(documentsCheckbox))))))
                                        .addGap(277, 277, 277))
                                    .addComponent(intraCaseRadio)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGap(21, 21, 21)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(withinDataSourceRadioButton)
                                            .addComponent(allDataSourcesRadioButton)))))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(47, 47, 47)
                                .addComponent(selectDataSourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 261, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(interCaseRadio)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGap(21, 21, 21)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(anCentralRepoCaseRadio)
                                            .addComponent(specificCentralRepoCaseRadio)
                                            .addGroup(layout.createSequentialGroup()
                                                .addGap(21, 21, 21)
                                                .addComponent(caseComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 261, javax.swing.GroupLayout.PREFERRED_SIZE)))))))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(errorText)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(searchButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(commonFilesSearchLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(intraCaseRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(allDataSourcesRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(withinDataSourceRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectDataSourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(interCaseRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(anCentralRepoCaseRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(specificCentralRepoCaseRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(caseComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(categoriesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectedFileCategoriesButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pictureVideoCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(documentsCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(allFileCategoriesRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(searchButton)
                    .addComponent(errorText))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        search();
        SwingUtilities.windowForComponent(this).dispose();
    }//GEN-LAST:event_searchButtonActionPerformed

    private void allDataSourcesRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allDataSourcesRadioButtonActionPerformed
        selectDataSourceComboBox.setEnabled(!allDataSourcesRadioButton.isSelected());
        singleDataSource = false;
    }//GEN-LAST:event_allDataSourcesRadioButtonActionPerformed

    private void selectDataSourceComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectDataSourceComboBoxActionPerformed
        final Object selectedItem = selectDataSourceComboBox.getSelectedItem();
        if (selectedItem != null) {
            selectedDataSource = selectedItem.toString();
        } else {
            selectedDataSource = "";
        }
    }//GEN-LAST:event_selectDataSourceComboBoxActionPerformed

    private void withinDataSourceRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_withinDataSourceRadioButtonActionPerformed
        withinDataSourceSelected(withinDataSourceRadioButton.isSelected());
    }//GEN-LAST:event_withinDataSourceRadioButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        SwingUtilities.windowForComponent(this).dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void allFileCategoriesRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allFileCategoriesRadioButtonActionPerformed
        this.manageCheckBoxState();
        this.toggleErrorTextAndSearchBox();
    }//GEN-LAST:event_allFileCategoriesRadioButtonActionPerformed

    private void selectedFileCategoriesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectedFileCategoriesButtonActionPerformed
        this.manageCheckBoxState();
    }//GEN-LAST:event_selectedFileCategoriesButtonActionPerformed

    private void pictureVideoCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pictureVideoCheckboxActionPerformed
        this.toggleErrorTextAndSearchBox();
    }//GEN-LAST:event_pictureVideoCheckboxActionPerformed

    private void documentsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_documentsCheckboxActionPerformed
        this.toggleErrorTextAndSearchBox();
    }//GEN-LAST:event_documentsCheckboxActionPerformed

    private void intraCaseRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_intraCaseRadioActionPerformed
        this.allDataSourcesRadioButton.setEnabled(true);
        this.withinDataSourceRadioButton.setEnabled(true);
        this.selectDataSourceComboBox.setEnabled(true);
        
        this.anCentralRepoCaseRadio.setEnabled(false);
        this.specificCentralRepoCaseRadio.setEnabled(false);
        this.caseComboBox.setEnabled(false);
    }//GEN-LAST:event_intraCaseRadioActionPerformed

    private void interCaseRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interCaseRadioActionPerformed
        this.anCentralRepoCaseRadio.setEnabled(true);
        this.specificCentralRepoCaseRadio.setEnabled(true);
        this.caseComboBox.setEnabled(true);
        
        this.allDataSourcesRadioButton.setEnabled(false);
        this.withinDataSourceRadioButton.setEnabled(false);
        this.selectDataSourceComboBox.setEnabled(false);
        
    }//GEN-LAST:event_interCaseRadioActionPerformed

    private void toggleErrorTextAndSearchBox() {
        if (!this.pictureVideoCheckbox.isSelected() && !this.documentsCheckbox.isSelected() && !this.allFileCategoriesRadioButton.isSelected()) {
            this.searchButton.setEnabled(false);
            this.errorText.setVisible(true);
        } else {
            this.searchButton.setEnabled(true);
            this.errorText.setVisible(false);
        }
    }

    private void withinDataSourceSelected(boolean selected) {
        selectDataSourceComboBox.setEnabled(selected);
        if (selectDataSourceComboBox.isEnabled()) {
            selectDataSourceComboBox.setSelectedIndex(0);
            singleDataSource = true;
        }
    }

    private void manageCheckBoxState() {

        if (this.allFileCategoriesRadioButton.isSelected()) {

            this.pictureViewCheckboxState = this.pictureVideoCheckbox.isSelected();
            this.documentsCheckboxState = this.documentsCheckbox.isSelected();

            this.pictureVideoCheckbox.setEnabled(false);
            this.documentsCheckbox.setEnabled(false);
        }

        if (this.selectedFileCategoriesButton.isSelected()) {

            this.pictureVideoCheckbox.setSelected(this.pictureViewCheckboxState);
            this.documentsCheckbox.setSelected(this.documentsCheckboxState);

            this.pictureVideoCheckbox.setEnabled(true);
            this.documentsCheckbox.setEnabled(true);
                        
            this.toggleErrorTextAndSearchBox();
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allDataSourcesRadioButton;
    private javax.swing.JRadioButton allFileCategoriesRadioButton;
    private javax.swing.JRadioButton anCentralRepoCaseRadio;
    private javax.swing.JButton cancelButton;
    private javax.swing.JComboBox<String> caseComboBox;
    private javax.swing.ButtonGroup caseSelectionButtonGroup;
    private javax.swing.JLabel categoriesLabel;
    private javax.swing.JLabel commonFilesSearchLabel1;
    private javax.swing.ButtonGroup dataSourcesButtonGroup;
    private javax.swing.JCheckBox documentsCheckbox;
    private javax.swing.JLabel errorText;
    private javax.swing.ButtonGroup fileTypeFilterButtonGroup;
    private javax.swing.JRadioButton interCaseRadio;
    private javax.swing.ButtonGroup interIntraButtonGroup;
    private javax.swing.JRadioButton intraCaseRadio;
    private javax.swing.JCheckBox pictureVideoCheckbox;
    private javax.swing.JButton searchButton;
    private javax.swing.JComboBox<String> selectDataSourceComboBox;
    private javax.swing.JRadioButton selectedFileCategoriesButton;
    private javax.swing.JRadioButton specificCentralRepoCaseRadio;
    private javax.swing.JRadioButton withinDataSourceRadioButton;
    // End of variables declaration//GEN-END:variables
}
