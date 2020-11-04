/*
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filerepository;

import java.beans.PropertyChangeEvent;
import java.util.EnumSet;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;

/**
 *
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class FileRepositoryOptionsPanel  extends IngestModuleGlobalSettingsPanel implements OptionsPanel {

    private final TextFieldListener textFieldListener;
    private boolean caseIsOpen;
    
    /**
     * Creates new form FileRepositoryOptionsPanel
     */
    public FileRepositoryOptionsPanel() {
        initComponents();
        
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            // Disable when case is open, enable when case is closed
            enablePanel(evt.getNewValue() == null);
        });
        
        errorLabel.setText("");
        textFieldListener = new TextFieldListener();
        addressTextField.getDocument().addDocumentListener(textFieldListener);
        portTextField.getDocument().addDocumentListener(textFieldListener);
    }
    
    private void enablePanel(boolean enable) {
        enableCheckBox.setEnabled(enable);
        updatePanel();
    }
    
    private void updatePanel() {
        boolean panelEnabled = enableCheckBox.isEnabled();
        boolean fileRepositoryEnabled = enableCheckBox.isSelected();
        addressLabel.setEnabled(panelEnabled && fileRepositoryEnabled);
        addressTextField.setEnabled(panelEnabled && fileRepositoryEnabled);
        portLabel.setEnabled(panelEnabled && fileRepositoryEnabled);
        portTextField.setEnabled(panelEnabled && fileRepositoryEnabled);
        
        validatePanel();
    }
    
    @Override
    public void store() {
        UserPreferences.setFileRepositoryEnabled(enableCheckBox.isSelected());
        if (enableCheckBox.isSelected()) {
            UserPreferences.setFileRepositoryAddress(addressTextField.getText());
            UserPreferences.setFileRepositoryPort(portTextField.getText());
        }
    }
    
    @Override
    public void load() {
        enableCheckBox.setSelected(UserPreferences.getFileRepositoryEnabled());
        addressTextField.setText(UserPreferences.getFileRepositoryAddress());
        portTextField.setText(UserPreferences.getFileRepositoryPort());
        updatePanel();
    }

    /**
     * This method validates that the dialog/panel is filled out correctly for
     * our usage.
     *
     * @return True if it is okay, false otherwise.
     */
    @NbBundle.Messages({
        "FileRepositoryOptionsPanel.error.emptyFields=Empty hostname/IP address or port",
        "FileRepositoryOptionsPanel.error.invalidPort=Invalid port number",
    })
    boolean validatePanel() {
        errorLabel.setText("");
        if (! enableCheckBox.isSelected()) {
            return true;
        }
        
        if (addressTextField.getText().isEmpty() || portTextField.getText().isEmpty()) {
            errorLabel.setText(Bundle.FileRepositoryOptionsPanel_error_emptyFields());
            return false;
        }
        
        try {
            int port = Integer.parseInt(portTextField.getText());
            if (port < 0 || port > 65535) { // invalid port numbers
                errorLabel.setText(Bundle.FileRepositoryOptionsPanel_error_invalidPort());
                return false;
            }
        } catch (NumberFormatException detailsNotImportant) {
            errorLabel.setText(Bundle.FileRepositoryOptionsPanel_error_invalidPort());
            return false;
        }
        return true;
    }

    @Override
    public void saveSettings() {
        store();
    }   
    
    /**
     * Listens for registered text fields that have changed and fires a
     * PropertyChangeEvent accordingly.
     */
    private class TextFieldListener implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent e) {
            validatePanel();
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            validatePanel();
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            validatePanel();
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }    
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        addressLabel = new javax.swing.JLabel();
        addressTextField = new javax.swing.JTextField();
        portLabel = new javax.swing.JLabel();
        portTextField = new javax.swing.JTextField();
        headingLabel = new javax.swing.JLabel();
        enableCheckBox = new javax.swing.JCheckBox();
        errorLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(addressLabel, org.openide.util.NbBundle.getMessage(FileRepositoryOptionsPanel.class, "FileRepositoryOptionsPanel.addressLabel.text")); // NOI18N

        addressTextField.setText(org.openide.util.NbBundle.getMessage(FileRepositoryOptionsPanel.class, "FileRepositoryOptionsPanel.addressTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(portLabel, org.openide.util.NbBundle.getMessage(FileRepositoryOptionsPanel.class, "FileRepositoryOptionsPanel.portLabel.text")); // NOI18N

        portTextField.setText(org.openide.util.NbBundle.getMessage(FileRepositoryOptionsPanel.class, "FileRepositoryOptionsPanel.portTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(headingLabel, org.openide.util.NbBundle.getMessage(FileRepositoryOptionsPanel.class, "FileRepositoryOptionsPanel.headingLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(enableCheckBox, org.openide.util.NbBundle.getMessage(FileRepositoryOptionsPanel.class, "FileRepositoryOptionsPanel.enableCheckBox.text")); // NOI18N
        enableCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableCheckBoxActionPerformed(evt);
            }
        });

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(FileRepositoryOptionsPanel.class, "FileRepositoryOptionsPanel.errorLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(headingLabel)
                    .addComponent(enableCheckBox)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(13, 13, 13)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(addressLabel)
                            .addComponent(portLabel)
                            .addComponent(portTextField)
                            .addComponent(addressTextField)
                            .addComponent(errorLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 457, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(86, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(headingLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(enableCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(addressLabel)
                .addGap(1, 1, 1)
                .addComponent(addressTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(portLabel)
                .addGap(1, 1, 1)
                .addComponent(portTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(errorLabel)
                .addContainerGap(215, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void enableCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableCheckBoxActionPerformed
        updatePanel();
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_enableCheckBoxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel addressLabel;
    private javax.swing.JTextField addressTextField;
    private javax.swing.JCheckBox enableCheckBox;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JLabel headingLabel;
    private javax.swing.JLabel portLabel;
    private javax.swing.JTextField portTextField;
    // End of variables declaration//GEN-END:variables
}
