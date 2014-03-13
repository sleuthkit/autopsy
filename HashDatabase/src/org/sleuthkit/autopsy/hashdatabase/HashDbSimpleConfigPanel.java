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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.hashdatabase.HashDbManager.HashDb;

/**
 * Instances of this class provide a simplified UI for managing the hash sets configuration.
 */
public class HashDbSimpleConfigPanel extends javax.swing.JPanel { 
    
    private HashDatabasesTableModel knownTableModel;
    private HashDatabasesTableModel knownBadTableModel;

    public HashDbSimpleConfigPanel() {
        knownTableModel = new HashDatabasesTableModel(HashDbManager.HashDb.KnownFilesType.KNOWN);
        knownBadTableModel = new HashDatabasesTableModel(HashDbManager.HashDb.KnownFilesType.KNOWN_BAD);
        initComponents();
        customizeComponents();
    }
        
    private void customizeComponents() {
        customizeHashDbsTable(jScrollPane1, knownHashTable, knownTableModel);
        customizeHashDbsTable(jScrollPane2, knownBadHashTable, knownBadTableModel);
        alwaysCalcHashesCheckbox.setSelected(HashDbManager.getInstance().getAlwaysCalculateHashes());
        
        // Add a listener to the always calculate hashes checkbox component.
        // The listener passes the user's selection on to the hash database manager.
        alwaysCalcHashesCheckbox.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                HashDbManager.getInstance().setAlwaysCalculateHashes(alwaysCalcHashesCheckbox.isSelected());
            }            
        });
        
        load();
    }

    private void customizeHashDbsTable(JScrollPane scrollPane, JTable table, HashDatabasesTableModel tableModel) {
        table.setModel(tableModel);        
        table.setTableHeader(null);
        table.setRowSelectionAllowed(false);

        final int width1 = scrollPane.getPreferredSize().width;
        knownHashTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        TableColumn column;
        for (int i = 0; i < table.getColumnCount(); i++) {
            column = table.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (width1 * 0.07)));
            } else {
                column.setPreferredWidth(((int) (width1 * 0.92)));
            }
        }        
    }
    
    public void load() {        
        knownTableModel.load();
        knownBadTableModel.load();
    }

    public void store() {
        HashDbManager.getInstance().save();        
    }
            
    private class HashDatabasesTableModel extends AbstractTableModel {        
        private final HashDbManager.HashDb.KnownFilesType hashDatabasesType;  
        private List<HashDb> hashDatabases;
        
        HashDatabasesTableModel(HashDbManager.HashDb.KnownFilesType hashDatabasesType) {
            this.hashDatabasesType = hashDatabasesType;
            getHashDatabases();
        }
            
        private void getHashDatabases() {
            if (HashDbManager.HashDb.KnownFilesType.KNOWN == hashDatabasesType) {
                hashDatabases = HashDbManager.getInstance().getKnownFileHashSets();
            }
            else {
                hashDatabases = HashDbManager.getInstance().getKnownBadFileHashSets();
            }            
        }    
        
        private void load() {
            getHashDatabases();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return hashDatabases.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HashDb db = hashDatabases.get(rowIndex);
            if (columnIndex == 0) {
                return db.getSearchDuringIngest();
            } else {
                return db.getHashSetName();
            }
        }
        
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return !IngestManager.getDefault().isIngestRunning() && columnIndex == 0;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if(columnIndex == 0) {
                HashDb db = hashDatabases.get(rowIndex);
                boolean dbHasIndex = false;
                try {
                    dbHasIndex = db.hasIndex();
                }
                catch (TskCoreException ex) {
                    Logger.getLogger(HashDbSimpleConfigPanel.class.getName()).log(Level.SEVERE, "Error getting info for " + db.getHashSetName() + " hash database", ex);            
                }
                if(((Boolean) getValueAt(rowIndex, columnIndex)) || dbHasIndex) {
                    db.setSearchDuringIngest((Boolean) aValue);
                } 
                else {
                    JOptionPane.showMessageDialog(HashDbSimpleConfigPanel.this,
                                                  NbBundle.getMessage(this.getClass(),
                                                                      "HashDbSimpleConfigPanel.dlgMsg.mustIndexDbBeforeUse"));
                }
            }
        }
        
        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
    }
        
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        knownHashTable = new javax.swing.JTable();
        knownBadHashDbsLabel = new javax.swing.JLabel();
        knownHashDbsLabel = new javax.swing.JLabel();
        alwaysCalcHashesCheckbox = new javax.swing.JCheckBox();
        jScrollPane2 = new javax.swing.JScrollPane();
        knownBadHashTable = new javax.swing.JTable();

        jScrollPane1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        knownHashTable.setBackground(new java.awt.Color(240, 240, 240));
        knownHashTable.setShowHorizontalLines(false);
        knownHashTable.setShowVerticalLines(false);
        jScrollPane1.setViewportView(knownHashTable);

        knownBadHashDbsLabel.setText(org.openide.util.NbBundle.getMessage(HashDbSimpleConfigPanel.class, "HashDbSimpleConfigPanel.knownBadHashDbsLabel.text")); // NOI18N

        knownHashDbsLabel.setText(org.openide.util.NbBundle.getMessage(HashDbSimpleConfigPanel.class, "HashDbSimpleConfigPanel.knownHashDbsLabel.text")); // NOI18N

        alwaysCalcHashesCheckbox.setText(org.openide.util.NbBundle.getMessage(HashDbSimpleConfigPanel.class, "HashDbSimpleConfigPanel.alwaysCalcHashesCheckbox.text")); // NOI18N
        alwaysCalcHashesCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                alwaysCalcHashesCheckboxActionPerformed(evt);
            }
        });

        jScrollPane2.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        knownBadHashTable.setBackground(new java.awt.Color(240, 240, 240));
        knownBadHashTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        knownBadHashTable.setShowHorizontalLines(false);
        knownBadHashTable.setShowVerticalLines(false);
        jScrollPane2.setViewportView(knownBadHashTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(alwaysCalcHashesCheckbox, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 285, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(knownHashDbsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 272, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(knownBadHashDbsLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(knownHashDbsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(knownBadHashDbsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(alwaysCalcHashesCheckbox)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void alwaysCalcHashesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alwaysCalcHashesCheckboxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_alwaysCalcHashesCheckboxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox alwaysCalcHashesCheckbox;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel knownBadHashDbsLabel;
    private javax.swing.JTable knownBadHashTable;
    private javax.swing.JLabel knownHashDbsLabel;
    private javax.swing.JTable knownHashTable;
    // End of variables declaration//GEN-END:variables
}
