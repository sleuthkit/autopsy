/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.filetypeid;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Collections;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.xml.bind.DatatypeConverter;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.modules.filetypeid.FileType.Signature;
import org.sleuthkit.autopsy.modules.filetypeid.UserDefinedFileTypesManager.UserDefinedFileTypesException;

/**
 * A panel to allow a user to make custom file type definitions. In addition to
 * being an ingest module global settings panel, an instance of this class also
 * appears in the NetBeans options dialog as an options panel.
 */
final class FileTypeIdGlobalSettingsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel {

    private static final String RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM = NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.signatureComboBox.rawItem");
    private static final String ASCII_SIGNATURE_TYPE_COMBO_BOX_ITEM = NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.signatureComboBox.asciiItem");

    private static final String START_OFFSET_RELATIVE_COMBO_BOX_ITEM = NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.offsetComboBox.startItem");
    private static final String END_OFFSET_RELATIVE_COMBO_BOX_ITEM = NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.offsetComboBox.endItem");
    /**
     * The list model for the file types list component of this panel is the set
     * of MIME types associated with the user-defined file types. A mapping of
     * the MIME types to file type objects lies behind the list model. This map
     * is obtained from the user-defined types manager.
     */
    private DefaultListModel<FileType> typesListModel;
    private java.util.List<FileType> fileTypes;
    private AddFileTypeSignatureDialog addSigDialog;

    /**
     * This panel implements a property change listener that listens to ingest
     * job events so it can disable the buttons on the panel if ingest is
     * running. This is done to prevent changes to user-defined types while the
     * type definitions are in use.
     */
    // TODO: Disabling during ingest would not be necessary if the file ingest
    // modules obtained and shared a per data source ingest job snapshot of the
    // file type definitions.
    IngestJobEventPropertyChangeListener ingestJobEventsListener;

    /**
     * Creates a panel to allow a user to make custom file type definitions.
     */
    FileTypeIdGlobalSettingsPanel() {
        initComponents();
        customizeComponents();
        addIngestJobEventsListener();
    }

    /**
     * Does child component initialization in addition to that done by the
     * Matisse generated code.
     */
    @NbBundle.Messages({"FileTypeIdGlobalSettingsPanel.Title=Global File Type Identification Settings"})
    private void customizeComponents() {
        setName(Bundle.FileTypeIdGlobalSettingsPanel_Title());        
        setFileTypesListModel();
        setSignatureTypeComboBoxModel();
        setOffsetRealtiveToComboBoxModel();
        clearTypeDetailsComponents();
        addTypeListSelectionListener();
        addTextFieldListeners();
    }

    /**
     * Sets the list model for the list of file types.
     */
    private void setFileTypesListModel() {
        typesListModel = new DefaultListModel<>();
        typesList.setModel(typesListModel);
    }

    /**
     * Sets the model for the signature type combo box.
     */
    private void setSignatureTypeComboBoxModel() {
        DefaultComboBoxModel<String> sigTypeComboBoxModel = new DefaultComboBoxModel<>();
        sigTypeComboBoxModel.addElement(FileTypeIdGlobalSettingsPanel.RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM);
        sigTypeComboBoxModel.addElement(FileTypeIdGlobalSettingsPanel.ASCII_SIGNATURE_TYPE_COMBO_BOX_ITEM);
    }
    
    /**
     * Sets the model for the signature type combo box.
     */
    private void setOffsetRealtiveToComboBoxModel() {
        DefaultComboBoxModel<String> offsetRelComboBoxModel = new DefaultComboBoxModel<>();
        offsetRelComboBoxModel.addElement(FileTypeIdGlobalSettingsPanel.START_OFFSET_RELATIVE_COMBO_BOX_ITEM);
        offsetRelComboBoxModel.addElement(FileTypeIdGlobalSettingsPanel.END_OFFSET_RELATIVE_COMBO_BOX_ITEM);
    }

    /**
     * Adds a listener to the types list component so that the components in the
     * file type details section of the panel can be populated and cleared based
     * on the selection in the list.
     */
    private void addTypeListSelectionListener() {
        typesList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() == false) {
                    if (typesList.getSelectedIndex() == -1) {
                        clearTypeDetailsComponents();
                    } else {
                        populateTypeDetailsComponents();
                    }
                }
            }
        });
    }

    /**
     * Adds listeners to the text fields that enable and disable the buttons on
     * the panel.
     */
    private void addTextFieldListeners() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                enableButtons();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                enableButtons();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                enableButtons();
            }
        };

        mimeTypeTextField.getDocument().addDocumentListener(listener);
        filesSetNameTextField.getDocument().addDocumentListener(listener);
    }

    /**
     * Add a property change listener that listens to ingest job events to
     * disable the buttons on the panel if ingest is running. This is done to
     * prevent changes to user-defined types while the type definitions are in
     * use.
     */
    // TODO: Disabling during ingest would not be necessary if the file ingest
    // modules obtained and shared a per data source ingest job snapshot of the
    // file type definitions.    
    private void addIngestJobEventsListener() {
        ingestJobEventsListener = new IngestJobEventPropertyChangeListener();
        IngestManager.getInstance().addIngestJobEventListener(ingestJobEventsListener);
    }

    /**
     * A property change listener that listens to ingest job events.
     */
    private class IngestJobEventPropertyChangeListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    enableButtons();
                }
            });
        }
    }

    /**
     * Enables or disables the panel buttons based on the state of the panel and
     * the application.
     */
    private void enableButtons() {
        boolean ingestIsRunning = IngestManager.getInstance().isIngestRunning();
        newTypeButton.setEnabled(!ingestIsRunning);

        boolean fileTypeIsSelected = typesList.getSelectedIndex() != -1;
        deleteTypeButton.setEnabled(!ingestIsRunning && fileTypeIsSelected);

        boolean requiredFieldsPopulated
                = !mimeTypeTextField.getText().isEmpty()
                && (postHitCheckBox.isSelected() ? !filesSetNameTextField.getText().isEmpty() : true);
        saveTypeButton.setEnabled(!ingestIsRunning && requiredFieldsPopulated);

        ingestRunningWarningLabel.setVisible(ingestIsRunning);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void load() {
        try {
            fileTypes = UserDefinedFileTypesManager.getInstance().getUserDefinedFileTypes();
            updateFileTypesListModel();
            if (!typesListModel.isEmpty()) {
                typesList.setSelectedIndex(0);
            }
        } catch (UserDefinedFileTypesException ex) {
            JOptionPane.showMessageDialog(null,
                    ex.getLocalizedMessage(),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.loadFailed.title"),
                    JOptionPane.ERROR_MESSAGE);
            fileTypes = Collections.emptyList();
        }
        enableButtons();
    }

    /**
     * Sets the list model for the file types list component.
     */
    private void updateFileTypesListModel() {
        typesListModel.clear();
        for (FileType fileType : fileTypes) {
            typesListModel.addElement(fileType);

        }
    }

    /**
     * Populates all of the components in the file type details portion of the
     * panel based on the current selection in the file types list.
     */
    private void populateTypeDetailsComponents() {
        FileType fileType = typesList.getSelectedValue();
        if (null != fileType) {
            mimeTypeTextField.setText(fileType.getMimeType());
            mimeTypeTextField.setEditable(false);
            Signature signature = fileType.getSignature();
            FileType.Signature.Type sigType = signature.getType();String signatureBytes;
            if (Signature.Type.RAW == signature.getType()) {
                signatureBytes = DatatypeConverter.printHexBinary(signature.getSignatureBytes());
            } else {
                try {
                    signatureBytes = new String(signature.getSignatureBytes(), "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                    JOptionPane.showMessageDialog(null,
                            ex.getLocalizedMessage(),
                            NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.storeFailed.title"),
                            JOptionPane.ERROR_MESSAGE);
                    signatureBytes = "";
                }
            }
            postHitCheckBox.setSelected(fileType.alertOnMatch());
            filesSetNameTextField.setEnabled(postHitCheckBox.isSelected());
            filesSetNameTextField.setText(fileType.getFilesSetName());
        }
        enableButtons();
    }

    /**
     * Clears all of the components in the individual type details portion of
     * the panel.
     */
    private void clearTypeDetailsComponents() {
        typesList.clearSelection();
        mimeTypeTextField.setText(""); //NON-NLS
        mimeTypeTextField.setEditable(true);
        postHitCheckBox.setSelected(false);
        filesSetNameTextField.setText(""); //NON-NLS
        filesSetNameTextField.setEnabled(false);
        enableButtons();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void store() {
        try {
            UserDefinedFileTypesManager.getInstance().setUserDefinedFileTypes(fileTypes);
        } catch (UserDefinedFileTypesManager.UserDefinedFileTypesException ex) {
            JOptionPane.showMessageDialog(null,
                    ex.getLocalizedMessage(),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.storeFailed.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void saveSettings() {
        store();
    }

    /**
     * @inheritDoc
     */
    @Override
    @SuppressWarnings("FinalizeDeclaration")
    protected void finalize() throws Throwable {
        IngestManager.getInstance().removeIngestJobEventListener(ingestJobEventsListener);
        super.finalize();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        typesScrollPane = new javax.swing.JScrollPane();
        typesList = new javax.swing.JList<FileType>();
        separator = new javax.swing.JSeparator();
        mimeTypeLabel = new javax.swing.JLabel();
        mimeTypeTextField = new javax.swing.JTextField();
        newTypeButton = new javax.swing.JButton();
        deleteTypeButton = new javax.swing.JButton();
        saveTypeButton = new javax.swing.JButton();
        postHitCheckBox = new javax.swing.JCheckBox();
        filesSetNameLabel = new javax.swing.JLabel();
        filesSetNameTextField = new javax.swing.JTextField();
        ingestRunningWarningLabel = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jList1 = new javax.swing.JList();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();

        setMaximumSize(new java.awt.Dimension(500, 300));
        setPreferredSize(new java.awt.Dimension(500, 300));

        typesList.setFont(typesList.getFont().deriveFont(typesList.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        typesList.setMaximumSize(new java.awt.Dimension(150, 0));
        typesList.setMinimumSize(new java.awt.Dimension(150, 0));
        typesScrollPane.setViewportView(typesList);

        separator.setOrientation(javax.swing.SwingConstants.VERTICAL);

        mimeTypeLabel.setFont(mimeTypeLabel.getFont().deriveFont(mimeTypeLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(mimeTypeLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.mimeTypeLabel.text")); // NOI18N

        mimeTypeTextField.setFont(mimeTypeTextField.getFont().deriveFont(mimeTypeTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        mimeTypeTextField.setText(org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.mimeTypeTextField.text")); // NOI18N

        newTypeButton.setFont(newTypeButton.getFont().deriveFont(newTypeButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(newTypeButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.newTypeButton.text")); // NOI18N
        newTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newTypeButtonActionPerformed(evt);
            }
        });

        deleteTypeButton.setFont(deleteTypeButton.getFont().deriveFont(deleteTypeButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(deleteTypeButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.deleteTypeButton.text")); // NOI18N
        deleteTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteTypeButtonActionPerformed(evt);
            }
        });

        saveTypeButton.setFont(saveTypeButton.getFont().deriveFont(saveTypeButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(saveTypeButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.saveTypeButton.text")); // NOI18N
        saveTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveTypeButtonActionPerformed(evt);
            }
        });

        postHitCheckBox.setFont(postHitCheckBox.getFont().deriveFont(postHitCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(postHitCheckBox, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.postHitCheckBox.text")); // NOI18N
        postHitCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                postHitCheckBoxActionPerformed(evt);
            }
        });

        filesSetNameLabel.setFont(filesSetNameLabel.getFont().deriveFont(filesSetNameLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(filesSetNameLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.filesSetNameLabel.text")); // NOI18N

        filesSetNameTextField.setFont(filesSetNameTextField.getFont().deriveFont(filesSetNameTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        filesSetNameTextField.setText(org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.filesSetNameTextField.text")); // NOI18N

        ingestRunningWarningLabel.setFont(ingestRunningWarningLabel.getFont().deriveFont(ingestRunningWarningLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        ingestRunningWarningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/filetypeid/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(ingestRunningWarningLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.ingestRunningWarningLabel.text")); // NOI18N

        jLabel2.setFont(jLabel2.getFont().deriveFont(jLabel2.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.jLabel2.text")); // NOI18N

        jLabel3.setFont(jLabel3.getFont().deriveFont(jLabel3.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.jLabel3.text")); // NOI18N

        jList1.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        jScrollPane1.setViewportView(jList1);

        org.openide.awt.Mnemonics.setLocalizedText(jButton1, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.jButton1.text")); // NOI18N
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jButton2, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.jButton2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jButton3, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.jButton3.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(ingestRunningWarningLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(30, 30, 30))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(saveTypeButton)
                                .addGroup(layout.createSequentialGroup()
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel2)
                                        .addGroup(layout.createSequentialGroup()
                                            .addGap(10, 10, 10)
                                            .addComponent(deleteTypeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addGap(18, 18, 18)
                                            .addComponent(newTypeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addComponent(typesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(separator, javax.swing.GroupLayout.PREFERRED_SIZE, 7, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(layout.createSequentialGroup()
                                            .addComponent(filesSetNameLabel)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(filesSetNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 277, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 333, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(postHitCheckBox)
                                        .addGroup(layout.createSequentialGroup()
                                            .addComponent(jButton1)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(jButton2)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(jButton3))
                                        .addGroup(layout.createSequentialGroup()
                                            .addComponent(mimeTypeLabel)
                                            .addGap(18, 18, 18)
                                            .addComponent(mimeTypeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 262, javax.swing.GroupLayout.PREFERRED_SIZE))))))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(typesScrollPane)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(deleteTypeButton)
                                    .addComponent(newTypeButton)))
                            .addComponent(separator))
                        .addGap(18, 18, 18)
                        .addComponent(ingestRunningWarningLabel))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(24, 24, 24)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(mimeTypeLabel)
                            .addComponent(mimeTypeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jButton1)
                            .addComponent(jButton2)
                            .addComponent(jButton3))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(postHitCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(filesSetNameLabel)
                            .addComponent(filesSetNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(saveTypeButton)
                        .addGap(0, 54, Short.MAX_VALUE))))
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {deleteTypeButton, newTypeButton, saveTypeButton});

    }// </editor-fold>//GEN-END:initComponents

    private void newTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newTypeButtonActionPerformed
        clearTypeDetailsComponents();
    }//GEN-LAST:event_newTypeButtonActionPerformed

    private void deleteTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteTypeButtonActionPerformed
        FileType fileType = typesList.getSelectedValue();
        fileTypes.remove(fileType);
        updateFileTypesListModel();
        if (!typesListModel.isEmpty()) {
            typesList.setSelectedIndex(0);
        }
    }//GEN-LAST:event_deleteTypeButtonActionPerformed

    private void saveTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveTypeButtonActionPerformed

    }//GEN-LAST:event_saveTypeButtonActionPerformed

    private void postHitCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_postHitCheckBoxActionPerformed
        filesSetNameTextField.setEnabled(postHitCheckBox.isSelected());
        enableButtons();
    }//GEN-LAST:event_postHitCheckBoxActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        this.addSigDialog = new AddFileTypeSignatureDialog();
        addSigDialog.addPropertyChangeListener((PropertyChangeEvent evt1) -> {
            if(evt1.getPropertyName().equals(AddFileTypeSignatureDialog.BUTTON_PRESSED.OK.toString())) {
                System.out.println("TEST");
            }
        });
    }//GEN-LAST:event_jButton1ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteTypeButton;
    private javax.swing.JLabel filesSetNameLabel;
    private javax.swing.JTextField filesSetNameTextField;
    private javax.swing.JLabel ingestRunningWarningLabel;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JList jList1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel mimeTypeLabel;
    private javax.swing.JTextField mimeTypeTextField;
    private javax.swing.JButton newTypeButton;
    private javax.swing.JCheckBox postHitCheckBox;
    private javax.swing.JButton saveTypeButton;
    private javax.swing.JSeparator separator;
    private javax.swing.JList<FileType> typesList;
    private javax.swing.JScrollPane typesScrollPane;
    // End of variables declaration//GEN-END:variables

}
