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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
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

    private static final Logger LOGGER = Logger.getLogger(CommonFilesPanel.class.getName());

    /**
     * Creates new form CommonFilesPanel
     */
    public CommonFilesPanel() {
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

        new SwingWorker<CommonFilesMetaData, Void>() {

            @Override
            @SuppressWarnings("FinallyDiscardsException")
            protected CommonFilesMetaData doInBackground() throws TskCoreException, NoCurrentCaseException, SQLException { //return type should be CommonFilesMetaData - done will be adjusted accordingly
                
                //contents of this whole function should be wrapped in a business logic class for the sake of testing
                
                /*
                
                Use this query to grab mapping of object_id to datasourcename:
                    select name, obj_id from tsk_files where obj_id in (SELECT obj_id FROM tsk_objects WHERE obj_id in (select obj_id from data_source_info));
                
                Use SleuthkitCase.executeSql to run the query and get back a result set which can be iterated like a regular old jdbc object.
                
                Use file.getDataSourceID() to get datasourceid and map it to the appropriate row from above
                */

//                Case currentCase = Case.getOpenCase();
//                SleuthkitCase tskDb = currentCase.getSleuthkitCase();
//                
//                CaseDbQuery query = tskDb.executeQuery("select obj_id, name from tsk_files where obj_id in (SELECT obj_id FROM tsk_objects WHERE obj_id in (select obj_id from data_source_info))");
//
//                ResultSet resultSet = query.getResultSet();
//                
//                Map<Long, String> dataSourceMap = new HashMap<>();
//                
//                while(resultSet.next()){
//                    Long objectId = resultSet.getLong(1);
//                    String dataSourceName = resultSet.getString(2);
//                    dataSourceMap.put(objectId, dataSourceName);
//                }
                
                return new CommonFilesMetaData();
            }

            @Override
            protected void done() {
                try {
                    super.done();

                    CommonFilesMetaData metadata = get();
                                        
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

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        searchButton = new javax.swing.JButton();

        setPreferredSize(new java.awt.Dimension(300, 300));

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(CommonFilesPanel.class, "CommonFilesPanel.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(325, Short.MAX_VALUE)
                .addComponent(searchButton)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(266, Short.MAX_VALUE)
                .addComponent(searchButton)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        search();
    }//GEN-LAST:event_searchButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton searchButton;
    // End of variables declaration//GEN-END:variables
}
