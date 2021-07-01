/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.infrastructure;

import org.sleuthkit.autopsy.report.modules.portablecase.PortableCaseReportModuleSettings;
import org.sleuthkit.autopsy.report.modules.portablecase.PortableCaseReportModule;
import org.sleuthkit.autopsy.report.ReportModuleSettings;
import java.awt.GridLayout;
import java.util.Map;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.report.modules.portablecase.PortableCaseReportModuleSettings.ChunkSize;

/**
 * The UI portion of the Portable Case config panel
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class ReportWizardPortableCaseOptionsVisualPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private final ReportWizardPortableCaseOptionsPanel wizPanel;
    private PortableCaseReportModuleSettings settings = null;
    private final Map<String, ReportModuleConfig> moduleConfigs;
    private final boolean useCaseSpecificData;

    /**
     * Creates new form ReportWizardPortableCaseOptionsVisualPanel
     */
    ReportWizardPortableCaseOptionsVisualPanel(ReportWizardPortableCaseOptionsPanel wizPanel, Map<String, ReportModuleConfig> moduleConfigs, boolean useCaseSpecificData) {
        this.wizPanel = wizPanel;
        this.useCaseSpecificData = useCaseSpecificData;
        this.moduleConfigs = moduleConfigs;
        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {

        if (!PlatformUtil.isWindowsOS()) {
            errorLabel.setVisible(true);
            compressCheckbox.setEnabled(false);
        } else {
            errorLabel.setVisible(false);
        }

        for (ChunkSize chunkSize : ChunkSize.values()) {
            chunkSizeComboBox.addItem(chunkSize);
        }

        // initialize settings
        if (moduleConfigs != null) {
            // get configuration for this module
            ReportModuleConfig config = moduleConfigs.get(PortableCaseReportModule.class.getCanonicalName());
            if (config != null) {
                // there is an existing configuration for this module
                ReportModuleSettings reportSettings = config.getModuleSettings();
                // check if the settings are for this module, it could be NoReportModuleSettings
                if (reportSettings instanceof PortableCaseReportModuleSettings) {
                    settings = (PortableCaseReportModuleSettings) reportSettings;
                }
            }
        }

        if (settings == null) {
            // get default module configuration
            settings = new PortableCaseReportModuleSettings();
        }

        // update according to input configuration
        compressCheckbox.setSelected(settings.shouldCompress());
        chunkSizeComboBox.setEnabled(settings.shouldCompress());
        chunkSizeComboBox.setSelectedItem(settings.getChunkSize());

        // initialize other panels and pass them the settings
        listPanel.setLayout(new GridLayout(1, 2));
        listPanel.add(new PortableCaseTagsListPanel(wizPanel, settings, useCaseSpecificData));
        listPanel.add(new PortableCaseInterestingItemsListPanel(wizPanel, settings, useCaseSpecificData));
    }

    @NbBundle.Messages({
        "ReportWizardPortableCaseOptionsVisualPanel.getName.title=Choose Portable Case settings",})
    @Override
    public String getName() {
        return Bundle.ReportWizardPortableCaseOptionsVisualPanel_getName_title();
    }

    /**
     * Get the selected chunk size
     *
     * @return the chunk size that was selected
     */
    private ChunkSize getChunkSize() {
        return (ChunkSize) chunkSizeComboBox.getSelectedItem();
    }

    /**
     * Update the selected compression options and enable/disable the finish
     * button
     */
    private void updateCompression() {
        if (settings != null) {
            settings.updateCompression(compressCheckbox.isSelected(), getChunkSize());
            wizPanel.setFinish(settings.isValid());
        }
    }

    /**
     * Update the include application option.
     */
    private void updateIncludeApplication() {
        if (settings != null) {
            settings.setIncludeApplication(includeAppCheckbox.isSelected());
        }
    }

    /**
     * Get the user-selected settings.
     *
     * @return the current settings
     */
    PortableCaseReportModuleSettings getPortableCaseReportSettings() {
        return settings;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        chunkSizeComboBox = new javax.swing.JComboBox<>();
        compressCheckbox = new javax.swing.JCheckBox();
        errorLabel = new javax.swing.JLabel();
        listPanel = new javax.swing.JPanel();
        includeAppCheckbox = new javax.swing.JCheckBox();

        setPreferredSize(new java.awt.Dimension(834, 374));

        chunkSizeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chunkSizeComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(compressCheckbox, org.openide.util.NbBundle.getMessage(ReportWizardPortableCaseOptionsVisualPanel.class, "ReportWizardPortableCaseOptionsVisualPanel.compressCheckbox.text")); // NOI18N
        compressCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(ReportWizardPortableCaseOptionsVisualPanel.class, "ReportWizardPortableCaseOptionsVisualPanel.compressCheckbox.toolTipText")); // NOI18N
        compressCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compressCheckboxActionPerformed(evt);
            }
        });

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(ReportWizardPortableCaseOptionsVisualPanel.class, "ReportWizardPortableCaseOptionsVisualPanel.errorLabel.text")); // NOI18N

        javax.swing.GroupLayout listPanelLayout = new javax.swing.GroupLayout(listPanel);
        listPanel.setLayout(listPanelLayout);
        listPanelLayout.setHorizontalGroup(
            listPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        listPanelLayout.setVerticalGroup(
            listPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 190, Short.MAX_VALUE)
        );

        org.openide.awt.Mnemonics.setLocalizedText(includeAppCheckbox, org.openide.util.NbBundle.getMessage(ReportWizardPortableCaseOptionsVisualPanel.class, "ReportWizardPortableCaseOptionsVisualPanel.includeAppCheckbox.text")); // NOI18N
        includeAppCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                includeAppCheckboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(listPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(compressCheckbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(chunkSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 187, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(errorLabel))
                    .addComponent(includeAppCheckbox))
                .addContainerGap(41, Short.MAX_VALUE))
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addComponent(listPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(compressCheckbox)
                    .addComponent(chunkSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(errorLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(includeAppCheckbox)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 463, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(mainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 259, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(mainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void chunkSizeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chunkSizeComboBoxActionPerformed
        updateCompression();
    }//GEN-LAST:event_chunkSizeComboBoxActionPerformed

    private void compressCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compressCheckboxActionPerformed
        chunkSizeComboBox.setEnabled(compressCheckbox.isSelected() && !includeAppCheckbox.isSelected());
        updateCompression();
    }//GEN-LAST:event_compressCheckboxActionPerformed

    private void includeAppCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_includeAppCheckboxActionPerformed
        if (includeAppCheckbox.isSelected()) {
            chunkSizeComboBox.setEnabled(false);
            chunkSizeComboBox.setSelectedItem(ChunkSize.NONE);
        } else {
            chunkSizeComboBox.setEnabled(compressCheckbox.isSelected());
        }
        updateIncludeApplication();
    }//GEN-LAST:event_includeAppCheckboxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<ChunkSize> chunkSizeComboBox;
    private javax.swing.JCheckBox compressCheckbox;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JCheckBox includeAppCheckbox;
    private javax.swing.JPanel listPanel;
    private javax.swing.JPanel mainPanel;
    // End of variables declaration//GEN-END:variables
}
