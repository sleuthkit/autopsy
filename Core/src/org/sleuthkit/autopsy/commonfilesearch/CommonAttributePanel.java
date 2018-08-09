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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.explorer.ExplorerManager;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel used for common files search configuration and configuration business
 * logic. Nested within CommonFilesDialog.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class CommonAttributePanel extends javax.swing.JDialog  {

    private static final long serialVersionUID = 1L;

    private static final Long NO_DATA_SOURCE_SELECTED = -1L;

    private static final Logger LOGGER = Logger.getLogger(CommonAttributePanel.class.getName());
    private boolean pictureViewCheckboxState;
    private boolean documentsCheckboxState;

    /**
     * Creates new form CommonFilesPanel
     */
    @NbBundle.Messages({
        "CommonFilesPanel.title=Common Files Panel",
        "CommonFilesPanel.exception=Unexpected Exception loading DataSources.",
        "CommonFilesPanel.frame.title=Find Common Files",
        "CommonFilesPanel.frame.msg=Find Common Files"})
    public CommonAttributePanel() {
        super(new JFrame(Bundle.CommonFilesPanel_frame_title()),
                Bundle.CommonFilesPanel_frame_msg(), true);
        initComponents();
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        this.errorText.setVisible(false);
        this.setupDataSources();

        if (CommonAttributePanel.isEamDbAvailable()) {
            this.setupCases();
        } else {
            this.disableIntercaseSearch();
        }
    }

    private static boolean isEamDbAvailable() {
        try {
            EamDb DbManager = EamDb.getInstance();
            return DbManager != null;
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Unexpected exception while  checking for EamDB enabled.", ex);
        }
        return false;
    }

    private void disableIntercaseSearch() {
        this.intraCaseRadio.setSelected(true);
        this.interCaseRadio.setEnabled(false);
    }

    @NbBundle.Messages({
        "CommonFilesPanel.search.results.titleAll=Common Files (All Data Sources)",
        "CommonFilesPanel.search.results.titleSingle=Common Files (Match Within Data Source: %s)",
        "CommonFilesPanel.search.results.pathText=Common Files Search Results",
        "CommonFilesPanel.search.done.searchProgressGathering=Gathering Common Files Search Results.",
        "CommonFilesPanel.search.done.searchProgressDisplay=Displaying Common Files Search Results.",
        "CommonFilesPanel.search.done.tskCoreException=Unable to run query against DB.",
        "CommonFilesPanel.search.done.noCurrentCaseException=Unable to open case file.",
        "CommonFilesPanel.search.done.exception=Unexpected exception running Common Files Search.",
        "CommonFilesPanel.search.done.interupted=Something went wrong finding common files.",
        "CommonFilesPanel.search.done.sqlException=Unable to query db for files or data sources."})
    private void search() {
        String pathText = Bundle.CommonFilesPanel_search_results_pathText();

        new SwingWorker<CommonAttributeSearchResults, Void>() {

            private String tabTitle;
            private ProgressHandle progress;

            private void setTitleForAllDataSources() {
                this.tabTitle = Bundle.CommonFilesPanel_search_results_titleAll();
            }

            private void setTitleForSingleSource(Long dataSourceId) {
                final String CommonFilesPanel_search_results_titleSingle = Bundle.CommonFilesPanel_search_results_titleSingle();
                final Object[] dataSourceName = new Object[]{intraCasePanel.getDataSourceMap().get(dataSourceId)};

                this.tabTitle = String.format(CommonFilesPanel_search_results_titleSingle, dataSourceName);
            }

            @Override
            @SuppressWarnings({"BoxedValueEquality", "NumberEquality"})
            protected CommonAttributeSearchResults doInBackground() throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException, Exception {
                progress = ProgressHandle.createHandle(Bundle.CommonFilesPanel_search_done_searchProgressGathering());
                progress.start();
                progress.switchToIndeterminate();

                Long dataSourceId = intraCasePanel.getSelectedDataSourceId();
                Integer caseId = interCasePanel.getSelectedCaseId();

                AbstractCommonAttributeSearcher builder;
                CommonAttributeSearchResults metadata;

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

                if (CommonAttributePanel.this.interCaseRadio.isSelected()) {

                    if (caseId == InterCasePanel.NO_CASE_SELECTED) {
                        builder = new AllInterCaseCommonAttributeSearcher(intraCasePanel.getDataSourceMap(), filterByMedia, filterByDocuments);
                    } else {
                        builder = new SingleInterCaseCommonAttributeSearcher(caseId, intraCasePanel.getDataSourceMap(), filterByMedia, filterByDocuments);
                    }
                } else {
                    if (dataSourceId == CommonAttributePanel.NO_DATA_SOURCE_SELECTED) {
                        builder = new AllIntraCaseCommonAttributeSearcher(intraCasePanel.getDataSourceMap(), filterByMedia, filterByDocuments);

                        setTitleForAllDataSources();
                    } else {
                        builder = new SingleIntraCaseCommonAttributeSearcher(dataSourceId, intraCasePanel.getDataSourceMap(), filterByMedia, filterByDocuments);

                        setTitleForSingleSource(dataSourceId);
                    }
                }
                metadata = builder.findFiles();
                this.tabTitle = builder.buildTabTitle();

                return metadata;
            }

            @Override
            protected void done() {
                try {
                    super.done();

                    CommonAttributeSearchResults metadata = this.get();

                    CommonAttributeSearchResultRootNode commonFilesNode = new CommonAttributeSearchResultRootNode(metadata);

                    // -3969
                    DataResultFilterNode dataResultFilterNode = new DataResultFilterNode(commonFilesNode, ExplorerManager.find(CommonAttributePanel.this));

                    TableFilterNode tableFilterWithDescendantsNode = new TableFilterNode(dataResultFilterNode, 3);

                    DataResultViewerTable table = new CommonAttributesSearchResultsViewerTable();

                    Collection<DataResultViewer> viewers = new ArrayList<>(1);
                    viewers.add(table);

                    progress.setDisplayName(Bundle.CommonFilesPanel_search_done_searchProgressDisplay());
                    DataResultTopComponent.createInstance(tabTitle, pathText, tableFilterWithDescendantsNode, metadata.size(), viewers);
                    progress.finish();

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
     * Sets up the data sources dropdown and returns the data sources map for
     * future usage.
     *
     * @return a mapping of data correlationCase ids to data correlationCase
     * names
     */
    @NbBundle.Messages({
        "CommonFilesPanel.setupDataSources.done.tskCoreException=Unable to run query against DB.",
        "CommonFilesPanel.setupDataSources.done.noCurrentCaseException=Unable to open case file.",
        "CommonFilesPanel.setupDataSources.done.exception=Unexpected exception loading data sources.",
        "CommonFilesPanel.setupDataSources.done.interupted=Something went wrong building the Common Files Search dialog box.",
        "CommonFilesPanel.setupDataSources.done.sqlException=Unable to query db for data sources.",
        "CommonFilesPanel.setupDataSources.updateUi.noDataSources=No data sources were found."})
    private void setupDataSources() {

        new SwingWorker<Map<Long, String>, Void>() {

            private void updateUi() {

                final Map<Long, String> dataSourceMap = CommonAttributePanel.this.intraCasePanel.getDataSourceMap();

                String[] dataSourcesNames = new String[dataSourceMap.size()];

                //only enable all this stuff if we actually have datasources
                if (dataSourcesNames.length > 0) {
                    dataSourcesNames = dataSourceMap.values().toArray(dataSourcesNames);
                    CommonAttributePanel.this.intraCasePanel.setDataModel(new DataSourceComboBoxModel(dataSourcesNames));

                    boolean multipleDataSources = this.caseHasMultipleSources();
                    CommonAttributePanel.this.intraCasePanel.rigForMultipleDataSources(multipleDataSources);

                    //TODO this should be attached to the intra/inter radio buttons
                    CommonAttributePanel.this.setSearchButtonEnabled(true);
                }
            }

            private boolean caseHasMultipleSources() {
                return CommonAttributePanel.this.intraCasePanel.getDataSourceMap().size() > 2;
            }

            @Override
            protected Map<Long, String> doInBackground() throws NoCurrentCaseException, TskCoreException, SQLException {
                DataSourceLoader loader = new DataSourceLoader();
                return loader.getDataSourceMap();
            }

            @Override
            protected void done() {

                try {
                    CommonAttributePanel.this.intraCasePanel.setDataSourceMap(this.get());
                    updateUi();

                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, "Interrupted while building Common Files Search dialog.", ex);
                    MessageNotifyUtil.Message.error(Bundle.CommonFilesPanel_setupDataSources_done_interupted());
                } catch (ExecutionException ex) {
                    String errorMessage;
                    Throwable inner = ex.getCause();
                    if (inner instanceof TskCoreException) {
                        LOGGER.log(Level.SEVERE, "Failed to load data sources from database.", ex);
                        errorMessage = Bundle.CommonFilesPanel_setupDataSources_done_tskCoreException();
                    } else if (inner instanceof NoCurrentCaseException) {
                        LOGGER.log(Level.SEVERE, "Current case has been closed.", ex);
                        errorMessage = Bundle.CommonFilesPanel_setupDataSources_done_noCurrentCaseException();
                    } else if (inner instanceof SQLException) {
                        LOGGER.log(Level.SEVERE, "Unable to query db for data sources.", ex);
                        errorMessage = Bundle.CommonFilesPanel_setupDataSources_done_sqlException();
                    } else {
                        LOGGER.log(Level.SEVERE, "Unexpected exception while building Common Files Search dialog panel.", ex);
                        errorMessage = Bundle.CommonFilesPanel_setupDataSources_done_exception();
                    }
                    MessageNotifyUtil.Message.error(errorMessage);
                }
            }
        }.execute();
    }

    @NbBundle.Messages({
        "CommonFilesPanel.setupCases.done.interruptedException=Something went wrong building the Common Files Search dialog box.",
        "CommonFilesPanel.setupCases.done.exeutionException=Unexpected exception loading cases."})
    private void setupCases() {

        new SwingWorker<Map<Integer, String>, Void>() {

            private void updateUi() {

                final Map<Integer, String> caseMap = CommonAttributePanel.this.interCasePanel.getCaseMap();

                String[] caseNames = new String[caseMap.size()];

                if (caseNames.length > 0) {
                    caseNames = caseMap.values().toArray(caseNames);
                    CommonAttributePanel.this.interCasePanel.setCaseList(new DataSourceComboBoxModel(caseNames));

                    boolean multipleCases = this.centralRepoHasMultipleCases();
                    CommonAttributePanel.this.interCasePanel.rigForMultipleCases(multipleCases);

                } else {
                    CommonAttributePanel.this.disableIntercaseSearch();
                }
            }

            private Map<Integer, String> mapDataSources(List<CorrelationCase> cases) throws Exception {
                Map<Integer, String> casemap = new HashMap<>();
                CorrelationCase currentCorCase = EamDb.getInstance().getCase(Case.getCurrentCase());
                for (CorrelationCase correlationCase : cases) {
                    if (currentCorCase.getID() != correlationCase.getID()) { // if not the current Case
                        casemap.put(correlationCase.getID(), correlationCase.getDisplayName());
                    }
                }

                return casemap;
            }

            @Override
            protected Map<Integer, String> doInBackground() throws Exception {

                List<CorrelationCase> dataSources = EamDb.getInstance().getCases();
                Map<Integer, String> caseMap = mapDataSources(dataSources);

                return caseMap;
            }

            @Override
            protected void done() {
                try {
                    Map<Integer, String> cases = this.get();
                    CommonAttributePanel.this.interCasePanel.setCaseMap(cases);
                    this.updateUi();
                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, "Interrupted while building Common Files Search dialog.", ex);
                    MessageNotifyUtil.Message.error(Bundle.CommonFilesPanel_setupCases_done_interruptedException());
                } catch (ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "Unexpected exception while building Common Files Search dialog.", ex);
                    MessageNotifyUtil.Message.error(Bundle.CommonFilesPanel_setupCases_done_exeutionException());
                }
            }

            private boolean centralRepoHasMultipleCases() {
                return CommonAttributePanel.this.interCasePanel.centralRepoHasMultipleCases();
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

        fileTypeFilterButtonGroup = new javax.swing.ButtonGroup();
        interIntraButtonGroup = new javax.swing.ButtonGroup();
        jPanel1 = new javax.swing.JPanel();
        commonFilesSearchLabel2 = new javax.swing.JLabel();
        searchButton = new javax.swing.JButton();
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
        layoutPanel = new java.awt.Panel();
        intraCasePanel = new org.sleuthkit.autopsy.commonfilesearch.IntraCasePanel();
        interCasePanel = new org.sleuthkit.autopsy.commonfilesearch.InterCasePanel();

        setMinimumSize(new java.awt.Dimension(412, 350));
        setPreferredSize(new java.awt.Dimension(412, 350));
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });

        jPanel1.setPreferredSize(new java.awt.Dimension(412, 350));

        org.openide.awt.Mnemonics.setLocalizedText(commonFilesSearchLabel2, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.commonFilesSearchLabel2.text")); // NOI18N
        commonFilesSearchLabel2.setFocusable(false);

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.searchButton.text")); // NOI18N
        searchButton.setEnabled(false);
        searchButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.cancelButton.text")); // NOI18N
        cancelButton.setActionCommand(org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.cancelButton.actionCommand")); // NOI18N
        cancelButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        fileTypeFilterButtonGroup.add(allFileCategoriesRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(allFileCategoriesRadioButton, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.allFileCategoriesRadioButton.text")); // NOI18N
        allFileCategoriesRadioButton.setToolTipText(org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.allFileCategoriesRadioButton.toolTipText")); // NOI18N
        allFileCategoriesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allFileCategoriesRadioButtonActionPerformed(evt);
            }
        });

        fileTypeFilterButtonGroup.add(selectedFileCategoriesButton);
        selectedFileCategoriesButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(selectedFileCategoriesButton, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.selectedFileCategoriesButton.text")); // NOI18N
        selectedFileCategoriesButton.setToolTipText(org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.selectedFileCategoriesButton.toolTipText")); // NOI18N
        selectedFileCategoriesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectedFileCategoriesButtonActionPerformed(evt);
            }
        });

        pictureVideoCheckbox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(pictureVideoCheckbox, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.pictureVideoCheckbox.text")); // NOI18N
        pictureVideoCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pictureVideoCheckboxActionPerformed(evt);
            }
        });

        documentsCheckbox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(documentsCheckbox, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.documentsCheckbox.text")); // NOI18N
        documentsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                documentsCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(categoriesLabel, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.categoriesLabel.text")); // NOI18N
        categoriesLabel.setName(""); // NOI18N

        errorText.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorText, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.errorText.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(commonFilesSearchLabel1, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.commonFilesSearchLabel1.text")); // NOI18N
        commonFilesSearchLabel1.setFocusable(false);

        interIntraButtonGroup.add(intraCaseRadio);
        intraCaseRadio.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(intraCaseRadio, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.intraCaseRadio.text")); // NOI18N
        intraCaseRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                intraCaseRadioActionPerformed(evt);
            }
        });

        interIntraButtonGroup.add(interCaseRadio);
        org.openide.awt.Mnemonics.setLocalizedText(interCaseRadio, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonFilesPanel.jRadioButton2.text")); // NOI18N
        interCaseRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                interCaseRadioActionPerformed(evt);
            }
        });

        layoutPanel.setLayout(new java.awt.CardLayout());
        layoutPanel.add(intraCasePanel, "card3");
        layoutPanel.add(interCasePanel, "card2");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(searchButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(errorText))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(commonFilesSearchLabel2)
                            .addComponent(intraCaseRadio)
                            .addComponent(interCaseRadio)
                            .addComponent(commonFilesSearchLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(categoriesLabel)
                            .addComponent(selectedFileCategoriesButton)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(35, 35, 35)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(documentsCheckbox)
                            .addComponent(pictureVideoCheckbox)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(allFileCategoriesRadioButton)))
                .addContainerGap())
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel1Layout.createSequentialGroup()
                    .addGap(20, 20, 20)
                    .addComponent(layoutPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(10, 10, 10)))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(commonFilesSearchLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(commonFilesSearchLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(intraCaseRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(interCaseRadio)
                .addGap(79, 79, 79)
                .addComponent(categoriesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectedFileCategoriesButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pictureVideoCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(documentsCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(allFileCategoriesRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(searchButton)
                    .addComponent(cancelButton)
                    .addComponent(errorText))
                .addContainerGap())
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                    .addGap(98, 98, 98)
                    .addComponent(layoutPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(180, 180, 180)))
        );

        getContentPane().add(jPanel1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        search();
        SwingUtilities.windowForComponent(this).dispose();
    }//GEN-LAST:event_searchButtonActionPerformed

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
        ((java.awt.CardLayout) this.layoutPanel.getLayout()).first(this.layoutPanel);
        handleIntraCaseSearchCriteriaChanged();
    }//GEN-LAST:event_intraCaseRadioActionPerformed

    public void handleIntraCaseSearchCriteriaChanged() {
        if (this.areIntraCaseSearchCriteriaMet()) {
            this.searchButton.setEnabled(true);
            this.hideErrorMessages();
        } else {
            this.searchButton.setEnabled(false);
            this.hideErrorMessages();
            this.showIntraCaseErrorMessage();
        }
    }

    private void interCaseRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interCaseRadioActionPerformed
        ((java.awt.CardLayout) this.layoutPanel.getLayout()).last(this.layoutPanel);
        handleInterCaseSearchCriteriaChanged();
    }//GEN-LAST:event_interCaseRadioActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        SwingUtilities.windowForComponent(this).dispose();
    }//GEN-LAST:event_formWindowClosed

    public void handleInterCaseSearchCriteriaChanged() {
        if (this.areInterCaseSearchCriteriaMet()) {
            this.searchButton.setEnabled(true);
            this.hideErrorMessages();
        } else {
            this.searchButton.setEnabled(false);
            this.hideErrorMessages();
            this.showInterCaseErrorMessage();
        }
    }

    private void toggleErrorTextAndSearchBox() {
        if (!this.pictureVideoCheckbox.isSelected() && !this.documentsCheckbox.isSelected() && !this.allFileCategoriesRadioButton.isSelected()) {
            this.searchButton.setEnabled(false);
            this.errorText.setVisible(true);
        } else {
            this.searchButton.setEnabled(true);
            this.errorText.setVisible(false);
        }
    }

    private void manageCheckBoxState() {

        this.pictureViewCheckboxState = this.pictureVideoCheckbox.isSelected();
        this.documentsCheckboxState = this.documentsCheckbox.isSelected();

        if (this.allFileCategoriesRadioButton.isSelected()) {
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
    private javax.swing.JRadioButton allFileCategoriesRadioButton;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel categoriesLabel;
    private javax.swing.JLabel commonFilesSearchLabel1;
    private javax.swing.JLabel commonFilesSearchLabel2;
    private javax.swing.JCheckBox documentsCheckbox;
    private javax.swing.JLabel errorText;
    private javax.swing.ButtonGroup fileTypeFilterButtonGroup;
    private org.sleuthkit.autopsy.commonfilesearch.InterCasePanel interCasePanel;
    private javax.swing.JRadioButton interCaseRadio;
    private javax.swing.ButtonGroup interIntraButtonGroup;
    private org.sleuthkit.autopsy.commonfilesearch.IntraCasePanel intraCasePanel;
    private javax.swing.JRadioButton intraCaseRadio;
    private javax.swing.JPanel jPanel1;
    private java.awt.Panel layoutPanel;
    private javax.swing.JCheckBox pictureVideoCheckbox;
    private javax.swing.JButton searchButton;
    private javax.swing.JRadioButton selectedFileCategoriesButton;
    // End of variables declaration//GEN-END:variables

    void setSearchButtonEnabled(boolean enabled) {
        this.searchButton.setEnabled(enabled);
    }

    private boolean areIntraCaseSearchCriteriaMet() {
        return this.intraCasePanel.areSearchCriteriaMet();
    }

    private boolean areInterCaseSearchCriteriaMet() {
        return this.interCasePanel.areSearchCriteriaMet();
    }

    private void hideErrorMessages() {
        this.errorText.setVisible(false);
    }

    private void showIntraCaseErrorMessage() {
        this.errorText.setText(this.intraCasePanel.getErrorMessage());
        this.errorText.setVisible(true);
    }

    private void showInterCaseErrorMessage() {
        this.errorText.setText(this.interCasePanel.getErrorMessage());
        this.errorText.setVisible(true);
    }
}
