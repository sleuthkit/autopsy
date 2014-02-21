/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.hashdatabase;

import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.autopsy.hashdatabase.HashDbManager.HashDb.KnownFilesType;

/**
 * Instances of this class provide a comprehensive UI for managing the hash sets configuration.
 */
public final class HashDbConfigPanel extends javax.swing.JPanel implements OptionsPanel {
    private static final String NO_SELECTION_TEXT = NbBundle
            .getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.noSelectionText");
    private static final String ERROR_GETTING_PATH_TEXT = NbBundle
            .getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.errorGettingPathText");
    private static final String ERROR_GETTING_INDEX_STATUS_TEXT = NbBundle
            .getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.errorGettingIndexStatusText");
    private HashDbManager hashSetManager = HashDbManager.getInstance();
    private HashSetTableModel hashSetTableModel = new HashSetTableModel();    
        
    public HashDbConfigPanel() {
        initComponents();
        customizeComponents();
        updateComponentsForNoSelection();
        
        // Listen to the ingest modules to refresh the enabled/disabled state of 
        // the components in sync with file ingest.
        IngestManager.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (isFileIngestStatusChangeEvent(evt)) {
                    updateComponents();
                }
            }
        });                
    }
        
    private void customizeComponents() {
        setName(NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.setName.hashSetConfig"));
        this.ingestWarningLabel.setVisible(false);
        this.hashSetTable.setModel(hashSetTableModel);
        this.hashSetTable.setTableHeader(null);
        hashSetTable.getParent().setBackground(hashSetTable.getBackground());
        hashSetTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hashSetTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    updateComponents();
                }
            }
        });        
    }

    private void updateComponents() {
        HashDb db = ((HashSetTable)hashSetTable).getSelection();
        if (db != null) {
            updateComponentsForSelection(db);
        } 
        else {
            updateComponentsForNoSelection();
        }            
    }
    
    private void updateComponentsForNoSelection() {
        boolean ingestIsRunning = IngestManager.getDefault().isIngestRunning();        

        // Update descriptive labels.
        hashDbNameLabel.setText(NO_SELECTION_TEXT);
        hashDbTypeLabel.setText(NO_SELECTION_TEXT);
        hashDbLocationLabel.setText(NO_SELECTION_TEXT);
        indexPathLabel.setText(NO_SELECTION_TEXT);

        // Update indexing components.
        hashDbIndexStatusLabel.setText(NO_SELECTION_TEXT);
        hashDbIndexStatusLabel.setForeground(Color.black);
        indexButton.setText(NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.index"));
        indexButton.setEnabled(false);            

        // Update ingest options.
        searchDuringIngestCheckbox.setSelected(false);
        searchDuringIngestCheckbox.setEnabled(false);
        sendIngestMessagesCheckBox.setSelected(false);
        sendIngestMessagesCheckBox.setEnabled(false);
        optionsLabel.setEnabled(false);
        optionsSeparator.setEnabled(false);
        
        // Update database action buttons.
        createDatabaseButton.setEnabled(true);
        importDatabaseButton.setEnabled(true);
        deleteDatabaseButton.setEnabled(false);
        
        // Update ingest in progress warning label.
        ingestWarningLabel.setVisible(ingestIsRunning);        
    }

    private void updateComponentsForSelection(HashDb db) {                
        boolean ingestIsRunning = IngestManager.getDefault().isIngestRunning();        

        // Update descriptive labels.        
        hashDbNameLabel.setText(db.getHashSetName());
        hashDbTypeLabel.setText(db.getKnownFilesType().getDisplayName());
        
        try {
            hashDbLocationLabel.setText(shortenPath(db.getDatabasePath()));
        }
        catch (TskCoreException ex) {
            Logger.getLogger(HashDbConfigPanel.class.getName()).log(Level.SEVERE, "Error getting database path of " + db.getHashSetName() + " hash database", ex);
            hashDbLocationLabel.setText(ERROR_GETTING_PATH_TEXT);
        }
        
        try {
            indexPathLabel.setText(shortenPath(db.getIndexPath()));
        }
        catch (TskCoreException ex) {
            Logger.getLogger(HashDbConfigPanel.class.getName()).log(Level.SEVERE, "Error getting index path of " + db.getHashSetName() + " hash database", ex);
            indexPathLabel.setText(ERROR_GETTING_PATH_TEXT);
        }
        
        // Update indexing components.
        try {
            if (db.isIndexing()) {
                indexButton.setText(
                        NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.indexing"));
                hashDbIndexStatusLabel.setText(
                        NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexStatusText.indexGen"));
                hashDbIndexStatusLabel.setForeground(Color.black);
                indexButton.setEnabled(false);
            }         
            else if (db.hasIndex()) {
                if (db.hasIndexOnly()) {
                    hashDbIndexStatusLabel.setText(
                            NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexStatusText.indexOnly"));
                }
                else {
                    hashDbIndexStatusLabel.setText(
                            NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexStatusText.indexed"));
                }
                hashDbIndexStatusLabel.setForeground(Color.black);
                if (db.canBeReIndexed()) {
                    indexButton.setText(
                            NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.reIndex"));
                    indexButton.setEnabled(true);
                }
                else {
                    indexButton.setText(NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.index"));
                    indexButton.setEnabled(false);                    
                }
            }
            else {
                hashDbIndexStatusLabel.setText(
                        NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexStatusText.noIndex"));
                hashDbIndexStatusLabel.setForeground(Color.red);
                indexButton.setText(NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.index"));
                indexButton.setEnabled(true);
            }
        }
        catch (TskCoreException ex) {
            Logger.getLogger(HashDbConfigPanel.class.getName()).log(Level.SEVERE, "Error getting index state of hash database", ex);                
            hashDbIndexStatusLabel.setText(ERROR_GETTING_INDEX_STATUS_TEXT);
            hashDbIndexStatusLabel.setForeground(Color.red);
            indexButton.setText(NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.index"));
            indexButton.setEnabled(false);
        }     
        
        // Disable the indexing button if ingest is in progress.
        if (ingestIsRunning) {
            indexButton.setEnabled(false);
        }

        // Update ingest option components.        
        searchDuringIngestCheckbox.setSelected(db.getSearchDuringIngest());
        searchDuringIngestCheckbox.setEnabled(!ingestIsRunning);
        sendIngestMessagesCheckBox.setSelected(db.getSendIngestMessages());
        sendIngestMessagesCheckBox.setEnabled(!ingestIsRunning && db.getSearchDuringIngest() && db.getKnownFilesType().equals(KnownFilesType.KNOWN_BAD));
        optionsLabel.setEnabled(!ingestIsRunning);
        optionsSeparator.setEnabled(!ingestIsRunning);
          
        // Update database action buttons.
        createDatabaseButton.setEnabled(true);
        importDatabaseButton.setEnabled(true);
        deleteDatabaseButton.setEnabled(!ingestIsRunning);
        
        // Update ingest in progress warning label.
        ingestWarningLabel.setVisible(ingestIsRunning);        
    }
        
    private static String shortenPath(String path) {
        String shortenedPath = path;
        if (shortenedPath.length() > 50){
            shortenedPath = shortenedPath.substring(0, 10 + shortenedPath.substring(10).indexOf(File.separator) + 1) + "..." + shortenedPath.substring((shortenedPath.length() - 20) + shortenedPath.substring(shortenedPath.length() - 20).indexOf(File.separator));
        }
        return shortenedPath;
    }
    
    private boolean isFileIngestStatusChangeEvent(PropertyChangeEvent evt) {        
        return evt.getPropertyName().equals(IngestManager.IngestModuleEvent.STARTED.toString()) || evt.getPropertyName().equals(IngestManager.IngestModuleEvent.COMPLETED.toString()) || evt.getPropertyName().equals(IngestManager.IngestModuleEvent.STOPPED.toString());         
    }
    
    @Override
    public void load() {
        hashSetTable.clearSelection();
        hashSetTableModel.refreshModel();         
    }

    @Override
    public void store() {
        //Checking for for any unindexed databases
        List<HashDb> unindexed = new ArrayList<>();
        for (HashDb hashSet : hashSetManager.getAllHashSets()) {
            try {
                if (!hashSet.hasIndex()) {
                    unindexed.add(hashSet);
                }
            }
            catch (TskCoreException ex) {
                Logger.getLogger(HashDbConfigPanel.class.getName()).log(Level.SEVERE, "Error getting index info for hash database", ex);                
            }
        }

        //If unindexed ones are found, show a popup box that will either index them, or remove them.
        if (unindexed.size() == 1){
            showInvalidIndex(false, unindexed);
        }
        else if (unindexed.size() > 1){
            showInvalidIndex(true, unindexed);
        }

        hashSetManager.save();        
    }
    
    public void cancel() {
        HashDbManager.getInstance().loadLastSavedConfiguration();                        
    }
    
    void removeThese(List<HashDb> toRemove) {
        for (HashDb hashDb : toRemove) {
            hashSetManager.removeHashDatabaseInternal(hashDb);
        }
        hashSetTableModel.refreshModel();        
    }
    
    /**
     * Displays the popup box that tells user that some of his databases are unindexed, along with solutions.
     * This method is related to ModalNoButtons, to be removed at a later date.
     * @param plural Whether or not there are multiple unindexed databases
     * @param unindexed The list of unindexed databases. Can be of size 1.
     */
    private void showInvalidIndex(boolean plural, List<HashDb> unindexed){
        String total = "";
        String message;
        for(HashDb hdb : unindexed){
            total+= "\n" + hdb.getHashSetName();
        }
        if(plural){
            message = NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.dbsNotIndexedMsg", total);
        }
        else{
            message = NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.dbNotIndexedMsg", total);
        }
        int res = JOptionPane.showConfirmDialog(this, message,
                                                NbBundle.getMessage(this.getClass(),
                                                                                   "HashDbConfigPanel.unindexedDbsMsg"),
                                                JOptionPane.YES_NO_OPTION);
        if(res == JOptionPane.YES_OPTION){
            ModalNoButtons indexingDialog = new ModalNoButtons(this, new Frame(),unindexed);
            indexingDialog.setLocationRelativeTo(null);
            indexingDialog.setVisible(true);
            indexingDialog.setModal(true);
            hashSetTableModel.refreshModel();
        }
        if(res == JOptionPane.NO_OPTION){
            JOptionPane.showMessageDialog(this, NbBundle.getMessage(this.getClass(),
                                                                    "HashDbConfigPanel.allUnindexedDbsRmFromListMsg"));
            removeThese(unindexed);
        }
    }

    boolean valid() {
        return true;
    }
        
    /** 
     * This class implements a table for displaying configured hash sets.
     */ 
    private class HashSetTable extends JTable {
        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            // Use the hash set name as the cell text.
            JComponent cellRenderer = (JComponent)super.prepareRenderer(renderer, row, column);
            cellRenderer.setToolTipText((String)getValueAt(row, column));
            
            // Give the user a visual indication of any hash sets with a hash
            // database that needs to be indexed by displaying the hash set name
            // in red.
            if (hashSetTableModel.indexExists(row)){
                cellRenderer.setForeground(Color.black);
            }
            else{
                cellRenderer.setForeground(Color.red);
            }
            
            return cellRenderer;
        }
    
        public HashDb getSelection() {
            return hashSetTableModel.getHashSetAt(getSelectionModel().getMinSelectionIndex());
        }

        public void setSelection(int index) {
            if (index >= 0 && index < hashSetTable.getRowCount()) {
                getSelectionModel().setSelectionInterval(index, index);
            }
        }
        
        public void selectRowByName(String name) {
            setSelection(hashSetTableModel.getIndexByName(name));
        }        
    }
    
    /**
     * This class implements the table model for the table used to display
     * configured hash sets.
     */  
    private class HashSetTableModel extends AbstractTableModel {   
        List<HashDb> hashSets = HashDbManager.getInstance().getAllHashSets();
            
        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public int getRowCount() {
            return hashSets.size();
        }

        @Override
        public String getColumnName(int column) {
            return NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.nameColLbl");
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return hashSets.get(rowIndex).getHashSetName();
        }
        
        private boolean indexExists(int rowIndex){
            try {
                return hashSets.get(rowIndex).hasIndex();
            }
            catch (TskCoreException ex) {
                Logger.getLogger(HashSetTableModel.class.getName()).log(Level.SEVERE, "Error getting index info for hash database", ex);                
                return false;
            }
        }
                                
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            throw new UnsupportedOperationException(
                    NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.editingCellsNotSupportedMsg"));
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        HashDb getHashSetAt(int index) {
            if (!hashSets.isEmpty() && index >= 0 && index < hashSets.size()) {
                return hashSets.get(index);            
            }
            else {
                return null;
            }
        }
        
        int getIndexByName(String name) {
            for (int i = 0; i < hashSets.size(); ++i) {
                if (hashSets.get(i).getHashSetName().equals(name)) {
                    return i;                    
                }                
            }                
            return -1;
        }
        
        void refreshModel() {
            hashSets = HashDbManager.getInstance().getAllHashSets();
            refreshDisplay();
        }
        
        void refreshDisplay() {
            fireTableDataChanged();            
        }
    }
            
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel2 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jButton3 = new javax.swing.JButton();
        ingestWarningLabel = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        hashSetTable = new HashSetTable();
        deleteDatabaseButton = new javax.swing.JButton();
        importDatabaseButton = new javax.swing.JButton();
        hashDatabasesLabel = new javax.swing.JLabel();
        nameLabel = new javax.swing.JLabel();
        hashDbNameLabel = new javax.swing.JLabel();
        hashDbLocationLabel = new javax.swing.JLabel();
        locationLabel = new javax.swing.JLabel();
        typeLabel = new javax.swing.JLabel();
        hashDbTypeLabel = new javax.swing.JLabel();
        hashDbIndexStatusLabel = new javax.swing.JLabel();
        indexLabel = new javax.swing.JLabel();
        indexButton = new javax.swing.JButton();
        searchDuringIngestCheckbox = new javax.swing.JCheckBox();
        sendIngestMessagesCheckBox = new javax.swing.JCheckBox();
        informationLabel = new javax.swing.JLabel();
        optionsLabel = new javax.swing.JLabel();
        informationSeparator = new javax.swing.JSeparator();
        optionsSeparator = new javax.swing.JSeparator();
        createDatabaseButton = new javax.swing.JButton();
        indexPathLabelLabel = new javax.swing.JLabel();
        indexPathLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.jLabel4.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel6, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.jLabel6.text")); // NOI18N

        jButton3.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(jButton3, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.jButton3.text")); // NOI18N

        setMinimumSize(new java.awt.Dimension(700, 500));
        setPreferredSize(new java.awt.Dimension(700, 500));

        ingestWarningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/hashdatabase/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(ingestWarningLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.ingestWarningLabel.text")); // NOI18N

        hashSetTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        hashSetTable.setShowHorizontalLines(false);
        hashSetTable.setShowVerticalLines(false);
        hashSetTable.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                hashSetTableKeyPressed(evt);
            }
        });
        jScrollPane1.setViewportView(hashSetTable);

        deleteDatabaseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/hashdatabase/delete16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteDatabaseButton, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.deleteDatabaseButton.text")); // NOI18N
        deleteDatabaseButton.setMaximumSize(new java.awt.Dimension(140, 25));
        deleteDatabaseButton.setMinimumSize(new java.awt.Dimension(140, 25));
        deleteDatabaseButton.setPreferredSize(new java.awt.Dimension(140, 25));
        deleteDatabaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteDatabaseButtonActionPerformed(evt);
            }
        });

        importDatabaseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/hashdatabase/import16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(importDatabaseButton, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.importDatabaseButton.text")); // NOI18N
        importDatabaseButton.setMaximumSize(new java.awt.Dimension(140, 25));
        importDatabaseButton.setMinimumSize(new java.awt.Dimension(140, 25));
        importDatabaseButton.setPreferredSize(new java.awt.Dimension(140, 25));
        importDatabaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importDatabaseButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(hashDatabasesLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.hashDatabasesLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(nameLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.nameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbNameLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.hashDbNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbLocationLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.hashDbLocationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(locationLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.locationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(typeLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.typeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbTypeLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.hashDbTypeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbIndexStatusLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.hashDbIndexStatusLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(indexLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.indexLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(indexButton, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.indexButton.text")); // NOI18N
        indexButton.setEnabled(false);
        indexButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                indexButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(searchDuringIngestCheckbox, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.searchDuringIngestCheckbox.text")); // NOI18N
        searchDuringIngestCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchDuringIngestCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(sendIngestMessagesCheckBox, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.sendIngestMessagesCheckBox.text")); // NOI18N
        sendIngestMessagesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sendIngestMessagesCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(informationLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.informationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(optionsLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.optionsLabel.text")); // NOI18N

        createDatabaseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/hashdatabase/new16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(createDatabaseButton, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.createDatabaseButton.text")); // NOI18N
        createDatabaseButton.setMaximumSize(new java.awt.Dimension(140, 25));
        createDatabaseButton.setMinimumSize(new java.awt.Dimension(140, 25));
        createDatabaseButton.setPreferredSize(new java.awt.Dimension(140, 25));
        createDatabaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createDatabaseButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(indexPathLabelLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.indexPathLabelLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(indexPathLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.indexPathLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 275, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(informationLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(informationSeparator))
                            .addComponent(ingestWarningLabel)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(locationLabel)
                                            .addComponent(indexButton, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(typeLabel)
                                            .addComponent(indexLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(indexPathLabelLabel))
                                        .addGap(10, 10, 10)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(hashDbTypeLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 241, Short.MAX_VALUE)
                                            .addComponent(hashDbLocationLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                            .addComponent(indexPathLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                            .addComponent(hashDbIndexStatusLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                                    .addGroup(layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(layout.createSequentialGroup()
                                                .addComponent(nameLabel)
                                                .addGap(53, 53, 53)
                                                .addComponent(hashDbNameLabel))
                                            .addComponent(searchDuringIngestCheckbox)
                                            .addGroup(layout.createSequentialGroup()
                                                .addGap(21, 21, 21)
                                                .addComponent(sendIngestMessagesCheckBox)))
                                        .addGap(0, 0, Short.MAX_VALUE))))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(optionsLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(optionsSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 324, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(hashDatabasesLabel)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(deleteDatabaseButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                                    .addComponent(createDatabaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(importDatabaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addGap(24, 24, 24))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(hashDatabasesLabel)
                .addGap(6, 6, 6)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(informationLabel)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(7, 7, 7)
                                .addComponent(informationSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 3, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(7, 7, 7)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(nameLabel)
                            .addComponent(hashDbNameLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(typeLabel)
                            .addComponent(hashDbTypeLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(locationLabel)
                            .addComponent(hashDbLocationLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(indexPathLabelLabel)
                            .addComponent(indexPathLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(indexLabel)
                            .addComponent(hashDbIndexStatusLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(indexButton)
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(optionsLabel)
                            .addComponent(optionsSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 6, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(searchDuringIngestCheckbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(sendIngestMessagesCheckBox)
                        .addGap(18, 18, 18)
                        .addComponent(ingestWarningLabel)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 391, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(importDatabaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(createDatabaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deleteDatabaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void indexButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_indexButtonActionPerformed
        final HashDb hashDb = ((HashSetTable)hashSetTable).getSelection();
        assert hashDb != null;        
    
        // Add a listener for the INDEXING_DONE event. This listener will update 
        // the UI.
        hashDb.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(HashDb.Event.INDEXING_DONE.toString())) {
                    HashDb selectedHashDb = ((HashSetTable)hashSetTable).getSelection();
                    if (selectedHashDb != null && hashDb != null && hashDb.equals(selectedHashDb)) {
                        updateComponents();
                    }
                    hashSetTableModel.refreshDisplay();
                }
            }            
        });

        // Display a modal dialog box to kick off the indexing on a worker thread
        // and try to persuade the user to wait for the indexing task to finish. 
        // TODO: If the user waits, this defeats the purpose of doing the indexing on a worker thread.
        // But if the user cancels the dialog, other operations on the database 
        // may be attempted when it is not in a suitable state.
        ModalNoButtons indexDialog = new ModalNoButtons(this, new Frame(), hashDb);
        indexDialog.setLocationRelativeTo(null);
        indexDialog.setVisible(true);
        indexDialog.setModal(true);    
    }//GEN-LAST:event_indexButtonActionPerformed

    private void deleteDatabaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteDatabaseButtonActionPerformed
        if (JOptionPane.showConfirmDialog(null,
                                          NbBundle.getMessage(this.getClass(),
                                                              "HashDbConfigPanel.deleteDbActionConfirmMsg"),
                                          NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.deleteDbActionMsg"),
                                          JOptionPane.YES_NO_OPTION,
                                          JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
            HashDb hashDb = ((HashSetTable)hashSetTable).getSelection();
            if (hashDb != null) {
                hashSetManager.removeHashDatabaseInternal(hashDb);
                hashSetTableModel.refreshModel();
            }
        }
    }//GEN-LAST:event_deleteDatabaseButtonActionPerformed

    private void hashSetTableKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_hashSetTableKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_DELETE) {
            HashDb hashDb = ((HashSetTable)hashSetTable).getSelection();
            if (hashDb != null) {
                hashSetManager.removeHashDatabaseInternal(hashDb);
                hashSetTableModel.refreshModel();
            }
        }                
    }//GEN-LAST:event_hashSetTableKeyPressed

    private void searchDuringIngestCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchDuringIngestCheckboxActionPerformed
        HashDb hashDb = ((HashSetTable)hashSetTable).getSelection();
        if (hashDb != null) {
            hashDb.setSearchDuringIngest(searchDuringIngestCheckbox.isSelected());
            if (!searchDuringIngestCheckbox.isSelected()) {
                sendIngestMessagesCheckBox.setSelected(false);
            }
            hashDb.setSendIngestMessages(sendIngestMessagesCheckBox.isSelected());            
        }
    }//GEN-LAST:event_searchDuringIngestCheckboxActionPerformed

    private void sendIngestMessagesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sendIngestMessagesCheckBoxActionPerformed
        HashDb hashDb = ((HashSetTable)hashSetTable).getSelection();
        if (hashDb != null) {
            hashDb.setSendIngestMessages(sendIngestMessagesCheckBox.isSelected());
        }
    }//GEN-LAST:event_sendIngestMessagesCheckBoxActionPerformed

    private void importDatabaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importDatabaseButtonActionPerformed
        HashDb hashDb = new HashDbImportDatabaseDialog().getHashDatabase();
        if (null != hashDb) {
            hashSetTableModel.refreshModel();
            ((HashSetTable)hashSetTable).selectRowByName(hashDb.getHashSetName());
        }    
    }//GEN-LAST:event_importDatabaseButtonActionPerformed

    private void createDatabaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createDatabaseButtonActionPerformed
        HashDb hashDb = new HashDbCreateDatabaseDialog().getHashDatabase();
        if (null != hashDb) {
            hashSetTableModel.refreshModel();
            ((HashSetTable)hashSetTable).selectRowByName(hashDb.getHashSetName());
        }
    }//GEN-LAST:event_createDatabaseButtonActionPerformed
           
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton createDatabaseButton;
    private javax.swing.JButton deleteDatabaseButton;
    private javax.swing.JLabel hashDatabasesLabel;
    private javax.swing.JLabel hashDbIndexStatusLabel;
    private javax.swing.JLabel hashDbLocationLabel;
    private javax.swing.JLabel hashDbNameLabel;
    private javax.swing.JLabel hashDbTypeLabel;
    private javax.swing.JTable hashSetTable;
    private javax.swing.JButton importDatabaseButton;
    private javax.swing.JButton indexButton;
    private javax.swing.JLabel indexLabel;
    private javax.swing.JLabel indexPathLabel;
    private javax.swing.JLabel indexPathLabelLabel;
    private javax.swing.JLabel informationLabel;
    private javax.swing.JSeparator informationSeparator;
    private javax.swing.JLabel ingestWarningLabel;
    private javax.swing.JButton jButton3;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel locationLabel;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JLabel optionsLabel;
    private javax.swing.JSeparator optionsSeparator;
    private javax.swing.JCheckBox searchDuringIngestCheckbox;
    private javax.swing.JCheckBox sendIngestMessagesCheckBox;
    private javax.swing.JLabel typeLabel;
    // End of variables declaration//GEN-END:variables
}