/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.hashdatabase;

import java.awt.event.KeyEvent;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import org.sleuthkit.datamodel.TskException;

final class HashDatabasePanel extends javax.swing.JPanel {

    private final HashDatabaseOptionsPanelController controller;
    private HashSetTableModel hashSetTableModel;
    private static final Logger logger = Logger.getLogger(HashDatabasePanel.class.getName());
    private static boolean ingestRunning = false;

    HashDatabasePanel(HashDatabaseOptionsPanelController controller) {
        this.controller = controller;
        this.hashSetTableModel = new HashSetTableModel();
        initComponents();
        customizeComponents();
        // TODO listen to changes in form fields and call controller.changed()
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
            setButtonFromIndexStatus(this.indexButton, this.hashDbIndexStatusLabel, db.status());
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
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        hashSetTable = new javax.swing.JTable();
        deleteButton = new javax.swing.JButton();
        importButton = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        nameLabel = new javax.swing.JLabel();
        locationLabel = new javax.swing.JLabel();
        typeLabel = new javax.swing.JLabel();
        indexLabel = new javax.swing.JLabel();
        hashDbNameLabel = new javax.swing.JLabel();
        hashDbLocationLabel = new javax.swing.JLabel();
        hashDbTypeLabel = new javax.swing.JLabel();
        hashDbIndexStatusLabel = new javax.swing.JLabel();
        indexButton = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        useForIngestCheckbox = new javax.swing.JCheckBox();
        showInboxMessagesCheckBox = new javax.swing.JCheckBox();
        ingestRunningLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.jLabel4.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel6, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.jLabel6.text")); // NOI18N

        jButton3.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(jButton3, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.jButton3.text")); // NOI18N

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.jPanel2.border.title"))); // NOI18N

        hashSetTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        hashSetTable.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                hashSetTableKeyPressed(evt);
            }
        });
        jScrollPane1.setViewportView(hashSetTable);

        deleteButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/hashdatabase/delte.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteButton, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.deleteButton.text")); // NOI18N
        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteButtonActionPerformed(evt);
            }
        });

        importButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/hashdatabase/import.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(importButton, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.importButton.text")); // NOI18N
        importButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addComponent(importButton, javax.swing.GroupLayout.PREFERRED_SIZE, 165, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(deleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 166, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addComponent(jScrollPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(importButton)
                    .addComponent(deleteButton))
                .addContainerGap())
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.jPanel3.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(nameLabel, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.nameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(locationLabel, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.locationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(typeLabel, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.typeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(indexLabel, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.indexLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbNameLabel, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.hashDbNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbLocationLabel, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.hashDbLocationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbTypeLabel, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.hashDbTypeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbIndexStatusLabel, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.hashDbIndexStatusLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(indexButton, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.indexButton.text")); // NOI18N
        indexButton.setEnabled(false);
        indexButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                indexButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(indexButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(nameLabel, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(locationLabel, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(typeLabel, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(indexLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(hashDbTypeLabel)
                    .addComponent(hashDbLocationLabel)
                    .addComponent(hashDbNameLabel)
                    .addComponent(hashDbIndexStatusLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nameLabel)
                    .addComponent(hashDbNameLabel))
                .addGap(5, 5, 5)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(locationLabel)
                    .addComponent(hashDbLocationLabel))
                .addGap(5, 5, 5)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(typeLabel)
                    .addComponent(hashDbTypeLabel))
                .addGap(5, 5, 5)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(indexLabel)
                    .addComponent(hashDbIndexStatusLabel))
                .addGap(5, 5, 5)
                .addComponent(indexButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.jPanel4.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(useForIngestCheckbox, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.useForIngestCheckbox.text")); // NOI18N
        useForIngestCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useForIngestCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(showInboxMessagesCheckBox, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.showInboxMessagesCheckBox.text")); // NOI18N
        showInboxMessagesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showInboxMessagesCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(useForIngestCheckbox)
                    .addComponent(showInboxMessagesCheckBox))
                .addContainerGap(135, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(useForIngestCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(showInboxMessagesCheckBox)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        ingestRunningLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(ingestRunningLabel, org.openide.util.NbBundle.getMessage(HashDatabasePanel.class, "HashDatabasePanel.ingestRunningLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(ingestRunningLabel)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(ingestRunningLabel)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void indexButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_indexButtonActionPerformed
        int selected = getSelection();
        HashDb current = HashDbXML.getCurrent().getAllSets().get(selected);
        try {
            current.createIndex();
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error creating index", ex);
        }
        setButtonFromIndexStatus(indexButton, this.hashDbIndexStatusLabel, current.status());
    }//GEN-LAST:event_indexButtonActionPerformed

    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
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

    void load() {
        // TODO read settings and initialize GUI
        // Example:        
        // someCheckBox.setSelected(Preferences.userNodeForPackage(HashDatabasePanel.class).getBoolean("someFlag", false));
        // or for org.openide.util with API spec. version >= 7.4:
        // someCheckBox.setSelected(NbPreferences.forModule(HashDatabasePanel.class).getBoolean("someFlag", false));
        // or:
        // someTextField.setText(SomeSystemOption.getDefault().getSomeStringProperty());
    }

    void store() {
        // TODO store modified settings
        // Example:
        // Preferences.userNodeForPackage(HashDatabasePanel.class).putBoolean("someFlag", someCheckBox.isSelected());
        // or for org.openide.util with API spec. version >= 7.4:
        // NbPreferences.forModule(HashDatabasePanel.class).putBoolean("someFlag", someCheckBox.isSelected());
        // or:
        // SomeSystemOption.getDefault().setSomeStringProperty(someTextField.getText());
    }

    boolean valid() {
        // TODO check whether form is consistent and complete
        return true;
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteButton;
    private javax.swing.JLabel hashDbIndexStatusLabel;
    private javax.swing.JLabel hashDbLocationLabel;
    private javax.swing.JLabel hashDbNameLabel;
    private javax.swing.JLabel hashDbTypeLabel;
    private javax.swing.JTable hashSetTable;
    private javax.swing.JButton importButton;
    private javax.swing.JButton indexButton;
    private javax.swing.JLabel indexLabel;
    private javax.swing.JLabel ingestRunningLabel;
    private javax.swing.JButton jButton3;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel locationLabel;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JCheckBox showInboxMessagesCheckBox;
    private javax.swing.JLabel typeLabel;
    private javax.swing.JCheckBox useForIngestCheckbox;
    // End of variables declaration//GEN-END:variables
    private void importHashSet(java.awt.event.ActionEvent evt) {
        new HashDbAddDatabaseDialog().display();
        hashSetTableModel.resync();
        /*int size = 0;
        if(!nsrl) {
            size = HashDbXML.getCurrent().getKnownBadSets().size();
        }
        setSelection(size);*/
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
            if (xmlHandle.getNSRLSet() != null) {
                if(rowIndex == 0) {
                    return xmlHandle.getNSRLSet().getName() + " (NSRL)";
                } else {
                    return xmlHandle.getKnownBadSets().get(rowIndex-1).getName();
                }
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
    
    static void setButtonFromIndexStatus(JButton theButton, JLabel theLabel, IndexStatus status) {
        theLabel.setText(status.message());
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
