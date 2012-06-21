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
 * HashDbManagementPanel.java
 *
 * Created on Jun 18, 2012, 12:13:03 PM
 */
package org.sleuthkit.autopsy.hashdatabase;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskException;

/**
 *
 * @author dfickling
 */
public class HashDbManagementPanel extends javax.swing.JPanel {

    private HashSetTableModel hashSetTableModel;
    private static final Logger logger = Logger.getLogger(HashDbManagementPanel.class.getName());
    private JFileChooser fc = new JFileChooser();
    private static HashDbManagementPanel instance;
    private static boolean ingestRunning = false;

    public static HashDbManagementPanel getDefault() {
        if (instance == null) {
            instance = new HashDbManagementPanel();
        }
        return instance;
    }

    /** Creates new form HashDbManagementPanel */
    private HashDbManagementPanel() {
        setName(HashDbMgmtAction.ACTION_NAME);
        this.hashSetTableModel = new HashSetTableModel();
        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {
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
        
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String[] EXTENSION = new String[] { "txt", "idx", "hash", "Hash" };
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Hash Database File", EXTENSION);
        fc.setFileFilter(filter);
        fc.setMultiSelectionEnabled(false);
    }

    private void initUI(HashDb db) {
        boolean useForIngestEnabled = db != null && !ingestRunning;
        boolean useForIngestSelected = db != null && db.getUseForIngest();
        boolean showInboxMessagesEnabled = db != null && !ingestRunning && useForIngestSelected;
        boolean showInboxMessagesSelected = db != null && db.getShowInboxMessages();
        boolean deleteButtonEnabled = db != null && !ingestRunning;
        boolean importButtonEnabled = !ingestRunning;
        if (db == null) {
            setButtonFromIndexStatus(this.indexButton, IndexStatus.NONE);
            this.hashDbLocationLabel.setText("No database selected");
            this.hashDbNameLabel.setText("No database selected");
        } else {
            setButtonFromIndexStatus(this.indexButton, db.status());
            String shortenPath = db.getDatabasePaths().get(0);
            this.hashDbLocationLabel.setToolTipText(shortenPath);
            if(shortenPath.length() > 50){
                shortenPath = shortenPath.substring(0, 10 + shortenPath.substring(10).indexOf(File.separator) + 1) + "..." +
                        shortenPath.substring((shortenPath.length() - 20) + shortenPath.substring(shortenPath.length() - 20).indexOf(File.separator));
            }
            this.hashDbLocationLabel.setText(shortenPath);
            this.hashDbNameLabel.setText(db.getName());
        }
        this.useForIngestCheckbox.setSelected(useForIngestSelected);
        this.useForIngestCheckbox.setEnabled(useForIngestEnabled);
        this.showInboxMessagesCheckBox.setSelected(showInboxMessagesSelected);
        this.showInboxMessagesCheckBox.setEnabled(showInboxMessagesEnabled);
        this.deleteButton.setEnabled(deleteButtonEnabled);
        this.importButton.setEnabled(importButtonEnabled);
    }
    
    /**
     * Don't allow any changes if ingest is running
     */
    void setIngestRunning(boolean running) {
        ingestRunning = running;
        if(running) {
            ingestRunningLabel.setText("Ingest is ongoing; some settings will be unavailable until it finishes.");
        } else {
            ingestRunningLabel.setText("");
        }
        
        int selection = getSelection();
        if(selection != -1) {
            initUI(HashDbXML.getCurrent().getAllSets().get(selection));
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

        jSplitPane1 = new javax.swing.JSplitPane();
        leftPanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        hashSetTable = new javax.swing.JTable();
        importButton = new javax.swing.JButton();
        deleteButton = new javax.swing.JButton();
        rightPanel = new javax.swing.JPanel();
        nameLabel = new javax.swing.JLabel();
        hashDbNameLabel = new javax.swing.JLabel();
        locationLabel = new javax.swing.JLabel();
        hashDbLocationLabel = new javax.swing.JLabel();
        useForIngestCheckbox = new javax.swing.JCheckBox();
        showInboxMessagesCheckBox = new javax.swing.JCheckBox();
        indexLabel = new javax.swing.JLabel();
        indexButton = new javax.swing.JButton();
        ingestRunningLabel = new javax.swing.JLabel();

        jScrollPane1.setBackground(new java.awt.Color(255, 255, 255));

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

        importButton.setText(org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.importButton.text")); // NOI18N
        importButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importButtonActionPerformed(evt);
            }
        });

        deleteButton.setText(org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.deleteButton.text")); // NOI18N
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout leftPanelLayout = new javax.swing.GroupLayout(leftPanel);
        leftPanel.setLayout(leftPanelLayout);
        leftPanelLayout.setHorizontalGroup(
            leftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 176, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, leftPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(importButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deleteButton)
                .addGap(32, 32, 32))
        );
        leftPanelLayout.setVerticalGroup(
            leftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, leftPanelLayout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 313, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addGroup(leftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(importButton)
                    .addComponent(deleteButton))
                .addGap(0, 0, 0))
        );

        jSplitPane1.setLeftComponent(leftPanel);

        nameLabel.setText(org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.nameLabel.text")); // NOI18N

        hashDbNameLabel.setText(org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.hashDbNameLabel.text")); // NOI18N

        locationLabel.setText(org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.locationLabel.text")); // NOI18N

        hashDbLocationLabel.setText(org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.hashDbLocationLabel.text")); // NOI18N

        useForIngestCheckbox.setText(org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.useForIngestCheckbox.text")); // NOI18N
        useForIngestCheckbox.setEnabled(false);
        useForIngestCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useForIngestCheckboxActionPerformed(evt);
            }
        });

        showInboxMessagesCheckBox.setText(org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.showInboxMessagesCheckBox.text")); // NOI18N
        showInboxMessagesCheckBox.setEnabled(false);
        showInboxMessagesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showInboxMessagesCheckBoxActionPerformed(evt);
            }
        });

        indexLabel.setText(org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.indexLabel.text")); // NOI18N

        indexButton.setText(org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.indexButton.text")); // NOI18N
        indexButton.setEnabled(false);
        indexButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                indexButtonActionPerformed(evt);
            }
        });

        ingestRunningLabel.setText(org.openide.util.NbBundle.getMessage(HashDbManagementPanel.class, "HashDbManagementPanel.ingestRunningLabel.text")); // NOI18N

        javax.swing.GroupLayout rightPanelLayout = new javax.swing.GroupLayout(rightPanel);
        rightPanel.setLayout(rightPanelLayout);
        rightPanelLayout.setHorizontalGroup(
            rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(rightPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(showInboxMessagesCheckBox)
                    .addComponent(useForIngestCheckbox)
                    .addGroup(rightPanelLayout.createSequentialGroup()
                        .addComponent(nameLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(hashDbNameLabel))
                    .addGroup(rightPanelLayout.createSequentialGroup()
                        .addComponent(locationLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(hashDbLocationLabel))
                    .addGroup(rightPanelLayout.createSequentialGroup()
                        .addComponent(indexLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(indexButton))
                    .addComponent(ingestRunningLabel))
                .addContainerGap(117, Short.MAX_VALUE))
        );
        rightPanelLayout.setVerticalGroup(
            rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(rightPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nameLabel)
                    .addComponent(hashDbNameLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(locationLabel)
                    .addComponent(hashDbLocationLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(indexLabel)
                    .addComponent(indexButton))
                .addGap(7, 7, 7)
                .addComponent(useForIngestCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(showInboxMessagesCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 195, Short.MAX_VALUE)
                .addComponent(ingestRunningLabel)
                .addContainerGap())
        );

        jSplitPane1.setRightComponent(rightPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 494, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 338, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
        int selected = getSelection();
        if(selected >= 0 && selected < HashDbXML.getCurrent().getKnownBadSets().size()) {
            HashDbXML.getCurrent().removeKnownBadSetAt(selected);
        } else {
            HashDbXML.getCurrent().removeNSRLSet();
        }
        hashSetTableModel.resync();
    }//GEN-LAST:event_deleteButtonActionPerformed

    private void importButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importButtonActionPerformed
        boolean nsrl = false;
        int retval = fc.showOpenDialog(this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                String filePath = f.getCanonicalPath();
                if (HashDb.isIndexPath(filePath)) {
                    filePath = HashDb.toDatabasePath(filePath);
                }
                String derivedName = SleuthkitJNI.getDatabaseName(filePath);
                JCheckBox nsrlCheckBox = new JCheckBox("Set as NSRL", false);
                JTextField nameTextField = new JTextField(derivedName);
                JOptionPane.showMessageDialog(this, new Object[]{"New Hash Set name:", nameTextField, nsrlCheckBox}, "New Hash Set", JOptionPane.PLAIN_MESSAGE);
                String setName = nameTextField.getText();
                if (setName != null && !setName.equals("")) {
                    HashDb newDb = new HashDb(setName, Arrays.asList(new String[]{filePath}), false, false);
                    int toIndex = JOptionPane.NO_OPTION;
                    if (IndexStatus.isIngestible(newDb.status())) {
                        newDb.setUseForIngest(true);
                    } else {
                        toIndex = JOptionPane.showConfirmDialog(this, "The database you added has no index.\n" + "It will not be used for ingest until you create one.\n" + "Would you like to do so now?", "No Index Exists", JOptionPane.YES_NO_OPTION);
                    }
                    if (toIndex == JOptionPane.YES_OPTION) {
                        try {
                            newDb.createIndex();
                        } catch (TskException ex) {
                            logger.log(Level.WARNING, "Error creating index", ex);
                        }
                    }
                    if(nsrlCheckBox.isSelected()) {
                        nsrl = true;
                        HashDbXML.getCurrent().setNSRLSet(newDb);
                    } else {
                        HashDbXML.getCurrent().addKnownBadSet(newDb);
                    }
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Couldn't get selected file path.", ex);
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Invalid database: ", ex);
                int tryAgain = JOptionPane.showConfirmDialog(this, "Database file you chose cannot be opened.\n" + "If it was just an index, please try to recreate it from the database.\n" + "Would you like to choose another database?", "Invalid File", JOptionPane.YES_NO_OPTION);
                if (tryAgain == JOptionPane.YES_OPTION) {
                    importButtonActionPerformed(null);
                }
            }
        }
        hashSetTableModel.resync();
        int size = 0;
        if(nsrl) {
            size = HashDbXML.getCurrent().getAllSets().size();
        } else {
            size = HashDbXML.getCurrent().getKnownBadSets().size();
        }
        setSelection(size-1);
    }//GEN-LAST:event_importButtonActionPerformed

    private void hashSetTableKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_hashSetTableKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_DELETE) {
            int selected = getSelection();
            if (selected >= 0 && selected < HashDbXML.getCurrent().getKnownBadSets().size()) {
                HashDbXML.getCurrent().removeKnownBadSetAt(selected);
            } else {
                HashDbXML.getCurrent().removeNSRLSet();
            }
            hashSetTableModel.resync();
        }
    }//GEN-LAST:event_hashSetTableKeyPressed

    private void useForIngestCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useForIngestCheckboxActionPerformed
        int selected = getSelection();
        if (selected >= 0 && selected < HashDbXML.getCurrent().getKnownBadSets().size()) {
            HashDb current = HashDbXML.getCurrent().getKnownBadSets().remove(selected);
            current.setUseForIngest(useForIngestCheckbox.isSelected());
            HashDbXML.getCurrent().addKnownBadSet(selected, current);
        } else {
            HashDb current = HashDbXML.getCurrent().getNSRLSet();
            current.setUseForIngest(useForIngestCheckbox.isSelected());
            HashDbXML.getCurrent().setNSRLSet(current);
        }
        this.showInboxMessagesCheckBox.setEnabled(useForIngestCheckbox.isSelected());
    }//GEN-LAST:event_useForIngestCheckboxActionPerformed

    private void showInboxMessagesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showInboxMessagesCheckBoxActionPerformed
        int selected = getSelection();
        if (selected >= 0 && selected < HashDbXML.getCurrent().getKnownBadSets().size()) {
            HashDb current = HashDbXML.getCurrent().getKnownBadSets().remove(selected);
            current.setShowInboxMessages(showInboxMessagesCheckBox.isSelected());
            HashDbXML.getCurrent().addKnownBadSet(selected, current);
        } else {
            HashDb current = HashDbXML.getCurrent().getNSRLSet();
            current.setShowInboxMessages(showInboxMessagesCheckBox.isSelected());
            HashDbXML.getCurrent().setNSRLSet(current);
        }
    }//GEN-LAST:event_showInboxMessagesCheckBoxActionPerformed

    private void indexButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_indexButtonActionPerformed
        int selected = getSelection();
        HashDb current = HashDbXML.getCurrent().getAllSets().get(selected);
        try {
            current.createIndex();
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error creating index", ex);
        }
        setButtonFromIndexStatus(indexButton, current.status());
    }//GEN-LAST:event_indexButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteButton;
    private javax.swing.JLabel hashDbLocationLabel;
    private javax.swing.JLabel hashDbNameLabel;
    private javax.swing.JTable hashSetTable;
    private javax.swing.JButton importButton;
    private javax.swing.JButton indexButton;
    private javax.swing.JLabel indexLabel;
    private javax.swing.JLabel ingestRunningLabel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JPanel leftPanel;
    private javax.swing.JLabel locationLabel;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JPanel rightPanel;
    private javax.swing.JCheckBox showInboxMessagesCheckBox;
    private javax.swing.JCheckBox useForIngestCheckbox;
    // End of variables declaration//GEN-END:variables

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
            if(rowIndex == this.getRowCount()-1 && xmlHandle.getNSRLSet() != null) {
                return xmlHandle.getNSRLSet().getName() + " (NSRL)";
            } else {
                return xmlHandle.getKnownBadSets().get(rowIndex).getName();
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
