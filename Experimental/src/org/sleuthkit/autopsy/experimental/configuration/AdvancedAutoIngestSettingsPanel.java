/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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

import javax.swing.DefaultComboBoxModel;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Configuration panel for advanced settings, such as number of concurrent jobs,
 * number of retries, etc.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class AdvancedAutoIngestSettingsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    AdvancedAutoIngestSettingsPanel(AutoIngestSettingsPanel.OptionsUiMode mode) {
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

    private void load(AutoIngestSettingsPanel.OptionsUiMode mode) {
        initThreadCount();
        spSecondsBetweenJobs.setValue(AutoIngestUserPreferences.getSecondsToSleepBetweenCases());
        spMaximumRetryAttempts.setValue(AutoIngestUserPreferences.getMaxNumTimesToProcessImage());
        int maxJobsPerCase = AutoIngestUserPreferences.getMaxConcurrentJobsForOneCase();
        spConcurrentJobsPerCase.setValue(maxJobsPerCase);
        spInputScanInterval.setValue(AutoIngestUserPreferences.getMinutesOfInputScanInterval());
        spInputScanInterval.setEnabled(mode == AutoIngestSettingsPanel.OptionsUiMode.AIM);
        spSecondsBetweenJobs.setEnabled(mode == AutoIngestSettingsPanel.OptionsUiMode.AIM);
        spMaximumRetryAttempts.setEnabled(mode == AutoIngestSettingsPanel.OptionsUiMode.AIM);
        cbTimeoutEnabled.setEnabled(mode == AutoIngestSettingsPanel.OptionsUiMode.AIM);
        lbSecondsBetweenJobs.setEnabled(mode == AutoIngestSettingsPanel.OptionsUiMode.AIM);
        lbInputScanInterval.setEnabled(mode == AutoIngestSettingsPanel.OptionsUiMode.AIM);
        lbTimeoutText.setEnabled(mode == AutoIngestSettingsPanel.OptionsUiMode.AIM);
        lbRetriesAllowed.setEnabled(mode == AutoIngestSettingsPanel.OptionsUiMode.AIM);
        cbTimeoutEnabled.setSelected(UserPreferences.getIsTimeOutEnabled());
        int timeOutHrs = UserPreferences.getProcessTimeOutHrs();
        spTimeoutHours.setValue(timeOutHrs);
        setCheckboxEnabledState();
    }

    void store() {
        AutoIngestUserPreferences.setSecondsToSleepBetweenCases((int) spSecondsBetweenJobs.getValue());
        AutoIngestUserPreferences.setMaxNumTimesToProcessImage((int) spMaximumRetryAttempts.getValue());
        AutoIngestUserPreferences.setMaxConcurrentIngestNodesForOneCase((int) spConcurrentJobsPerCase.getValue());
        AutoIngestUserPreferences.setMinutesOfInputScanInterval((int) spInputScanInterval.getValue());
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

        spMainScrollPane = new javax.swing.JScrollPane();
        tbWarning = new javax.swing.JTextArea();
        jPanelAutoIngestJobSettings = new javax.swing.JPanel();
        lbSecondsBetweenJobs = new javax.swing.JLabel();
        lbTimeoutText = new javax.swing.JLabel();
        lbInputScanInterval = new javax.swing.JLabel();
        lbRetriesAllowed = new javax.swing.JLabel();
        lbNumberOfThreads = new javax.swing.JLabel();
        lbConcurrentJobsPerCase = new javax.swing.JLabel();
        cbTimeoutEnabled = new javax.swing.JCheckBox();
        numberOfFileIngestThreadsComboBox = new javax.swing.JComboBox<>();
        lbRestartRequired = new javax.swing.JLabel();
        spConcurrentJobsPerCase = new javax.swing.JSpinner();
        spMaximumRetryAttempts = new javax.swing.JSpinner();
        spInputScanInterval = new javax.swing.JSpinner();
        spTimeoutHours = new javax.swing.JSpinner();
        spSecondsBetweenJobs = new javax.swing.JSpinner();
        lbSecondsBetweenJobsSeconds = new javax.swing.JLabel();
        lbTimeoutHours = new javax.swing.JLabel();
        lbInputScanIntervalMinutes = new javax.swing.JLabel();

        tbWarning.setEditable(false);
        tbWarning.setColumns(20);
        tbWarning.setFont(tbWarning.getFont().deriveFont(tbWarning.getFont().getStyle() | java.awt.Font.BOLD, tbWarning.getFont().getSize()+1));
        tbWarning.setRows(5);
        tbWarning.setText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.tbWarning.text")); // NOI18N
        tbWarning.setAutoscrolls(false);
        spMainScrollPane.setViewportView(tbWarning);

        jPanelAutoIngestJobSettings.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.jPanelAutoIngestJobSettings.border.title"))); // NOI18N
        jPanelAutoIngestJobSettings.setName("Automated Ingest Job Settings"); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbSecondsBetweenJobs, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbSecondsBetweenJobs.text")); // NOI18N
        lbSecondsBetweenJobs.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbSecondsBetweenJobs.toolTipText_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbTimeoutText, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbTimeoutText.text")); // NOI18N
        lbTimeoutText.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbTimeoutText.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbInputScanInterval, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbInputScanInterval.text")); // NOI18N
        lbInputScanInterval.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbInputScanInterval.toolTipText_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbRetriesAllowed, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbRetriesAllowed.text")); // NOI18N
        lbRetriesAllowed.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbRetriesAllowed.toolTipText_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbNumberOfThreads, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbNumberOfThreads.text")); // NOI18N
        lbNumberOfThreads.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbNumberOfThreads.toolTipText_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbConcurrentJobsPerCase, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbConcurrentJobsPerCase.text")); // NOI18N
        lbConcurrentJobsPerCase.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbConcurrentJobsPerCase.toolTipText_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(cbTimeoutEnabled, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.cbTimeoutEnabled.text")); // NOI18N
        cbTimeoutEnabled.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.cbTimeoutEnabled.toolTipText")); // NOI18N
        cbTimeoutEnabled.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cbTimeoutEnabledItemStateChanged(evt);
            }
        });
        cbTimeoutEnabled.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbTimeoutEnabledActionPerformed(evt);
            }
        });

        numberOfFileIngestThreadsComboBox.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.numberOfFileIngestThreadsComboBox.toolTipText")); // NOI18N
        numberOfFileIngestThreadsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                numberOfFileIngestThreadsComboBoxActionPerformed(evt);
            }
        });

        lbRestartRequired.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbRestartRequired, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbRestartRequired.text")); // NOI18N

        spConcurrentJobsPerCase.setModel(new javax.swing.SpinnerNumberModel(3, 1, 100, 1));
        spConcurrentJobsPerCase.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbConcurrentJobsPerCase.toolTipText")); // NOI18N

        spMaximumRetryAttempts.setModel(new javax.swing.SpinnerNumberModel(2, 0, 9999999, 1));
        spMaximumRetryAttempts.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbRetriesAllowed.toolTipText_2")); // NOI18N

        spInputScanInterval.setModel(new javax.swing.SpinnerNumberModel(60, 1, 100000, 1));
        spInputScanInterval.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.spInputScanInterval.toolTipText")); // NOI18N

        spTimeoutHours.setModel(new javax.swing.SpinnerNumberModel(60, 1, 100000, 1));
        spTimeoutHours.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.spTimeoutHours.toolTipText")); // NOI18N

        spSecondsBetweenJobs.setModel(new javax.swing.SpinnerNumberModel(30, 30, 3600, 10));
        spSecondsBetweenJobs.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.spSecondsBetweenJobs.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbSecondsBetweenJobsSeconds, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbSecondsBetweenJobsSeconds.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbTimeoutHours, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbTimeoutHours.text")); // NOI18N
        lbTimeoutHours.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbTimeoutHours.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbInputScanIntervalMinutes, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbInputScanIntervalMinutes.text")); // NOI18N
        lbInputScanIntervalMinutes.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbInputScanIntervalMinutes.toolTipText")); // NOI18N

        javax.swing.GroupLayout jPanelAutoIngestJobSettingsLayout = new javax.swing.GroupLayout(jPanelAutoIngestJobSettings);
        jPanelAutoIngestJobSettings.setLayout(jPanelAutoIngestJobSettingsLayout);
        jPanelAutoIngestJobSettingsLayout.setHorizontalGroup(
            jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelAutoIngestJobSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(cbTimeoutEnabled)
                .addGap(5, 5, 5)
                .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelAutoIngestJobSettingsLayout.createSequentialGroup()
                        .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addGroup(jPanelAutoIngestJobSettingsLayout.createSequentialGroup()
                                .addComponent(lbInputScanInterval)
                                .addGap(49, 49, 49))
                            .addGroup(jPanelAutoIngestJobSettingsLayout.createSequentialGroup()
                                .addComponent(lbRetriesAllowed)
                                .addGap(54, 54, 54))
                            .addComponent(lbConcurrentJobsPerCase, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbNumberOfThreads, javax.swing.GroupLayout.Alignment.LEADING))
                        .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanelAutoIngestJobSettingsLayout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(spInputScanInterval, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(spMaximumRetryAttempts, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(spConcurrentJobsPerCase, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addComponent(numberOfFileIngestThreadsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanelAutoIngestJobSettingsLayout.createSequentialGroup()
                        .addComponent(lbSecondsBetweenJobs)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(spSecondsBetweenJobs, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelAutoIngestJobSettingsLayout.createSequentialGroup()
                        .addComponent(lbTimeoutText)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(spTimeoutHours, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbRestartRequired)
                    .addComponent(lbSecondsBetweenJobsSeconds)
                    .addComponent(lbTimeoutHours)
                    .addComponent(lbInputScanIntervalMinutes))
                .addContainerGap(70, Short.MAX_VALUE))
        );
        jPanelAutoIngestJobSettingsLayout.setVerticalGroup(
            jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelAutoIngestJobSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(lbRestartRequired)
                    .addGroup(jPanelAutoIngestJobSettingsLayout.createSequentialGroup()
                        .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lbSecondsBetweenJobs)
                            .addComponent(spSecondsBetweenJobs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lbSecondsBetweenJobsSeconds))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbTimeoutText)
                                .addComponent(spTimeoutHours, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(lbTimeoutHours))
                            .addComponent(cbTimeoutEnabled))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lbInputScanInterval)
                            .addComponent(spInputScanInterval, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lbInputScanIntervalMinutes))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lbRetriesAllowed)
                            .addComponent(spMaximumRetryAttempts, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lbConcurrentJobsPerCase)
                            .addComponent(spConcurrentJobsPerCase, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelAutoIngestJobSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lbNumberOfThreads)
                            .addComponent(numberOfFileIngestThreadsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        spMaximumRetryAttempts.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.spMaximumRetryAttempts.AccessibleContext.accessibleDescription")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jPanelAutoIngestJobSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGap(16, 16, 16)
                        .addComponent(spMainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 640, Short.MAX_VALUE)))
                .addGap(16, 16, 16))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addComponent(spMainScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 106, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelAutoIngestJobSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(26, 26, 26))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void numberOfFileIngestThreadsComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_numberOfFileIngestThreadsComboBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_numberOfFileIngestThreadsComboBoxActionPerformed

    private void cbTimeoutEnabledActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbTimeoutEnabledActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_cbTimeoutEnabledActionPerformed

    private void cbTimeoutEnabledItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cbTimeoutEnabledItemStateChanged
        setCheckboxEnabledState();
    }//GEN-LAST:event_cbTimeoutEnabledItemStateChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox cbTimeoutEnabled;
    private javax.swing.JPanel jPanelAutoIngestJobSettings;
    private javax.swing.JLabel lbConcurrentJobsPerCase;
    private javax.swing.JLabel lbInputScanInterval;
    private javax.swing.JLabel lbInputScanIntervalMinutes;
    private javax.swing.JLabel lbNumberOfThreads;
    private javax.swing.JLabel lbRestartRequired;
    private javax.swing.JLabel lbRetriesAllowed;
    private javax.swing.JLabel lbSecondsBetweenJobs;
    private javax.swing.JLabel lbSecondsBetweenJobsSeconds;
    private javax.swing.JLabel lbTimeoutHours;
    private javax.swing.JLabel lbTimeoutText;
    private javax.swing.JComboBox<Integer> numberOfFileIngestThreadsComboBox;
    private javax.swing.JSpinner spConcurrentJobsPerCase;
    private javax.swing.JSpinner spInputScanInterval;
    private javax.swing.JScrollPane spMainScrollPane;
    private javax.swing.JSpinner spMaximumRetryAttempts;
    private javax.swing.JSpinner spSecondsBetweenJobs;
    private javax.swing.JSpinner spTimeoutHours;
    private javax.swing.JTextArea tbWarning;
    // End of variables declaration//GEN-END:variables
}
