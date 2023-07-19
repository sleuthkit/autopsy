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

import java.beans.PropertyChangeEvent;
import java.util.EnumSet;
import java.util.Objects;
import java.util.TimeZone;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import org.netbeans.spi.options.OptionsPanelController;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;

/**
 * Panel for configuring view preferences.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class ViewPreferencesPanel extends JPanel implements OptionsPanel {

    private final boolean immediateUpdates;

    /**
     * Creates new form ViewPreferencesPanel
     *
     * @param immediateUpdates If true, value changes will be persisted at the
     * moment they occur.
     */
    public ViewPreferencesPanel(boolean immediateUpdates) {
        initComponents();
        this.immediateUpdates = immediateUpdates;
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            //disable when case is closed, enable when case is open
            currentCaseSettingsPanel.setEnabled(evt.getNewValue() != null);
            radioGroupByDataType.setEnabled(evt.getNewValue() != null);
            radioGroupByPersonHost.setEnabled(evt.getNewValue() != null);
        });
        this.timeZoneList.setListData(TimeZoneUtils.createTimeZoneList().stream().toArray(String[]::new));

        // Disable manual editing of max results spinner
        ((JSpinner.DefaultEditor) maxResultsSpinner.getEditor()).getTextField().setEditable(false);
    }

    @Override
    public void load() {
        // Global Settings
        boolean keepPreferredViewer = UserPreferences.keepPreferredContentViewer();
        keepCurrentViewerRadioButton.setSelected(keepPreferredViewer);
        useBestViewerRadioButton.setSelected(!keepPreferredViewer);

        boolean useLocalTime = UserPreferences.displayTimesInLocalTime();
        timeZoneList.setEnabled(!useLocalTime);
        timeZoneList.setSelectedValue(TimeZoneUtils.createTimeZoneString(TimeZone.getTimeZone(UserPreferences.getTimeZoneForDisplays())), true);
        useLocalTimeRadioButton.setSelected(useLocalTime);
        useAnotherTimeRadioButton.setSelected(!useLocalTime);

        dataSourcesHideKnownCheckbox.setSelected(UserPreferences.hideKnownFilesInDataSourcesTree());
        viewsHideKnownCheckbox.setSelected(UserPreferences.hideKnownFilesInViewsTree());

        dataSourcesHideSlackCheckbox.setSelected(UserPreferences.hideSlackFilesInDataSourcesTree());
        viewsHideSlackCheckbox.setSelected(UserPreferences.hideSlackFilesInViewsTree());

        scoColumnsCheckbox.setSelected(UserPreferences.getHideSCOColumns());

        hideOtherUsersTagsCheckbox.setSelected(UserPreferences.showOnlyCurrentUserTags());
        fileNameTranslationColumnCheckbox.setSelected(UserPreferences.displayTranslatedFileNames());

        TextTranslationService tts = TextTranslationService.getInstance();
        fileNameTranslationColumnCheckbox.setEnabled(tts.hasProvider());

        maxResultsSpinner.setValue(UserPreferences.getResultsTablePageSize());

        // Current Case Settings
        boolean caseIsOpen = Case.isCaseOpen();
        currentCaseSettingsPanel.setEnabled(caseIsOpen);
        radioGroupByDataType.setEnabled(caseIsOpen);
        radioGroupByPersonHost.setEnabled(caseIsOpen);

        boolean groupByHostPersonTypes = Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true);
        radioGroupByDataType.setSelected(!groupByHostPersonTypes);
        radioGroupByPersonHost.setSelected(groupByHostPersonTypes);
            
        if (!caseIsOpen) {
            radioGroupByDataType.setEnabled(false);
            radioGroupByPersonHost.setEnabled(false);
        }

        // Current Session Settings
        hideRejectedResultsCheckbox.setSelected(DirectoryTreeTopComponent.getDefault().getShowRejectedResults() == false);
    }

    @Override
    public void store() {
        UserPreferences.setKeepPreferredContentViewer(keepCurrentViewerRadioButton.isSelected());
        UserPreferences.setDisplayTimesInLocalTime(useLocalTimeRadioButton.isSelected());
        if (useAnotherTimeRadioButton.isSelected()) {
            UserPreferences.setTimeZoneForDisplays(timeZoneList.getSelectedValue().substring(11).trim());
        }
        UserPreferences.setHideKnownFilesInDataSourcesTree(dataSourcesHideKnownCheckbox.isSelected());
        UserPreferences.setHideKnownFilesInViewsTree(viewsHideKnownCheckbox.isSelected());
        UserPreferences.setHideSlackFilesInDataSourcesTree(dataSourcesHideSlackCheckbox.isSelected());
        UserPreferences.setHideSlackFilesInViewsTree(viewsHideSlackCheckbox.isSelected());
        UserPreferences.setShowOnlyCurrentUserTags(hideOtherUsersTagsCheckbox.isSelected());
        UserPreferences.setHideSCOColumns(scoColumnsCheckbox.isSelected());
        UserPreferences.setDisplayTranslatedFileNames(fileNameTranslationColumnCheckbox.isSelected());
        UserPreferences.setResultsTablePageSize((int) maxResultsSpinner.getValue());

        storeGroupItemsInTreeByDataSource();

        DirectoryTreeTopComponent.getDefault().setShowRejectedResults(hideRejectedResultsCheckbox.isSelected() == false);
    }

    /**
     * Store the 'radioGroupByPersonHost' value.
     *
     * Note: The value will not be stored if the value hasn't previously been
     * stored and the radio button isn't selected. This is so
     * GroupDataSourcesDialog can prompt the user for this in the event the
     * value hasn't been initialized.
     */
    private void storeGroupItemsInTreeByDataSource() {

        if (Case.isCaseOpen() && (CasePreferences.getGroupItemsInTreeByDataSource() != null || radioGroupByPersonHost.isSelected())) {
            CasePreferences.setGroupItemsInTreeByDataSource(radioGroupByPersonHost.isSelected());
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

        curCaseRadioGroup = new javax.swing.ButtonGroup();
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
        useAnotherTimeRadioButton = new javax.swing.JRadioButton();
        hideOtherUsersTagsCheckbox = new javax.swing.JCheckBox();
        hideOtherUsersTagsLabel = new javax.swing.JLabel();
        scoColumnsLabel = new javax.swing.JLabel();
        scoColumnsCheckbox = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        timeZoneList = new javax.swing.JList<>();
        translateTextLabel = new javax.swing.JLabel();
        scoColumnsWrapAroundText = new javax.swing.JLabel();
        fileNameTranslationColumnCheckbox = new javax.swing.JCheckBox();
        maxResultsLabel = new javax.swing.JLabel();
        maxResultsSpinner = new javax.swing.JSpinner();
        currentCaseSettingsPanel = new javax.swing.JPanel();
        radioGroupByPersonHost = new javax.swing.JRadioButton();
        radioGroupByDataType = new javax.swing.JRadioButton();
        currentSessionSettingsPanel = new javax.swing.JPanel();
        hideRejectedResultsCheckbox = new javax.swing.JCheckBox();

        setMinimumSize(new java.awt.Dimension(727, 520));
        setPreferredSize(new java.awt.Dimension(727, 520));

        viewPreferencesScrollPane.setBorder(null);
        viewPreferencesScrollPane.setMinimumSize(new java.awt.Dimension(727, 500));
        viewPreferencesScrollPane.setPreferredSize(new java.awt.Dimension(727, 500));

        viewPreferencesPanel.setPreferredSize(new java.awt.Dimension(727, 492));

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

        org.openide.awt.Mnemonics.setLocalizedText(useAnotherTimeRadioButton, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.useAnotherTimeRadioButton.text")); // NOI18N
        useAnotherTimeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useAnotherTimeRadioButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(hideOtherUsersTagsCheckbox, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.hideOtherUsersTagsCheckbox.text")); // NOI18N
        hideOtherUsersTagsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hideOtherUsersTagsCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(hideOtherUsersTagsLabel, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.hideOtherUsersTagsLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(scoColumnsLabel, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.scoColumnsLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(scoColumnsCheckbox, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.scoColumnsCheckbox.text")); // NOI18N
        scoColumnsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scoColumnsCheckboxActionPerformed(evt);
            }
        });

        jScrollPane1.setMaximumSize(new java.awt.Dimension(150, 130));
        jScrollPane1.setPreferredSize(new java.awt.Dimension(150, 130));

        timeZoneList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                timeZoneListValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(timeZoneList);

        org.openide.awt.Mnemonics.setLocalizedText(translateTextLabel, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.translateTextLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(scoColumnsWrapAroundText, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.scoColumnsWrapAroundText.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(fileNameTranslationColumnCheckbox, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.fileNameTranslationColumnCheckbox.text")); // NOI18N
        fileNameTranslationColumnCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileNameTranslationColumnCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(maxResultsLabel, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.maxResultsLabel.text")); // NOI18N
        maxResultsLabel.setToolTipText(org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.maxResultsLabel.toolTipText")); // NOI18N

        maxResultsSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 50000, 10000));
        maxResultsSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                maxResultsSpinnerStateChanged(evt);
            }
        });

        javax.swing.GroupLayout globalSettingsPanelLayout = new javax.swing.GroupLayout(globalSettingsPanel);
        globalSettingsPanel.setLayout(globalSettingsPanelLayout);
        globalSettingsPanelLayout.setHorizontalGroup(
            globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                        .addComponent(maxResultsLabel)
                        .addGap(13, 13, 13)
                        .addComponent(maxResultsSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                        .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(viewsHideKnownCheckbox)
                                    .addComponent(dataSourcesHideKnownCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(hideOtherUsersTagsCheckbox)
                                    .addComponent(viewsHideSlackCheckbox)
                                    .addComponent(hideOtherUsersTagsLabel)
                                    .addComponent(scoColumnsLabel)
                                    .addComponent(scoColumnsCheckbox)
                                    .addComponent(dataSourcesHideSlackCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(scoColumnsWrapAroundText)))
                            .addComponent(hideSlackFilesLabel)
                            .addComponent(hideKnownFilesLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 289, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(useLocalTimeRadioButton)
                                    .addComponent(keepCurrentViewerRadioButton)
                                    .addComponent(fileNameTranslationColumnCheckbox)
                                    .addComponent(useAnotherTimeRadioButton)
                                    .addComponent(useBestViewerRadioButton)
                                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 246, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addComponent(displayTimeLabel)
                            .addComponent(selectFileLabel)
                            .addComponent(translateTextLabel))))
                .addContainerGap(94, Short.MAX_VALUE))
        );
        globalSettingsPanelLayout.setVerticalGroup(
            globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(globalSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
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
                        .addComponent(viewsHideSlackCheckbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(hideOtherUsersTagsLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(hideOtherUsersTagsCheckbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(scoColumnsLabel)
                        .addGap(3, 3, 3)
                        .addComponent(scoColumnsCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scoColumnsWrapAroundText))
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
                        .addComponent(useAnotherTimeRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(translateTextLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(fileNameTranslationColumnCheckbox)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(globalSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(maxResultsLabel)
                    .addComponent(maxResultsSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        currentCaseSettingsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.currentCaseSettingsPanel.border.title"))); // NOI18N

        curCaseRadioGroup.add(radioGroupByPersonHost);
        org.openide.awt.Mnemonics.setLocalizedText(radioGroupByPersonHost, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.radioGroupByPersonHost.text")); // NOI18N
        radioGroupByPersonHost.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                radioGroupByPersonHostActionPerformed(evt);
            }
        });

        curCaseRadioGroup.add(radioGroupByDataType);
        org.openide.awt.Mnemonics.setLocalizedText(radioGroupByDataType, org.openide.util.NbBundle.getMessage(ViewPreferencesPanel.class, "ViewPreferencesPanel.radioGroupByDataType.text")); // NOI18N
        radioGroupByDataType.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                radioGroupByDataTypeActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout currentCaseSettingsPanelLayout = new javax.swing.GroupLayout(currentCaseSettingsPanel);
        currentCaseSettingsPanel.setLayout(currentCaseSettingsPanelLayout);
        currentCaseSettingsPanelLayout.setHorizontalGroup(
            currentCaseSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(currentCaseSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(currentCaseSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(radioGroupByPersonHost)
                    .addComponent(radioGroupByDataType))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        currentCaseSettingsPanelLayout.setVerticalGroup(
            currentCaseSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(currentCaseSettingsPanelLayout.createSequentialGroup()
                .addComponent(radioGroupByDataType)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(radioGroupByPersonHost)
                .addGap(0, 6, Short.MAX_VALUE))
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(currentCaseSettingsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(currentSessionSettingsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        viewPreferencesScrollPane.setViewportView(viewPreferencesPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(viewPreferencesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(viewPreferencesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 520, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void hideRejectedResultsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hideRejectedResultsCheckboxActionPerformed
        if (immediateUpdates) {
            DirectoryTreeTopComponent.getDefault().setShowRejectedResults(hideRejectedResultsCheckbox.isSelected() == false);
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_hideRejectedResultsCheckboxActionPerformed

    private void maxResultsSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_maxResultsSpinnerStateChanged
        if (immediateUpdates) {
            UserPreferences.setResultsTablePageSize((int) maxResultsSpinner.getValue());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_maxResultsSpinnerStateChanged

    private void fileNameTranslationColumnCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileNameTranslationColumnCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setDisplayTranslatedFileNames(fileNameTranslationColumnCheckbox.isSelected());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_fileNameTranslationColumnCheckboxActionPerformed

    private void timeZoneListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_timeZoneListValueChanged
        if (immediateUpdates && useAnotherTimeRadioButton.isSelected()) {
            UserPreferences.setTimeZoneForDisplays(timeZoneList.getSelectedValue().substring(11).trim());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_timeZoneListValueChanged

    private void scoColumnsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scoColumnsCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setHideSCOColumns(scoColumnsCheckbox.isSelected());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_scoColumnsCheckboxActionPerformed

    private void hideOtherUsersTagsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hideOtherUsersTagsCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setShowOnlyCurrentUserTags(hideOtherUsersTagsCheckbox.isSelected());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_hideOtherUsersTagsCheckboxActionPerformed

    private void useAnotherTimeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useAnotherTimeRadioButtonActionPerformed
        useLocalTimeRadioButton.setSelected(false);
        useAnotherTimeRadioButton.setSelected(true);
        timeZoneList.setEnabled(true);
        if (immediateUpdates) {
            UserPreferences.setDisplayTimesInLocalTime(false);
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_useAnotherTimeRadioButtonActionPerformed

    private void useLocalTimeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useLocalTimeRadioButtonActionPerformed
        useLocalTimeRadioButton.setSelected(true);
        useAnotherTimeRadioButton.setSelected(false);
        timeZoneList.setEnabled(false);
        if (immediateUpdates) {
            UserPreferences.setDisplayTimesInLocalTime(true);
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_useLocalTimeRadioButtonActionPerformed

    private void viewsHideSlackCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewsHideSlackCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setHideSlackFilesInViewsTree(viewsHideSlackCheckbox.isSelected());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_viewsHideSlackCheckboxActionPerformed

    private void dataSourcesHideSlackCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourcesHideSlackCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setHideSlackFilesInDataSourcesTree(dataSourcesHideSlackCheckbox.isSelected());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_dataSourcesHideSlackCheckboxActionPerformed

    private void viewsHideKnownCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewsHideKnownCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setHideKnownFilesInViewsTree(viewsHideKnownCheckbox.isSelected());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_viewsHideKnownCheckboxActionPerformed

    private void dataSourcesHideKnownCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourcesHideKnownCheckboxActionPerformed
        if (immediateUpdates) {
            UserPreferences.setHideKnownFilesInDataSourcesTree(dataSourcesHideKnownCheckbox.isSelected());
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_dataSourcesHideKnownCheckboxActionPerformed

    private void keepCurrentViewerRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keepCurrentViewerRadioButtonActionPerformed
        useBestViewerRadioButton.setSelected(false);
        keepCurrentViewerRadioButton.setSelected(true);
        if (immediateUpdates) {
            UserPreferences.setKeepPreferredContentViewer(true);
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_keepCurrentViewerRadioButtonActionPerformed

    private void useBestViewerRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useBestViewerRadioButtonActionPerformed
        useBestViewerRadioButton.setSelected(true);
        keepCurrentViewerRadioButton.setSelected(false);
        if (immediateUpdates) {
            UserPreferences.setKeepPreferredContentViewer(false);
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_useBestViewerRadioButtonActionPerformed

    private void radioGroupByDataTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_radioGroupByDataTypeActionPerformed
        if (immediateUpdates) {
            storeGroupItemsInTreeByDataSource();
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_radioGroupByDataTypeActionPerformed

    private void radioGroupByPersonHostActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_radioGroupByPersonHostActionPerformed
        if (immediateUpdates) {
            storeGroupItemsInTreeByDataSource();
        } else {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_radioGroupByPersonHostActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup curCaseRadioGroup;
    private javax.swing.JPanel currentCaseSettingsPanel;
    private javax.swing.JPanel currentSessionSettingsPanel;
    private javax.swing.JCheckBox dataSourcesHideKnownCheckbox;
    private javax.swing.JCheckBox dataSourcesHideSlackCheckbox;
    private javax.swing.JLabel displayTimeLabel;
    private javax.swing.JCheckBox fileNameTranslationColumnCheckbox;
    private javax.swing.JPanel globalSettingsPanel;
    private javax.swing.JLabel hideKnownFilesLabel;
    private javax.swing.JCheckBox hideOtherUsersTagsCheckbox;
    private javax.swing.JLabel hideOtherUsersTagsLabel;
    private javax.swing.JCheckBox hideRejectedResultsCheckbox;
    private javax.swing.JLabel hideSlackFilesLabel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JRadioButton keepCurrentViewerRadioButton;
    private javax.swing.JLabel maxResultsLabel;
    private javax.swing.JSpinner maxResultsSpinner;
    private javax.swing.JRadioButton radioGroupByDataType;
    private javax.swing.JRadioButton radioGroupByPersonHost;
    private javax.swing.JCheckBox scoColumnsCheckbox;
    private javax.swing.JLabel scoColumnsLabel;
    private javax.swing.JLabel scoColumnsWrapAroundText;
    private javax.swing.JLabel selectFileLabel;
    private javax.swing.JList<String> timeZoneList;
    private javax.swing.JLabel translateTextLabel;
    private javax.swing.JRadioButton useAnotherTimeRadioButton;
    private javax.swing.JRadioButton useBestViewerRadioButton;
    private javax.swing.JRadioButton useLocalTimeRadioButton;
    private javax.swing.JPanel viewPreferencesPanel;
    private javax.swing.JScrollPane viewPreferencesScrollPane;
    private javax.swing.JCheckBox viewsHideKnownCheckbox;
    private javax.swing.JCheckBox viewsHideSlackCheckbox;
    // End of variables declaration//GEN-END:variables
}
