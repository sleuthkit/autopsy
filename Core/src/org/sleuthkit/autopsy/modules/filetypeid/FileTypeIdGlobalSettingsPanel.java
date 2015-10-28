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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.xml.bind.DatatypeConverter;
import org.openide.util.Exceptions;
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

    /**
     * The list model for the file types list component of this panel is the set
     * of MIME types associated with the user-defined file types. A mapping of
     * the MIME types to file type objects lies behind the list model. This map
     * is obtained from the user-defined types manager.
     */
    private DefaultListModel<FileType> typesListModel;
    private java.util.List<FileType> fileTypes;

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
    private void customizeComponents() {
        setFileTypesListModel();
        setSignatureTypeComboBoxModel();
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
        signatureTypeComboBox.setModel(sigTypeComboBoxModel);
        signatureTypeComboBox.setSelectedItem(FileTypeIdGlobalSettingsPanel.RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM);
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
        offsetTextField.getDocument().addDocumentListener(listener);
        signatureTextField.getDocument().addDocumentListener(listener);
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
                && !offsetTextField.getText().isEmpty()
                && !signatureTextField.getText().isEmpty()
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
            FileType.Signature.Type sigType = signature.getType();
            signatureTypeComboBox.setSelectedItem(sigType == FileType.Signature.Type.RAW ? FileTypeIdGlobalSettingsPanel.RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM : FileTypeIdGlobalSettingsPanel.ASCII_SIGNATURE_TYPE_COMBO_BOX_ITEM);
            String signatureBytes;
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
            signatureTextField.setText(signatureBytes);
            isTrailingCheckBox.setSelected(signature.isTrailing());
            offsetTextField.setText(Long.toString(signature.getOffset()));
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
        signatureTypeComboBox.setSelectedItem(FileTypeIdGlobalSettingsPanel.RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM);
        hexPrefixLabel.setVisible(true);
        signatureTextField.setText("0000"); //NON-NLS
        isTrailingCheckBox.setSelected(false);
        offsetTextField.setText(""); //NON-NLS
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
        signatureTypeLabel = new javax.swing.JLabel();
        signatureTextField = new javax.swing.JTextField();
        offsetLabel = new javax.swing.JLabel();
        offsetTextField = new javax.swing.JTextField();
        newTypeButton = new javax.swing.JButton();
        deleteTypeButton = new javax.swing.JButton();
        saveTypeButton = new javax.swing.JButton();
        hexPrefixLabel = new javax.swing.JLabel();
        signatureTypeComboBox = new javax.swing.JComboBox<String>();
        signatureLabel = new javax.swing.JLabel();
        postHitCheckBox = new javax.swing.JCheckBox();
        filesSetNameLabel = new javax.swing.JLabel();
        filesSetNameTextField = new javax.swing.JTextField();
        ingestRunningWarningLabel = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        isTrailingCheckBox = new javax.swing.JCheckBox();

        setMaximumSize(new java.awt.Dimension(500, 300));
        setPreferredSize(new java.awt.Dimension(500, 300));

        typesList.setMaximumSize(new java.awt.Dimension(150, 0));
        typesList.setMinimumSize(new java.awt.Dimension(150, 0));
        typesScrollPane.setViewportView(typesList);

        separator.setOrientation(javax.swing.SwingConstants.VERTICAL);

        org.openide.awt.Mnemonics.setLocalizedText(mimeTypeLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.mimeTypeLabel.text")); // NOI18N

        mimeTypeTextField.setText(org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.mimeTypeTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(signatureTypeLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.signatureTypeLabel.text")); // NOI18N

        signatureTextField.setText(org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.signatureTextField.text")); // NOI18N
        signatureTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                signatureTextFieldActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(offsetLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.offsetLabel.text")); // NOI18N

        offsetTextField.setText(org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.offsetTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(newTypeButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.newTypeButton.text")); // NOI18N
        newTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newTypeButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deleteTypeButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.deleteTypeButton.text")); // NOI18N
        deleteTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteTypeButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(saveTypeButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.saveTypeButton.text")); // NOI18N
        saveTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveTypeButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(hexPrefixLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.hexPrefixLabel.text")); // NOI18N

        signatureTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                signatureTypeComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(signatureLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.signatureLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(postHitCheckBox, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.postHitCheckBox.text")); // NOI18N
        postHitCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                postHitCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(filesSetNameLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.filesSetNameLabel.text")); // NOI18N

        filesSetNameTextField.setText(org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.filesSetNameTextField.text")); // NOI18N

        ingestRunningWarningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/filetypeid/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(ingestRunningWarningLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.ingestRunningWarningLabel.text")); // NOI18N

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.jLabel1.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.jLabel3.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(isTrailingCheckBox, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.isTrailingCheckBox.text")); // NOI18N
        isTrailingCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isTrailingCheckBoxActionPerformed(evt);
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
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(layout.createSequentialGroup()
                                            .addComponent(mimeTypeLabel)
                                            .addGap(30, 30, 30)
                                            .addComponent(mimeTypeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 176, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addComponent(postHitCheckBox)
                                        .addGroup(layout.createSequentialGroup()
                                            .addComponent(signatureTypeLabel)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(signatureTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 176, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addGroup(layout.createSequentialGroup()
                                            .addComponent(signatureLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(hexPrefixLabel)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                            .addComponent(signatureTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addGroup(layout.createSequentialGroup()
                                            .addComponent(offsetLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(offsetTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 178, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                            .addGap(21, 21, 21)
                                            .addComponent(filesSetNameLabel)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(filesSetNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 182, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                        .addComponent(saveTypeButton)
                                        .addGap(8, 8, 8))
                                    .addComponent(isTrailingCheckBox)))
                            .addComponent(jLabel1)
                            .addComponent(jLabel3))
                        .addContainerGap(50, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(typesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 173, Short.MAX_VALUE)
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
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(signatureTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(signatureTypeLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(hexPrefixLabel)
                                .addComponent(signatureTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(signatureLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(isTrailingCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(offsetTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(offsetLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(postHitCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(filesSetNameLabel)
                            .addComponent(filesSetNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(saveTypeButton)
                        .addGap(0, 0, Short.MAX_VALUE))))
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
        /**
         * Get the MIME type.
         */
        String typeName = mimeTypeTextField.getText();
        if (typeName.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidMIMEType.message"),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidMIMEType.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        /**
         * Get the signature type.
         */
        FileType.Signature.Type sigType = signatureTypeComboBox.getSelectedItem() == FileTypeIdGlobalSettingsPanel.RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM ? FileType.Signature.Type.RAW : FileType.Signature.Type.ASCII;

        /**
         * Get the signature bytes.
         */
        String sigString = signatureTextField.getText();
        if (sigString.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidSignature.message"),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidSignature.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        byte[] signatureBytes;
        if (FileType.Signature.Type.RAW == sigType) {
            try {
                signatureBytes = DatatypeConverter.parseHexBinary(sigString);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidRawSignatureBytes.message"),
                        NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidSignatureBytes.title"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            signatureBytes = sigString.getBytes(Charset.forName("UTF-8"));
        }

        /**
         * Get the offset.
         */
        long offset;
        try {
            offset = Long.parseUnsignedLong(offsetTextField.getText());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidOffset.message"),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidOffset.title"),
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

        /**
         * Put it all together and reset the file types list component.
         */
        FileType.Signature signature = new FileType.Signature(signatureBytes, offset, sigType, isTrailingCheckBox.isSelected());
        FileType fileType = new FileType(typeName, signature, filesSetName, postHitCheckBox.isSelected());
        FileType selected = typesList.getSelectedValue();
        if (selected != null) {
            fileTypes.remove(selected);
        }
        fileTypes.add(fileType);
        updateFileTypesListModel();
        typesList.setSelectedValue(fileType.getMimeType(), true);
    }//GEN-LAST:event_saveTypeButtonActionPerformed

    private void postHitCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_postHitCheckBoxActionPerformed
        filesSetNameTextField.setEnabled(postHitCheckBox.isSelected());
        enableButtons();
    }//GEN-LAST:event_postHitCheckBoxActionPerformed

    private void signatureTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_signatureTypeComboBoxActionPerformed
        if (signatureTypeComboBox.getSelectedItem() == FileTypeIdGlobalSettingsPanel.RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM) {
            hexPrefixLabel.setVisible(true);
            signatureTextField.setText("0000");
        } else {
            hexPrefixLabel.setVisible(false);
            signatureTextField.setText("");
        }
    }//GEN-LAST:event_signatureTypeComboBoxActionPerformed

    private void signatureTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_signatureTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_signatureTextFieldActionPerformed

    private void isTrailingCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isTrailingCheckBoxActionPerformed

    }//GEN-LAST:event_isTrailingCheckBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteTypeButton;
    private javax.swing.JLabel filesSetNameLabel;
    private javax.swing.JTextField filesSetNameTextField;
    private javax.swing.JLabel hexPrefixLabel;
    private javax.swing.JLabel ingestRunningWarningLabel;
    private javax.swing.JCheckBox isTrailingCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel mimeTypeLabel;
    private javax.swing.JTextField mimeTypeTextField;
    private javax.swing.JButton newTypeButton;
    private javax.swing.JLabel offsetLabel;
    private javax.swing.JTextField offsetTextField;
    private javax.swing.JCheckBox postHitCheckBox;
    private javax.swing.JButton saveTypeButton;
    private javax.swing.JSeparator separator;
    private javax.swing.JLabel signatureLabel;
    private javax.swing.JTextField signatureTextField;
    private javax.swing.JComboBox<String> signatureTypeComboBox;
    private javax.swing.JLabel signatureTypeLabel;
    private javax.swing.JList<FileType> typesList;
    private javax.swing.JScrollPane typesScrollPane;
    // End of variables declaration//GEN-END:variables

}
