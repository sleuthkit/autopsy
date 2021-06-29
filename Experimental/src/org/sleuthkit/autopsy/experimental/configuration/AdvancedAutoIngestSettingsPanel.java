/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2021 Basis Technology Corp.
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

import java.awt.Component;
import java.time.DayOfWeek;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.ingest.ScheduledIngestPauseSettings;

/**
 * Configuration panel for advanced settings, such as number of concurrent jobs,
 * number of retries, etc.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class AdvancedAutoIngestSettingsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private final DefaultComboBoxModel<DayOfWeek> cbModel = new DefaultComboBoxModel<>();

    AdvancedAutoIngestSettingsPanel(AutoIngestSettingsPanel.OptionsUiMode mode) {
        initComponents();
        // Set up the combo box model before calling load.
        for (DayOfWeek day : DayOfWeek.values()) {
            cbModel.addElement(day);
        }
        cbPauseDay.setModel(cbModel);
        cbPauseDay.setRenderer(new DayOfTheWeekRenderer());

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

        setPauseEnabled(ScheduledIngestPauseSettings.getPauseEnabled());
        spPauseStartHour.setValue(ScheduledIngestPauseSettings.getPauseStartTimeHour());
        spPauseStartMinutes.setValue(ScheduledIngestPauseSettings.getPauseStartTimeMinute());
        spDuration.setValue(ScheduledIngestPauseSettings.getPauseDurationMinutes() / 60);
        cbPauseDay.setSelectedItem(ScheduledIngestPauseSettings.getPauseDayOfWeek());
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

        ScheduledIngestPauseSettings.setPauseEnabled(cbEnablePause.isSelected());
        ScheduledIngestPauseSettings.setPauseDayOfWeek((DayOfWeek) cbPauseDay.getSelectedItem());
        ScheduledIngestPauseSettings.setPauseStartTimeMinute((int) spPauseStartMinutes.getValue());
        ScheduledIngestPauseSettings.setPauseStartTimeHour((int) spPauseStartHour.getValue());
        ScheduledIngestPauseSettings.setPauseDurationMinutes((int) spDuration.getValue() * 60);
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

    private void setPauseEnabled(boolean enabled) {
        cbEnablePause.setSelected(enabled);
        spPauseStartMinutes.setEnabled(enabled);
        spPauseStartHour.setEnabled(enabled);
        spDuration.setEnabled(enabled);
        lbpauseDay.setEnabled(enabled);
        lbDurationHours.setEnabled(enabled);
        lbPauseDuration.setEnabled(enabled);
        lbPauseTime.setEnabled(enabled);
        cbPauseDay.setEnabled(enabled);
    }

    @Messages({
        "DayOfTheWeekRenderer_Monday_Label=Monday",
        "DayOfTheWeekRenderer_Tuesday_Label=Tuesday",
        "DayOfTheWeekRenderer_Wednesday_Label=Wednesday",
        "DayOfTheWeekRenderer_Thursday_Label=Thursday",
        "DayOfTheWeekRenderer_Friday_Label=Friday",
        "DayOfTheWeekRenderer_Saturday_Label=Saturday",
        "DayOfTheWeekRenderer_Sunday_Label=Sunday",})
    /**
     * Renderer for the Day of the week combo box.
     */
    private final class DayOfTheWeekRenderer implements ListCellRenderer<DayOfWeek> {

        private final ListCellRenderer<? super DayOfWeek> delegate;

        /**
         * Construct a new Renderer.
         */
        DayOfTheWeekRenderer() {
            JComboBox<DayOfWeek> cb = new JComboBox<>();
            delegate = cb.getRenderer();
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends DayOfWeek> list, DayOfWeek value, int index, boolean isSelected, boolean cellHasFocus) {
            Component comp = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            String text = "";
            if (value != null) {
                text = value.toString();
                switch (value) {
                    case MONDAY:
                        text = Bundle.DayOfTheWeekRenderer_Monday_Label();
                        break;
                    case TUESDAY:
                        text = Bundle.DayOfTheWeekRenderer_Tuesday_Label();
                        break;
                    case WEDNESDAY:
                        text = Bundle.DayOfTheWeekRenderer_Wednesday_Label();
                        break;
                    case THURSDAY:
                        text = Bundle.DayOfTheWeekRenderer_Thursday_Label();
                        break;
                    case FRIDAY:
                        text = Bundle.DayOfTheWeekRenderer_Friday_Label();
                        break;
                    case SATURDAY:
                        text = Bundle.DayOfTheWeekRenderer_Saturday_Label();
                        break;
                    case SUNDAY:
                        text = Bundle.DayOfTheWeekRenderer_Sunday_Label();
                        break;
                }
            }
            ((JLabel) comp).setText(text);
            return comp;
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
        java.awt.GridBagConstraints gridBagConstraints;

        spMainScrollPane = new javax.swing.JScrollPane();
        tbWarning = new javax.swing.JTextArea();
        jPanelAutoIngestJobSettings = new javax.swing.JPanel();
        lbSecondsBetweenJobs = new javax.swing.JLabel();
        lbTimeoutText = new javax.swing.JLabel();
        lbInputScanInterval = new javax.swing.JLabel();
        lbRetriesAllowed = new javax.swing.JLabel();
        javax.swing.JLabel lbNumberOfThreads = new javax.swing.JLabel();
        javax.swing.JLabel lbConcurrentJobsPerCase = new javax.swing.JLabel();
        cbTimeoutEnabled = new javax.swing.JCheckBox();
        numberOfFileIngestThreadsComboBox = new javax.swing.JComboBox<>();
        javax.swing.JLabel lbRestartRequired = new javax.swing.JLabel();
        spConcurrentJobsPerCase = new javax.swing.JSpinner();
        spMaximumRetryAttempts = new javax.swing.JSpinner();
        spInputScanInterval = new javax.swing.JSpinner();
        spTimeoutHours = new javax.swing.JSpinner();
        spSecondsBetweenJobs = new javax.swing.JSpinner();
        lbSecondsBetweenJobsSeconds = new javax.swing.JLabel();
        lbTimeoutHours = new javax.swing.JLabel();
        lbInputScanIntervalMinutes = new javax.swing.JLabel();
        pausePanel = new javax.swing.JPanel();
        lbpauseDay = new javax.swing.JLabel();
        lbPauseTime = new javax.swing.JLabel();
        lbPauseDuration = new javax.swing.JLabel();
        cbPauseDay = new javax.swing.JComboBox<>();
        spPauseStartHour = new javax.swing.JSpinner();
        javax.swing.JLabel lbColon = new javax.swing.JLabel();
        spPauseStartMinutes = new javax.swing.JSpinner();
        spDuration = new javax.swing.JSpinner();
        lbDurationHours = new javax.swing.JLabel();
        cbEnablePause = new javax.swing.JCheckBox();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));

        setLayout(new java.awt.GridBagLayout());

        tbWarning.setEditable(false);
        tbWarning.setColumns(20);
        tbWarning.setFont(tbWarning.getFont().deriveFont(tbWarning.getFont().getStyle() | java.awt.Font.BOLD, tbWarning.getFont().getSize()+1));
        tbWarning.setRows(5);
        tbWarning.setText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.tbWarning.text")); // NOI18N
        tbWarning.setAutoscrolls(false);
        spMainScrollPane.setViewportView(tbWarning);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 16, 0, 16);
        add(spMainScrollPane, gridBagConstraints);

        jPanelAutoIngestJobSettings.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.jPanelAutoIngestJobSettings.border.title"))); // NOI18N
        jPanelAutoIngestJobSettings.setName("Automated Ingest Job Settings"); // NOI18N
        jPanelAutoIngestJobSettings.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(lbSecondsBetweenJobs, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbSecondsBetweenJobs.text")); // NOI18N
        lbSecondsBetweenJobs.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbSecondsBetweenJobs.toolTipText_1")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 5, 0);
        jPanelAutoIngestJobSettings.add(lbSecondsBetweenJobs, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbTimeoutText, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbTimeoutText.text")); // NOI18N
        lbTimeoutText.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbTimeoutText.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        jPanelAutoIngestJobSettings.add(lbTimeoutText, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbInputScanInterval, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbInputScanInterval.text")); // NOI18N
        lbInputScanInterval.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbInputScanInterval.toolTipText_1")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        jPanelAutoIngestJobSettings.add(lbInputScanInterval, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbRetriesAllowed, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbRetriesAllowed.text")); // NOI18N
        lbRetriesAllowed.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbRetriesAllowed.toolTipText_1")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        jPanelAutoIngestJobSettings.add(lbRetriesAllowed, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbNumberOfThreads, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbNumberOfThreads.text")); // NOI18N
        lbNumberOfThreads.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbNumberOfThreads.toolTipText_1")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        jPanelAutoIngestJobSettings.add(lbNumberOfThreads, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbConcurrentJobsPerCase, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbConcurrentJobsPerCase.text")); // NOI18N
        lbConcurrentJobsPerCase.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbConcurrentJobsPerCase.toolTipText_1")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        jPanelAutoIngestJobSettings.add(lbConcurrentJobsPerCase, gridBagConstraints);

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
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        jPanelAutoIngestJobSettings.add(cbTimeoutEnabled, gridBagConstraints);

        numberOfFileIngestThreadsComboBox.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.numberOfFileIngestThreadsComboBox.toolTipText")); // NOI18N
        numberOfFileIngestThreadsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                numberOfFileIngestThreadsComboBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        jPanelAutoIngestJobSettings.add(numberOfFileIngestThreadsComboBox, gridBagConstraints);

        lbRestartRequired.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbRestartRequired, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbRestartRequired.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 9;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        jPanelAutoIngestJobSettings.add(lbRestartRequired, gridBagConstraints);

        spConcurrentJobsPerCase.setModel(new javax.swing.SpinnerNumberModel(3, 1, 100, 1));
        spConcurrentJobsPerCase.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbConcurrentJobsPerCase.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.ipadx = 43;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        jPanelAutoIngestJobSettings.add(spConcurrentJobsPerCase, gridBagConstraints);

        spMaximumRetryAttempts.setModel(new javax.swing.SpinnerNumberModel(2, 0, 9999999, 1));
        spMaximumRetryAttempts.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbRetriesAllowed.toolTipText_2")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.ipadx = -5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        jPanelAutoIngestJobSettings.add(spMaximumRetryAttempts, gridBagConstraints);
        spMaximumRetryAttempts.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.spMaximumRetryAttempts.AccessibleContext.accessibleDescription")); // NOI18N

        spInputScanInterval.setModel(new javax.swing.SpinnerNumberModel(60, 1, 100000, 1));
        spInputScanInterval.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.spInputScanInterval.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.ipadx = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        jPanelAutoIngestJobSettings.add(spInputScanInterval, gridBagConstraints);

        spTimeoutHours.setModel(new javax.swing.SpinnerNumberModel(60, 1, 100000, 1));
        spTimeoutHours.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.spTimeoutHours.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.ipadx = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        jPanelAutoIngestJobSettings.add(spTimeoutHours, gridBagConstraints);

        spSecondsBetweenJobs.setModel(new javax.swing.SpinnerNumberModel(30, 30, 3600, 10));
        spSecondsBetweenJobs.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.spSecondsBetweenJobs.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.ipadx = 27;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 0);
        jPanelAutoIngestJobSettings.add(spSecondsBetweenJobs, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbSecondsBetweenJobsSeconds, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbSecondsBetweenJobsSeconds.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 9;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 0);
        jPanelAutoIngestJobSettings.add(lbSecondsBetweenJobsSeconds, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbTimeoutHours, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbTimeoutHours.text")); // NOI18N
        lbTimeoutHours.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbTimeoutHours.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 9;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        jPanelAutoIngestJobSettings.add(lbTimeoutHours, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbInputScanIntervalMinutes, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbInputScanIntervalMinutes.text")); // NOI18N
        lbInputScanIntervalMinutes.setToolTipText(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbInputScanIntervalMinutes.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 9;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        jPanelAutoIngestJobSettings.add(lbInputScanIntervalMinutes, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(6, 5, 6, 10);
        add(jPanelAutoIngestJobSettings, gridBagConstraints);

        pausePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.pausePanel.border.title"))); // NOI18N
        pausePanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(lbpauseDay, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbpauseDay.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        pausePanel.add(lbpauseDay, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbPauseTime, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbPauseTime.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        pausePanel.add(lbPauseTime, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbPauseDuration, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbPauseDuration.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        pausePanel.add(lbPauseDuration, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        pausePanel.add(cbPauseDay, gridBagConstraints);

        spPauseStartHour.setModel(new javax.swing.SpinnerNumberModel(0, 0, 23, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        pausePanel.add(spPauseStartHour, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbColon, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbColon.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 5, 2);
        pausePanel.add(lbColon, gridBagConstraints);

        spPauseStartMinutes.setModel(new javax.swing.SpinnerNumberModel(0, 0, 59, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        pausePanel.add(spPauseStartMinutes, gridBagConstraints);

        spDuration.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        pausePanel.add(spDuration, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbDurationHours, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.lbDurationHours.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        pausePanel.add(lbDurationHours, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(cbEnablePause, org.openide.util.NbBundle.getMessage(AdvancedAutoIngestSettingsPanel.class, "AdvancedAutoIngestSettingsPanel.cbEnablePause.text")); // NOI18N
        cbEnablePause.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbEnablePauseActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 5, 0);
        pausePanel.add(cbEnablePause, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.weighty = 1.0;
        pausePanel.add(filler1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 10, 6, 0);
        add(pausePanel, gridBagConstraints);
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

    private void cbEnablePauseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbEnablePauseActionPerformed
        setPauseEnabled(cbEnablePause.isSelected());
    }//GEN-LAST:event_cbEnablePauseActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox cbEnablePause;
    private javax.swing.JComboBox<DayOfWeek> cbPauseDay;
    private javax.swing.JCheckBox cbTimeoutEnabled;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JPanel jPanelAutoIngestJobSettings;
    private javax.swing.JLabel lbDurationHours;
    private javax.swing.JLabel lbInputScanInterval;
    private javax.swing.JLabel lbInputScanIntervalMinutes;
    private javax.swing.JLabel lbPauseDuration;
    private javax.swing.JLabel lbPauseTime;
    private javax.swing.JLabel lbRetriesAllowed;
    private javax.swing.JLabel lbSecondsBetweenJobs;
    private javax.swing.JLabel lbSecondsBetweenJobsSeconds;
    private javax.swing.JLabel lbTimeoutHours;
    private javax.swing.JLabel lbTimeoutText;
    private javax.swing.JLabel lbpauseDay;
    private javax.swing.JComboBox<Integer> numberOfFileIngestThreadsComboBox;
    private javax.swing.JPanel pausePanel;
    private javax.swing.JSpinner spConcurrentJobsPerCase;
    private javax.swing.JSpinner spDuration;
    private javax.swing.JSpinner spInputScanInterval;
    private javax.swing.JScrollPane spMainScrollPane;
    private javax.swing.JSpinner spMaximumRetryAttempts;
    private javax.swing.JSpinner spPauseStartHour;
    private javax.swing.JSpinner spPauseStartMinutes;
    private javax.swing.JSpinner spSecondsBetweenJobs;
    private javax.swing.JSpinner spTimeoutHours;
    private javax.swing.JTextArea tbWarning;
    // End of variables declaration//GEN-END:variables
}
