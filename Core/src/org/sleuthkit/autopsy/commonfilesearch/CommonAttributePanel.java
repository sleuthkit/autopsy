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

import java.awt.Dimension;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.explorer.ExplorerManager;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
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
final class CommonAttributePanel extends javax.swing.JDialog implements Observer {

    private static final Logger LOGGER = Logger.getLogger(CommonAttributePanel.class.getName());
    private static final long serialVersionUID = 1L;

    private static final Long NO_DATA_SOURCE_SELECTED = -1L;

    private final UserInputErrorManager errorManager;

    private int percentageThresholdValue = 20;

    /**
     * Creates new form CommonFilesPanel
     */
    @NbBundle.Messages({
        "CommonAttributePanel.title=Common Attribute Panel",
        "CommonAttributePanel.exception=Unexpected Exception loading DataSources.",
        "CommonAttributePanel.frame.title=Find Common Attributes",
        "CommonAttributePanel.frame.msg=Find Common Attributes",
        "CommonAttributePanel.intraCasePanel.title=Curren Case Options"})
    CommonAttributePanel() {
        super(new JFrame(Bundle.CommonAttributePanel_frame_title()),
                Bundle.CommonAttributePanel_frame_msg(), true);
        initComponents();
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        this.setupDataSources();
        intraCasePanel.setVisible(true);
        interCasePanel.setVisible(false);
        if (CommonAttributePanel.isEamDbAvailableForIntercaseSearch()) {
            this.setupCases();
            this.interCasePanel.setupCorrelationTypeFilter();
        } else {
            this.disableIntercaseSearch();
        }

        this.enablePercentageOptions(CommonAttributePanel.isEamDbAvailableForPercentageFrequencyCalculations());

        this.errorManager = new UserInputErrorManager();

        this.percentageThresholdInputBox.getDocument().addDocumentListener(new DocumentListener() {

            private Dimension preferredSize = CommonAttributePanel.this.percentageThresholdInputBox.getPreferredSize();

            private void maintainSize() {
                CommonAttributePanel.this.percentageThresholdInputBox.setSize(preferredSize);
            }

            @Override
            public void insertUpdate(DocumentEvent event) {
                this.maintainSize();
                CommonAttributePanel.this.percentageThresholdChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                this.maintainSize();
                CommonAttributePanel.this.percentageThresholdChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                this.maintainSize();
                CommonAttributePanel.this.percentageThresholdChanged();
            }
        });
    }

    /**
     * Get whether or not the central repository will be enabled as a search
     * option.
     *
     * @return true if the central repository exists and has at least 2 cases in
     *         and includes the current case, false otherwise.
     */
    static boolean isEamDbAvailableForIntercaseSearch() {
        try {
            return EamDb.isEnabled()
                    && EamDb.getInstance() != null
                    && EamDb.getInstance().getCases().size() > 1
                    && Case.isCaseOpen()
                    && Case.getCurrentCase() != null
                    && EamDb.getInstance().getCase(Case.getCurrentCase()) != null;
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Unexpected exception while  checking for EamDB enabled.", ex);
        }
        return false;
    }

    @Override
    public void update(Observable o, Object arg) {
        checkFileTypeCheckBoxState();
    }

    /**
     * Get whether or not the central repository will be available for filtering
     * results.
     *
     * @return true if the central repository exists and has at least 1 case in
     *         it, false otherwise.
     */
    private static boolean isEamDbAvailableForPercentageFrequencyCalculations() {
        try {
            return EamDb.isEnabled()
                    && EamDb.getInstance() != null
                    && EamDb.getInstance().getCases().size() > 0;
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Unexpected exception while  checking for EamDB enabled.", ex);
        }
        return false;
    }

    /**
     * Disable the option to search for common attributes in the central
     * repository.
     */
    private void disableIntercaseSearch() {
        this.intraCaseRadio.setSelected(true);
        this.interCaseRadio.setEnabled(false);
    }

    /**
     * Perform the common attribute search.
     */
    @NbBundle.Messages({
        "CommonAttributePanel.search.results.titleAll=Common Attributes (All Data Sources)",
        "CommonAttributePanel.search.results.titleSingle=Common Attributes (Match Within Data Source: %s)",
        "CommonAttributePanel.search.results.pathText=Common Attribute Search Results",
        "CommonAttributePanel.search.done.searchProgressGathering=Gathering Common Attribute Search Results.",
        "CommonAttributePanel.search.done.searchProgressDisplay=Displaying Common Attribute Search Results.",
        "CommonAttributePanel.search.done.tskCoreException=Unable to run query against DB.",
        "CommonAttributePanel.search.done.noCurrentCaseException=Unable to open case file.",
        "CommonAttributePanel.search.done.exception=Unexpected exception running Common Attribute Search.",
        "CommonAttributePanel.search.done.interupted=Something went wrong finding common attributes.",
        "CommonAttributePanel.search.done.sqlException=Unable to query db for attributes or data sources."})
    private void search() {
        String pathText = Bundle.CommonAttributePanel_search_results_pathText();

        new SwingWorker<CommonAttributeSearchResults, Void>() {

            private String tabTitle;
            private ProgressHandle progress;

            /**
             * Set the title of the search results for searches using all data
             * sources.
             */
            private void setTitleForAllDataSources() {
                this.tabTitle = Bundle.CommonAttributePanel_search_results_titleAll();
            }

            /**
             * Set the title of the search results for searches using a single
             * source.
             *
             * @param dataSourceId the datasource ID of the the source being
             *                     used
             */
            private void setTitleForSingleSource(Long dataSourceId) {
                final String CommonAttributePanel_search_results_titleSingle = Bundle.CommonAttributePanel_search_results_titleSingle();
                final Object[] dataSourceName = new Object[]{intraCasePanel.getDataSourceMap().get(dataSourceId)};

                this.tabTitle = String.format(CommonAttributePanel_search_results_titleSingle, dataSourceName);
            }

            @Override
            protected CommonAttributeSearchResults doInBackground() throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException {
                progress = ProgressHandle.createHandle(Bundle.CommonAttributePanel_search_done_searchProgressGathering());
                progress.start();
                progress.switchToIndeterminate();

                Long dataSourceId = intraCasePanel.getSelectedDataSourceId();
                Integer caseId = interCasePanel.getSelectedCaseId();

                AbstractCommonAttributeSearcher builder;
                CommonAttributeSearchResults metadata;

                boolean filterByMedia = false;
                boolean filterByDocuments = false;

                int percentageThreshold = CommonAttributePanel.this.percentageThresholdValue;

                if (!CommonAttributePanel.this.percentageThresholdCheck.isSelected()) {
                    //0 has the effect of disabling the feature
                    percentageThreshold = 0;
                }

                if (CommonAttributePanel.this.interCaseRadio.isSelected()) {
                    CorrelationAttributeInstance.Type corType = interCasePanel.getSelectedCorrelationType();
                    if (interCasePanel.fileCategoriesButtonIsSelected()) {
                        filterByMedia = interCasePanel.pictureVideoCheckboxIsSelected();
                        filterByDocuments = interCasePanel.documentsCheckboxIsSelected();
                    }
                    if (corType == null) {
                        corType = CorrelationAttributeInstance.getDefaultCorrelationTypes().get(0);
                    }
                    if (caseId == InterCasePanel.NO_CASE_SELECTED) {
                        builder = new AllInterCaseCommonAttributeSearcher(intraCasePanel.getDataSourceMap(), filterByMedia, filterByDocuments, corType, percentageThreshold);
                    } else {

                        builder = new SingleInterCaseCommonAttributeSearcher(caseId, intraCasePanel.getDataSourceMap(), filterByMedia, filterByDocuments, corType, percentageThreshold);
                    }

                } else {
                    if (intraCasePanel.fileCategoriesButtonIsSelected()) {
                        filterByMedia = intraCasePanel.pictureVideoCheckboxIsSelected();
                        filterByDocuments = intraCasePanel.documentsCheckboxIsSelected();
                    }
                    if (dataSourceId == CommonAttributePanel.NO_DATA_SOURCE_SELECTED) {
                        builder = new AllIntraCaseCommonAttributeSearcher(intraCasePanel.getDataSourceMap(), filterByMedia, filterByDocuments, percentageThreshold);

                        setTitleForAllDataSources();
                    } else {
                        builder = new SingleIntraCaseCommonAttributeSearcher(dataSourceId, intraCasePanel.getDataSourceMap(), filterByMedia, filterByDocuments, percentageThreshold);

                        setTitleForSingleSource(dataSourceId);
                    }

                }
                metadata = builder.findMatches();
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

                    progress.setDisplayName(Bundle.CommonAttributePanel_search_done_searchProgressDisplay());
                    DataResultTopComponent.createInstance(tabTitle, pathText, tableFilterWithDescendantsNode, metadata.size(), viewers);
                    progress.finish();

                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, "Interrupted while loading Common Files", ex);
                    MessageNotifyUtil.Message.error(Bundle.CommonAttributePanel_search_done_interupted());
                } catch (ExecutionException ex) {
                    String errorMessage;
                    Throwable inner = ex.getCause();
                    if (inner instanceof TskCoreException) {
                        LOGGER.log(Level.SEVERE, "Failed to load files from database.", ex);
                        errorMessage = Bundle.CommonAttributePanel_search_done_tskCoreException();
                    } else if (inner instanceof NoCurrentCaseException) {
                        LOGGER.log(Level.SEVERE, "Current case has been closed.", ex);
                        errorMessage = Bundle.CommonAttributePanel_search_done_noCurrentCaseException();
                    } else if (inner instanceof SQLException) {
                        LOGGER.log(Level.SEVERE, "Unable to query db for files.", ex);
                        errorMessage = Bundle.CommonAttributePanel_search_done_sqlException();
                    } else {
                        LOGGER.log(Level.SEVERE, "Unexpected exception while running Common Files Search.", ex);
                        errorMessage = Bundle.CommonAttributePanel_search_done_exception();
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
     *         names
     */
    @NbBundle.Messages({
        "CommonAttributePanel.setupDataSources.done.tskCoreException=Unable to run query against DB.",
        "CommonAttributePanel.setupDataSources.done.noCurrentCaseException=Unable to open case file.",
        "CommonAttributePanel.setupDataSources.done.exception=Unexpected exception loading data sources.",
        "CommonAttributePanel.setupDataSources.done.interupted=Something went wrong building the Common Files Search dialog box.",
        "CommonAttributePanel.setupDataSources.done.sqlException=Unable to query db for data sources.",
        "CommonAttributePanel.setupDataSources.updateUi.noDataSources=No data sources were found."})
    private void setupDataSources() {

        new SwingWorker<Map<Long, String>, Void>() {

            /**
             * Update the user interface of the panel to reflect the datasources
             * available.
             */
            private void updateUi() {

                final Map<Long, String> dataSourceMap = CommonAttributePanel.this.intraCasePanel.getDataSourceMap();

                String[] dataSourcesNames = new String[dataSourceMap.size()];

                //only enable all this stuff if we actually have datasources
                if (dataSourcesNames.length > 0) {
                    dataSourcesNames = dataSourceMap.values().toArray(dataSourcesNames);
                    CommonAttributePanel.this.intraCasePanel.setDatasourceComboboxModel(new DataSourceComboBoxModel(dataSourcesNames));

                    if (!this.caseHasMultipleSources()) { //disable intra case search when only 1 data source in current case
                        intraCaseRadio.setEnabled(false);
                        interCaseRadio.setSelected(true);
                        intraCasePanel.setVisible(false);
                        interCasePanel.setVisible(true);
                    }
                    CommonAttributePanel.this.updateErrorTextAndSearchButton();
                }
            }

            /**
             * Check if the case has multiple data sources
             *
             * @return true if the case has multiple data sources, false
             *         otherwise
             */
            private boolean caseHasMultipleSources() {
                return CommonAttributePanel.this.intraCasePanel.getDataSourceMap().size() > 2;  //WJS-TODO should be > than 1?
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
                    MessageNotifyUtil.Message.error(Bundle.CommonAttributePanel_setupDataSources_done_interupted());
                } catch (ExecutionException ex) {
                    String errorMessage;
                    Throwable inner = ex.getCause();
                    if (inner instanceof TskCoreException) {
                        LOGGER.log(Level.SEVERE, "Failed to load data sources from database.", ex);
                        errorMessage = Bundle.CommonAttributePanel_setupDataSources_done_tskCoreException();
                    } else if (inner instanceof NoCurrentCaseException) {
                        LOGGER.log(Level.SEVERE, "Current case has been closed.", ex);
                        errorMessage = Bundle.CommonAttributePanel_setupDataSources_done_noCurrentCaseException();
                    } else if (inner instanceof SQLException) {
                        LOGGER.log(Level.SEVERE, "Unable to query db for data sources.", ex);
                        errorMessage = Bundle.CommonAttributePanel_setupDataSources_done_sqlException();
                    } else {
                        LOGGER.log(Level.SEVERE, "Unexpected exception while building Common Files Search dialog panel.", ex);
                        errorMessage = Bundle.CommonAttributePanel_setupDataSources_done_exception();
                    }
                    MessageNotifyUtil.Message.error(errorMessage);
                }
            }
        }.execute();
    }

    @NbBundle.Messages({
        "CommonAttributePanel.setupCases.done.interruptedException=Something went wrong building the Common Files Search dialog box.",
        "CommonAttributePanel.setupCases.done.exeutionException=Unexpected exception loading cases."})
    private void setupCases() {

        new SwingWorker<Map<Integer, String>, Void>() {

            /**
             * Update the user interface of the panel to reflect the cases
             * available.
             */
            private void updateUi() {

                final Map<Integer, String> caseMap = CommonAttributePanel.this.interCasePanel.getCaseMap();

                String[] caseNames = new String[caseMap.size()];

                if (caseNames.length > 0) {
                    caseNames = caseMap.values().toArray(caseNames);
                    CommonAttributePanel.this.interCasePanel.setCaseComboboxModel(new DataSourceComboBoxModel(caseNames));
                } else {
                    CommonAttributePanel.this.disableIntercaseSearch();
                }
            }

            /**
             * Create a map of cases from a list of cases.
             *
             * @param cases
             *
             * @return a map of Cases
             *
             * @throws EamDbException
             */
            private Map<Integer, String> mapCases(List<CorrelationCase> cases) throws EamDbException {
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
            protected Map<Integer, String> doInBackground() throws EamDbException {

                List<CorrelationCase> dataSources = EamDb.getInstance().getCases();
                Map<Integer, String> caseMap = mapCases(dataSources);

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
                    MessageNotifyUtil.Message.error(Bundle.CommonAttributePanel_setupCases_done_interruptedException());
                } catch (ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "Unexpected exception while building Common Files Search dialog.", ex);
                    MessageNotifyUtil.Message.error(Bundle.CommonAttributePanel_setupCases_done_exeutionException());
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

        interIntraButtonGroup = new javax.swing.ButtonGroup();
        jPanel1 = new javax.swing.JPanel();
        scopeLabel = new javax.swing.JLabel();
        searchButton = new javax.swing.JButton();
        errorText = new javax.swing.JLabel();
        commonItemSearchDescription = new javax.swing.JLabel();
        intraCaseRadio = new javax.swing.JRadioButton();
        interCaseRadio = new javax.swing.JRadioButton();
        percentageThresholdCheck = new javax.swing.JCheckBox();
        percentageThresholdInputBox = new javax.swing.JTextField();
        percentageThresholdTextTwo = new javax.swing.JLabel();
        intraCasePanel = new org.sleuthkit.autopsy.commonfilesearch.IntraCasePanel();
        interCasePanel = new org.sleuthkit.autopsy.commonfilesearch.InterCasePanel();

        setMinimumSize(new java.awt.Dimension(450, 440));
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });

        jPanel1.setMaximumSize(new java.awt.Dimension(450, 400));
        jPanel1.setMinimumSize(new java.awt.Dimension(450, 400));
        jPanel1.setPreferredSize(new java.awt.Dimension(450, 400));
        jPanel1.setRequestFocusEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(scopeLabel, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.scopeLabel.text")); // NOI18N
        scopeLabel.setFocusable(false);

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.searchButton.text")); // NOI18N
        searchButton.setEnabled(false);
        searchButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        errorText.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorText, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.errorText.text")); // NOI18N
        errorText.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        org.openide.awt.Mnemonics.setLocalizedText(commonItemSearchDescription, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.commonItemSearchDescription.text")); // NOI18N
        commonItemSearchDescription.setFocusable(false);

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

        org.openide.awt.Mnemonics.setLocalizedText(percentageThresholdCheck, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.percentageThresholdCheck.text_1_1")); // NOI18N
        percentageThresholdCheck.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                percentageThresholdCheckActionPerformed(evt);
            }
        });

        percentageThresholdInputBox.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        percentageThresholdInputBox.setText(org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.percentageThresholdInputBox.text")); // NOI18N
        percentageThresholdInputBox.setMaximumSize(new java.awt.Dimension(40, 24));
        percentageThresholdInputBox.setMinimumSize(new java.awt.Dimension(40, 24));
        percentageThresholdInputBox.setPreferredSize(new java.awt.Dimension(40, 24));

        org.openide.awt.Mnemonics.setLocalizedText(percentageThresholdTextTwo, org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.percentageThresholdTextTwo.text_1")); // NOI18N

        intraCasePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.intraCasePanel.border.title"))); // NOI18N
        intraCasePanel.setMinimumSize(new java.awt.Dimension(204, 204));
        intraCasePanel.setPreferredSize(new java.awt.Dimension(430, 204));

        interCasePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(CommonAttributePanel.class, "CommonAttributePanel.interCasePanel.border.title"))); // NOI18N
        interCasePanel.setMinimumSize(new java.awt.Dimension(430, 249));
        interCasePanel.setPreferredSize(new java.awt.Dimension(430, 249));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(errorText, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                        .addGap(65, 65, 65)
                        .addComponent(searchButton)
                        .addContainerGap())
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(percentageThresholdCheck)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(percentageThresholdInputBox, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(percentageThresholdTextTwo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(scopeLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(37, 37, 37))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addComponent(commonItemSearchDescription, javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                    .addGap(20, 20, 20)
                                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addComponent(intraCaseRadio, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(interCaseRadio, javax.swing.GroupLayout.DEFAULT_SIZE, 383, Short.MAX_VALUE))))
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addComponent(interCasePanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(intraCasePanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(commonItemSearchDescription, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(scopeLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(intraCaseRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(interCaseRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(interCasePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 240, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(intraCasePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 197, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(percentageThresholdCheck)
                    .addComponent(percentageThresholdInputBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(percentageThresholdTextTwo))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 50, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(searchButton)
                    .addComponent(errorText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        getContentPane().add(jPanel1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        SwingUtilities.windowForComponent(this).dispose();
    }//GEN-LAST:event_formWindowClosed

    private void percentageThresholdCheckActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_percentageThresholdCheckActionPerformed
        if (this.percentageThresholdCheck.isSelected()) {
            this.percentageThresholdInputBox.setEnabled(true);
        } else {
            this.percentageThresholdInputBox.setEnabled(false);
        }

        this.handleFrequencyPercentageState();
    }//GEN-LAST:event_percentageThresholdCheckActionPerformed

    private void interCaseRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interCaseRadioActionPerformed
        intraCasePanel.setVisible(false);
        interCasePanel.setVisible(true);
    }//GEN-LAST:event_interCaseRadioActionPerformed

    private void intraCaseRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_intraCaseRadioActionPerformed
        intraCasePanel.setVisible(true);
        interCasePanel.setVisible(false);
    }//GEN-LAST:event_intraCaseRadioActionPerformed

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        search();
        SwingUtilities.windowForComponent(this).dispose();
    }//GEN-LAST:event_searchButtonActionPerformed

    /**
     * Convert the text in the percentage threshold input box into an integer,
     * -1 is used when the string can not be converted to an integer.
     */
    private void percentageThresholdChanged() {
        String percentageString = this.percentageThresholdInputBox.getText();

        try {
            this.percentageThresholdValue = Integer.parseInt(percentageString);

        } catch (NumberFormatException ignored) {
            this.percentageThresholdValue = -1;
        }

        this.handleFrequencyPercentageState();
    }

    /**
     * Update the error text and the enabled status of the search button to
     * reflect the current validity of the search settings.
     */
    private void updateErrorTextAndSearchButton() {
        if (this.errorManager.anyErrors()) {
            this.searchButton.setEnabled(false);
            //grab the first error error and show it
            this.errorText.setText(this.errorManager.getErrors().get(0));
            this.errorText.setVisible(true);
        } else {
            this.searchButton.setEnabled(true);
            this.errorText.setVisible(false);
        }
    }

    /**
     * Set the enabled status of the percentage threshold options
     *
     * @param enabled true to enable percentage threshold options, false to
     *                disable them
     */
    private void enablePercentageOptions(boolean enabled) {
        this.percentageThresholdInputBox.setEnabled(enabled);
        this.percentageThresholdCheck.setEnabled(enabled);
        this.percentageThresholdCheck.setSelected(enabled);
        this.percentageThresholdTextTwo.setEnabled(enabled);
    }

    /**
     * Check that the integer value of what is entered in the percentage
     * threshold text box is a valid percentage and update the errorManager to
     * reflect the validity.
     */
    private void handleFrequencyPercentageState() {
        if (this.percentageThresholdValue > 0 && this.percentageThresholdValue <= 100) {
            this.errorManager.setError(UserInputErrorManager.FREQUENCY_PERCENTAGE_OUT_OF_RANGE_KEY, false);
        } else {

            this.errorManager.setError(UserInputErrorManager.FREQUENCY_PERCENTAGE_OUT_OF_RANGE_KEY, true);
        }
        this.updateErrorTextAndSearchButton();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel commonItemSearchDescription;
    private javax.swing.JLabel errorText;
    private org.sleuthkit.autopsy.commonfilesearch.InterCasePanel interCasePanel;
    private javax.swing.JRadioButton interCaseRadio;
    private javax.swing.ButtonGroup interIntraButtonGroup;
    private org.sleuthkit.autopsy.commonfilesearch.IntraCasePanel intraCasePanel;
    private javax.swing.JRadioButton intraCaseRadio;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JCheckBox percentageThresholdCheck;
    private javax.swing.JTextField percentageThresholdInputBox;
    private javax.swing.JLabel percentageThresholdTextTwo;
    private javax.swing.JLabel scopeLabel;
    private javax.swing.JButton searchButton;
    // End of variables declaration//GEN-END:variables

    /**
     * Add this panel as an observer of it's sub panels so that errors can be
     * indicated accurately.
     */
    void observeSubPanels() {
        intraCasePanel.addObserver(this);
        interCasePanel.addObserver(this);
    }

    /**
     * Check that the sub panels have valid options selected regarding their
     * file type filtering options, and update the errorManager with the
     * validity.
     */
    private void checkFileTypeCheckBoxState() {
        boolean validCheckBoxState = true;
        if (CommonAttributePanel.this.interCaseRadio.isSelected()) {
            if (interCasePanel.fileCategoriesButtonIsSelected()) {
                validCheckBoxState = interCasePanel.pictureVideoCheckboxIsSelected() || interCasePanel.documentsCheckboxIsSelected();
            }
        } else {
            if (intraCasePanel.fileCategoriesButtonIsSelected()) {
                validCheckBoxState = intraCasePanel.pictureVideoCheckboxIsSelected() || intraCasePanel.documentsCheckboxIsSelected();
            }
        }
        if (validCheckBoxState) {
            this.errorManager.setError(UserInputErrorManager.NO_FILE_CATEGORIES_SELECTED_KEY, false);
        } else {
            this.errorManager.setError(UserInputErrorManager.NO_FILE_CATEGORIES_SELECTED_KEY, true);
        }
        this.updateErrorTextAndSearchButton();
    }

}
