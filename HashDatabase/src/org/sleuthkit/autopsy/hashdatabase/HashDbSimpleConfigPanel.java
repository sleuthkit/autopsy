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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class provide a simplified UI for managing the hash sets configuration.
 */
public class HashDbSimpleConfigPanel extends javax.swing.JPanel {    
    private HashDbsTableModel knownTableModel;
    private HashDbsTableModel knownBadTableModel;

    public HashDbSimpleConfigPanel() {
        knownTableModel = new HashDbsTableModel(HashDbManager.getInstance().getKnownHashSets());
        knownBadTableModel = new HashDbsTableModel(HashDbManager.getInstance().getKnownBadHashSets());
        initComponents();
        customizeComponents();
    }
        
    private void customizeComponents() {
        customizeHashDbsTable(jScrollPane1, knownHashTable, knownTableModel);
        customizeHashDbsTable(jScrollPane2, knownBadHashTable, knownBadTableModel);
        
        // Add a listener to the always calculate hashes checkbox component.
        // The listener passes the user's selection on to the hash database manager.
        calcHashesButton.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                HashDbManager.getInstance().alwaysCalculateHashes(calcHashesButton.isSelected());
            }            
        });
        
        refreshComponents();
    }

    private void customizeHashDbsTable(JScrollPane scrollPane, JTable table, HashDbsTableModel tableModel) {
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
    
    public void refreshComponents() {        
        knownTableModel.refresh();
        knownBadTableModel.refresh();
        refreshAlwaysCalcHashesComponents(); 
    }

    private void refreshAlwaysCalcHashesComponents() {        
        boolean noHashDbsConfiguredForIngest = true; 
        for (HashDb hashDb : HashDbManager.getInstance().getAllHashSets()) {
            try {
                if (hashDb.getUseForIngest()== true && hashDb.hasLookupIndex()) {
                    noHashDbsConfiguredForIngest = false;
                    break;
                }
            }
            catch (TskCoreException ex) {
                Logger.getLogger(HashDbSimpleConfigPanel.class.getName()).log(Level.SEVERE, "Error getting info for hash database at " + hashDb.getDatabasePath(), ex);            
            }            
        }

        // If there are no hash databases configured for use during file ingest,
        // default to always calculating hashes of the files.
        if (noHashDbsConfiguredForIngest) {
            calcHashesButton.setEnabled(true);
            calcHashesButton.setSelected(true);
            HashDbManager.getInstance().alwaysCalculateHashes(true);
        } else {
            calcHashesButton.setEnabled(false);
            calcHashesButton.setSelected(false);
            HashDbManager.getInstance().alwaysCalculateHashes(false);
        }
    }
        
    private class HashDbsTableModel extends AbstractTableModel {        
        private final List<HashDb> hashDbs;
        
        HashDbsTableModel(List<HashDb> hashDbs) {
            this.hashDbs = hashDbs;
        }
        
        private void refresh() {
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return hashDbs.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HashDb db = hashDbs.get(rowIndex);
            if (columnIndex == 0) {
                return db.getUseForIngest();
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
                HashDb db = hashDbs.get(rowIndex);
                boolean dbHasIndex = false;
                try {
                    dbHasIndex = db.hasLookupIndex();
                }
                catch (TskCoreException ex) {
                    Logger.getLogger(HashDbSimpleConfigPanel.class.getName()).log(Level.SEVERE, "Error getting info for hash database at " + db.getDatabasePath(), ex);            
                }
                if(((Boolean) getValueAt(rowIndex, columnIndex)) || dbHasIndex) {
                    db.setUseForIngest((Boolean) aValue);
                } 
                else {
                    JOptionPane.showMessageDialog(HashDbSimpleConfigPanel.this, "Hash databases must be indexed before they can be used for ingest");
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
        calcHashesButton = new javax.swing.JCheckBox();
        jScrollPane2 = new javax.swing.JScrollPane();
        knownBadHashTable = new javax.swing.JTable();

        jScrollPane1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        knownHashTable.setBackground(new java.awt.Color(240, 240, 240));
        knownHashTable.setShowHorizontalLines(false);
        knownHashTable.setShowVerticalLines(false);
        jScrollPane1.setViewportView(knownHashTable);

        knownBadHashDbsLabel.setText(org.openide.util.NbBundle.getMessage(HashDbSimpleConfigPanel.class, "HashDbSimpleConfigPanel.knownBadHashDbsLabel.text")); // NOI18N

        knownHashDbsLabel.setText(org.openide.util.NbBundle.getMessage(HashDbSimpleConfigPanel.class, "HashDbSimpleConfigPanel.knownHashDbsLabel.text")); // NOI18N

        calcHashesButton.setText(org.openide.util.NbBundle.getMessage(HashDbSimpleConfigPanel.class, "HashDbSimpleConfigPanel.calcHashesButton.text")); // NOI18N

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
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(knownHashDbsLabel)
                            .addComponent(knownBadHashDbsLabel))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(calcHashesButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(knownHashDbsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(knownBadHashDbsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(calcHashesButton)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox calcHashesButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel knownBadHashDbsLabel;
    private javax.swing.JTable knownBadHashTable;
    private javax.swing.JLabel knownHashDbsLabel;
    private javax.swing.JTable knownHashTable;
    // End of variables declaration//GEN-END:variables
}
