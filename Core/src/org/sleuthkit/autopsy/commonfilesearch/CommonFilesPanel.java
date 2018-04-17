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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ListDataListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel used for common files search configuration and configuration business
 * logic. Nested within CommonFilesDialog.
 */
public final class CommonFilesPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    public static final Long NO_DATA_SOURCE_SELECTED = -1L;

    private ComboBoxModel<String> dataSourcesList = new DataSourceComboBoxModel();
    private Map<Long, String> dataSourceMap;

    private static final Logger LOGGER = Logger.getLogger(CommonFilesPanel.class.getName());
    private boolean singleDataSource = false;
    private String selectedDataSource = "";

    /**
     * Creates new form CommonFilesPanel
     */
    @NbBundle.Messages({
        "CommonFilesPanel.title=Common Files Panel",
        "CommonFilesPanel.exception=Unexpected Exception loading DataSources."})
    public CommonFilesPanel() {
        initComponents();

        setupDataSources();
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
                try (CaseDbQuery query = tskDb.executeQuery(SELECT_DATA_SOURCES_LOGICAL)) {
                    ResultSet resultSet = query.getResultSet();
                    while (resultSet.next()) {
                        Long objectId = resultSet.getLong(1);
                        String dataSourceName = resultSet.getString(2);
                        dataSouceMap.put(objectId, dataSourceName);
                    }
                }
            }

            private void loadImageSources(SleuthkitCase tskDb, Map<Long, String> dataSouceMap) throws SQLException, TskCoreException {
                //try block releases resources - exceptions are handled in done()
                try (CaseDbQuery query = tskDb.executeQuery(SELECT_DATA_SOURCES_IMAGE)) {
                    ResultSet resultSet = query.getResultSet();
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

                Case currentCase = Case.getOpenCase();
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

        new SwingWorker<CommonFilesMetaData, Void>() {

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
            protected CommonFilesMetaData doInBackground() throws TskCoreException, NoCurrentCaseException, SQLException {
                Long dataSourceId = determineDataSourceId();

                CommonFilesMetaDataBuilder builder;

                if (dataSourceId == CommonFilesPanel.NO_DATA_SOURCE_SELECTED) {
                    builder = new AllDataSources(CommonFilesPanel.this.dataSourceMap);

                    setTitleForAllDataSources();
                } else {
                    builder = new SingleDataSource(dataSourceId, CommonFilesPanel.this.dataSourceMap);

                    setTitleForSingleSource(dataSourceId);
                }
                                
                CommonFilesMetaData metaData = builder.findCommonFiles();

                return metaData;
            }

            @Override
            protected void done() {
                try {
                    super.done();

                    CommonFilesMetaData metadata = get();

                    CommonFilesNode commonFilesNode = new CommonFilesNode(metadata);

                    //TODO consider getting em from ExplorerManager.find(this) rather the this wonky stuff seen here...
                    DataResultFilterNode dataResultFilterNode = new DataResultFilterNode(commonFilesNode, DirectoryTreeTopComponent.getDefault().getExplorerManager());

                    TableFilterNode tableFilterWithDescendantsNode = new TableFilterNode(dataResultFilterNode);

                    DataResultTopComponent component = DataResultTopComponent.createInstance(this.tabTitle);

                    DataResultTopComponent.initInstance(pathText, tableFilterWithDescendantsNode, metadata.size(), component);

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

    private class DataSourceComboBoxModel extends AbstractListModel<String> implements ComboBoxModel<String> {

        private static final long serialVersionUID = 1L;
        private final String[] dataSourceList;
        String selection = null;

        /**
         * Use this to initialize the panel
         */
        DataSourceComboBoxModel() {
            this.dataSourceList = new String[0];
        }

        /**
         * Use this when we have data to display.
         *
         * @param theDataSoureList names of data sources for user to pick from
         */
        DataSourceComboBoxModel(String[] theDataSoureList) {
            dataSourceList = theDataSoureList;
        }

        @Override
        public void setSelectedItem(Object anItem) {
            selection = (String) anItem;
        }

        @Override
        public Object getSelectedItem() {
            return selection;
        }

        @Override
        public int getSize() {
            return dataSourceList.length;
        }

        @Override
        public String getElementAt(int index) {
            return dataSourceList[index];
        }

        @Override
        public void addListDataListener(ListDataListener l) {
            this.listenerList.add(ListDataListener.class, l);
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
            this.listenerList.remove(ListDataListener.class, l);
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

        dataSourcesButtonGroup = new javax.swing.ButtonGroup();
        searchButton = new javax.swing.JButton();
        allDataSourcesRadioButton = new javax.swing.JRadioButton();
        withinDataSourceRadioButton = new javax.swing.JRadioButton();
        selectDataSourceComboBox = new javax.swing.JComboBox<>();
        jLabel1 = new javax.swing.JLabel();
        cancelButton = new javax.swing.JButton();

        setPreferredSize(new java.awt.Dimension(300, 200));

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.searchButton.text")); // NOI18N
        searchButton.setEnabled(false);
        searchButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        dataSourcesButtonGroup.add(allDataSourcesRadioButton);
        allDataSourcesRadioButton.setSelected(true);
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

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.jLabel1.text")); // NOI18N
        jLabel1.setFocusable(false);

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.cancelButton.text")); // NOI18N
        cancelButton.setActionCommand(org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.cancelButton.actionCommand")); // NOI18N
        cancelButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(selectDataSourceComboBox, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(withinDataSourceRadioButton)
                            .addComponent(allDataSourcesRadioButton)
                            .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 3, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(searchButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(allDataSourcesRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(withinDataSourceRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(selectDataSourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 50, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(cancelButton)
                            .addComponent(searchButton))
                        .addContainerGap())))
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

    private void withinDataSourceSelected(boolean selected) {
        selectDataSourceComboBox.setEnabled(selected);
        if (selectDataSourceComboBox.isEnabled()) {
            selectDataSourceComboBox.setSelectedIndex(0);
            singleDataSource = true;
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allDataSourcesRadioButton;
    private javax.swing.JButton cancelButton;
    private javax.swing.ButtonGroup dataSourcesButtonGroup;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JButton searchButton;
    private javax.swing.JComboBox<String> selectDataSourceComboBox;
    private javax.swing.JRadioButton withinDataSourceRadioButton;
    // End of variables declaration//GEN-END:variables
}
