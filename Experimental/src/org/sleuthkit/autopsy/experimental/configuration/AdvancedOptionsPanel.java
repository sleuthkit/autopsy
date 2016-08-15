/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.configuration;

import java.util.ArrayList;
import java.util.Collection;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.UserPreferences;

class AdvancedOptionsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    AdvancedOptionsPanel(OptionsPanel.OptionsUiMode mode) {
        initComponents();
        tbWarning.setLineWrap(true);
        tbWarning.setWrapStyleWord(true);
        load(mode);
    }

    private void initThreadCount() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        Integer fileIngestThreadCountChoices[];
        if (availableProcessors >= 16) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4, 6, 8, 12, 16};
        } else if (availableProcessors >= 12 && availableProcessors <= 15) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4, 6, 8, 12};
        } else if (availableProcessors >= 8 && availableProcessors <= 11) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4, 6, 8};
        } else if (availableProcessors >= 6 && availableProcessors <= 7) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4, 6};
        } else if (availableProcessors >= 4 && availableProcessors <= 5) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4};
        } else if (availableProcessors >= 2 && availableProcessors <= 3) {
            fileIngestThreadCountChoices = new Integer[]{1, 2};
        } else {
            fileIngestThreadCountChoices = new Integer[]{1};
        }
        numberOfFileIngestThreadsComboBox.setModel(new DefaultComboBoxModel<>(fileIngestThreadCountChoices));
        numberOfFileIngestThreadsComboBox.setSelectedItem(UserPreferences.numberOfFileIngestThreads());
    }

    private void load(OptionsPanel.OptionsUiMode mode) {
        initThreadCount();
        spSecondsBetweenJobs.setValue(AutoIngestUserPreferences.getSecondsToSleepBetweenCases());
        spMaximumRetryAttempts.setValue(AutoIngestUserPreferences.getMaxNumTimesToProcessImage());
        int maxJobsPerCase = AutoIngestUserPreferences.getMaxConcurrentJobsForOneCase();
        spConcurrentJobsPerCase.setValue(maxJobsPerCase);
        spSecondsBetweenJobs.setEnabled(mode == OptionsPanel.OptionsUiMode.UTILITY || mode == OptionsPanel.OptionsUiMode.AIM);
        spMaximumRetryAttempts.setEnabled(mode == OptionsPanel.OptionsUiMode.UTILITY || mode == OptionsPanel.OptionsUiMode.AIM);
        cbTimeoutEnabled.setEnabled(mode == OptionsPanel.OptionsUiMode.UTILITY || mode == OptionsPanel.OptionsUiMode.AIM);
        lbSecondsBetweenJobs.setEnabled(mode == OptionsPanel.OptionsUiMode.UTILITY || mode == OptionsPanel.OptionsUiMode.AIM);
        lbTimeoutText.setEnabled(mode == OptionsPanel.OptionsUiMode.UTILITY || mode == OptionsPanel.OptionsUiMode.AIM);
        lbRetriesAllowed.setEnabled(mode == OptionsPanel.OptionsUiMode.UTILITY || mode == OptionsPanel.OptionsUiMode.AIM);
        cbTimeoutEnabled.setSelected(UserPreferences.getIsTimeOutEnabled());
        int timeOutHrs = UserPreferences.getProcessTimeOutHrs();
        spTimeoutHours.setValue(timeOutHrs);
        setCheckboxEnabledState();

        Collection<JComponent> uiComponents = new ArrayList<>();
        uiComponents.add(cbTimeoutEnabled);
        uiComponents.add(spSecondsBetweenJobs);
        uiComponents.add(spMaximumRetryAttempts);
        uiComponents.add(spSecondsBetweenJobs);
        uiComponents.add(lbSecondsBetweenJobs);
        uiComponents.add(lbTimeoutText);
        uiComponents.add(lbRetriesAllowed);
        uiComponents.add(lbNumberOfThreads);
        uiComponents.add(numberOfFileIngestThreadsComboBox);
        uiComponents.add(lbRestartRequired);
        uiComponents.add(lbTimeoutHours);
        uiComponents.add(spTimeoutHours);
        uiComponents.add(spConcurrentJobsPerCase);

        String disabledText = " " + NbBundle.getMessage(OptionsPanel.class, "AdvancedOptionsPanel.ItemDisabled.text");
        for (JComponent item : uiComponents) {
            if (!item.isEnabled()) {
                item.setToolTipText(item.getToolTipText() + disabledText);
            }
        }
    }

    void store() {
        AutoIngestUserPreferences.setSecondsToSleepBetweenCases((int) spSecondsBetweenJobs.getValue());
        AutoIngestUserPreferences.setMaxNumTimesToProcessImage((int) spMaximumRetryAttempts.getValue());
        AutoIngestUserPreferences.setMaxConcurrentIngestNodesForOneCase((int) spConcurrentJobsPerCase.getValue());
        UserPreferences.setNumberOfFileIngestThreads((Integer) numberOfFileIngestThreadsComboBox.getSelectedItem());
        boolean isChecked = cbTimeoutEnabled.isSelected();
        UserPreferences.setIsTimeOutEnabled(isChecked);
        if (isChecked) {
            // only store time out if it is enabled
            int timeOutHrs = (int) spTimeoutHours.getValue();
            UserPreferences.setProcessTimeOutHrs(timeOutHrs);
        }
    }

    private void setCheckboxEnabledState() {
        // Enable the timeout edit box iff the checkbox is checked and enabled
        if (cbTimeoutEnabled.isEnabled() && cbTimeoutEnabled.isSelected()) {
            spTimeoutHours.setEnabled(true);
            lbTimeoutHours.setEnabled(true);
        } else {
            spTimeoutHours.setEnabled(false);
            lbTimeoutHours.setEnabled(false);
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

        lbSecondsBetweenJobs = new javax.swing.JLabel();
        spSecondsBetweenJobs = new javax.swing.JSpinner();
        lbNumberOfThreads = new javax.swing.JLabel();
        numberOfFileIngestThreadsComboBox = new javax.swing.JComboBox<>();
        lbRestartRequired = new javax.swing.JLabel();
        lbRetriesAllowed = new javax.swing.JLabel();
        spMainScrollPane = new javax.swing.JScrollPane();
        tbWarning = new javax.swing.JTextArea();
        lbTimeoutText = new javax.swing.JLabel();
        lbTimeoutHours = new javax.swing.JLabel();
        cbTimeoutEnabled = new javax.swing.JCheckBox();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(130, 0), new java.awt.Dimension(130, 0), new java.awt.Dimension(130, 32767));
        spMaximumRetryAttempts = new javax.swing.JSpinner();
        lbConcurrentJobsPerCase = new javax.swing.JLabel();
        spConcurrentJobsPerCase = new javax.swing.JSpinner();
        spTimeoutHours = new javax.swing.JSpinner();
        lbSecondsBetweenJobsSeconds = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(lbSecondsBetweenJobs, org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbSecondsBetweenJobs.text")); // NOI18N
        lbSecondsBetweenJobs.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbSecondsBetweenJobs.toolTipText")); // NOI18N

        spSecondsBetweenJobs.setModel(new javax.swing.SpinnerNumberModel(30, 30, 3600, 10));
        spSecondsBetweenJobs.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.spSecondsBetweenJobs.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbNumberOfThreads, org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbNumberOfThreads.text")); // NOI18N

        numberOfFileIngestThreadsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                numberOfFileIngestThreadsComboBoxActionPerformed(evt);
            }
        });

        lbRestartRequired.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbRestartRequired, org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbRestartRequired.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbRetriesAllowed, org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbRetriesAllowed.text")); // NOI18N
        lbRetriesAllowed.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbRetriesAllowed.toolTipText")); // NOI18N
        lbRetriesAllowed.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);

        tbWarning.setEditable(false);
        tbWarning.setColumns(20);
        tbWarning.setFont(new java.awt.Font("Monospaced", 1, 14)); // NOI18N
        tbWarning.setRows(5);
        tbWarning.setText(org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.tbWarning.text")); // NOI18N
        tbWarning.setAutoscrolls(false);
        spMainScrollPane.setViewportView(tbWarning);

        org.openide.awt.Mnemonics.setLocalizedText(lbTimeoutText, org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbTimeoutText.text")); // NOI18N
        lbTimeoutText.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbTimeoutText.toolTipText")); // NOI18N
        lbTimeoutText.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lbTimeoutTextMouseClicked(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTimeoutHours, org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbTimeoutHours.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(cbTimeoutEnabled, org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.cbTimeoutEnabled.text")); // NOI18N
        cbTimeoutEnabled.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.cbTimeoutEnabled.toolTipText")); // NOI18N
        cbTimeoutEnabled.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cbTimeoutEnabledItemStateChanged(evt);
            }
        });

        spMaximumRetryAttempts.setModel(new javax.swing.SpinnerNumberModel(2, 0, 9999999, 1));
        spMaximumRetryAttempts.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.spMaximumRetryAttempts.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbConcurrentJobsPerCase, org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbConcurrentJobsPerCase.text")); // NOI18N
        lbConcurrentJobsPerCase.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbConcurrentJobsPerCase.toolTipText")); // NOI18N
        lbConcurrentJobsPerCase.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);

        spConcurrentJobsPerCase.setModel(new javax.swing.SpinnerNumberModel(3, 1, 100, 1));
        spConcurrentJobsPerCase.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbConcurrentJobsPerCase.toolTipText")); // NOI18N

        spTimeoutHours.setModel(new javax.swing.SpinnerNumberModel(60, 1, 100000, 1));
        spTimeoutHours.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.spTimeoutHours.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbSecondsBetweenJobsSeconds, org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.lbSecondsBetweenJobsSeconds.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(213, 213, 213)
                .addComponent(filler1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(58, 58, 58))
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(spMainScrollPane)
                .addGap(16, 16, 16))
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(cbTimeoutEnabled)
                .addGap(6, 6, 6)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbSecondsBetweenJobs)
                    .addComponent(lbTimeoutText)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbRetriesAllowed)
                            .addComponent(lbConcurrentJobsPerCase)
                            .addComponent(lbNumberOfThreads))
                        .addGap(25, 25, 25)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(spMaximumRetryAttempts, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(spConcurrentJobsPerCase, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(spTimeoutHours, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(spSecondsBetweenJobs, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(lbSecondsBetweenJobsSeconds)
                                    .addComponent(lbTimeoutHours)))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(numberOfFileIngestThreadsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(lbRestartRequired)))))
                .addContainerGap(18, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addComponent(spMainScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 106, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbSecondsBetweenJobs)
                    .addComponent(spSecondsBetweenJobs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbSecondsBetweenJobsSeconds))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(lbTimeoutText)
                        .addComponent(lbTimeoutHours)
                        .addComponent(spTimeoutHours, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(cbTimeoutEnabled))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(spMaximumRetryAttempts, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbRetriesAllowed))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(spConcurrentJobsPerCase, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbConcurrentJobsPerCase))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(lbRestartRequired)
                    .addComponent(lbNumberOfThreads)
                    .addComponent(numberOfFileIngestThreadsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(241, Short.MAX_VALUE))
        );

        spMaximumRetryAttempts.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(AdvancedOptionsPanel.class, "AdvancedOptionsPanel.spMaximumRetryAttempts.AccessibleContext.accessibleDescription")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents

    private void cbTimeoutEnabledItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cbTimeoutEnabledItemStateChanged
        setCheckboxEnabledState();
    }//GEN-LAST:event_cbTimeoutEnabledItemStateChanged

    private void lbTimeoutTextMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lbTimeoutTextMouseClicked
        if (cbTimeoutEnabled.isEnabled()) {
            if (cbTimeoutEnabled.isSelected()) {
                spTimeoutHours.setEnabled(true);
                lbTimeoutHours.setEnabled(true);
                cbTimeoutEnabled.setSelected(false);
            } else {
                spTimeoutHours.setEnabled(false);
                lbTimeoutHours.setEnabled(false);
                cbTimeoutEnabled.setSelected(true);
            }
        }
    }//GEN-LAST:event_lbTimeoutTextMouseClicked

    private void numberOfFileIngestThreadsComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_numberOfFileIngestThreadsComboBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_numberOfFileIngestThreadsComboBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox cbTimeoutEnabled;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JLabel lbConcurrentJobsPerCase;
    private javax.swing.JLabel lbNumberOfThreads;
    private javax.swing.JLabel lbRestartRequired;
    private javax.swing.JLabel lbRetriesAllowed;
    private javax.swing.JLabel lbSecondsBetweenJobs;
    private javax.swing.JLabel lbSecondsBetweenJobsSeconds;
    private javax.swing.JLabel lbTimeoutHours;
    private javax.swing.JLabel lbTimeoutText;
    private javax.swing.JComboBox<Integer> numberOfFileIngestThreadsComboBox;
    private javax.swing.JSpinner spConcurrentJobsPerCase;
    private javax.swing.JScrollPane spMainScrollPane;
    private javax.swing.JSpinner spMaximumRetryAttempts;
    private javax.swing.JSpinner spSecondsBetweenJobs;
    private javax.swing.JSpinner spTimeoutHours;
    private javax.swing.JTextArea tbWarning;
    // End of variables declaration//GEN-END:variables
}
