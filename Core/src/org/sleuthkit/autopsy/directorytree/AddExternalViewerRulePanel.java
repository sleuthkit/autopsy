/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2018 Basis Technology Corp.
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
class AddExternalViewerRulePanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(AddExternalViewerRulePanel.class.getName());
    private final JFileChooser fc = new JFileChooser();
    private static final GeneralFilter exeFilter = new GeneralFilter(GeneralFilter.EXECUTABLE_EXTS, GeneralFilter.EXECUTABLE_DESC);

    enum EVENT {
        CHANGED
    }

    /**
     * Creates new form AddRulePanel
     */
    AddExternalViewerRulePanel() {
        initComponents();
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        fc.setFileFilter(exeFilter);
        customize();
    }

    /**
     * Creates new form AddRulePanel if the user is editing a rule. Loads
     * information of the rule being edited.
     *
     * @param rule to be edited
     */
    AddExternalViewerRulePanel(ExternalViewerRule rule) {
        this();
        nameTextField.setText(rule.getName());
        exePathTextField.setText(rule.getExePath());
        if (rule.getRuleType() == ExternalViewerRule.RuleType.EXT) {
            extRadioButton.setSelected(true);
        }
        customize();
    }

    /**
     * Allows listeners for when the name or exePath text fields are modified.
     * Set action commands for the radio buttons.
     */
    private void customize() {
        mimeRadioButton.setActionCommand("mime");
        extRadioButton.setActionCommand("ext");
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
     * Check if the text fields are filled and if a radio button is selected.
     *
     * @return true if neither of the text fields are empty and a radio button
     *         is selected
     */
    boolean hasFields() {
        return !exePathTextField.getText().isEmpty() && !nameTextField.getText().isEmpty() &&
                (mimeRadioButton.isSelected() || extRadioButton.isSelected());
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
        String exePath = exePathTextField.getText();
        if (exePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(ExternalViewerGlobalSettingsPanel.class, "ExternalViewerGlobalSettingsPanel.JOptionPane.invalidExePath.message"),
                    NbBundle.getMessage(ExternalViewerGlobalSettingsPanel.class, "ExternalViewerGlobalSettingsPanel.JOptionPane.invalidExePath.title"),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }

        String name = nameTextField.getText();
        if (mimeRadioButton.isSelected()) {
            FileTypeDetector detector;
            try {
                detector = new FileTypeDetector();
            } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                logger.log(Level.WARNING, "Couldn't create file type detector for file ext mismatch settings.", ex);
                return null;
            }
            if (name.isEmpty() || !detector.isDetectable(name)) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(ExternalViewerGlobalSettingsPanel.class, "ExternalViewerGlobalSettingsPanel.JOptionPane.invalidMime.message"),
                        NbBundle.getMessage(ExternalViewerGlobalSettingsPanel.class, "ExternalViewerGlobalSettingsPanel.JOptionPane.invalidMime.title"),
                        JOptionPane.ERROR_MESSAGE);
                return null;
            }
            return new ExternalViewerRule(name, exePath, ExternalViewerRule.RuleType.MIME);
        } else if (extRadioButton.isSelected()) {
            if (name.isEmpty() || !name.matches("^\\.?\\w+$")) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(ExternalViewerGlobalSettingsPanel.class, "ExternalViewerGlobalSettingsPanel.JOptionPane.invalidExt.message"),
                        NbBundle.getMessage(ExternalViewerGlobalSettingsPanel.class, "ExternalViewerGlobalSettingsPanel.JOptionPane.invalidExt.title"),
                        JOptionPane.ERROR_MESSAGE);
                return null;
            }
            if (name.charAt(0) != '.') {
                name = "." + name;
            }
            return new ExternalViewerRule(name.toLowerCase(), exePath, ExternalViewerRule.RuleType.EXT);
        }
        return null;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup = new javax.swing.ButtonGroup();
        nameLabel = new javax.swing.JLabel();
        nameTextField = new javax.swing.JTextField();
        mimeRadioButton = new javax.swing.JRadioButton();
        extRadioButton = new javax.swing.JRadioButton();
        exePathLabel = new javax.swing.JLabel();
        exePathTextField = new javax.swing.JTextField();
        browseButton = new javax.swing.JButton();

        org.openide.awt.Mnemonics.setLocalizedText(nameLabel, org.openide.util.NbBundle.getMessage(AddExternalViewerRulePanel.class, "AddExternalViewerRulePanel.nameLabel.text")); // NOI18N

        nameTextField.setText(org.openide.util.NbBundle.getMessage(AddExternalViewerRulePanel.class, "AddExternalViewerRulePanel.nameTextField.text")); // NOI18N

        buttonGroup.add(mimeRadioButton);
        mimeRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(mimeRadioButton, org.openide.util.NbBundle.getMessage(AddExternalViewerRulePanel.class, "AddExternalViewerRulePanel.mimeRadioButton.text")); // NOI18N

        buttonGroup.add(extRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(extRadioButton, org.openide.util.NbBundle.getMessage(AddExternalViewerRulePanel.class, "AddExternalViewerRulePanel.extRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(exePathLabel, org.openide.util.NbBundle.getMessage(AddExternalViewerRulePanel.class, "AddExternalViewerRulePanel.exePathLabel.text")); // NOI18N

        exePathTextField.setEditable(false);
        exePathTextField.setText(org.openide.util.NbBundle.getMessage(AddExternalViewerRulePanel.class, "AddExternalViewerRulePanel.exePathTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(browseButton, org.openide.util.NbBundle.getMessage(AddExternalViewerRulePanel.class, "AddExternalViewerRulePanel.browseButton.text")); // NOI18N
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
                        .addComponent(exePathTextField)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseButton))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(exePathLabel)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(nameLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(mimeRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(extRadioButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nameLabel)
                    .addComponent(mimeRadioButton)
                    .addComponent(extRadioButton))
                .addGap(2, 2, 2)
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
    private javax.swing.ButtonGroup buttonGroup;
    private javax.swing.JLabel exePathLabel;
    private javax.swing.JTextField exePathTextField;
    private javax.swing.JRadioButton extRadioButton;
    private javax.swing.JRadioButton mimeRadioButton;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JTextField nameTextField;
    // End of variables declaration//GEN-END:variables
}
