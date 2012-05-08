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
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.sleuthkit.autopsy.hashdatabase.HashDb.DBType;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskException;

/**
 *
 * @author dfickling
 */
public class HashDbMgmtPanel extends javax.swing.JPanel {
    
    private static final Logger logger = Logger.getLogger(HashDbMgmtPanel.class.getName());
    private HashSetTableModel notableTableModel;
    private HashSetTableModel nsrlTableModel;
    private JFileChooser fc = new JFileChooser();
    private static HashDbMgmtPanel instance;

    /** Creates new form HashDbMgmtPanel */
    private HashDbMgmtPanel() {
        notableTableModel = new HashSetTableModel();
        nsrlTableModel = new HashSetTableModel();
        initComponents();
        customizeComponents();
    }
    
    public static HashDbMgmtPanel getDefault() {
        if(instance == null) {
            instance = new HashDbMgmtPanel();
        }
        return instance;
    }
    
    private void customizeComponents() {
        notableHashSetTable.setModel(notableTableModel);
        notableHashSetTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        notableHashSetTable.setRowHeight(25);
        notableTableModel.resync(DBType.NOTABLE);
        nsrlHashSetTable.setModel(nsrlTableModel);
        nsrlHashSetTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        nsrlHashSetTable.setRowHeight(25);
        nsrlTableModel.resync(DBType.NSRL);
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String[] EXTENSION = new String[] { "txt", "idx", "hash", "Hash" };
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Hash Database File", EXTENSION);
        fc.setFileFilter(filter);
        fc.setMultiSelectionEnabled(false);
        
        final int width1 = jScrollPane1.getPreferredSize().width;
        TableColumn column1 = null;
        for (int i = 0; i < notableHashSetTable.getColumnCount(); i++) {
            column1 = notableHashSetTable.getColumnModel().getColumn(i);
            if (i == 2) {
                ButtonRenderer br = new ButtonRenderer();
                br.getTheButton().addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        int row = notableHashSetTable.getSelectedRow();
                        try {
                            notableTableModel.getHashSetAt(row).createIndex();
                        } catch (TskException ex) {
                            logger.log(Level.WARNING, "Error creating index", ex);
                        }
                        notableTableModel.resync(DBType.NOTABLE);
                    }
                });
                column1.setCellRenderer(br);
                column1.setCellEditor(br);
            }
            if (i == 3) {
                column1.setCellRenderer(new CheckBoxRenderer());
            }
        }
        
        final int width2 = jScrollPane2.getPreferredSize().width;
        TableColumn column2 = null;
        for (int i = 0; i < nsrlHashSetTable.getColumnCount(); i++) {
            column2 = nsrlHashSetTable.getColumnModel().getColumn(i);
            if (i == 2) {
                ButtonRenderer br = new ButtonRenderer();
                br.getTheButton().addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        int row = nsrlHashSetTable.getSelectedRow();
                        try {
                            nsrlTableModel.getHashSetAt(row).createIndex();
                        } catch (TskException ex) {
                            logger.log(Level.WARNING, "Error creating index", ex);
                        }
                        nsrlTableModel.resync(DBType.NSRL);
                    }
                });
                column2.setCellRenderer(br);
                column2.setCellEditor(br);
            }
            if (i == 3) {
                column2.setCellRenderer(new CheckBoxRenderer());
            }
        }
    }
    
    /**
     * Checks if indexes exist for all defined databases
     * @return true if Sleuth Kit can open the indexes of all databases
     * than have been selected
     */
    boolean indexesExist() {
        return notableTableModel.indexesExist() && nsrlTableModel.indexesExist();
    }
    
    /**
     * Save the table settings
     * @return whether save was successful
     */
    boolean save() {
        notableTableModel.saveAll();
        nsrlTableModel.saveAll();
        return true;
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
        addNSRLButton = new javax.swing.JButton();
        removeNSRLButton = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        nsrlHashSetTable = new javax.swing.JTable();
        addNotableButton = new javax.swing.JButton();
        removeNotableButton = new javax.swing.JButton();

        jScrollPane1.setViewportView(notableHashSetTable);

        addNSRLButton.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.addNSRLButton.text")); // NOI18N
        addNSRLButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addNSRLButtonActionPerformed(evt);
            }
        });

        removeNSRLButton.setText(org.openide.util.NbBundle.getMessage(HashDbMgmtPanel.class, "HashDbMgmtPanel.removeNSRLButton.text")); // NOI18N
        removeNSRLButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeNSRLButtonActionPerformed(evt);
            }
        });

        nsrlHashSetTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        jScrollPane2.setViewportView(nsrlHashSetTable);

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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(addNSRLButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 235, Short.MAX_VALUE)
                .addComponent(removeNSRLButton)
                .addContainerGap())
            .addComponent(jScrollPane1, 0, 0, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(addNotableButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 223, Short.MAX_VALUE)
                .addComponent(removeNotableButton)
                .addContainerGap())
            .addComponent(jScrollPane2, 0, 0, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addNotableButton)
                    .addComponent(removeNotableButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 186, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addNSRLButton)
                    .addComponent(removeNSRLButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void addNSRLButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addNSRLButtonActionPerformed
        save();
        
        int retval = fc.showOpenDialog(this);
        
        if(retval == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                String filePath = f.getCanonicalPath();

                if (HashDb.isIndexPath(filePath)) {
                    filePath = HashDb.toDatabasePath(filePath);
                }
                String derivedName;
                try {
                    derivedName = SleuthkitJNI.getDatabaseName(filePath);
                } catch (TskException ex) {
                    derivedName = "";
                }
                
                String setName = (String) JOptionPane.showInputDialog(this, "New Hash Set name:", "New Hash Set", 
                        JOptionPane.PLAIN_MESSAGE, null, null, derivedName);
                
                nsrlTableModel.newSet(setName, Arrays.asList(new String[] {filePath}), false, HashDb.DBType.NSRL); // TODO: support multiple file paths


            } catch (IOException ex) {
                logger.log(Level.WARNING, "Couldn't get selected file path.", ex);
            }
        }
    }//GEN-LAST:event_addNSRLButtonActionPerformed

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
                String derivedName;
                try {
                    derivedName = SleuthkitJNI.getDatabaseName(filePath);
                } catch (TskException ex) {
                    derivedName = "";
                }
                
                String setName = (String) JOptionPane.showInputDialog(this, "New Hash Set name:", "New Hash Set", 
                        JOptionPane.PLAIN_MESSAGE, null, null, derivedName);
                
                if(setName != null && !setName.equals(""))
                    notableTableModel.newSet(setName, Arrays.asList(new String[] {filePath}), false, HashDb.DBType.NOTABLE); // TODO: support multiple file paths


            } catch (IOException ex) {
                logger.log(Level.WARNING, "Couldn't get selected file path.", ex);
            }
        }
    }//GEN-LAST:event_addNotableButtonActionPerformed

    private void removeNotableButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeNotableButtonActionPerformed
        notableTableModel.removeSetAt(notableHashSetTable.getSelectedRow());
    }//GEN-LAST:event_removeNotableButtonActionPerformed

    private void removeNSRLButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeNSRLButtonActionPerformed
        nsrlTableModel.removeSetAt(nsrlHashSetTable.getSelectedRow());
    }//GEN-LAST:event_removeNSRLButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addNSRLButton;
    private javax.swing.JButton addNotableButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable notableHashSetTable;
    private javax.swing.JTable nsrlHashSetTable;
    private javax.swing.JButton removeNSRLButton;
    private javax.swing.JButton removeNotableButton;
    // End of variables declaration//GEN-END:variables

    private class HashSetTableModel extends AbstractTableModel {
        //data

        private HashDbXML xmlHandle = HashDbXML.getCurrent();
        private List<HashDb> data = new ArrayList<HashDb>();

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public int getRowCount() {
            return data.size();
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
                    return "Use For Ingest";
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HashDb entry = data.get(rowIndex);
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
            return columnIndex == 2 || columnIndex == 3; //(status or ingest)
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            HashDb entry = data.get(rowIndex);
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
                    entry.setUseForIngest((Boolean) aValue);
            }
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
        
        void resync(DBType type) {
            data.clear();
            data.addAll(xmlHandle.getSets(type));
            fireTableDataChanged();
        }
        
        void newSet(String name, List<String> paths, boolean useForIngest, DBType type) {
            xmlHandle.addSet(new HashDb(name, type, paths, useForIngest));
            resync(type);
        }
        
        void removeSetAt(int index) {
            HashDb db = data.get(index);
            xmlHandle.removeSet(db);
            resync(db.getType());
        }
        
        void saveAll() {
            xmlHandle.putAll(data);
        }
        
        boolean indexesExist() {
            boolean ret = true;
            for(HashDb db : xmlHandle.getSets()) {
                ret = ret && db.databaseExists();
            }
            return ret;
        }
        
        HashDb getHashSetAt(int row) {
            return data.get(row);
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
    
    private class ButtonRenderer extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
        
        private JButton theButton;
        
        private ButtonRenderer() {
            theButton = new JButton();
        }
        
        JButton getTheButton() {
            return theButton;
        }
        
        void updateData(
                JTable table, boolean isSelected, int row, int column) {
            theButton.setHorizontalAlignment(JButton.CENTER);
            theButton.setVerticalAlignment(JButton.CENTER);
            
            
            IndexStatus selected = (IndexStatus) table.getModel().getValueAt(row, column);
            theButton.setText(selected.toString());
            
            switch (selected) {
                case INDEX_OUTDATED:
                    theButton.setEnabled(true);
                    break;
                case INDEX_CURRENT:
                    theButton.setEnabled(true);
                    break;
                case NO_INDEX:
                    theButton.setEnabled(true);
                    break;
                default:
                    theButton.setEnabled(false);
            }
            
            if (isSelected) {
                theButton.setBackground(notableHashSetTable.getSelectionBackground());
            } else {
                theButton.setBackground(notableHashSetTable.getBackground());
            }
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {
            updateData(table, isSelected, row, column);
            return theButton;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            updateData(table, isSelected, row, column);
            return theButton;
        }

        @Override
        public Object getCellEditorValue() {
            return null;
        }
        
    }
}

