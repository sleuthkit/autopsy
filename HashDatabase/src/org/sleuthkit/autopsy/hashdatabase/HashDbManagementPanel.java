/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;

final class HashDbManagementPanel extends javax.swing.JPanel implements OptionsPanel {

    private HashSetTableModel hashSetTableModel;
    private static final Logger logger = Logger.getLogger(HashDbManagementPanel.class.getName());
    private boolean ingestRunning = false;
    
    
    //keep track of dbs being indexed, since HashDb objects are reloaded, 
    //we cannot rely on their status
    private final Map<String,Boolean>indexingState = new HashMap<String,Boolean>();

    HashDbManagementPanel() {
        this.hashSetTableModel = new HashSetTableModel();
        initComponents();
        customizeComponents();
        
    }
    
    private void customizeComponents() {
        setName("Hash Database Configuration");
        this.ingestWarningLabel.setVisible(false);
        this.hashSetTable.setModel(hashSetTableModel);
        this.hashSetTable.setTableHeader(null);
        hashSetTable.getParent().setBackground(hashSetTable.getBackground());
        hashSetTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hashSetTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    int index = listSelectionModel.getMinSelectionIndex();
                    listSelectionModel.setSelectionInterval(index, index);
                    HashDbXML loader = HashDbXML.getCurrent();
                    HashDb current = loader.getAllSets().get(index);
                    initUI(current);
                } else {
                    initUI(null);
                }
            }
        });
    }

    private void initUI(HashDb db) {
        boolean useForIngestEnabled = db != null && !ingestRunning;
        boolean useForIngestSelected = db != null && db.getUseForIngest();
        boolean showInboxMessagesEnabled = db != null && !ingestRunning && useForIngestSelected && db.getDbType().equals(HashDb.DBType.KNOWN_BAD);
        boolean showInboxMessagesSelected = db != null && db.getShowInboxMessages();
        boolean deleteButtonEnabled = db != null && !ingestRunning;
        boolean importButtonEnabled = !ingestRunning;
        if (db == null) {
            setButtonFromIndexStatus(this.indexButton, this.hashDbIndexStatusLabel, IndexStatus.NONE);
            this.hashDbLocationLabel.setText("No database selected");
            this.hashDbNameLabel.setText("No database selected");
            this.hashDbIndexStatusLabel.setText("No database selected");
            this.hashDbTypeLabel.setText("No database selected");
        } else {
            //check if dn in indexing state
            String dbName = db.getName();
            IndexStatus status = db.status();
            Boolean state = indexingState.get(dbName);
            if (state != null && state.equals(Boolean.TRUE) ) {
                status = IndexStatus.INDEXING;
            }
            
            setButtonFromIndexStatus(this.indexButton, this.hashDbIndexStatusLabel, status);
            String shortenPath = db.getDatabasePaths().get(0);
            this.hashDbLocationLabel.setToolTipText(shortenPath);
            if(shortenPath.length() > 50){
                shortenPath = shortenPath.substring(0, 10 + shortenPath.substring(10).indexOf(File.separator) + 1) + "..." +
                        shortenPath.substring((shortenPath.length() - 20) + shortenPath.substring(shortenPath.length() - 20).indexOf(File.separator));
            }
            this.hashDbLocationLabel.setText(shortenPath);
            this.hashDbNameLabel.setText(db.getName());
            this.hashDbTypeLabel.setText(db.getDbType().getDisplayName());
        }
        this.useForIngestCheckbox.setSelected(useForIngestSelected);
        this.useForIngestCheckbox.setEnabled(useForIngestEnabled);
        this.showInboxMessagesCheckBox.setSelected(showInboxMessagesSelected);
        this.showInboxMessagesCheckBox.setEnabled(showInboxMessagesEnabled);
        this.deleteButton.setEnabled(deleteButtonEnabled);
        this.importButton.setEnabled(importButtonEnabled);
        this.optionsLabel.setEnabled(useForIngestEnabled || showInboxMessagesEnabled);
        this.optionsSeparator.setEnabled(useForIngestEnabled || showInboxMessagesEnabled);
    }
    
    /**
     * Sets the current state of ingest.
     * Don't allow any changes if ingest is running.
     * @param running Whether ingest is running or not.
     */
    private void setIngestStatus(boolean running) {
        ingestRunning = running;
        ingestWarningLabel.setVisible(running);
        importButton.setEnabled(!running);
        
        int selection = getSelection();
        if(selection != -1) {
            initUI(HashDbXML.getCurrent().getAllSets().get(selection));
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

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.jLabel4.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel6, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.jLabel6.text")); // NOI18N

        jButton3.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(jButton3, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.jButton3.text")); // NOI18N

        setMinimumSize(new java.awt.Dimension(700, 500));
        setPreferredSize(new java.awt.Dimension(700, 500));

        ingestWarningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/hashdatabase/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(ingestWarningLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.ingestWarningLabel.text")); // NOI18N

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
        org.openide.awt.Mnemonics.setLocalizedText(deleteButton, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.deleteButton.text")); // NOI18N
        deleteButton.setMaximumSize(new java.awt.Dimension(140, 25));
        deleteButton.setMinimumSize(new java.awt.Dimension(140, 25));
        deleteButton.setPreferredSize(new java.awt.Dimension(140, 25));
        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteButtonActionPerformed(evt);
            }
        });

        importButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/hashdatabase/import16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(importButton, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.importButton.text")); // NOI18N
        importButton.setMaximumSize(new java.awt.Dimension(140, 25));
        importButton.setMinimumSize(new java.awt.Dimension(140, 25));
        importButton.setPreferredSize(new java.awt.Dimension(140, 25));
        importButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(hashDatabasesLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.hashDatabasesLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(nameLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.nameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbNameLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.hashDbNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbLocationLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.hashDbLocationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(locationLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.locationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(typeLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.typeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbTypeLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.hashDbTypeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbIndexStatusLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.hashDbIndexStatusLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(indexLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.indexLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(indexButton, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.indexButton.text")); // NOI18N
        indexButton.setEnabled(false);
        indexButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                indexButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(useForIngestCheckbox, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.useForIngestCheckbox.text")); // NOI18N
        useForIngestCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useForIngestCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(showInboxMessagesCheckBox, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.showInboxMessagesCheckBox.text")); // NOI18N
        showInboxMessagesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showInboxMessagesCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(informationLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.informationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(optionsLabel, org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.optionsLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(hashDatabasesLabel)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addComponent(importButton, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(deleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)))
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
                                    .addComponent(indexButton, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE))))))
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
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 422, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(importButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(deleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void indexButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_indexButtonActionPerformed
        int selected = getSelection();
        final HashDb current = HashDbXML.getCurrent().getAllSets().get(selected);
        current.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(HashDb.EVENT.INDEXING_DONE.toString())) {
                    //update tracking of indexing status
                    indexingState.put((String)evt.getNewValue(), Boolean.FALSE);
                    
                    setButtonFromIndexStatus(indexButton, hashDbIndexStatusLabel, current.status());
                    resync();
                }
            }
            
        });
            indexingState.put(current.getName(), Boolean.TRUE);
            ModalNoButtons singleMNB = new ModalNoButtons(this, new Frame(), current);  //Modal reference, to be removed later
            singleMNB.setLocationRelativeTo(null);
            singleMNB.setVisible(true);
            singleMNB.setModal(true);                                                   //End Modal reference
            indexingState.put(current.getName(), Boolean.FALSE);
        setButtonFromIndexStatus(indexButton, this.hashDbIndexStatusLabel, current.status());      
    }//GEN-LAST:event_indexButtonActionPerformed

    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
        if (JOptionPane.showConfirmDialog(null, "This will remove the hash database entry globally (for all Cases). Do you want to proceed? ",
                "Deleting a Hash Database Entry", 
                JOptionPane.YES_NO_OPTION, 
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {

            int selected = getSelection();
            HashDbXML xmlHandle = HashDbXML.getCurrent();
            if (xmlHandle.getNSRLSet() != null) {
                if (selected == 0) {
                    HashDbXML.getCurrent().removeNSRLSet();
                } else {
                    HashDbXML.getCurrent().removeKnownBadSetAt(selected - 1);
                }
            } else {
                HashDbXML.getCurrent().removeKnownBadSetAt(selected);
            }
            hashSetTableModel.resync();
        }
    }//GEN-LAST:event_deleteButtonActionPerformed

    private void hashSetTableKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_hashSetTableKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_DELETE) {
            int selected = getSelection();
            HashDbXML xmlHandle = HashDbXML.getCurrent();
            if (xmlHandle.getNSRLSet() != null) {
                if (selected == 0) {
                    HashDbXML.getCurrent().removeNSRLSet();
                } else {
                    HashDbXML.getCurrent().removeKnownBadSetAt(selected - 1);
                }
            } else {
                HashDbXML.getCurrent().removeKnownBadSetAt(selected);
            }
        }
        hashSetTableModel.resync();
    }//GEN-LAST:event_hashSetTableKeyPressed

    private void useForIngestCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useForIngestCheckboxActionPerformed
        int selected = getSelection();
        HashDbXML xmlHandle = HashDbXML.getCurrent();
        if (xmlHandle.getNSRLSet() != null) {
            if (selected == 0) {
                HashDb current = HashDbXML.getCurrent().getNSRLSet();
                current.setUseForIngest(useForIngestCheckbox.isSelected());
                HashDbXML.getCurrent().setNSRLSet(current);
            } else {
                HashDb current = HashDbXML.getCurrent().getKnownBadSets().remove(selected - 1);
                current.setUseForIngest(useForIngestCheckbox.isSelected());
                HashDbXML.getCurrent().addKnownBadSet(selected - 1, current);
                this.showInboxMessagesCheckBox.setEnabled(useForIngestCheckbox.isSelected());
            }
        } else {
            HashDb current = HashDbXML.getCurrent().getKnownBadSets().remove(selected);
            current.setUseForIngest(useForIngestCheckbox.isSelected());
            HashDbXML.getCurrent().addKnownBadSet(selected, current);
            this.showInboxMessagesCheckBox.setEnabled(useForIngestCheckbox.isSelected());
        }
    }//GEN-LAST:event_useForIngestCheckboxActionPerformed

    private void showInboxMessagesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showInboxMessagesCheckBoxActionPerformed
        int selected = getSelection();
        HashDbXML xmlHandle = HashDbXML.getCurrent();
        if (xmlHandle.getNSRLSet() != null) {
            if (selected == 0) {
                HashDb current = HashDbXML.getCurrent().getNSRLSet();
                current.setShowInboxMessages(showInboxMessagesCheckBox.isSelected());
                HashDbXML.getCurrent().setNSRLSet(current);
            } else {
                HashDb current = HashDbXML.getCurrent().getKnownBadSets().remove(selected - 1);
                current.setShowInboxMessages(showInboxMessagesCheckBox.isSelected());
                HashDbXML.getCurrent().addKnownBadSet(selected - 1, current);
            }
        } else {
            HashDb current = HashDbXML.getCurrent().getKnownBadSets().remove(selected);
            current.setShowInboxMessages(showInboxMessagesCheckBox.isSelected());
            HashDbXML.getCurrent().addKnownBadSet(selected, current);
        }
    }//GEN-LAST:event_showInboxMessagesCheckBoxActionPerformed

    private void importButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importButtonActionPerformed
        importHashSet(evt);
    }//GEN-LAST:event_importButtonActionPerformed

    @Override
    public void load() {
        hashSetTable.clearSelection();      // Deselect all rows
        HashDbXML.getCurrent().reload();    // Reload XML
        initUI(null);                       // Update the UI
        hashSetTableModel.resync();         // resync the table
        setIngestStatus(IngestManager.getDefault().isIngestRunning()); // check if ingest is running
    }

      @Override
      /**
       * Saves the HashDb's current state. 
       * This version of store is modified heavily to make use of the ModalNoButtons class. 
       * The only call that matters is the final HashDbXML.getCurrent().save()
       */
    public void store() {
        //Checking for for any unindexed databases
        List<HashDb> unindexed = new ArrayList<HashDb>();
        for(int i = 0; i < hashSetTableModel.getRowCount(); i++){
            if(! hashSetTableModel.indexExists(i)){
                unindexed.add(hashSetTableModel.getDBAt(i));
            }
        }
        //If unindexed ones are found, show a popup box that will either index them, or remove them.
        if (unindexed.size() == 1){
            showInvalidIndex(false, unindexed);
        }
        else if (unindexed.size() > 1){
            showInvalidIndex(true, unindexed);
        }
        HashDbXML.getCurrent().save();
        
    }
    
    
    /**
    * Removes a list of HashDbs from the dialog panel that do not have a companion -md5.idx file. 
    * Occurs when user clicks "No" to the dialog popup box.
    * @param toRemove a list of HashDbs that are unindexed
    */
    void removeThese(List<HashDb> toRemove) {
        HashDbXML xmlHandle = HashDbXML.getCurrent();
        for (HashDb hdb : toRemove) {
            for (int i = 0; i < hashSetTableModel.getRowCount(); i++) {
                if (hashSetTableModel.getDBAt(i).equals(hdb)) {
                    if (xmlHandle.getNSRLSet() != null) {
                        if (i == 0) {
                            HashDbXML.getCurrent().removeNSRLSet();
                        } else {
                            HashDbXML.getCurrent().removeKnownBadSetAt(i - 1);
                        }
                    } else {
                        HashDbXML.getCurrent().removeKnownBadSetAt(i);
                    }
                    hashSetTableModel.resync();
                }
            }
        }
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
            total+= "\n" + hdb.getName();
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
            hashSetTableModel.resync();
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
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteButton;
    private javax.swing.JLabel hashDatabasesLabel;
    private javax.swing.JLabel hashDbIndexStatusLabel;
    private javax.swing.JLabel hashDbLocationLabel;
    private javax.swing.JLabel hashDbNameLabel;
    private javax.swing.JLabel hashDbTypeLabel;
    private javax.swing.JTable hashSetTable;
    private javax.swing.JButton importButton;
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
    private void importHashSet(java.awt.event.ActionEvent evt) {
        String name = new HashDbAddDatabaseDialog().display();
        if(name != null) {
            hashSetTableModel.selectRowByName(name);
        }
        resync();
    }
    
    /** 
     * The visual display of hash databases loaded.
     */ 
    private class HashSetTable extends JTable {
        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            Component c = super.prepareRenderer(renderer, row, column);
            JComponent jc = (JComponent) c;
            String valueText = (String) getValueAt(row, column);
            jc.setToolTipText(valueText);
            
            //Letting the user know which DBs need to be indexed
            if(hashSetTableModel.indexExists(row)){
                c.setForeground(Color.black);
            }
            else{
                c.setForeground(Color.red);
            }
            return c;
        }
    }
    
  
    private class HashSetTableModel extends AbstractTableModel {

        private HashDbXML xmlHandle = HashDbXML.getCurrent();

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public int getRowCount() {
            return xmlHandle.getAllSets().size();
        }

        @Override
        public String getColumnName(int column) {
            return "Name";
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if(xmlHandle.getNSRLSet() == null) {
                return getDBAt(rowIndex).getName();
            } else {
                return rowIndex == 0 ? getDBAt(rowIndex).getName() + " (NSRL)" : getDBAt(rowIndex).getName();
            }
        }
        
        //Internal function for determining whether a companion -md5.idx file exists
        private boolean indexExists(int rowIndex){
            return getDBAt(rowIndex).indexExists();
        }
        
        
        //Internal function for getting the DB at a certain index. Used as-is, as well as by dispatch from getValueAt() and indexExists()
        private HashDb getDBAt(int rowIndex){
            if (xmlHandle.getNSRLSet() != null) {
                if(rowIndex == 0) {
                    return xmlHandle.getNSRLSet();
                } else {
                    return xmlHandle.getKnownBadSets().get(rowIndex-1);
                }
            } else {
                return xmlHandle.getKnownBadSets().get(rowIndex);
            }
        }
        
        // Selects the row with the given name
        private void selectRowByName(String name) {
            HashDb NSRL = xmlHandle.getNSRLSet();
            List<HashDb> bad = xmlHandle.getKnownBadSets();
            if(NSRL != null) {
                if(NSRL.getName().equals(name)) {
                    setSelection(0);
                } else {
                    for(int i=0; i<bad.size(); i++) {
                        if(bad.get(i).getName().equals(name)) {
                            setSelection(i + 1);
                        }
                    }
                }
            } else {
                for(int i=0; i<bad.size(); i++) {
                    if(bad.get(i).getName().equals(name)) {
                        setSelection(i);
                    }
                }
            }
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

        void resync() {
            fireTableDataChanged();
        }
    }
    
    static void setButtonFromIndexStatus(JButton theButton, JLabel theLabel, IndexStatus status) {
        theLabel.setText(status.message());
        switch (status) {
            case INDEX_OUTDATED:
                theButton.setText("Re-index");
                theLabel.setForeground(Color.black);
                theButton.setEnabled(true);
                break;
            case INDEX_CURRENT:
                theButton.setText("Re-index");
                theLabel.setForeground(Color.black);
                theButton.setEnabled(true);
                break;
            case NO_INDEX:
                theButton.setText("Index");
                theLabel.setForeground(Color.red);
                theButton.setEnabled(true);
                break;
            case INDEXING:
                theButton.setText("Indexing");
                theLabel.setForeground(Color.black);
                theButton.setEnabled(false);
                break;
            default:
                theButton.setText("Index");
                theLabel.setForeground(Color.black);
                theButton.setEnabled(false);
        }
        if (IngestManager.getDefault().isIngestRunning()) {
            theButton.setEnabled(false);
        }
    }
    
    private int getSelection() {
        return hashSetTable.getSelectionModel().getMinSelectionIndex();
    }
    
    private void setSelection(int index) {
        if(index >= 0 && index < hashSetTable.getRowCount()) {
            hashSetTable.getSelectionModel().setSelectionInterval(index, index);
        }
    }
    void resync() {
        int index = getSelection();
        this.hashSetTableModel.resync();
        setSelection(index);
    }
   
}