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

package org.sleuthkit.autopsy.fileextmismatch;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.filetypeid.FileTypeIdIngestModule;

/**
 * Container panel for File Extension Mismatch Ingest Module advanced configuration options
 */
final class FileExtMismatchConfigPanel extends javax.swing.JPanel implements OptionsPanel {
    private static Logger logger = Logger.getLogger(FileExtMismatchConfigPanel.class.getName());
    private HashMap<String, String[]> editableMap = new HashMap<>();
    private ArrayList<String> mimeList = null;
    private ArrayList<String> currentExtensions = null;
    private MimeTableModel mimeTableModel;
    private ExtTableModel extTableModel;
    private final String EXT_HEADER_LABEL = NbBundle.getMessage(FileExtMismatchConfigPanel.class,
                                                                "AddFileExtensionAction.extHeaderLbl.text");
    private String selectedMime = "";
    private String selectedExt = "";
    ListSelectionModel lsm = null;
    
    public FileExtMismatchConfigPanel() {
        mimeTableModel = new MimeTableModel();
        extTableModel = new ExtTableModel();
        
        initComponents();
        customizeComponents();
    }
    
    private void customizeComponents() {
        setName(NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.name.text"));
               
        // Handle selections on the left table
        lsm = mimeTable.getSelectionModel();
        lsm.addListSelectionListener(new ListSelectionListener() {        

            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    int index = listSelectionModel.getMinSelectionIndex();
                    listSelectionModel.setSelectionInterval(index, index);
                    
                    selectedMime = mimeList.get(index);
                    String labelStr = EXT_HEADER_LABEL + selectedMime + ":";
                    if (labelStr.length() > 80) {
                        labelStr = labelStr.substring(0, 80);
                    }
                    extHeaderLabel.setText(labelStr);
                    updateExtList();
             
                    extTableModel.resync();
                    //initButtons();
                } else {
                    selectedMime = "";
                    currentExtensions = null;
                    extTableModel.resync();
                }
                
                clearErrLabels();
            }        
        });
        
        // Handle selections on the right table
        ListSelectionModel extLsm = extTable.getSelectionModel();
        extLsm.addListSelectionListener(new ListSelectionListener() {        

            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    int index = listSelectionModel.getMinSelectionIndex();
                    listSelectionModel.setSelectionInterval(index, index);
                    
                    selectedExt = currentExtensions.get(index);
                } else {
                    selectedExt = "";
                }
                
                extRemoveErrLabel.setText(" ");
                
            }        
        });        
        
    }

    private void clearErrLabels() {
        mimeErrLabel.setText(" ");
        mimeRemoveErrLabel.setText(" ");
        extRemoveErrLabel.setText(" ");
        extErrorLabel.setText(" ");
        saveMsgLabel.setText(" ");    
    }            
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        saveButton = new javax.swing.JButton();
        jSplitPane1 = new javax.swing.JSplitPane();
        mimePanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        mimeTable = new javax.swing.JTable();
        userTypeTextField = new javax.swing.JTextField();
        addTypeButton = new javax.swing.JButton();
        removeTypeButton = new javax.swing.JButton();
        mimeErrLabel = new javax.swing.JLabel();
        mimeRemoveErrLabel = new javax.swing.JLabel();
        extensionPanel = new javax.swing.JPanel();
        userExtTextField = new javax.swing.JTextField();
        addExtButton = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        extTable = new javax.swing.JTable();
        removeExtButton = new javax.swing.JButton();
        extHeaderLabel = new javax.swing.JLabel();
        extErrorLabel = new javax.swing.JLabel();
        extRemoveErrLabel = new javax.swing.JLabel();
        saveMsgLabel = new javax.swing.JLabel();

        saveButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/fileextmismatch/save16.png"))); // NOI18N
        saveButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.saveButton.text")); // NOI18N
        saveButton.setEnabled(false);
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        jSplitPane1.setDividerLocation(430);

        jLabel1.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.jLabel1.text")); // NOI18N

        mimeTable.setModel(mimeTableModel);
        jScrollPane2.setViewportView(mimeTable);

        userTypeTextField.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.userTypeTextField.text")); // NOI18N
        userTypeTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                userTypeTextFieldFocusGained(evt);
            }
        });

        addTypeButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.addTypeButton.text")); // NOI18N
        addTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addTypeButtonActionPerformed(evt);
            }
        });

        removeTypeButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.removeTypeButton.text")); // NOI18N
        removeTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeTypeButtonActionPerformed(evt);
            }
        });

        mimeErrLabel.setForeground(new java.awt.Color(255, 0, 0));
        mimeErrLabel.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.mimeErrLabel.text")); // NOI18N

        mimeRemoveErrLabel.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.mimeRemoveErrLabel.text")); // NOI18N

        javax.swing.GroupLayout mimePanelLayout = new javax.swing.GroupLayout(mimePanel);
        mimePanel.setLayout(mimePanelLayout);
        mimePanelLayout.setHorizontalGroup(
            mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mimePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(mimePanelLayout.createSequentialGroup()
                        .addGroup(mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addGroup(mimePanelLayout.createSequentialGroup()
                                .addComponent(userTypeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(addTypeButton))
                            .addComponent(removeTypeButton))
                        .addGap(0, 196, Short.MAX_VALUE))
                    .addComponent(mimeErrLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(mimeRemoveErrLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        mimePanelLayout.setVerticalGroup(
            mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mimePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(userTypeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(addTypeButton))
                .addGap(3, 3, 3)
                .addComponent(mimeErrLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(removeTypeButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(mimeRemoveErrLabel)
                .addContainerGap(45, Short.MAX_VALUE))
        );

        jSplitPane1.setLeftComponent(mimePanel);

        userExtTextField.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.userExtTextField.text")); // NOI18N
        userExtTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                userExtTextFieldFocusGained(evt);
            }
        });

        addExtButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.addExtButton.text")); // NOI18N
        addExtButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addExtButtonActionPerformed(evt);
            }
        });

        extTable.setModel(extTableModel);
        jScrollPane3.setViewportView(extTable);

        removeExtButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.removeExtButton.text")); // NOI18N
        removeExtButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeExtButtonActionPerformed(evt);
            }
        });

        extHeaderLabel.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.extHeaderLabel.text")); // NOI18N

        extErrorLabel.setForeground(new java.awt.Color(255, 0, 0));
        extErrorLabel.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.extErrorLabel.text")); // NOI18N

        extRemoveErrLabel.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.extRemoveErrLabel.text")); // NOI18N

        javax.swing.GroupLayout extensionPanelLayout = new javax.swing.GroupLayout(extensionPanel);
        extensionPanel.setLayout(extensionPanelLayout);
        extensionPanelLayout.setHorizontalGroup(
            extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(extensionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(extHeaderLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(extensionPanelLayout.createSequentialGroup()
                        .addGroup(extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(removeExtButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(extensionPanelLayout.createSequentialGroup()
                                .addComponent(userExtTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(addExtButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addGap(0, 66, Short.MAX_VALUE))
                    .addComponent(extErrorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(extRemoveErrLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        extensionPanelLayout.setVerticalGroup(
            extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, extensionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(extHeaderLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 285, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(userExtTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(addExtButton))
                .addGap(2, 2, 2)
                .addComponent(extErrorLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(removeExtButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(extRemoveErrLabel)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jSplitPane1.setRightComponent(extensionPanel);

        saveMsgLabel.setForeground(new java.awt.Color(0, 0, 255));
        saveMsgLabel.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchConfigPanel.class, "FileExtMismatchConfigPanel.saveMsgLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSplitPane1)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(saveButton, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(saveMsgLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 145, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSplitPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(saveButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(saveMsgLabel)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Add a user-provided filename extension string to the selecte mimetype
    private void addExtButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addExtButtonActionPerformed
        String newExt = userExtTextField.getText();
        if (newExt.isEmpty()) {
            extErrorLabel.setForeground(Color.red);            
            extErrorLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addExtButton.errLabel.empty"));
            return;
        }
        
        if (selectedMime.isEmpty()) {
            extErrorLabel.setForeground(Color.red);  
            extErrorLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addExtButton.errLabel.noMimeType"));
            return;
        }
        
        if (currentExtensions.contains(newExt)) {
            extErrorLabel.setForeground(Color.red);
            extErrorLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addExtButton.errLabel.extExists"));
            return;            
        }                
        
        ArrayList<String> editedExtensions = new ArrayList<>(Arrays.asList(editableMap.get(selectedMime)));        
        editedExtensions.add(newExt);
        
        // Old array will be replaced by new array for this key
        editableMap.put(selectedMime, editedExtensions.toArray(new String[0])); 

        // Refresh table
        updateExtList();       
        extTableModel.resync();
        
        // user feedback for successful add
        extErrorLabel.setForeground(Color.blue);
        extErrorLabel.setText(
                NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addExtButton.errLabel.extAdded",
                                    newExt));
        extRemoveErrLabel.setText(" ");
        userExtTextField.setText("");
        setIsModified();
    }//GEN-LAST:event_addExtButtonActionPerformed

    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
        store();
    }//GEN-LAST:event_saveButtonActionPerformed

    private void addTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addTypeButtonActionPerformed
        String newMime = userTypeTextField.getText();
        if (newMime.isEmpty()) {
            mimeErrLabel.setForeground(Color.red);
            mimeErrLabel.setText(NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addTypeButton.empty"));
            return;
        }
        if (newMime.equals( "application/octet-stream")){
            mimeErrLabel.setForeground(Color.red);
            mimeErrLabel.setText(NbBundle.getMessage(this.getClass(),
                                                     "FileExtMismatchConfigPanel.addTypeButton.mimeTypeNotSupported"));
            return;   
        }
        if (mimeList.contains(newMime)) {
            mimeErrLabel.setForeground(Color.red);
            mimeErrLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addTypeButton.mimeTypeExists"));
            return;            
        }
        
        if (!FileTypeIdIngestModule.isMimeTypeDetectable(newMime)) {
            mimeErrLabel.setForeground(Color.red);
            mimeErrLabel.setText(NbBundle.getMessage(this.getClass(),
                                                     "FileExtMismatchConfigPanel.addTypeButton.mimeTypeNotDetectable"));
            return;                        
        }
        
        editableMap.put(newMime, new String[0]);

        // Refresh table
        updateMimeList();
        mimeTableModel.resync();

        // user feedback for successful add
        //selectByMimeString(newMime);
        mimeErrLabel.setForeground(Color.blue);
        mimeErrLabel.setText(
                NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addTypeButton.mimeTypeAdded", newMime));
        mimeRemoveErrLabel.setText(" ");
        userTypeTextField.setText("");
        setIsModified();
    }//GEN-LAST:event_addTypeButtonActionPerformed

    private void userExtTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_userExtTextFieldFocusGained
        extErrorLabel.setText(" "); //space so Swing doesn't mess up vertical spacing
    }//GEN-LAST:event_userExtTextFieldFocusGained

    private void userTypeTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_userTypeTextFieldFocusGained
        mimeErrLabel.setText(" "); //space so Swing doesn't mess up vertical spacing
    }//GEN-LAST:event_userTypeTextFieldFocusGained

    private void removeTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeTypeButtonActionPerformed
        if (selectedMime.isEmpty()) {
            mimeRemoveErrLabel.setForeground(Color.red);
            mimeRemoveErrLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.removeTypeButton.noneSelected"));
            return;
        }        
        
        editableMap.remove(selectedMime);    
        String deadMime = selectedMime;
        
        // Refresh table
        updateMimeList();
        mimeTableModel.resync();                
                
        // user feedback for successful add
        mimeRemoveErrLabel.setForeground(Color.blue);
        mimeRemoveErrLabel.setText(
                NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.remoteTypeButton.deleted", deadMime));
        setIsModified();
    }//GEN-LAST:event_removeTypeButtonActionPerformed

    private void removeExtButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeExtButtonActionPerformed
        if (selectedExt.isEmpty()) {
            extRemoveErrLabel.setForeground(Color.red);
            extRemoveErrLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.removeExtButton.noneSelected"));
            return;
        }        
             
        if (selectedMime.isEmpty()) {
            extErrorLabel.setForeground(Color.red);  
            extErrorLabel.setText(NbBundle.getMessage(this.getClass(),
                                                      "FileExtMismatchConfigPanel.removeExtButton.noMimeTypeSelected"));
            return;
        }        
        
        ArrayList<String> editedExtensions = new ArrayList<>(Arrays.asList(editableMap.get(selectedMime)));        
        editedExtensions.remove(selectedExt);
        String deadExt = selectedExt;
        
        // Old array will be replaced by new array for this key
        editableMap.put(selectedMime, editedExtensions.toArray(new String[0]));         
        
        // Refresh tables        
        updateExtList();
        extTableModel.resync();             
        
        // user feedback for successful add
        extRemoveErrLabel.setForeground(Color.blue);
        extRemoveErrLabel.setText(
                NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.removeExtButton.deleted", deadExt));
        setIsModified();
    }//GEN-LAST:event_removeExtButtonActionPerformed

    private void updateMimeList() {
        mimeList = new ArrayList<>(editableMap.keySet());
        if (mimeList.size() > 0) {
            Collections.sort(mimeList);
        }
    }
    
    private void updateExtList() {
        String[] temp = editableMap.get(selectedMime);
        if (temp != null) {
            currentExtensions = new ArrayList<>(Arrays.asList(temp));
            if (temp.length > 0) {
                Collections.sort(currentExtensions);
            }
        } else {
            currentExtensions = null;
        }
    }
    
    @Override
    public void load() {
        // Load the XML into a buffer that the user can modify. They can choose
        // to save it back to the file after making changes.
        editableMap = FileExtMismatchXML.getDefault().load();
        updateMimeList();
        updateExtList();
    }

    @Override
    public void store() {
        if (FileExtMismatchXML.getDefault().save(editableMap)) {            
            mimeErrLabel.setText(" ");
            mimeRemoveErrLabel.setText(" ");
            extRemoveErrLabel.setText(" ");
            extErrorLabel.setText(" ");
            
            saveMsgLabel.setText(NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.store.msg"));
            saveButton.setEnabled(false);
        } else {
            //error
            JOptionPane.showMessageDialog(this,
                                          NbBundle.getMessage(this.getClass(),
                                                              "FileExtMismatchConfigPanel.store.msgDlg.msg"),
                                          NbBundle.getMessage(this.getClass(),
                                                              "FileExtMismatchConfigPanel.save.msgDlg.title"),
                                          JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setIsModified() {
        saveButton.setEnabled(true);
        saveMsgLabel.setText(" ");
    }
    
    public void cancel() {
        clearErrLabels();
        load(); // The next time this panel is opened, we want it to be fresh
    }
    
    public void ok() {
        // if data is unsaved
        if (saveButton.isEnabled()) {
           int choice = JOptionPane.showConfirmDialog(this,
                                                      NbBundle.getMessage(this.getClass(),
                                                                          "FileExtMismatchConfigPanel.ok.confDlg.msg"),
                                                      NbBundle.getMessage(this.getClass(),
                                                                          "FileExtMismatchConfigPanel.confDlg.title"),
                                                      JOptionPane.YES_NO_OPTION);
           if (choice == JOptionPane.YES_OPTION) {
               store();
           }
        }
        clearErrLabels();   
        load(); // The next time this panel is opened, we want it to be fresh
    }
    
    boolean valid() {
        return true;
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addExtButton;
    private javax.swing.JButton addTypeButton;
    private javax.swing.JLabel extErrorLabel;
    private javax.swing.JLabel extHeaderLabel;
    private javax.swing.JLabel extRemoveErrLabel;
    private javax.swing.JTable extTable;
    private javax.swing.JPanel extensionPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JLabel mimeErrLabel;
    private javax.swing.JPanel mimePanel;
    private javax.swing.JLabel mimeRemoveErrLabel;
    private javax.swing.JTable mimeTable;
    private javax.swing.JButton removeExtButton;
    private javax.swing.JButton removeTypeButton;
    private javax.swing.JButton saveButton;
    private javax.swing.JLabel saveMsgLabel;
    private javax.swing.JTextField userExtTextField;
    private javax.swing.JTextField userTypeTextField;
    // End of variables declaration//GEN-END:variables

    private class MimeTableModel extends AbstractTableModel {

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public int getRowCount() {
            return editableMap == null ? 0 : editableMap.size();
        }

        @Override
        public String getColumnName(int column) {
            String colName = null;

            switch (column) {
                case 0:
                    colName = NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.mimeTableModel.colName");
                    break;
                default:
                    ;

            }
            return colName;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;
            if ((mimeList == null) || (rowIndex > mimeList.size())) {
                return "";
            }
            String word = mimeList.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    ret = (Object) word;
                    break;
                default:
                    logger.log(Level.SEVERE, "Invalid table column index: " + columnIndex);
                    break;
            }
            return ret;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        void resync() {
            fireTableDataChanged();
        }
    }
    
    private class ExtTableModel extends AbstractTableModel {

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public int getRowCount() {
            return currentExtensions == null ? 0 : currentExtensions.size();
        }

        @Override
        public String getColumnName(int column) {
            String colName = null;

            switch (column) {
                case 0:
                    colName = NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.extTableModel.colName");
                    break;
                default:
                    ;

            }
            return colName;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;
            
            if ((currentExtensions == null) || (currentExtensions.size() == 0) || (rowIndex > currentExtensions.size())) {
                return "";
            }
            String word = currentExtensions.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    ret = (Object) word;
                    break;
                default:
                    logger.log(Level.SEVERE, "Invalid table column index: " + columnIndex);
                    break;
            }
            return ret;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        void resync() {
            fireTableDataChanged();
        }
    }    

}
