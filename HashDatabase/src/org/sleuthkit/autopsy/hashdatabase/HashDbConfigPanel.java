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
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.hashdatabase.IndexStatus.NO_INDEX;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class provide a UI for managing the hash sets configuration.
 */
final class HashDbConfigPanel extends javax.swing.JPanel implements OptionsPanel {
    private HashDbManager hashSetManager = HashDbManager.getInstance();
    private HashSetTableModel hashSetTableModel = new HashSetTableModel();
        
    HashDbConfigPanel() {
        initComponents();
        customizeComponents();        
    }
    
    private void customizeComponents() {
        setName("Hash Set Configuration");
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
        if (db == null) {
            hashDbLocationLabel.setText("No database selected");
            hashDbNameLabel.setText("No database selected");
            hashDbIndexStatusLabel.setText("No database selected");
            hashDbTypeLabel.setText("No database selected");
        } 
        else {
            this.hashDbLocationLabel.setToolTipText(db.getDatabasePath());
            if (db.getDatabasePath().length() > 50){
                String shortenedPath = db.getDatabasePath();
                shortenedPath = shortenedPath.substring(0, 10 + shortenedPath.substring(10).indexOf(File.separator) + 1) + "..." + shortenedPath.substring((shortenedPath.length() - 20) + shortenedPath.substring(shortenedPath.length() - 20).indexOf(File.separator));
                hashDbLocationLabel.setText(shortenedPath);
            }
            else {
                hashDbLocationLabel.setText(db.getDatabasePath());
            }
            hashDbNameLabel.setText(db.getDisplayName());
            hashDbTypeLabel.setText(db.getKnownFilesType().getDisplayName());
        }
        updateStatusComponents(db);
        boolean ingestRunning = IngestManager.getDefault().isIngestRunning();
        boolean useForIngestEnabled = db != null && !ingestRunning;
        boolean useForIngestSelected = db != null && db.getUseForIngest();
        boolean showInboxMessagesEnabled = db != null && !ingestRunning && useForIngestSelected && db.getKnownFilesType().equals(HashDb.KnownFilesType.KNOWN_BAD);
        boolean showInboxMessagesSelected = db != null && db.getShowInboxMessages();
        boolean deleteButtonEnabled = db != null && !ingestRunning;
        boolean importButtonEnabled = !ingestRunning;
        useForIngestCheckbox.setSelected(useForIngestSelected);
        useForIngestCheckbox.setEnabled(useForIngestEnabled);
        showInboxMessagesCheckBox.setSelected(showInboxMessagesSelected);
        showInboxMessagesCheckBox.setEnabled(showInboxMessagesEnabled);
        deleteButton.setEnabled(deleteButtonEnabled);
        importButton.setEnabled(importButtonEnabled);
        optionsLabel.setEnabled(useForIngestEnabled || showInboxMessagesEnabled);
        optionsSeparator.setEnabled(useForIngestEnabled || showInboxMessagesEnabled);
        ingestWarningLabel.setVisible(ingestRunning);
        importButton.setEnabled(!ingestRunning); // RJCTODO: What about the other buttons?
    }
    
   void updateStatusComponents(HashDb hashDb) {
        if (hashDb == null) {
            indexButton.setText("Index");
            hashDbIndexStatusLabel.setForeground(Color.black);
            indexButton.setEnabled(false);            
        }
        else {
            IndexStatus status = IndexStatus.UNKNOWN;
            try {
                status = hashDb.getStatus();
            }
            catch (TskCoreException ex) {
                // RJCTODO: Need a status unknown?
                // Logger.getLogger(HashDbIngestModule.class.getName())
            }            
                        
            hashDbIndexStatusLabel.setText(status.message());
            switch (status) {
                case NO_INDEX:
                    indexButton.setText("Index");
                    hashDbIndexStatusLabel.setForeground(Color.red);
                    indexButton.setEnabled(true);
                    break;
                case INDEXING:
                    indexButton.setText("Indexing");
                    hashDbIndexStatusLabel.setForeground(Color.black);
                    indexButton.setEnabled(false);
                    break;
                case UNKNOWN:
                    indexButton.setText("Index");
                    hashDbIndexStatusLabel.setForeground(Color.red);
                    indexButton.setEnabled(false);
                    break;
                case INDEXED:
                    // TODO: Restore ability to re-index an indexed                    
                case INDEX_ONLY:
                default:
                    indexButton.setText("Index");
                    hashDbIndexStatusLabel.setForeground(Color.black);
                    indexButton.setEnabled(false);
                    break;
            }
        }
        
        if (IngestManager.getDefault().isIngestRunning()) {
            indexButton.setEnabled(false);
        }
    }
        
    @Override
    public void load() {
        hashSetTable.clearSelection();
        hashSetTableModel.refresh();         
//        updateComponents(null);
    }

    @Override
    public void store() {
        //Checking for for any unindexed databases
        List<HashDb> unindexed = new ArrayList<>();
        for (HashDb hashSet : hashSetManager.getAllHashSets()) {
            if (!hashSet.hasLookupIndex()) {
                unindexed.add(hashSet);
            }
        }
        
        // RJCTODO:Whaaaaat?
        //If unindexed ones are found, show a popup box that will either index them, or remove them.
        if (unindexed.size() == 1){
            showInvalidIndex(false, unindexed);
        }
        else if (unindexed.size() > 1){
            showInvalidIndex(true, unindexed);
        }
        
        hashSetManager.save();        
    }
    
    /**
    * Removes a list of HashDbs from the dialog panel that do not have a companion -md5.idx file. 
    * Occurs when user clicks "No" to the dialog popup box.
    * @param toRemove a list of HashDbs that are unindexed
    */
    void removeThese(List<HashDb> toRemove) {
        for (HashDb hashDb : toRemove) {
            hashSetManager.removeHashSet(hashDb);
        }
        hashSetTableModel.refresh();        
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
            total+= "\n" + hdb.getDisplayName();
        }
        if(plural){
            message = "The following databases are not indexed, would you like to index them now? \n " + total;
        }
        else{
            message = "The following database is not indexed, would you like to index it now? \n" + total;
        }
        int res = JOptionPane.showConfirmDialog(this, message, "Unindexed databases", JOptionPane.YES_NO_OPTION);
        if(res == JOptionPane.YES_OPTION){
            ModalNoButtons indexingDialog = new ModalNoButtons(this, new Frame(),unindexed);
            indexingDialog.setLocationRelativeTo(null);
            indexingDialog.setVisible(true);
            indexingDialog.setModal(true);
            hashSetTableModel.refresh();
        }
        if(res == JOptionPane.NO_OPTION){
            JOptionPane.showMessageDialog(this, "All unindexed databases will be removed the list");
            removeThese(unindexed);
        }
    }

    boolean valid() {
        // TODO check whether form is consistent and complete
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
            return "Name";
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return hashSets.get(rowIndex).getDisplayName();
        }
        
        private boolean indexExists(int rowIndex){
            return hashSets.get(rowIndex).hasLookupIndex();
        }
                                
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            throw new UnsupportedOperationException("Editing of cells is not supported");
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
                if (hashSets.get(i).getDisplayName().equals(name)) {
                    return i;                    
                }                
            }                
            return -1;
        }
        
        void refresh() {
            hashSets = HashDbManager.getInstance().getAllHashSets();
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
        deleteButton = new javax.swing.JButton();
        importButton = new javax.swing.JButton();
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
        useForIngestCheckbox = new javax.swing.JCheckBox();
        showInboxMessagesCheckBox = new javax.swing.JCheckBox();
        informationLabel = new javax.swing.JLabel();
        optionsLabel = new javax.swing.JLabel();
        informationSeparator = new javax.swing.JSeparator();
        optionsSeparator = new javax.swing.JSeparator();
        importButton1 = new javax.swing.JButton();

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

        deleteButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/hashdatabase/delete16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteButton, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.deleteButton.text")); // NOI18N
        deleteButton.setMaximumSize(new java.awt.Dimension(140, 25));
        deleteButton.setMinimumSize(new java.awt.Dimension(140, 25));
        deleteButton.setPreferredSize(new java.awt.Dimension(140, 25));
        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteButtonActionPerformed(evt);
            }
        });

        importButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/hashdatabase/import16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(importButton, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.importButton.text")); // NOI18N
        importButton.setMaximumSize(new java.awt.Dimension(140, 25));
        importButton.setMinimumSize(new java.awt.Dimension(140, 25));
        importButton.setPreferredSize(new java.awt.Dimension(140, 25));
        importButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importButtonActionPerformed(evt);
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

        org.openide.awt.Mnemonics.setLocalizedText(useForIngestCheckbox, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.useForIngestCheckbox.text")); // NOI18N
        useForIngestCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useForIngestCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(showInboxMessagesCheckBox, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.showInboxMessagesCheckBox.text")); // NOI18N
        showInboxMessagesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showInboxMessagesCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(informationLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.informationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(optionsLabel, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.optionsLabel.text")); // NOI18N

        importButton1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/hashdatabase/new16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(importButton1, org.openide.util.NbBundle.getMessage(HashDbConfigPanel.class, "HashDbConfigPanel.importButton1.text")); // NOI18N
        importButton1.setMaximumSize(new java.awt.Dimension(140, 25));
        importButton1.setMinimumSize(new java.awt.Dimension(140, 25));
        importButton1.setPreferredSize(new java.awt.Dimension(140, 25));
        importButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importButton1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(hashDatabasesLabel)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 275, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(informationLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(informationSeparator))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(optionsLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(optionsSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 324, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(ingestWarningLabel)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                    .addComponent(nameLabel)
                                                    .addComponent(locationLabel)
                                                    .addComponent(typeLabel))
                                                .addGap(40, 40, 40))
                                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                                .addComponent(indexLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addGap(18, 18, 18)))
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                            .addComponent(hashDbTypeLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                            .addComponent(hashDbLocationLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                            .addComponent(hashDbIndexStatusLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                            .addComponent(hashDbNameLabel)))
                                    .addComponent(useForIngestCheckbox)
                                    .addComponent(showInboxMessagesCheckBox)
                                    .addComponent(indexButton, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(importButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(importButton, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(deleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(40, Short.MAX_VALUE))
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
                        .addGap(5, 5, 5)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(locationLabel)
                            .addComponent(hashDbLocationLabel))
                        .addGap(5, 5, 5)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(typeLabel)
                            .addComponent(hashDbTypeLabel))
                        .addGap(5, 5, 5)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(hashDbIndexStatusLabel)
                            .addComponent(indexLabel))
                        .addGap(5, 5, 5)
                        .addComponent(indexButton)
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(optionsLabel)
                            .addComponent(optionsSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 6, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(useForIngestCheckbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(showInboxMessagesCheckBox)
                        .addGap(18, 18, 18)
                        .addComponent(ingestWarningLabel)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 391, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(importButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(importButton1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void indexButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_indexButtonActionPerformed
        final HashDb hashDbToBeIndexed = ((HashSetTable)hashSetTable).getSelection();
        if (hashDbToBeIndexed != null) {
            // Add a listener for the INDEXING_DONE event. The listener will update 
            // the UI.
            hashDbToBeIndexed.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getPropertyName().equals(HashDb.Event.INDEXING_DONE.toString())) {
                        HashDb selectedHashDb = ((HashSetTable)hashSetTable).getSelection();
                        if (selectedHashDb != null && hashDbToBeIndexed != null && hashDbToBeIndexed.equals(selectedHashDb)) {
                            updateStatusComponents(selectedHashDb);
                        }
                    }
                }            
            });
            
            // Display a modal dialog box to kick off the indexing on a worker thread
            // and try to persuade the user to wait for the indexing task to finish. 
            // TODO: This defeats the purpose of doing the indexing on a worker thread.
            // The user may also cancel the dialog and change the hash sets configuration.
            // That should be fine, as long as the indexing DB is not deleted, which 
            // shu;d be able to be controlled.
            ModalNoButtons indexDialog = new ModalNoButtons(this, new Frame(), hashDbToBeIndexed);
            indexDialog.setLocationRelativeTo(null);
            indexDialog.setVisible(true);
            indexDialog.setModal(true);    
        }
    }//GEN-LAST:event_indexButtonActionPerformed

    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
        if (JOptionPane.showConfirmDialog(null, "This will remove the hash database entry globally (for all Cases). Do you want to proceed? ", "Deleting a Hash Database Entry", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
            HashDb hashDb = ((HashSetTable)hashSetTable).getSelection();
            if (hashDb != null) {
                hashSetManager.removeHashSet(hashDb);
                hashSetTableModel.refresh();
            }
        }
    }//GEN-LAST:event_deleteButtonActionPerformed

    private void hashSetTableKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_hashSetTableKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_DELETE) {
            HashDb hashDb = ((HashSetTable)hashSetTable).getSelection();
            if (hashDb != null) {
                hashSetManager.removeHashSet(hashDb);
                hashSetTableModel.refresh();
            }
        }                
    }//GEN-LAST:event_hashSetTableKeyPressed

    private void useForIngestCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useForIngestCheckboxActionPerformed
        HashDb hashDb = ((HashSetTable)hashSetTable).getSelection();
        if (hashDb != null) {
            hashDb.setUseForIngest(useForIngestCheckbox.isSelected());
            showInboxMessagesCheckBox.setEnabled(useForIngestCheckbox.isSelected());
        }
    }//GEN-LAST:event_useForIngestCheckboxActionPerformed

    private void showInboxMessagesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showInboxMessagesCheckBoxActionPerformed
        HashDb hashDb = ((HashSetTable)hashSetTable).getSelection();
        if (hashDb != null) {
            hashDb.setShowInboxMessages(showInboxMessagesCheckBox.isSelected());
        }
    }//GEN-LAST:event_showInboxMessagesCheckBoxActionPerformed

    private void importButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importButtonActionPerformed
        HashDb hashDb = new HashDbImportDatabaseDialog().doDialog();
        if (hashDb != null) {
            hashSetManager.addHashSet(hashDb);
            hashSetTableModel.refresh();
            ((HashSetTable)hashSetTable).selectRowByName(hashDb.getDisplayName());
        }    
    }//GEN-LAST:event_importButtonActionPerformed

    private void importButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importButton1ActionPerformed
        HashDb hashDb = new HashDbCreateDatabaseDialog().doDialog();
        if (null != hashDb) {
            hashSetManager.addHashSet(hashDb);
            hashSetTableModel.refresh();
            ((HashSetTable)hashSetTable).selectRowByName(hashDb.getDisplayName());
        }
    }//GEN-LAST:event_importButton1ActionPerformed
           
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteButton;
    private javax.swing.JLabel hashDatabasesLabel;
    private javax.swing.JLabel hashDbIndexStatusLabel;
    private javax.swing.JLabel hashDbLocationLabel;
    private javax.swing.JLabel hashDbNameLabel;
    private javax.swing.JLabel hashDbTypeLabel;
    private javax.swing.JTable hashSetTable;
    private javax.swing.JButton importButton;
    private javax.swing.JButton importButton1;
    private javax.swing.JButton indexButton;
    private javax.swing.JLabel indexLabel;
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
    private javax.swing.JCheckBox showInboxMessagesCheckBox;
    private javax.swing.JLabel typeLabel;
    private javax.swing.JCheckBox useForIngestCheckbox;
    // End of variables declaration//GEN-END:variables
}