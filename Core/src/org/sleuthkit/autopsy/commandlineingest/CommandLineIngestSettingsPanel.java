/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commandlineingest;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettingsPanel;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.report.infrastructure.ReportWizardAction.doReportWizard;
import org.sleuthkit.autopsy.report.infrastructure.ReportWizardAction;

/**
 * Configuration panel for auto ingest settings.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class CommandLineIngestSettingsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(CommandLineIngestSettingsPanel.class.getName());

    private static final String REPORTING_CONFIGURATION_NAME = "CommandLineIngest";
    private static final boolean DISPLAY_CASE_SPECIFIC_DATA = false; // do not try to display case specific data
    private static final boolean RUN_REPORTS = false; // do not generate reports as part of running the report wizard

    /**
     * Creates new form AutoIngestSettingsPanel
     *
     * @param theController Controller to notify of changes.
     */
    public CommandLineIngestSettingsPanel(CommandLineIngestSettingsPanelController theController) {
        initComponents();
        setupReportList();
    }

    /**
     * @return the REPORTING_CONFIGURATION_NAME
     */
    public static String getDefaultReportingConfigName() {
        return REPORTING_CONFIGURATION_NAME;
    }

    private void displayIngestJobSettingsPanel() {
        this.getParent().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        IngestJobSettings ingestJobSettings = new IngestJobSettings(org.sleuthkit.autopsy.commandlineingest.UserPreferences.getCommandLineModeIngestModuleContextString());
        showWarnings(ingestJobSettings);
        IngestJobSettingsPanel ingestJobSettingsPanel = new IngestJobSettingsPanel(ingestJobSettings);

        add(ingestJobSettingsPanel, BorderLayout.PAGE_START);

        if (JOptionPane.showConfirmDialog(this, ingestJobSettingsPanel, "Ingest Module Configuration", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            // store the updated settings
            ingestJobSettings = ingestJobSettingsPanel.getSettings();
            ingestJobSettings.save();
            showWarnings(ingestJobSettings);
        }

        this.getParent().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    private static void showWarnings(IngestJobSettings ingestJobSettings) {
        List<String> warnings = ingestJobSettings.getWarnings();
        if (warnings.isEmpty() == false) {
            StringBuilder warningMessage = new StringBuilder();
            for (String warning : warnings) {
                warningMessage.append(warning).append("\n");
            }
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), warningMessage.toString());
        }
    }

    @Messages({
        "CommandListIngestSettingsPanel_Default_Report_DisplayName=Default",
        "CommandListIngestSettingsPanel_Make_Config=Make new profile..."
    })
    /**
     * Initializes the report profile list combo box.
     */
    private void setupReportList() {
        Set<String> configNames = ReportWizardAction.getReportConfigNames();
        configNames.remove(REPORTING_CONFIGURATION_NAME);

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(Bundle.CommandListIngestSettingsPanel_Default_Report_DisplayName());
        for (String name : configNames) {
            model.addElement(name);
        }

        model.addElement(Bundle.CommandListIngestSettingsPanel_Make_Config());

        reportProfileCB.setModel(model);
        reportProfileCB.setSelectedIndex(0);
    }

    /**
     * Checks to see if there is currently a profile define with the given name.
     * 
     * @param name Profile name to check.
     * 
     * @return True if there is a report with the given name.
     */
    private boolean doesReportProfileNameExist(String name) {
        Set<String> configNames = ReportWizardAction.getReportConfigNames();
        return configNames.contains(name);
    }

    /**
     * Returns the currently selected report profile name from the cb. For backwards
     * compatibility if Default is selected, the existing default 
     * profile name of CommandLineIngest will be returned.
     * 
     * @return The selected profile name.
     */
    private String getReportName() {
        String reportName = (String) reportProfileCB.getSelectedItem();

        if (reportName.equals(Bundle.CommandListIngestSettingsPanel_Default_Report_DisplayName())) {
            reportName = REPORTING_CONFIGURATION_NAME;
        }

        return reportName;
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

        nodePanel = new javax.swing.JPanel();
        bnEditIngestSettings = new javax.swing.JButton();
        bnEditReportSettings = new javax.swing.JButton();
        javax.swing.JTextPane ingestDescriptionTextPane = new javax.swing.JTextPane();
        javax.swing.JLabel ingestProfileLabel = new javax.swing.JLabel();
        ingestProfileCB = new javax.swing.JComboBox<>();
        javax.swing.JTextPane reportDescriptionTextPane = new javax.swing.JTextPane();
        javax.swing.JLabel reportProfileLabel = new javax.swing.JLabel();
        reportProfileCB = new javax.swing.JComboBox<>();
        javax.swing.Box.Filler bottomFiller = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0));
        jTextPane1 = new javax.swing.JTextPane();

        setPreferredSize(new java.awt.Dimension(810, 422));
        setLayout(new java.awt.BorderLayout());

        nodePanel.setMinimumSize(new java.awt.Dimension(100, 100));
        nodePanel.setPreferredSize(new java.awt.Dimension(801, 551));
        nodePanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(bnEditIngestSettings, org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.bnEditIngestSettings.text")); // NOI18N
        bnEditIngestSettings.setToolTipText(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.bnEditIngestSettings.toolTipText")); // NOI18N
        bnEditIngestSettings.setActionCommand(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.bnEditIngestSettings.text")); // NOI18N
        bnEditIngestSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnEditIngestSettingsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 9, 0, 0);
        nodePanel.add(bnEditIngestSettings, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(bnEditReportSettings, org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.bnEditReportSettings.text")); // NOI18N
        bnEditReportSettings.setToolTipText(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.bnEditReportSettings.toolTipText")); // NOI18N
        bnEditReportSettings.setActionCommand(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.bnEditReportSettings.actionCommand")); // NOI18N
        bnEditReportSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnEditReportSettingsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 9, 0, 0);
        nodePanel.add(bnEditReportSettings, gridBagConstraints);
        bnEditReportSettings.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.bnEditReportSettings.AccessibleContext.accessibleName")); // NOI18N

        ingestDescriptionTextPane.setEditable(false);
        ingestDescriptionTextPane.setBackground(javax.swing.UIManager.getDefaults().getColor("Label.background"));
        ingestDescriptionTextPane.setText(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.ingestDescriptionTextPane.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 10, 5);
        nodePanel.add(ingestDescriptionTextPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(ingestProfileLabel, org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.ingestProfileLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 25, 0, 0);
        nodePanel.add(ingestProfileLabel, gridBagConstraints);

        ingestProfileCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Default" }));
        ingestProfileCB.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        nodePanel.add(ingestProfileCB, gridBagConstraints);

        reportDescriptionTextPane.setEditable(false);
        reportDescriptionTextPane.setBackground(javax.swing.UIManager.getDefaults().getColor("Label.background"));
        reportDescriptionTextPane.setText(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.reportDescriptionTextPane.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(15, 5, 5, 5);
        nodePanel.add(reportDescriptionTextPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(reportProfileLabel, org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.reportProfileLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 25, 0, 0);
        nodePanel.add(reportProfileLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        nodePanel.add(reportProfileCB, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.weighty = 1.0;
        nodePanel.add(bottomFiller, gridBagConstraints);

        jTextPane1.setEditable(false);
        jTextPane1.setBackground(javax.swing.UIManager.getDefaults().getColor("Label.background"));
        jTextPane1.setText(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.jTextPane1.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);
        nodePanel.add(jTextPane1, gridBagConstraints);

        add(nodePanel, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents
    @Messages({
        "CommandListIngestSettingsPanel_Report_Name_Msg=Please supply a report profile name (letters, digits, and underscore characters only):",
        "CommandLineIngestSettingPanel_empty_report_name_mgs=Report profile name was empty, no profile created.",
        "CommandLineIngestSettingPanel_existing_report_name_mgs=Report profile name was already exists, no profile created.",
        "CommandLineIngestSettingPanel_invalid_report_name_mgs=Report profile name contained illegal characters, no profile created."
    })
    private void bnEditReportSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnEditReportSettingsActionPerformed
        String reportName = getReportName();
        if (reportName.equals(Bundle.CommandListIngestSettingsPanel_Make_Config())) {
            reportName = JOptionPane.showInputDialog(this, Bundle.CommandListIngestSettingsPanel_Report_Name_Msg());

            // User hit cancel
            if (reportName == null) {
                return;
            } else if (reportName.isEmpty()) {
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), Bundle.CommandLineIngestSettingPanel_empty_report_name_mgs());
                return;
            } else if (doesReportProfileNameExist(reportName)) {
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), Bundle.CommandLineIngestSettingPanel_existing_report_name_mgs());
                return;
            } else {
                // sanitize report name
                String originalReportName = reportName;
                reportName = reportName.replaceAll("[^A-Za-z0-9_]", "");
                if (reportName.isEmpty() || (!(originalReportName.equals(reportName)))) {
                    // report name contained only invalid characters, display error
                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), Bundle.CommandLineIngestSettingPanel_invalid_report_name_mgs());
                    return;
                }               
            }
        }

        this.getParent().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        doReportWizard(reportName, DISPLAY_CASE_SPECIFIC_DATA, RUN_REPORTS);

        setupReportList();

        if (((DefaultComboBoxModel) reportProfileCB.getModel()).getIndexOf(reportName) >= 0) {
            reportProfileCB.setSelectedItem(reportName);
        }

        this.getParent().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_bnEditReportSettingsActionPerformed

    private void bnEditIngestSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnEditIngestSettingsActionPerformed
        displayIngestJobSettingsPanel();
    }//GEN-LAST:event_bnEditIngestSettingsActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnEditIngestSettings;
    private javax.swing.JButton bnEditReportSettings;
    private javax.swing.JComboBox<String> ingestProfileCB;
    private javax.swing.JTextPane jTextPane1;
    private javax.swing.JPanel nodePanel;
    private javax.swing.JComboBox<String> reportProfileCB;
    // End of variables declaration//GEN-END:variables
}
