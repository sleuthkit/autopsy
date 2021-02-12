/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.DefaultListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Viewer panel widget for keyword lists that is used in the ingest config and
 * options area.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class TermsExtractionDialog extends javax.swing.JDialog {

    private static final Logger logger = Logger.getLogger(TermsExtractionDialog.class.getName());
    
    // ELTODO
    private final String keywordSearchErrorDialogHeader = org.openide.util.NbBundle.getMessage(this.getClass(), "AbstractKeywordSearchPerformer.search.dialogErrorHeader");
    private static TermsExtractionDialog instance;
    private ActionListener searchAddListener;
    private boolean ingestRunning;
    private final Map<Long, String> dataSourceMap = new HashMap<>();
    private List<DataSource> dataSources = new ArrayList<>();
    private final DefaultListModel<String> dataSourceListModel = new DefaultListModel<>();    

    /**
     * Creates new form TermsExtractionPanel
     */
    private TermsExtractionDialog() {
        initComponents();
        customizeComponents();
        dataSourceList.setModel(getDataSourceListModel());

        dataSourceList.addListSelectionListener((ListSelectionEvent evt) -> {
            firePropertyChange(Bundle.DropdownSingleTermSearchPanel_selected(), null, null); // ELTODO
        });
    }

    static synchronized TermsExtractionDialog getDefault() {
        if (instance == null) {
            instance = new TermsExtractionDialog();
        }
        return instance;
    }
    
    /**
     * Display the Search Other Cases dialog.
     */
    void display() {
        updateComponents();
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    private void customizeComponents() {

        ingestRunning = IngestManager.getInstance().isIngestRunning();
        updateComponents();

        IngestManager.getInstance().addIngestJobEventListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Object source = evt.getSource();
                if (source instanceof String && ((String) source).equals("LOCAL")) { //NON-NLS
                    EventQueue.invokeLater(() -> {
                        ingestRunning = IngestManager.getInstance().isIngestRunning();
                        updateComponents();
                    });
                }
            }
        });

        searchAddListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (ingestRunning) {
// ELTODO                    IngestSearchRunner.getInstance().addKeywordListsToAllJobs(listsTableModel.getSelectedLists());
                    logger.log(Level.INFO, "Ingest is running"); //NON-NLS
                } else {
                    searchAction(e);
                }
            }
        };

        extractTermsButton.addActionListener(searchAddListener);
    }

    private void updateComponents() {
        ingestRunning = IngestManager.getInstance().isIngestRunning();
        if (ingestRunning) { // ELTODO
            extractTermsButton.setText(NbBundle.getMessage(this.getClass(), "KeywordSearchListsViewerPanel.initIngest.addIngestTitle"));
            extractTermsButton.setToolTipText(NbBundle.getMessage(this.getClass(), "KeywordSearchListsViewerPanel.initIngest.addIngestMsg"));

        } else {
            extractTermsButton.setText(NbBundle.getMessage(this.getClass(), "KeywordSearchListsViewerPanel.initIngest.searchIngestTitle"));
            extractTermsButton.setToolTipText(NbBundle.getMessage(this.getClass(), "KeywordSearchListsViewerPanel.initIngest.addIdxSearchMsg"));
        }
        
        try {
            updateDataSourceListModel();
        } catch (Exception ex) {
            // ELTODO
            logger.log(Level.SEVERE, "Unable to populate list of data sources", ex); //NON-NLS
        }
    }

     /**
     * Get a list of data source display names.
     *
     * @return The list of data source name
     */
    synchronized List<String> getDataSourceArray() {
        List<String> dsList = new ArrayList<>();
        Collections.sort(this.dataSources, (DataSource ds1, DataSource ds2) -> ds1.getName().compareTo(ds2.getName()));
        for (DataSource ds : dataSources) {
            String dsName = ds.getName();
            File dataSourceFullName = new File(dsName);
            String displayName = dataSourceFullName.getName();
            dataSourceMap.put(ds.getId(), displayName);
            dsList.add(displayName);
        }
        return dsList;
    }

    /**
     * Set dataSources
     * @param dataSources A list of DataSource 
     */
    synchronized void setDataSources(List<DataSource> dataSources) {
        this.dataSources = dataSources;
    }

    /**
     * Get dataSourceMap with object id and data source display name.
     *
     * @return The list of data source name
     */
    Map<Long, String> getDataSourceMap() {
        return dataSourceMap;
    }
    
    /**
     * Get a list of DataSourceListModel
     * @return A list of DataSourceListModel
     */
    final DefaultListModel<String> getDataSourceListModel() {
        return dataSourceListModel;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        extractTermsButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        dataSourceList = new javax.swing.JList<>();
        selectDataSourceLabel = new javax.swing.JLabel();

        setSize(new java.awt.Dimension(500, 200));

        extractTermsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/search-icon.png"))); // NOI18N
        extractTermsButton.setText(org.openide.util.NbBundle.getMessage(TermsExtractionDialog.class, "KeywordSearchListsViewerPanel.searchAddButton.text")); // NOI18N
        extractTermsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extractTermsButtonActionPerformed(evt);
            }
        });

        dataSourceList.setMinimumSize(new java.awt.Dimension(0, 200));
        jScrollPane1.setViewportView(dataSourceList);

        selectDataSourceLabel.setFont(selectDataSourceLabel.getFont().deriveFont(selectDataSourceLabel.getFont().getSize()-1f));
        selectDataSourceLabel.setText(org.openide.util.NbBundle.getMessage(TermsExtractionDialog.class, "TermsExtractionDialog.selectDataSourceLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(extractTermsButton)
                    .addComponent(selectDataSourceLabel))
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addComponent(selectDataSourceLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 13, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(extractTermsButton)
                .addContainerGap(20, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void extractTermsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extractTermsButtonActionPerformed
    }//GEN-LAST:event_extractTermsButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<String> dataSourceList;
    private javax.swing.JButton extractTermsButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel selectDataSourceLabel;
    // End of variables declaration//GEN-END:variables

    private void searchAction(ActionEvent e) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        try {
            search();
        } finally {
            setCursor(null);
        }
    }
    
    /**
     * Performs the search using the selected keywords. Creates a
     * DataResultTopComponent with the results.
     *
     * @param saveResults Flag whether to save search results as KWS artifacts.
     */
    void search() {
        boolean isIngestRunning = IngestManager.getInstance().isIngestRunning();

        int filesIndexed = 0;
            try { // see if another node added any indexed files
                filesIndexed = KeywordSearch.getServer().queryNumIndexedFiles();
            } catch (KeywordSearchModuleException | NoOpenCoreException ignored) {
            }

        if (filesIndexed == 0) {
            // ELTODO
            if (isIngestRunning) {
                KeywordSearchUtil.displayDialog(keywordSearchErrorDialogHeader, NbBundle.getMessage(this.getClass(),
                        "AbstractKeywordSearchPerformer.search.noFilesInIdxMsg",
                        KeywordSearchSettings.getUpdateFrequency().getTime()), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
            } else {
                KeywordSearchUtil.displayDialog(keywordSearchErrorDialogHeader, NbBundle.getMessage(this.getClass(),
                        "AbstractKeywordSearchPerformer.search.noFilesIdxdMsg"), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
            }
            return;
        }

        //check if keyword search module  ingest is running (indexing, etc)
        if (isIngestRunning) {
            if (KeywordSearchUtil.displayConfirmDialog(org.openide.util.NbBundle.getMessage(this.getClass(), "AbstractKeywordSearchPerformer.search.searchIngestInProgressTitle"),
                    NbBundle.getMessage(this.getClass(), "AbstractKeywordSearchPerformer.search.ingestInProgressBody"), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN) == false) {
                return;
            }
        }

        final Server server = KeywordSearch.getServer();
        Long dsID = Long.valueOf(4);
        Set<Long> selectedDs = getDataSourcesSelected();
        try {
            server.extractAllTermsForDataSource(selectedDs.iterator().next()); //ELTODO check if not empty
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    void addSearchButtonActionListener(ActionListener al) {
        extractTermsButton.addActionListener(al);
    }

   /**
     * Get a set of data source object ids that are selected.
     * @return A set of selected object ids. 
     */
    Set<Long> getDataSourcesSelected() {
        Set<Long> dataSourceObjIdSet = new HashSet<>();
        for (Long key : getDataSourceMap().keySet()) {
            String value = getDataSourceMap().get(key);
            for (String dataSource : this.dataSourceList.getSelectedValuesList()) {
                if (value.equals(dataSource)) {
                    dataSourceObjIdSet.add(key);
                }
            }
        }
        return dataSourceObjIdSet;
    }
    
    /**
     * Update the dataSourceListModel
     */
    @NbBundle.Messages({"TermsExtractionPanel.selected=Ad Hoc Search data source filter is selected"})
    void updateDataSourceListModel() throws NoCurrentCaseException, TskCoreException {
        dataSources = getDataSourceList();
        getDataSourceListModel().removeAllElements();
        for (String dsName : getDataSourceArray()) {
            getDataSourceListModel().addElement(dsName);
        }
        setComponentsEnabled();
        firePropertyChange(Bundle.TermsExtractionPanel_selected(), null, null); // ELTODO        
    }
    
    /**
     * Get a list of DataSource from case database
     * @return A list of DataSource
     * @throws NoCurrentCaseException
     * @throws TskCoreException 
     */
    private synchronized List<DataSource> getDataSourceList() throws NoCurrentCaseException, TskCoreException {
        Case openCase = Case.getCurrentCaseThrows();
        return openCase.getSleuthkitCase().getDataSources();
    }    
    
    /**
     * Set the dataSourceList enabled if there are data sources to select
     */
    private void setComponentsEnabled() {
        
        this.dataSourceList.setEnabled(false);
        extractTermsButton.setEnabled(false);
        if (getDataSourceListModel().size() > 1) {
            this.dataSourceList.setEnabled(true);
            this.dataSourceList.setSelectionInterval(0, this.dataSourceList.getModel().getSize()-1);
            dataSourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // do not allow multiselect
            extractTermsButton.setEnabled(true);
        } else if (getDataSourceListModel().size() == 1) {
            this.dataSourceList.setEnabled(false);
            this.dataSourceList.setSelectionInterval(0, 0);
            extractTermsButton.setEnabled(true);
        }
    }
}
