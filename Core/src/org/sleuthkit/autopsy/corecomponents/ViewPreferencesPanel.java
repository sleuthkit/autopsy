/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.netbeans.spi.options.OptionsPanelController;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;

/**
 * Panel for configuring view preferences.
 */
public class ViewPreferencesPanel extends JPanel implements OptionsPanel {
    
    private static final Logger logger = Logger.getLogger(ViewPreferencesPanel.class.getName());
    
    private boolean immediateUpdates;
    
    /**
     * Creates new form ViewPreferencesPanel
     * 
     * //DLG: Might not need this constructor anymore.
     */
    public ViewPreferencesPanel() {
        initComponents();
    }
    
    /**
     * Creates new form ViewPreferencesPanel
     * 
     * //DLG:
     */
    public ViewPreferencesPanel(boolean immediateUpdates) {
        initComponents();
        this.immediateUpdates = immediateUpdates;
    }
    
    @Override
    public void load() {
        // Global Settings
        boolean keepPreferredViewer = UserPreferences.keepPreferredContentViewer();
        keepCurrentViewerRadioButton.setSelected(keepPreferredViewer);
        useBestViewerRadioButton.setSelected(!keepPreferredViewer);
        
        boolean useLocalTime = UserPreferences.displayTimesInLocalTime();
        useLocalTimeRadioButton.setSelected(useLocalTime);
        useGMTTimeRadioButton.setSelected(!useLocalTime);
        
        dataSourcesHideKnownCheckbox.setSelected(UserPreferences.hideKnownFilesInDataSourcesTree());
        viewsHideKnownCheckbox.setSelected(UserPreferences.hideKnownFilesInViewsTree());
        
        dataSourcesHideSlackCheckbox.setSelected(UserPreferences.hideSlackFilesInDataSourcesTree());
        viewsHideSlackCheckbox.setSelected(UserPreferences.hideSlackFilesInViewsTree());
        
        // Current Case Settings
        boolean caseIsOpen = Case.isCaseOpen();
        currentCaseSettingsPanel.setEnabled(caseIsOpen);
        hideOtherUsersTagsCheckbox.setEnabled(caseIsOpen);
        groupByDataSourceCheckbox.setEnabled(caseIsOpen);
            
        hideOtherUsersTagsCheckbox.setSelected(UserPreferences.showOnlyCurrentUserTags() == false);
        groupByDataSourceCheckbox.setSelected(Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true));
        
        // Current Session Settings
        hideRejectedResultsCheckbox.setSelected(DirectoryTreeTopComponent.getDefault().getShowRejectedResults() == false);
    }
    
    @Override
    public void store() {
        UserPreferences.setKeepPreferredContentViewer(keepCurrentViewerRadioButton.isSelected());
        UserPreferences.setDisplayTimesInLocalTime(useLocalTimeRadioButton.isSelected());
        UserPreferences.setHideKnownFilesInDataSourcesTree(dataSourcesHideKnownCheckbox.isSelected());
        UserPreferences.setHideKnownFilesInViewsTree(viewsHideKnownCheckbox.isSelected());
        UserPreferences.setHideSlackFilesInDataSourcesTree(dataSourcesHideSlackCheckbox.isSelected());
        UserPreferences.setHideSlackFilesInViewsTree(viewsHideSlackCheckbox.isSelected());
        UserPreferences.setShowOnlyCurrentUserTags(hideOtherUsersTagsCheckbox.isSelected() == false);
        
        storeGroupItemsInTreeByDataSource();
        
        DirectoryTreeTopComponent.getDefault().setShowRejectedResults(hideRejectedResultsCheckbox.isSelected() == false);
    }
    
    private void storeGroupItemsInTreeByDataSource() {
        if (Case.isCaseOpen()) {
            /*
             * Only write the value if it has already been previously stored, or
             * if the checkbox is selected. This allows GroupDataSourcesDialog
             * to work.
             */
            if (CasePreferences.getGroupItemsInTreeByDataSource() != null || groupByDataSourceCheckbox.isSelected()) {
                CasePreferences.setGroupItemsInTreeByDataSource(groupByDataSourceCheckbox.isSelected());
            }
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

        viewPreferencesScrollPane = new javax.swing.JScrollPane();
        viewPreferencesPanel = new javax.swing.JPanel();
        globalSettingsPanel = new javax.swing.JPanel();
        selectFileLabel = new javax.swing.JLabel();
        useBestViewerRadioButton = new javax.swing.JRadioButton();
        keepCurrentViewerRadioButton = new javax.swing.JRadioButton();
        hideKnownFilesLabel = new javax.swing.JLabel();
        dataSourcesHideKnownCheckbox = new javax.swing.JCheckBox();
        viewsHideKnownCheckbox = new javax.swing.JCheckBox();
        hideSlackFilesLabel = new javax.swing.JLabel();
        dataSourcesHideSlackCheckbox = new javax.swing.JCheckBox();
        viewsHideSlackCheckbox = new javax.swing.JCheckBox();
        displayTimeLabel = new javax.swing.JLabel();
        useLocalTimeRadioButton = new javax.swing.JRadioButton();
        useGMTTimeRadioButton = new javax.swing.JRadioButton();
        currentCaseSettingsPanel = new javax.swing.JPanel();
        hideOtherUsersTagsCheckbox = new javax.swing.JCheckBox();
        groupByDataSourceCheckbox = new javax.swing.JCheckBox();
        currentSessionSettingsPanel = new javax.swing.JPanel();
        hideRejectedResultsCheckbox = new javax.swing.JCheckBox();

        viewPreferencesScrollPane.setBorder(null);

        globalSettingsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.globalSettingsPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(selectFileLabel, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.selectFileLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(useBestViewerRadioButton, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.useBestViewerRadioButton.text")); // NOI18N
        useBestViewerRadioButton.setToolTipText(org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.useBestViewerRadioButton.toolTipText")); // NOI18N
        useBestViewerRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useBestViewerRadioButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(keepCurrentViewerRadioButton, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.keepCurrentViewerRadioButton.text")); // NOI18N
        keepCurrentViewerRadioButton.setToolTipText(org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.keepCurrentViewerRadioButton.toolTipText")); // NOI18N
        keepCurrentViewerRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                keepCurrentViewerRadioButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(hideKnownFilesLabel, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.hideKnownFilesLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(dataSourcesHideKnownCheckbox, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.dataSourcesHideKnownCheckbox.text")); // NOI18N
        dataSourcesHideKnownCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSourcesHideKnownCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(viewsHideKnownCheckbox, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.viewsHideKnownCheckbox.text")); // NOI18N
        viewsHideKnownCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewsHideKnownCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(hideSlackFilesLabel, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.hideSlackFilesLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(dataSourcesHideSlackCheckbox, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.dataSourcesHideSlackCheckbox.text")); // NOI18N
        dataSourcesHideSlackCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSourcesHideSlackCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(viewsHideSlackCheckbox, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.viewsHideSlackCheckbox.text")); // NOI18N
        viewsHideSlackCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewsHideSlackCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(displayTimeLabel, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.displayTimeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(useLocalTimeRadioButton, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.useLocalTimeRadioButton.text")); // NOI18N
        useLocalTimeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useLocalTimeRadioButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(useGMTTimeRadioButton, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.useGMTTimeRadioButton.text")); // NOI18N
        useGMTTimeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useGMTTimeRadioButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout globalSettingsPanelLayout = new javax.swing.GroupLayout(globalSettingsPanel);
        globalSettingsPanel.setLayout(globalSettingsPanelLayout);
        globalSettingsPanelLayout.setHorizontalGroup(
            globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(displayTimeLabel)
                    .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(keepCurrentViewerRadioButton)
                            .addComponent(useBestViewerRadioButton)
                            .addComponent(useGMTTimeRadioButton)
                            .addComponent(useLocalTimeRadioButton)))
                    .addComponent(selectFileLabel))
                .addGap(18, 18, 18)
                .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(hideKnownFilesLabel)
                    .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(dataSourcesHideSlackCheckbox)
                                    .addComponent(viewsHideSlackCheckbox)))
                            .addComponent(hideSlackFilesLabel))
                        .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                            .addGap(10, 10, 10)
                            .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(dataSourcesHideKnownCheckbox)
                                .addComponent(viewsHideKnownCheckbox)))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        globalSettingsPanelLayout.setVerticalGroup(
            globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, globalSettingsPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                        .addComponent(hideKnownFilesLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(dataSourcesHideKnownCheckbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(viewsHideKnownCheckbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(hideSlackFilesLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(dataSourcesHideSlackCheckbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(viewsHideSlackCheckbox))
                    .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                        .addComponent(selectFileLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(useBestViewerRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(keepCurrentViewerRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(displayTimeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(useLocalTimeRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(useGMTTimeRadioButton))))
        );

        currentCaseSettingsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.currentCaseSettingsPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hideOtherUsersTagsCheckbox, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.hideOtherUsersTagsCheckbox.text")); // NOI18N
        hideOtherUsersTagsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hideOtherUsersTagsCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(groupByDataSourceCheckbox, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.groupByDataSourceCheckbox.text")); // NOI18N
        groupByDataSourceCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                groupByDataSourceCheckboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout currentCaseSettingsPanelLayout = new javax.swing.GroupLayout(currentCaseSettingsPanel);
        currentCaseSettingsPanel.setLayout(currentCaseSettingsPanelLayout);
        currentCaseSettingsPanelLayout.setHorizontalGroup(
            currentCaseSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(currentCaseSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(currentCaseSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(hideOtherUsersTagsCheckbox)
                    .addComponent(groupByDataSourceCheckbox))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        currentCaseSettingsPanelLayout.setVerticalGroup(
            currentCaseSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(currentCaseSettingsPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(hideOtherUsersTagsCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(groupByDataSourceCheckbox)
                .addGap(20, 20, 20))
        );

        currentSessionSettingsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.currentSessionSettingsPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hideRejectedResultsCheckbox, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.hideRejectedResultsCheckbox.text")); // NOI18N
        hideRejectedResultsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hideRejectedResultsCheckboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout currentSessionSettingsPanelLayout = new javax.swing.GroupLayout(currentSessionSettingsPanel);
        currentSessionSettingsPanel.setLayout(currentSessionSettingsPanelLayout);
        currentSessionSettingsPanelLayout.setHorizontalGroup(
            currentSessionSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(currentSessionSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(hideRejectedResultsCheckbox)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        currentSessionSettingsPanelLayout.setVerticalGroup(
            currentSessionSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(currentSessionSettingsPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(hideRejectedResultsCheckbox))
        );

        javax.swing.GroupLayout viewPreferencesPanelLayout = new javax.swing.GroupLayout(viewPreferencesPanel);
        viewPreferencesPanel.setLayout(viewPreferencesPanelLayout);
        viewPreferencesPanelLayout.setHorizontalGroup(
            viewPreferencesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, viewPreferencesPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(viewPreferencesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(currentSessionSettingsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(currentCaseSettingsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(globalSettingsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        viewPreferencesPanelLayout.setVerticalGroup(
            viewPreferencesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(viewPreferencesPanelLayout.createSequentialGroup()
                .addComponent(globalSettingsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(11, 11, 11)
                .addComponent(currentCaseSettingsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(currentSessionSettingsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        viewPreferencesScrollPane.setViewportView(viewPreferencesPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(viewPreferencesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 562, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(viewPreferencesScrollPane)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void useBestViewerRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useBestViewerRadioButtonActionPerformed
        useBestViewerRadioButton.setSelected(true);
        keepCurrentViewerRadioButton.setSelected(false);
        if (immediateUpdates) {
            UserPreferences.setKeepPreferredContentViewer(false);
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_useBestViewerRadioButtonActionPerformed

    private void keepCurrentViewerRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keepCurrentViewerRadioButtonActionPerformed
        useBestViewerRadioButton.setSelected(false);
        keepCurrentViewerRadioButton.setSelected(true);
        if (immediateUpdates) {
            UserPreferences.setKeepPreferredContentViewer(true);
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_keepCurrentViewerRadioButtonActionPerformed

    private void useLocalTimeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useLocalTimeRadioButtonActionPerformed
        useLocalTimeRadioButton.setSelected(true);
        useGMTTimeRadioButton.setSelected(false);
        if (immediateUpdates) {
            UserPreferences.setDisplayTimesInLocalTime(true);
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_useLocalTimeRadioButtonActionPerformed

    private void useGMTTimeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useGMTTimeRadioButtonActionPerformed
        useLocalTimeRadioButton.setSelected(false);
        useGMTTimeRadioButton.setSelected(true);
        if (immediateUpdates) {
            UserPreferences.setDisplayTimesInLocalTime(false);
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_useGMTTimeRadioButtonActionPerformed

    private void dataSourcesHideKnownCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourcesHideKnownCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setHideKnownFilesInDataSourcesTree(dataSourcesHideKnownCheckbox.isSelected());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_dataSourcesHideKnownCheckboxActionPerformed

    private void viewsHideKnownCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewsHideKnownCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setHideKnownFilesInViewsTree(viewsHideKnownCheckbox.isSelected());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_viewsHideKnownCheckboxActionPerformed

    private void dataSourcesHideSlackCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourcesHideSlackCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setHideSlackFilesInDataSourcesTree(dataSourcesHideSlackCheckbox.isSelected());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_dataSourcesHideSlackCheckboxActionPerformed

    private void viewsHideSlackCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewsHideSlackCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setHideSlackFilesInViewsTree(viewsHideSlackCheckbox.isSelected());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_viewsHideSlackCheckboxActionPerformed

    private void hideOtherUsersTagsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hideOtherUsersTagsCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setShowOnlyCurrentUserTags(hideOtherUsersTagsCheckbox.isSelected() == false);
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_hideOtherUsersTagsCheckboxActionPerformed

    private void groupByDataSourceCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_groupByDataSourceCheckboxActionPerformed
        if (immediateUpdates) {
            storeGroupItemsInTreeByDataSource();
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_groupByDataSourceCheckboxActionPerformed

    private void hideRejectedResultsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hideRejectedResultsCheckboxActionPerformed
        if (immediateUpdates) {
            DirectoryTreeTopComponent.getDefault().setShowRejectedResults(hideRejectedResultsCheckbox.isSelected() == false);
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_hideRejectedResultsCheckboxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel currentCaseSettingsPanel;
    private javax.swing.JPanel currentSessionSettingsPanel;
    private javax.swing.JCheckBox dataSourcesHideKnownCheckbox;
    private javax.swing.JCheckBox dataSourcesHideSlackCheckbox;
    private javax.swing.JLabel displayTimeLabel;
    private javax.swing.JPanel globalSettingsPanel;
    private javax.swing.JCheckBox groupByDataSourceCheckbox;
    private javax.swing.JLabel hideKnownFilesLabel;
    private javax.swing.JCheckBox hideOtherUsersTagsCheckbox;
    private javax.swing.JCheckBox hideRejectedResultsCheckbox;
    private javax.swing.JLabel hideSlackFilesLabel;
    private javax.swing.JRadioButton keepCurrentViewerRadioButton;
    private javax.swing.JLabel selectFileLabel;
    private javax.swing.JRadioButton useBestViewerRadioButton;
    private javax.swing.JRadioButton useGMTTimeRadioButton;
    private javax.swing.JRadioButton useLocalTimeRadioButton;
    private javax.swing.JPanel viewPreferencesPanel;
    private javax.swing.JScrollPane viewPreferencesScrollPane;
    private javax.swing.JCheckBox viewsHideKnownCheckbox;
    private javax.swing.JCheckBox viewsHideSlackCheckbox;
    // End of variables declaration//GEN-END:variables
}
