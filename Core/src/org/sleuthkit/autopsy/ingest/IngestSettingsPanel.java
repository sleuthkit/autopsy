/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.text.NumberFormat;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFormattedTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Options panel that allow users to set application preferences.
 */
final class IngestSettingsPanel extends IngestModuleGlobalSettingsPanel {

    IngestSettingsPanel() {
        initComponents();
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        Integer fileIngestThreadCountChoices[];
        int recommendedFileIngestThreadCount;
        if (availableProcessors >= 8) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4, 6, 8};
            recommendedFileIngestThreadCount = 4;
        } else if (availableProcessors >= 6) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4, 6};
            recommendedFileIngestThreadCount = 4;
        } else if (availableProcessors >= 4) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4};
            recommendedFileIngestThreadCount = 2;
        } else if (availableProcessors >= 2) {
            fileIngestThreadCountChoices = new Integer[]{1, 2};
            recommendedFileIngestThreadCount = 1;
        } else {
            fileIngestThreadCountChoices = new Integer[]{1};
            recommendedFileIngestThreadCount = 1;
        }
        numberOfFileIngestThreadsComboBox.setModel(new DefaultComboBoxModel<>(fileIngestThreadCountChoices));
        restartRequiredLabel.setText(NbBundle.getMessage(IngestSettingsPanel.class, "IngestSettingsPanel.restartRequiredLabel.text", recommendedFileIngestThreadCount));
        // TODO listen to changes in form fields and call controller.changed()
        DocumentListener docListener = new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            }
        };
        this.jFormattedTextFieldProcTimeOutHrs.getDocument().addDocumentListener(docListener);
        this.ingestWarningLabel.setVisible(false);
    }

    /**
     * Load the existing settings.
     */
    void load() {
        numberOfFileIngestThreadsComboBox.setSelectedItem(UserPreferences.numberOfFileIngestThreads());
        if (UserPreferences.getIsTimeOutEnabled()) {
            // user specified time out
            jCheckBoxEnableProcTimeout.setSelected(true);
            jFormattedTextFieldProcTimeOutHrs.setEditable(true);
            int timeOutHrs = UserPreferences.getProcessTimeOutHrs();
            jFormattedTextFieldProcTimeOutHrs.setValue((long) timeOutHrs);
        } else {
            // never time out
            jCheckBoxEnableProcTimeout.setSelected(false);
            jFormattedTextFieldProcTimeOutHrs.setEditable(false);
            int timeOutHrs = UserPreferences.getProcessTimeOutHrs();
            jFormattedTextFieldProcTimeOutHrs.setValue((long) timeOutHrs);
        }
    }

    /**
     * Store the existing settings.
     */
    void store() {
        UserPreferences.setNumberOfFileIngestThreads((Integer) numberOfFileIngestThreadsComboBox.getSelectedItem());

        UserPreferences.setIsTimeOutEnabled(jCheckBoxEnableProcTimeout.isSelected());
        if (jCheckBoxEnableProcTimeout.isSelected()) {
            // only store time out if it is enabled
            long timeOutHrs = (long) jFormattedTextFieldProcTimeOutHrs.getValue();
            UserPreferences.setProcessTimeOutHrs((int) timeOutHrs);
        }
    }

    boolean valid() {
        return true;
    }

    /**
     * Enable or Disable buttons based on whether Ingest is running.
     *
     * @param isEnabled
     */
    void enableButtons(boolean isEnabled) {
        numberOfFileIngestThreadsComboBox.setEnabled(isEnabled);
        jFormattedTextFieldProcTimeOutHrs.setEnabled(isEnabled);
        jCheckBoxEnableProcTimeout.setEnabled(isEnabled);
        ingestWarningLabel.setVisible(!isEnabled);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        jLabelNumThreads = new javax.swing.JLabel();
        numberOfFileIngestThreadsComboBox = new javax.swing.JComboBox<>();
        restartRequiredLabel = new javax.swing.JLabel();
        jLabelSetProcessTimeOut = new javax.swing.JLabel();
        jCheckBoxEnableProcTimeout = new javax.swing.JCheckBox();
        jFormattedTextFieldProcTimeOutHrs = new JFormattedTextField(NumberFormat.getIntegerInstance());
        jLabelProcessTimeOutUnits = new javax.swing.JLabel();
        ingestWarningLabel = new javax.swing.JLabel();

        setPreferredSize(new java.awt.Dimension(693, 413));

        jScrollPane1.setBorder(null);

        jPanel1.setPreferredSize(new java.awt.Dimension(664, 400));

        org.openide.awt.Mnemonics.setLocalizedText(jLabelNumThreads, org.openide.util.NbBundle.getMessage(IngestSettingsPanel.class, "IngestSettingsPanel.jLabelNumThreads.text")); // NOI18N

        numberOfFileIngestThreadsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                numberOfFileIngestThreadsComboBoxActionPerformed(evt);
            }
        });

        restartRequiredLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(restartRequiredLabel, org.openide.util.NbBundle.getMessage(IngestSettingsPanel.class, "IngestSettingsPanel.restartRequiredLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSetProcessTimeOut, org.openide.util.NbBundle.getMessage(IngestSettingsPanel.class, "IngestSettingsPanel.jLabelSetProcessTimeOut.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jCheckBoxEnableProcTimeout, org.openide.util.NbBundle.getMessage(IngestSettingsPanel.class, "IngestSettingsPanel.jCheckBoxEnableProcTimeout.text")); // NOI18N
        jCheckBoxEnableProcTimeout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxEnableProcTimeoutActionPerformed(evt);
            }
        });

        jFormattedTextFieldProcTimeOutHrs.setText(org.openide.util.NbBundle.getMessage(IngestSettingsPanel.class, "IngestSettingsPanel.jFormattedTextFieldProcTimeOutHrs.text")); // NOI18N
        jFormattedTextFieldProcTimeOutHrs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jFormattedTextFieldProcTimeOutHrsActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabelProcessTimeOutUnits, org.openide.util.NbBundle.getMessage(IngestSettingsPanel.class, "IngestSettingsPanel.jLabelProcessTimeOutUnits.text")); // NOI18N

        ingestWarningLabel.setFont(ingestWarningLabel.getFont().deriveFont(ingestWarningLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        ingestWarningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/hashdatabase/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(ingestWarningLabel, org.openide.util.NbBundle.getMessage(IngestSettingsPanel.class, "IngestSettingsPanel.ingestWarningLabel.text")); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addComponent(numberOfFileIngestThreadsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(restartRequiredLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabelNumThreads)
                                    .addComponent(jLabelSetProcessTimeOut)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addGap(10, 10, 10)
                                        .addComponent(jCheckBoxEnableProcTimeout)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jFormattedTextFieldProcTimeOutHrs, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jLabelProcessTimeOutUnits)))
                                .addGap(213, 213, 213)))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(ingestWarningLabel)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addComponent(jLabelNumThreads)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(numberOfFileIngestThreadsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(restartRequiredLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabelSetProcessTimeOut)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jCheckBoxEnableProcTimeout)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jFormattedTextFieldProcTimeOutHrs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabelProcessTimeOutUnits)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ingestWarningLabel)
                .addContainerGap(257, Short.MAX_VALUE))
        );

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 691, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jFormattedTextFieldProcTimeOutHrsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jFormattedTextFieldProcTimeOutHrsActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_jFormattedTextFieldProcTimeOutHrsActionPerformed

    private void jCheckBoxEnableProcTimeoutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxEnableProcTimeoutActionPerformed
        jFormattedTextFieldProcTimeOutHrs.setEditable(jCheckBoxEnableProcTimeout.isSelected());
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_jCheckBoxEnableProcTimeoutActionPerformed

    private void numberOfFileIngestThreadsComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_numberOfFileIngestThreadsComboBoxActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_numberOfFileIngestThreadsComboBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel ingestWarningLabel;
    private javax.swing.JCheckBox jCheckBoxEnableProcTimeout;
    private javax.swing.JFormattedTextField jFormattedTextFieldProcTimeOutHrs;
    private javax.swing.JLabel jLabelNumThreads;
    private javax.swing.JLabel jLabelProcessTimeOutUnits;
    private javax.swing.JLabel jLabelSetProcessTimeOut;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JComboBox<Integer> numberOfFileIngestThreadsComboBox;
    private javax.swing.JLabel restartRequiredLabel;
    // End of variables declaration//GEN-END:variables

    @Override
    public void saveSettings() {
        this.store();
    }
}
