/*
 * 
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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.ComboBoxModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author bsweeney
 */
public class IntraCasePanel extends javax.swing.JPanel {
    
    private static final long serialVersionUID = 1L;
        
    private static final Logger LOGGER = Logger.getLogger(CommonFilesPanel.class.getName());
        
    private boolean singleDataSource;
    private String selectedDataSource;
    private ComboBoxModel<String> dataSourcesList = new DataSourceComboBoxModel();
    private Map<Long, String> dataSourceMap;
    private CommonFilesPanel parent;

    /**
     * Creates new form IntraCasePanel
     */
    public IntraCasePanel() {
        initComponents();
        
        this.setupDataSources();
    }
    
    public void setParent(CommonFilesPanel parent){
        this.parent = parent;
    }
    
    public void setDataSourceList(){
        //TODO
    }
    
    public boolean isSingleDataSource(){
        return this.singleDataSource;
    }
    
    public String getSelectedDataSource(){
        if(this.singleDataSource && this.selectedDataSource != null){
            return selectedDataSource;            
        } else {
            return "";
        }        
    }
    
    public Map<Long, String> getDataSourceMap(){
        return this.dataSourceMap;
    }
    
    /**
     * Sets up the data sources dropdown and returns the data sources map for
     * future usage.
     *
     * @return a mapping of data source ids to data source names
     */
    @NbBundle.Messages({
        "IntraCasePanel.setupDataSources.done.tskCoreException=Unable to run query against DB.",
        "IntraCasePanel.setupDataSources.done.noCurrentCaseException=Unable to open case file.",
        "IntraCasePanel.setupDataSources.done.exception=Unexpected exception building data sources map.",
        "IntraCasePanel.setupDataSources.done.interupted=Something went wrong building the Common Files Search dialog box.",
        "IntraCasePanel.setupDataSources.done.sqlException=Unable to query db for data sources.",
        "IntraCasePanel.setupDataSources.updateUi.noDataSources=No data sources were found."})
    private void setupDataSources() {

        new SwingWorker<Map<Long, String>, Void>() {

            private static final String SELECT_DATA_SOURCES_LOGICAL = "select obj_id, name from tsk_files where obj_id in (SELECT obj_id FROM tsk_objects WHERE obj_id in (select obj_id from data_source_info))";

            private static final String SELECT_DATA_SOURCES_IMAGE = "select obj_id, name from tsk_image_names where obj_id in (SELECT obj_id FROM tsk_objects WHERE obj_id in (select obj_id from data_source_info))";

            private void updateUi() {

                String[] dataSourcesNames = new String[IntraCasePanel.this.dataSourceMap.size()];

                //only enable all this stuff if we actually have datasources
                if (dataSourcesNames.length > 0) {
                    dataSourcesNames = IntraCasePanel.this.dataSourceMap.values().toArray(dataSourcesNames);
                    //TODO use setter on intra case panel
                    IntraCasePanel.this.dataSourcesList = new DataSourceComboBoxModel(dataSourcesNames);
                    IntraCasePanel.this.selectDataSourceComboBox.setModel(IntraCasePanel.this.dataSourcesList);

                    boolean multipleDataSources = this.caseHasMultipleSources();
                    IntraCasePanel.this.allDataSourcesRadioButton.setEnabled(multipleDataSources);
                    IntraCasePanel.this.allDataSourcesRadioButton.setSelected(multipleDataSources);

                    if (!multipleDataSources) {
                        IntraCasePanel.this.withinDataSourceRadioButton.setSelected(true);
                        withinDataSourceSelected(true);
                    }

                    IntraCasePanel.this.parent.setSearchButtonEnabled(true);
                } else {
                    MessageNotifyUtil.Message.info(Bundle.IntraCasePanel_setupDataSources_updateUi_noDataSources());
                    SwingUtilities.windowForComponent(IntraCasePanel.this.parent).dispose();
                }
            }

            private boolean caseHasMultipleSources() {
                return IntraCasePanel.this.dataSourceMap.size() >= 2;
            }

            private void loadLogicalSources(SleuthkitCase tskDb, Map<Long, String> dataSouceMap) throws TskCoreException, SQLException {
                //try block releases resources - exceptions are handled in done()
                try (
                        SleuthkitCase.CaseDbQuery query = tskDb.executeQuery(SELECT_DATA_SOURCES_LOGICAL);
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
                        SleuthkitCase.CaseDbQuery query = tskDb.executeQuery(SELECT_DATA_SOURCES_IMAGE);
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
                    IntraCasePanel.this.dataSourceMap = this.get();

                    updateUi();

                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, "Interrupted while building Common Files Search dialog.", ex);
                    MessageNotifyUtil.Message.error(Bundle.IntraCasePanel_setupDataSources_done_interupted());
                } catch (ExecutionException ex) {
                    String errorMessage;
                    Throwable inner = ex.getCause();
                    if (inner instanceof TskCoreException) {
                        LOGGER.log(Level.SEVERE, "Failed to load data sources from database.", ex);
                        errorMessage = Bundle.IntraCasePanel_setupDataSources_done_tskCoreException();
                    } else if (inner instanceof NoCurrentCaseException) {
                        LOGGER.log(Level.SEVERE, "Current case has been closed.", ex);
                        errorMessage = Bundle.IntraCasePanel_setupDataSources_done_noCurrentCaseException();
                    } else if (inner instanceof SQLException) {
                        LOGGER.log(Level.SEVERE, "Unable to query db for data sources.", ex);
                        errorMessage = Bundle.IntraCasePanel_setupDataSources_done_sqlException();
                    } else {
                        LOGGER.log(Level.SEVERE, "Unexpected exception while building Common Files Search dialog panel.", ex);
                        errorMessage = Bundle.IntraCasePanel_setupDataSources_done_exception();
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

        buttonGroup = new javax.swing.ButtonGroup();
        allDataSourcesRadioButton = new javax.swing.JRadioButton();
        withinDataSourceRadioButton = new javax.swing.JRadioButton();
        selectDataSourceComboBox = new javax.swing.JComboBox<>();

        buttonGroup.add(allDataSourcesRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(allDataSourcesRadioButton, org.openide.util.NbBundle.getMessage(IntraCasePanel.class, "IntraCasePanel.allDataSourcesRadioButton.text")); // NOI18N
        allDataSourcesRadioButton.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        allDataSourcesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allDataSourcesRadioButtonActionPerformed(evt);
            }
        });

        buttonGroup.add(withinDataSourceRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(withinDataSourceRadioButton, org.openide.util.NbBundle.getMessage(IntraCasePanel.class, "IntraCasePanel.withinDataSourceRadioButton.text")); // NOI18N
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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(withinDataSourceRadioButton)
                    .addComponent(allDataSourcesRadioButton)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(20, 20, 20)
                        .addComponent(selectDataSourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 261, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(allDataSourcesRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(withinDataSourceRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectDataSourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void allDataSourcesRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allDataSourcesRadioButtonActionPerformed
        selectDataSourceComboBox.setEnabled(!allDataSourcesRadioButton.isSelected());
        singleDataSource = false;
    }//GEN-LAST:event_allDataSourcesRadioButtonActionPerformed

    private void withinDataSourceRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_withinDataSourceRadioButtonActionPerformed
        withinDataSourceSelected(withinDataSourceRadioButton.isSelected());
    }//GEN-LAST:event_withinDataSourceRadioButtonActionPerformed

    private void selectDataSourceComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectDataSourceComboBoxActionPerformed
        final Object selectedItem = selectDataSourceComboBox.getSelectedItem();
        if (selectedItem != null) {
            selectedDataSource = selectedItem.toString();
        } else {
            selectedDataSource = "";
        }
    }//GEN-LAST:event_selectDataSourceComboBoxActionPerformed

    private void withinDataSourceSelected(boolean selected) {
        selectDataSourceComboBox.setEnabled(selected);
        if (selectDataSourceComboBox.isEnabled()) {
            selectDataSourceComboBox.setSelectedIndex(0);
            singleDataSource = true;
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allDataSourcesRadioButton;
    private javax.swing.ButtonGroup buttonGroup;
    private javax.swing.JComboBox<String> selectDataSourceComboBox;
    private javax.swing.JRadioButton withinDataSourceRadioButton;
    // End of variables declaration//GEN-END:variables
}
