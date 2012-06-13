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

/*
 * HashDbMgmtPanel.java
 *
 * Created on May 7, 2012, 9:53:42 AM
 */
package org.sleuthkit.autopsy.hashdatabase;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskException;

/**
 *
 * @author dfickling
 */
public class HashDbMgmtPanel extends javax.swing.JPanel {
    
    private static final Logger logger = Logger.getLogger(HashDbMgmtPanel.class.getName());
    private HashSetTableModel notableTableModel;
    private JFileChooser fc = new JFileChooser();
    private static HashDbMgmtPanel instance;
    private HashDb nsrlSet;
    private static boolean ingestRunning = false;

    /** Creates new form HashDbMgmtPanel */
    private HashDbMgmtPanel() {
        setName(HashDbMgmtAction.ACTION_NAME);
        notableTableModel = new HashSetTableModel();
        initComponents();
        customizeComponents();
    }
    
    public static HashDbMgmtPanel getDefault() {
        if(instance == null) {
            instance = new HashDbMgmtPanel();
        }
        instance.notableTableModel.resync();
        return instance;
    }
    
    void resync() {
        notableTableModel.resync();
    }
    
    private void customizeComponents() {
        notableHashSetTable.setModel(notableTableModel);
        notableHashSetTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        notableHashSetTable.setRowHeight(20);
        notableTableModel.resync();
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String[] EXTENSION = new String[] { "txt", "idx", "hash", "Hash" };
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Hash Database File", EXTENSION);
        fc.setFileFilter(filter);
        fc.setMultiSelectionEnabled(false);
        
        TableColumn column1 = null;
        final int width1 = jScrollPane1.getPreferredSize().width;
        for (int i = 0; i < notableHashSetTable.getColumnCount(); i++) {
            column1 = notableHashSetTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column1.setPreferredWidth((int) (width1*.20));
            }
            if (i == 1) {
                column1.setPreferredWidth((int) (width1*.57));
            }
            if (i == 2) {
                column1.setPreferredWidth((int) (width1*.15));
            }
            if (i == 3) {
                column1.setPreferredWidth((int) (width1*.07));
                column1.setCellRenderer(new CheckBoxRenderer());
            }
        }
        
        Action indexSet = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                int row = notableHashSetTable.getSelectedRow();
                try {
                    notableTableModel.getHashSetAt(row).createIndex();
                } catch (TskException ex) {
                    logger.log(Level.WARNING, "Error creating index", ex);
                }
                notableTableModel.resync();
            }
        };
        new ButtonColumn(notableHashSetTable, indexSet, 2);

        nsrlSet = HashDbXML.getCurrent().getNSRLSet();
        if(nsrlSet != null) {
            nsrlNameLabel.setText(nsrlSet.getName());
            setButtonFromIndexStatus(indexNSRLButton, nsrlSet.status());
        } else {
            setButtonFromIndexStatus(indexNSRLButton, IndexStatus.NO_DB);
            removeNSRLButton.setEnabled(false);
        }
    }

    /**
     * Save the modified settings
     */
    void save() {
        HashDbXML.getCurrent().setNSRLSet(nsrlSet);
    }
    
    /**
     * Don't allow any changes if ingest is running
     */
    void setIngestRunning(boolean running) {
        addNotableButton.setEnabled(!running);
        removeNotableButton.setEnabled(!running);
        setNSRLButton.setEnabled(!running);
        indexNSRLButton.setEnabled(!running);
        removeNSRLButton.setEnabled(!running);
        ingestRunning = running;
        if(running)
            ingestRunningLabel.setText("Ingest is ongoing; some settings will be unavailable until it finishes.");
        else
            ingestRunningLabel.setText("");
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
        notableHashSetTable = new javax.swing.JTable();
        addNotableButton = new javax.swing.JButton();
        removeNotableButton = new javax.swing.JButton();
        setNSRLButton = new javax.swing.JButton();
        nsrlNameLabel = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        indexNSRLButton = new javax.swing.JButton();
        removeNSRLButton = new javax.swing.JButton();
        ingestRunningLabel = new javax.swing.JLabel();

        jScrollPane1.setViewportView(notableHashSetTable);

        addNotableButton.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.addNotableButton.text")); // NOI18N
        addNotableButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addNotableButtonActionPerformed(evt);
            }
        });

        removeNotableButton.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.removeNotableButton.text")); // NOI18N
        removeNotableButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeNotableButtonActionPerformed(evt);
            }
        });

        setNSRLButton.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.setNSRLButton.text")); // NOI18N
        setNSRLButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setNSRLButtonActionPerformed(evt);
            }
        });

        nsrlNameLabel.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.nsrlNameLabel.text")); // NOI18N

        jLabel1.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.jLabel1.text")); // NOI18N

        jLabel2.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.jLabel2.text")); // NOI18N

        indexNSRLButton.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.indexNSRLButton.text")); // NOI18N
        indexNSRLButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                indexNSRLButtonActionPerformed(evt);
            }
        });

        removeNSRLButton.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.removeNSRLButton.text")); // NOI18N
        removeNSRLButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeNSRLButtonActionPerformed(evt);
            }
        });

        ingestRunningLabel.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.ingestRunningLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addContainerGap(405, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(nsrlNameLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 218, Short.MAX_VALUE)
                        .addComponent(indexNSRLButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(setNSRLButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeNSRLButton))
                    .addComponent(jLabel2))
                .addContainerGap())
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 534, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(addNotableButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 242, Short.MAX_VALUE)
                .addComponent(removeNotableButton)
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(ingestRunningLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 514, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nsrlNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(removeNSRLButton)
                    .addComponent(setNSRLButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(indexNSRLButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 163, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(removeNotableButton)
                    .addComponent(addNotableButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ingestRunningLabel)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void addNotableButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addNotableButtonActionPerformed
        save();
        
        int retval = fc.showOpenDialog(this);
        
        if(retval == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                String filePath = f.getCanonicalPath();

                if (HashDb.isIndexPath(filePath)) {
                    filePath = HashDb.toDatabasePath(filePath);
                }
                String derivedName = SleuthkitJNI.getDatabaseName(filePath);
                
                String setName = (String) JOptionPane.showInputDialog(this, "New Hash Set name:", "New Hash Set", 
                        JOptionPane.PLAIN_MESSAGE, null, null, derivedName);
                
                if(setName != null && !setName.equals("")) {
                    HashDb newDb = new HashDb(setName, Arrays.asList(new String[]{filePath}), false);
                    int toIndex = JOptionPane.NO_OPTION;
                    if (IndexStatus.isIngestible(newDb.status())) {
                        newDb.setUseForIngest(true);
                    } else {
                        toIndex = JOptionPane.showConfirmDialog(this,
                                "The database you added has no index.\n"
                                + "It will not be used for ingest until you create one.\n"
                                + "Would you like to do so now?", "No Index Exists", JOptionPane.YES_NO_OPTION);
                    }

                    if (toIndex == JOptionPane.YES_OPTION) {
                        try {
                            newDb.createIndex();
                        } catch (TskException ex) {
                            logger.log(Level.WARNING, "Error creating index", ex);
                        }
                    }
                    notableTableModel.newSet(newDb); // TODO: support multiple file paths
                }


            } catch (IOException ex) {
                logger.log(Level.WARNING, "Couldn't get selected file path.", ex);
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Invalid database: ", ex);
                int tryAgain = JOptionPane.showConfirmDialog(this, 
                            "Database file you chose cannot be opened.\n" + 
                            "If it was just an index, please try to recreate it from the database.\n" + 
                            "Would you like to choose another database?", "Invalid File", JOptionPane.YES_NO_OPTION);
                if(tryAgain == JOptionPane.YES_OPTION) {
                    setNSRLButtonActionPerformed(null);
                }
            }
        }
        save();
    }//GEN-LAST:event_addNotableButtonActionPerformed

    private void removeNotableButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeNotableButtonActionPerformed
        int selected = notableHashSetTable.getSelectedRow();
        if(selected >= 0)
            notableTableModel.removeSetAt(notableHashSetTable.getSelectedRow());
    }//GEN-LAST:event_removeNotableButtonActionPerformed

    private void setNSRLButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setNSRLButtonActionPerformed
        save();
        
        int retval = fc.showOpenDialog(this);
        
        if(retval == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                String filePath = f.getCanonicalPath();

                if (HashDb.isIndexPath(filePath)) {
                    filePath = HashDb.toDatabasePath(filePath);
                }
                String derivedName = SleuthkitJNI.getDatabaseName(filePath);

                this.nsrlSet = new HashDb(derivedName, Arrays.asList(new String[]{filePath}), false); // TODO: support multiple file paths
                int toIndex = JOptionPane.NO_OPTION;
                if(IndexStatus.isIngestible(this.nsrlSet.status())) {
                    this.nsrlSet.setUseForIngest(true);
                } else {
                    toIndex = JOptionPane.showConfirmDialog(this, 
                            "The NSRL database you added has no index.\n" + 
                            "It will not be used for ingest until you create one.\n" + 
                            "Would you like to do so now?", "No Index Exists", JOptionPane.YES_NO_OPTION);
                }

                nsrlNameLabel.setText(nsrlSet.getName());
                setButtonFromIndexStatus(indexNSRLButton, nsrlSet.status());
                removeNSRLButton.setEnabled(true);
                save();
                
                if(toIndex == JOptionPane.YES_OPTION) {
                    indexNSRLButtonActionPerformed(null);
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Couldn't get selected file path.", ex);
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Invalid database: ", ex);
                int tryAgain = JOptionPane.showConfirmDialog(this, 
                            "Database file you chose cannot be opened.\n" + 
                            "If it was just an index, please try to recreate it from the database.\n" + 
                            "Would you like to choose another database?", "Invalid File", JOptionPane.YES_NO_OPTION);
                if(tryAgain == JOptionPane.YES_OPTION) {
                    setNSRLButtonActionPerformed(null);
                }
            }
        }
    }//GEN-LAST:event_setNSRLButtonActionPerformed

    private void indexNSRLButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_indexNSRLButtonActionPerformed
        try {
            nsrlSet.createIndex();
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error creating index", ex);
        }
        setButtonFromIndexStatus(indexNSRLButton, nsrlSet.status());
    }//GEN-LAST:event_indexNSRLButtonActionPerformed

    private void removeNSRLButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeNSRLButtonActionPerformed
        this.nsrlSet = null;
        save();
        setButtonFromIndexStatus(indexNSRLButton, IndexStatus.NO_DB);
        nsrlNameLabel.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.nsrlNameLabel.text"));
        removeNSRLButton.setEnabled(false);
    }//GEN-LAST:event_removeNSRLButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addNotableButton;
    private javax.swing.JButton indexNSRLButton;
    private javax.swing.JLabel ingestRunningLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable notableHashSetTable;
    private javax.swing.JLabel nsrlNameLabel;
    private javax.swing.JButton removeNSRLButton;
    private javax.swing.JButton removeNotableButton;
    private javax.swing.JButton setNSRLButton;
    // End of variables declaration//GEN-END:variables

    private class HashSetTableModel extends AbstractTableModel {
        //data

        private HashDbXML xmlHandle = HashDbXML.getCurrent();

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public int getRowCount() {
            return xmlHandle.getKnownBadSets().size();
        }

        @Override
        public String getColumnName(int column) {
            switch(column) {
                case 0:
                    return "Name";
                case 1:
                    return "Location";
                case 2:
                    return "Status";
                default:
                    return "Ingest";
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HashDb entry = xmlHandle.getKnownBadSets().get(rowIndex);
            switch(columnIndex) {
                case 0:
                    return entry.getName();
                case 1:
                    return entry.getDatabasePaths().get(0); //TODO: support multiple paths
                case 2:
                    return entry.status();
                default:
                    return entry.getUseForIngest();
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if(ingestRunning)
                return false;
            if(columnIndex == 2)
                return true;
            if(columnIndex == 3)
                return true;
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            HashDb entry = xmlHandle.getKnownBadSets().get(rowIndex);
            switch(columnIndex) {
                case 0:
                    entry.setName((String) aValue);
                    break;
                case 1:
                    entry.setDatabasePaths(Arrays.asList(new String[]{(String) aValue}));//TODO: support multiple paths
                    break;
                case 2:
                    break;
                case 3:
                    if(((Boolean) getValueAt(rowIndex, columnIndex)) || IndexStatus.isIngestible(entry.status()))
                        entry.setUseForIngest((Boolean) aValue);
                    else
                        JOptionPane.showMessageDialog(HashDbMgmtPanel.this, "Databases must be indexed before they can be used for ingest");
            }
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
        
        void resync() {
            fireTableDataChanged();
        }
        
        void newSet(HashDb db) {
            xmlHandle.addKnownBadSet(db);
            resync();
        }
        
        void removeSetAt(int index) {
            xmlHandle.removeKnownBadSetAt(index);
            resync();
        }
        
        HashDb getHashSetAt(int row) {
            return xmlHandle.getKnownBadSets().get(row);
        }
    }
    
    private class CheckBoxRenderer extends JCheckBox implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {

            this.setHorizontalAlignment(JCheckBox.CENTER);
            this.setVerticalAlignment(JCheckBox.CENTER);
            setEnabled(!ingestRunning);

            Boolean selected = (Boolean) table.getModel().getValueAt(row, column);
            setSelected(selected);
            if (isSelected) {
                setBackground(notableHashSetTable.getSelectionBackground());
            } else {
                setBackground(notableHashSetTable.getBackground());
            }
            return this;
        }
    }
    
    static void setButtonFromIndexStatus(JButton theButton, IndexStatus status) {
        switch (status) {
            case INDEX_OUTDATED:
                theButton.setText("Re-index");
                theButton.setEnabled(true);
                break;
            case INDEX_CURRENT:
                theButton.setText("Re-index");
                theButton.setEnabled(true);
                break;
            case NO_INDEX:
                theButton.setText("Index");
                theButton.setEnabled(true);
                break;
            case INDEXING:
                theButton.setText("Indexing");
                theButton.setEnabled(false);
                break;
            default:
                theButton.setText("Index");
                theButton.setEnabled(false);
        }
        if (ingestRunning) {
            theButton.setEnabled(false);
        }
    }
}

