/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.GeneralFilter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;

/**
 * Panel found in an AddRuleDialog
 */
class AddRulePanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(AddRulePanel.class.getName());
    private final JFileChooser fc = new JFileChooser();
    private static final GeneralFilter exeFilter = new GeneralFilter(GeneralFilter.EXECUTABLE_EXTS, GeneralFilter.EXECUTABLE_DESC);

    enum EVENT {
        CHANGED
    }

    /**
     * Creates new form AddRulePanel
     */
    AddRulePanel() {
        initComponents();
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        fc.setFileFilter(exeFilter);
        addListener();
    }

    /**
     * Creates new form AddRulePanel if the user is editing a rule. Loads
     * information of the rule being edited.
     *
     * @param rule to be edited
     */
    AddRulePanel(ExternalViewerRule rule) {
        this();
        nameTextField.setText(rule.getName());
        exePathTextField.setText(rule.getExePath());
        addListener();
    }

    /**
     * Allows listeners for when the name or exePath text fields are modified.
     */
    private void addListener() {
        nameTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                fire();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                fire();
            }
            @Override
            public void insertUpdate(DocumentEvent e) {
                fire();
            }
            private void fire() {
                firePropertyChange(EVENT.CHANGED.toString(), null, null);
            }
        });
        exePathTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                fire();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                fire();
            }
            @Override
            public void insertUpdate(DocumentEvent e) {
                fire();
            }
            private void fire() {
                firePropertyChange(EVENT.CHANGED.toString(), null, null);
            }
        });
    }

    /**
     * Check if the text fields are empty or not.
     *
     * @return true if neither of the text fields are empty
     */
    boolean hasFields() {
        return !exePathTextField.getText().isEmpty() && !nameTextField.getText().isEmpty();
    }

    /**
     * Returns the ExternalViewerRule created from input text. Returns null if
     * the name is not a valid MIME type (as defined by both autopsy and the
     * user, checked through FileTypeDetector) or in the form of a valid
     * extension.
     *
     * @return ExternalViewerRule or null
     */
    ExternalViewerRule getRule() {
        String name = nameTextField.getText();
        FileTypeDetector detector;
        try {
            detector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            logger.log(Level.WARNING, "Couldn't create file type detector for file ext mismatch settings.", ex);
            return null;
        }
        // Regex for MIME: ^[-\\w]+/[-\\w]+$
        if (name.isEmpty() || (!detector.isDetectable(name) && !name.matches("^\\.\\w+$"))) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(ExternalViewerGlobalSettingsPanel.class, "ExternalViewerGlobalSettingsPanel.JOptionPane.invalidExtOrMIME.message"),
                    NbBundle.getMessage(ExternalViewerGlobalSettingsPanel.class, "ExternalViewerGlobalSettingsPanel.JOptionPane.invalidExtOrMIME.title"),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        String exePath = exePathTextField.getText();
        if (exePath.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(ExternalViewerGlobalSettingsPanel.class, "ExternalViewerGlobalSettingsPanel.JOptionPane.invalidExePath.message"),
                    NbBundle.getMessage(ExternalViewerGlobalSettingsPanel.class, "ExternalViewerGlobalSettingsPanel.JOptionPane.invalidExePath.title"),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return new ExternalViewerRule(name, exePath);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        nameLabel = new javax.swing.JLabel();
        nameTextField = new javax.swing.JTextField();
        exePathLabel = new javax.swing.JLabel();
        exePathTextField = new javax.swing.JTextField();
        browseButton = new javax.swing.JButton();

        org.openide.awt.Mnemonics.setLocalizedText(nameLabel, org.openide.util.NbBundle.getMessage(AddRulePanel.class, "AddRulePanel.nameLabel.text")); // NOI18N

        nameTextField.setText(org.openide.util.NbBundle.getMessage(AddRulePanel.class, "AddRulePanel.nameTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(exePathLabel, org.openide.util.NbBundle.getMessage(AddRulePanel.class, "AddRulePanel.exePathLabel.text")); // NOI18N

        exePathTextField.setText(org.openide.util.NbBundle.getMessage(AddRulePanel.class, "AddRulePanel.exePathTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(browseButton, org.openide.util.NbBundle.getMessage(AddRulePanel.class, "AddRulePanel.browseButton.text")); // NOI18N
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(nameTextField)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(nameLabel)
                            .addComponent(exePathLabel))
                        .addGap(0, 80, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(exePathTextField)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(nameLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(exePathLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exePathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(browseButton))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        int returnState = fc.showOpenDialog(this);
        if (returnState == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            exePathTextField.setText(path);
        }
    }//GEN-LAST:event_browseButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JLabel exePathLabel;
    private javax.swing.JTextField exePathTextField;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JTextField nameTextField;
    // End of variables declaration//GEN-END:variables
}
