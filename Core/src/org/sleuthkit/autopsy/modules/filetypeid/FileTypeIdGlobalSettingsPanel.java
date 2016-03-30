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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.ListCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.modules.filetypeid.AddFileTypeSignatureDialog.BUTTON_PRESSED;
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
    private DefaultListModel<Signature> signaturesListModel;

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
        setSignaturesListModel();
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

    private void setSignaturesListModel() {
        this.signaturesListModel = new DefaultListModel<>();
        signatureList.setModel(signaturesListModel);
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
            List<Signature> signatures = fileType.getSignatures();
            this.signaturesListModel.clear();
            for (Signature sig : signatures) {
                signaturesListModel.addElement(sig);
            }
            postHitCheckBox.setSelected(fileType.alertOnMatch());
            filesSetNameTextField.setEnabled(postHitCheckBox.isSelected());
            filesSetNameTextField.setText(fileType.getFilesSetName());
            this.signatureList.setEnabled(false);
            this.addSigButton.setEnabled(false);
            this.deleteSigButton.setEnabled(false);
            this.editSigButton.setEnabled(false);
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
        this.signatureList.setEnabled(true);
        this.addSigButton.setEnabled(true);
        this.deleteSigButton.setEnabled(true);
        this.editSigButton.setEnabled(true);
        postHitCheckBox.setSelected(false);
        filesSetNameTextField.setText(""); //NON-NLS
        filesSetNameTextField.setEnabled(false);
        this.signaturesListModel.clear();
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
        signatureList = new javax.swing.JList<Signature>();
        addSigButton = new javax.swing.JButton();
        editSigButton = new javax.swing.JButton();
        deleteSigButton = new javax.swing.JButton();

        setMaximumSize(new java.awt.Dimension(552, 297));
        setPreferredSize(new java.awt.Dimension(552, 297));

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

        signatureList.setModel(new javax.swing.AbstractListModel<Signature>() {
            Signature[] signatures = {};
            public int getSize() { return signatures.length; }
            public Signature getElementAt(int i) { return signatures[i]; }
        });
        jScrollPane1.setViewportView(signatureList);

        org.openide.awt.Mnemonics.setLocalizedText(addSigButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.addSigButton.text")); // NOI18N
        addSigButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addSigButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(editSigButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.editSigButton.text")); // NOI18N
        editSigButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editSigButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deleteSigButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.deleteSigButton.text")); // NOI18N
        deleteSigButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSigButtonActionPerformed(evt);
            }
        });

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
                                            .addComponent(addSigButton)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(editSigButton)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(deleteSigButton))
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
                                .addComponent(typesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 183, Short.MAX_VALUE)
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
                            .addComponent(addSigButton)
                            .addComponent(editSigButton)
                            .addComponent(deleteSigButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(postHitCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(filesSetNameLabel)
                            .addComponent(filesSetNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(saveTypeButton))))
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
    @Messages({
        "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidSigList.message=Must have at least one signature.",
        "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidSigList.title=Invalid Signature List"})
    private void saveTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveTypeButtonActionPerformed
        String typeName = mimeTypeTextField.getText();
        if (typeName.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidMIMEType.message"),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidMIMEType.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<Signature> sigList = new ArrayList<>();
        for (int i = 0; i < this.signaturesListModel.getSize(); i++) {
            sigList.add(this.signaturesListModel.elementAt(i));
        }
        if (sigList.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    Bundle.AddFileTypeSignatureDialog_invalidSignature_message(),
                    Bundle.FileTypeIdGlobalSettingsPanel_JOptionPane_invalidSigList_title(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        /**
         * Get the interesting files set details.
         */
        String filesSetName = "";
        if (postHitCheckBox.isSelected()) {
            filesSetName = filesSetNameTextField.getText();
        }
        if (postHitCheckBox.isSelected() && filesSetName.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidInterestingFilesSetName.message"),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidInterestingFilesSetName.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        FileType fileType = new FileType(typeName, sigList, filesSetName, postHitCheckBox.isSelected());
        FileType selected = typesList.getSelectedValue();
        if (selected != null) {
            fileTypes.remove(selected);
        }
        fileTypes.add(fileType);
        updateFileTypesListModel();
        typesList.setSelectedValue(fileType, true);
    }//GEN-LAST:event_saveTypeButtonActionPerformed

    private void postHitCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_postHitCheckBoxActionPerformed
        filesSetNameTextField.setEnabled(postHitCheckBox.isSelected());
        enableButtons();
    }//GEN-LAST:event_postHitCheckBoxActionPerformed

    private void addSigButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addSigButtonActionPerformed
        if (evt.getSource().equals(this.addSigButton)) {
            this.addSigDialog = new AddFileTypeSignatureDialog();
            if (addSigDialog.getResult() == BUTTON_PRESSED.ADD) {
                signaturesListModel.addElement(this.addSigDialog.getSignature());
            }
        }
    }//GEN-LAST:event_addSigButtonActionPerformed

    private void deleteSigButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSigButtonActionPerformed
        if (this.signatureList.getSelectedIndex() != -1) {
            signaturesListModel.removeElementAt(this.signatureList.getSelectedIndex());
            if (!this.signaturesListModel.isEmpty()) {
                signatureList.setSelectedIndex(0);
            }
        }
    }//GEN-LAST:event_deleteSigButtonActionPerformed

    private void editSigButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editSigButtonActionPerformed
        if (evt.getSource().equals(this.editSigButton) && this.signatureList.getSelectedValue() != null) {
            this.addSigDialog = new AddFileTypeSignatureDialog(this.signatureList.getSelectedValue());
            if (addSigDialog.getResult() == BUTTON_PRESSED.ADD) {
                signaturesListModel.removeElementAt(this.signatureList.getSelectedIndex());
                this.signaturesListModel.addElement(this.addSigDialog.getSignature());
            }
        }
    }//GEN-LAST:event_editSigButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addSigButton;
    private javax.swing.JButton deleteSigButton;
    private javax.swing.JButton deleteTypeButton;
    private javax.swing.JButton editSigButton;
    private javax.swing.JLabel filesSetNameLabel;
    private javax.swing.JTextField filesSetNameTextField;
    private javax.swing.JLabel ingestRunningWarningLabel;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel mimeTypeLabel;
    private javax.swing.JTextField mimeTypeTextField;
    private javax.swing.JButton newTypeButton;
    private javax.swing.JCheckBox postHitCheckBox;
    private javax.swing.JButton saveTypeButton;
    private javax.swing.JSeparator separator;
    private javax.swing.JList<Signature> signatureList;
    private javax.swing.JList<FileType> typesList;
    private javax.swing.JScrollPane typesScrollPane;
    // End of variables declaration//GEN-END:variables

}
