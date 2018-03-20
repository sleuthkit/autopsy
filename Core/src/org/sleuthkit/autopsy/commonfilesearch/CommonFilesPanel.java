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

import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import static java.awt.event.ItemEvent.SELECTED;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.SwingWorker;
import javax.swing.event.ListDataListener;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel used for common files search configuration and configuration business
 * logic. Nested within CommonFilesDialog.
 */
public final class CommonFilesPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    
    private final ComboBoxModel<String> dataSourcesList;

    private static final Logger LOGGER = Logger.getLogger(CommonFilesPanel.class.getName());

    /**
     * Creates new form CommonFilesPanel
     */
    public CommonFilesPanel() {
        this.dataSourcesList = new DataSourceComboBoxModel(new String[1]);
        initComponents();
    }

    void addListenerToAll(ActionListener l) {       //TODO double click the button
        this.searchButton.addActionListener(l);
    }

    @NbBundle.Messages({
        "CommonFilesPanel.search.results.title=Common Files",
        "CommonFilesPanel.search.results.pathText=Common Files Search Results\\:",
        "CommonFilesPanel.search.done.tskCoreException=Unable to run query against DB.",
        "CommonFilesPanel.search.done.noCurrentCaseException=Unable to open case file.",
        "CommonFilesPanel.search.done.exception=Unexpected exception running Common Files Search.",
        "CommonFilesPanel.search.done.interupted=Something went wrong finding common files."})
    private void search() {

        String title = Bundle.CommonFilesPanel_search_results_title();
        String pathText = Bundle.CommonFilesPanel_search_results_pathText();

        new SwingWorker<List<AbstractFile>, Void>() {

            @Override
            @SuppressWarnings("FinallyDiscardsException")
            protected List<AbstractFile> doInBackground() throws TskCoreException, NoCurrentCaseException, SQLException { //return type should be CommonFilesMetaData - done will be adjusted accordingly
                
                //contents of this whole function should be wrapped in a business logic class for the sake of testing
                
                /*
                
                Use this query to grab mapping of object_id to datasourcename:
                    select name, obj_id from tsk_files where obj_id in (SELECT obj_id FROM tsk_objects WHERE obj_id in (select obj_id from data_source_info));
                
                Use SleuthkitCase.executeSql to run the query and get back a result set which can be iterated like a regular old jdbc object.
                
                Use file.getDataSourceID() to get datasourceid and map it to the appropriate row from above
                */

                Case currentCase = Case.getOpenCase();
                SleuthkitCase tskDb = currentCase.getSleuthkitCase();
                
                CaseDbQuery query = tskDb.executeQuery("select obj_id, name from tsk_files where obj_id in (SELECT obj_id FROM tsk_objects WHERE obj_id in (select obj_id from data_source_info))");

                ResultSet resultSet = query.getResultSet();
                
                Map<Long, String> dataSourceMap = new HashMap<>();
                
                while(resultSet.next()){
                    Long objectId = resultSet.getLong(1);
                    String dataSourceName = resultSet.getString(2);
                    dataSourceMap.put(objectId, dataSourceName);
                }
                
                return tskDb.findAllFilesWhere("md5 in (select md5 from tsk_files where (known != 1 OR known IS NULL) GROUP BY  md5 HAVING  COUNT(*) > 1) order by md5");
            }

            @Override
            protected void done() {
                try {
                    super.done();

                    List<AbstractFile> contentList = get();                                         //
                                                                                                    //// To background thread (doInBackground)
                    CommonFilesMetaData metadata = CommonFilesMetaData.CollateFiles(contentList);    //
                                        
                    CommonFilesSearchNode contentFilesNode = new CommonFilesSearchNode(metadata);

                    DataResultFilterNode dataResultFilterNode = new DataResultFilterNode(contentFilesNode, DirectoryTreeTopComponent.findInstance().getExplorerManager());
                                        
                    TableFilterNode tableFilterNode = new TableFilterNode(dataResultFilterNode, true);
                    
                    TopComponent component = DataResultTopComponent.createInstance(
                            title,
                            pathText,
                            tableFilterNode,
                            metadata.getFilesList().size());

                    component.requestActive(); // make it the active top component

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
                        LOGGER.log(Level.SEVERE, "Current case has been closed", ex); //NON-NLS
                        errorMessage = Bundle.CommonFilesPanel_search_done_noCurrentCaseException();
                    } else {
                        LOGGER.log(Level.SEVERE, "Unexpected exception while running Common Files Search", ex);
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
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        allDatasourcesRadio = new javax.swing.JRadioButton();
        withinDataSourceRadioButton = new javax.swing.JRadioButton();
        selectDataSourceComboBox = new javax.swing.JComboBox<>();

        setPreferredSize(new java.awt.Dimension(300, 300));

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        dataSourcesButtonGroup.add(allDatasourcesRadio);
        allDatasourcesRadio.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(allDatasourcesRadio, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.allDatasourcesRadio.text")); // NOI18N
        allDatasourcesRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allDatasourcesRadioActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(withinDataSourceRadioButton, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.withinDataSourceRadioButton.text")); // NOI18N
        withinDataSourceRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                withinDataSourceRadioButtonActionPerformed(evt);
            }
        });

        selectDataSourceComboBox.setModel(dataSourcesList);
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
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(searchButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(allDatasourcesRadio)
                            .addComponent(withinDataSourceRadioButton)
                            .addComponent(selectDataSourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(allDatasourcesRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(withinDataSourceRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectDataSourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 90, Short.MAX_VALUE)
                .addComponent(searchButton)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        search();
    }//GEN-LAST:event_searchButtonActionPerformed

    private void allDatasourcesRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allDatasourcesRadioActionPerformed
         selectDataSourceComboBox.setEnabled(evt.getID() != SELECTED);
        
    }//GEN-LAST:event_allDatasourcesRadioActionPerformed

    private void selectDataSourceComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectDataSourceComboBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_selectDataSourceComboBoxActionPerformed

    private void withinDataSourceRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_withinDataSourceRadioButtonActionPerformed
        selectDataSourceComboBox.setEnabled(evt.getID() == SELECTED);
    }//GEN-LAST:event_withinDataSourceRadioButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allDatasourcesRadio;
    private javax.swing.ButtonGroup dataSourcesButtonGroup;
    private javax.swing.JButton searchButton;
    private javax.swing.JComboBox<String> selectDataSourceComboBox;
    private javax.swing.JRadioButton withinDataSourceRadioButton;
    // End of variables declaration//GEN-END:variables
}
