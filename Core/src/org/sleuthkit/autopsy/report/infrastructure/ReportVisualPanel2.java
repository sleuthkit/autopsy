/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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

import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.event.ListDataListener;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Display data on which to allow reports module to report.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class ReportVisualPanel2 extends JPanel {

    private final ReportWizardPanel2 wizPanel;
    private final Map<String, Boolean> tagStates = new LinkedHashMap<>();
    private final List<String> tags = new ArrayList<>();
    final ArtifactSelectionDialog dialog = new ArtifactSelectionDialog((JFrame) WindowManager.getDefault().getMainWindow(), true);
    private Map<BlackboardArtifact.Type, Boolean> artifactStates = new HashMap<>();
    private List<BlackboardArtifact.Type> artifacts = new ArrayList<>();
    private final boolean useCaseSpecificData;
    private TagsListModel tagsModel;
    private TagsListRenderer tagsRenderer;

    /**
     * Creates new form ReportVisualPanel2
     */
    public ReportVisualPanel2(ReportWizardPanel2 wizPanel, boolean useCaseSpecificData, TableReportSettings settings) {
        this.useCaseSpecificData = useCaseSpecificData;
        initComponents();
        initTags();
        initArtifactTypes();
        this.wizPanel = wizPanel;

        this.allResultsRadioButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                tagsList.setEnabled(specificTaggedResultsRadioButton.isSelected());
                selectAllButton.setEnabled(specificTaggedResultsRadioButton.isSelected());
                deselectAllButton.setEnabled(specificTaggedResultsRadioButton.isSelected());
                advancedButton.setEnabled(allResultsRadioButton.isSelected() && useCaseSpecificData);
                updateFinishButton();
            }
        });
        this.allTaggedResultsRadioButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                tagsList.setEnabled(specificTaggedResultsRadioButton.isSelected());
                selectAllButton.setEnabled(specificTaggedResultsRadioButton.isSelected());
                deselectAllButton.setEnabled(specificTaggedResultsRadioButton.isSelected());
                advancedButton.setEnabled(allResultsRadioButton.isSelected() && useCaseSpecificData);
                updateFinishButton();
            }
        });
        this.specificTaggedResultsRadioButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                tagsList.setEnabled(specificTaggedResultsRadioButton.isSelected());
                selectAllButton.setEnabled(specificTaggedResultsRadioButton.isSelected());
                deselectAllButton.setEnabled(specificTaggedResultsRadioButton.isSelected());
                advancedButton.setEnabled(allResultsRadioButton.isSelected() && useCaseSpecificData);
                updateFinishButton();
            }
        });
        
        // enable things based on input settings
        advancedButton.setEnabled(useCaseSpecificData);
        specificTaggedResultsRadioButton.setEnabled(useCaseSpecificData);
        TableReportSettings.TableReportOption type = TableReportSettings.TableReportOption.ALL_RESULTS;
        if (settings != null) {
            type = settings.getSelectedReportOption();
        }
        switch (type) {
            case ALL_TAGGED_RESULTS:
                allTaggedResultsRadioButton.setSelected(true);
                tagsList.setEnabled(false);
                selectAllButton.setEnabled(false);
                deselectAllButton.setEnabled(false);
                break;
            case SPECIFIC_TAGGED_RESULTS:
                specificTaggedResultsRadioButton.setSelected(useCaseSpecificData);
                tagsList.setEnabled(useCaseSpecificData);
                selectAllButton.setEnabled(useCaseSpecificData);
                deselectAllButton.setEnabled(useCaseSpecificData);
                break;
            case ALL_RESULTS:
            default:
                allResultsRadioButton.setSelected(true);
                tagsList.setEnabled(false);
                selectAllButton.setEnabled(false);
                deselectAllButton.setEnabled(false);
                break;
        }
        updateFinishButton();
    }

    // Initialize the list of Tags
    private void initTags() {

        List<TagName> tagNamesInUse = new ArrayList<>();
        // NOTE: we do not want to load tag names that came from persisted settings
        // because there might have been new/additional tag names that have been added 
        // by user. We should read tag names from the database each time.
        try {
            // only try to load tag names if we are displaying case specific data, otherwise
            // we will be displaying case specific data in command line wizard if there is 
            // a case open in the background
            if (useCaseSpecificData) {
                // get tags and artifact types from current case
                tagNamesInUse = Case.getCurrentCaseThrows().getServices().getTagsManager().getTagNamesInUse();
            }
        } catch (TskCoreException | NoCurrentCaseException ex) {
            Logger.getLogger(ReportVisualPanel2.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
            return;
        }

        for (TagName tagName : tagNamesInUse) {
            String notableString = tagName.getTagType() == TskData.TagType.BAD ? TagsManager.getNotableTagLabel() : "";
            tagStates.put(tagName.getDisplayName() + notableString, Boolean.FALSE);
        }
        tags.addAll(tagStates.keySet());

        tagsModel = new TagsListModel();
        tagsRenderer = new TagsListRenderer();
        tagsList.setModel(tagsModel);
        tagsList.setCellRenderer(tagsRenderer);
        tagsList.setVisibleRowCount(-1);

        // Add the ability to enable and disable Tag checkboxes to the list
        tagsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent evt) {
                if (!specificTaggedResultsRadioButton.isSelected()) {
                    return;
                }
                int index = tagsList.locationToIndex(evt.getPoint());
                if (index < tagsModel.getSize() && index >= 0) {
                    String value = tagsModel.getElementAt(index);
                    tagStates.put(value, !tagStates.get(value));
                    tagsList.repaint();
                    updateFinishButton();
                }
            }
        });
    }

    // Initialize the list of Artifacts
    @SuppressWarnings("deprecation")
    private void initArtifactTypes() {

        // only try to load existing artifact types if we are displaying case 
        // specific data, otherwise there may not be a case open.
        if (!useCaseSpecificData) {
            return;
        }

        try {
            Case openCase = Case.getCurrentCaseThrows();
            ArrayList<BlackboardArtifact.Type> doNotReport = new ArrayList<>();
            doNotReport.add(new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO));
            doNotReport.add(new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_TOOL_OUTPUT)); // output is too unstructured for table review
            doNotReport.add(new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT));
            doNotReport.add(new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_TL_EVENT));
            // get artifact types that exist in the current case
            artifacts = openCase.getSleuthkitCase().getArtifactTypesInUse();

            artifacts.removeAll(doNotReport);

            artifactStates = new HashMap<>();
            for (BlackboardArtifact.Type type : artifacts) {
                artifactStates.put(type, Boolean.TRUE);
            }
        } catch (TskCoreException | NoCurrentCaseException ex) {
            Logger.getLogger(ReportVisualPanel2.class.getName()).log(Level.SEVERE, "Error getting list of artifacts in use: " + ex.getLocalizedMessage(), ex); //NON-NLS
        }
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "ReportVisualPanel2.getName.text");
    }

    /**
     * Gets the enabled/disabled state of all artifacts
     *
     * @return the enabled/disabled state of all Artifacts
     */
    Map<BlackboardArtifact.Type, Boolean> getArtifactStates() {
        return artifactStates;
    }

    /**
     * @return the enabled/disabled state of all Tags
     */
    Map<String, Boolean> getTagStates() {
        return tagStates;
    }

    /**
     * Are any tags selected?
     *
     * @return True if any tags are selected; otherwise false.
     */
    private boolean areTagsSelected() {
        boolean result = false;
        for (Entry<String, Boolean> entry : tagStates.entrySet()) {
            if (entry.getValue()) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * Set the Finish button as either enabled or disabled depending on the UI
     * component selections.
     */
    private void updateFinishButton() {
        if (specificTaggedResultsRadioButton.isSelected()) {
            wizPanel.setFinish(areTagsSelected());
        } else {
            wizPanel.setFinish(true);
        }
    }

    /**
     * @return true if the Specific Tags radio button is selected, false
     * otherwise
     */
    TableReportSettings.TableReportOption getSelectedReportType() {
        if (allTaggedResultsRadioButton.isSelected()) {
            return TableReportSettings.TableReportOption.ALL_TAGGED_RESULTS;
        } else if (specificTaggedResultsRadioButton.isSelected()) {
            return TableReportSettings.TableReportOption.SPECIFIC_TAGGED_RESULTS;
        }
        return TableReportSettings.TableReportOption.ALL_RESULTS;
    }

    /**
     * Set all tagged results as either selected or unselected.
     *
     * @param selected Should all tagged results be selected?
     */
    void setAllTaggedResultsSelected(boolean selected) {
        for (String tag : tags) {
            tagStates.put(tag, (selected ? Boolean.TRUE : Boolean.FALSE));
        }
        tagsList.repaint();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        optionsButtonGroup = new javax.swing.ButtonGroup();
        specificTaggedResultsRadioButton = new javax.swing.JRadioButton();
        allResultsRadioButton = new javax.swing.JRadioButton();
        dataLabel = new javax.swing.JLabel();
        selectAllButton = new javax.swing.JButton();
        deselectAllButton = new javax.swing.JButton();
        tagsScrollPane = new javax.swing.JScrollPane();
        tagsList = new javax.swing.JList<>();
        advancedButton = new javax.swing.JButton();
        allTaggedResultsRadioButton = new javax.swing.JRadioButton();

        setPreferredSize(new java.awt.Dimension(834, 374));

        optionsButtonGroup.add(specificTaggedResultsRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(specificTaggedResultsRadioButton, org.openide.util.NbBundle.getMessage(ReportVisualPanel2.class, "ReportVisualPanel2.specificTaggedResultsRadioButton.text")); // NOI18N

        optionsButtonGroup.add(allResultsRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(allResultsRadioButton, org.openide.util.NbBundle.getMessage(ReportVisualPanel2.class, "ReportVisualPanel2.allResultsRadioButton.text")); // NOI18N
        allResultsRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allResultsRadioButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(dataLabel, org.openide.util.NbBundle.getMessage(ReportVisualPanel2.class, "ReportVisualPanel2.dataLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(selectAllButton, org.openide.util.NbBundle.getMessage(ReportVisualPanel2.class, "ReportVisualPanel2.selectAllButton.text")); // NOI18N
        selectAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deselectAllButton, org.openide.util.NbBundle.getMessage(ReportVisualPanel2.class, "ReportVisualPanel2.deselectAllButton.text")); // NOI18N
        deselectAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deselectAllButtonActionPerformed(evt);
            }
        });

        tagsList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tagsList.setLayoutOrientation(javax.swing.JList.VERTICAL_WRAP);
        tagsScrollPane.setViewportView(tagsList);

        org.openide.awt.Mnemonics.setLocalizedText(advancedButton, org.openide.util.NbBundle.getMessage(ReportVisualPanel2.class, "ReportVisualPanel2.advancedButton.text")); // NOI18N
        advancedButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                advancedButtonActionPerformed(evt);
            }
        });

        optionsButtonGroup.add(allTaggedResultsRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(allTaggedResultsRadioButton, org.openide.util.NbBundle.getMessage(ReportVisualPanel2.class, "ReportVisualPanel2.allTaggedResultsRadioButton.text")); // NOI18N
        allTaggedResultsRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allTaggedResultsRadioButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(allTaggedResultsRadioButton)
                    .addComponent(dataLabel)
                    .addComponent(allResultsRadioButton)
                    .addComponent(specificTaggedResultsRadioButton)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(advancedButton)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(tagsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 699, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(deselectAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(selectAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {deselectAllButton, selectAllButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(dataLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(allResultsRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(allTaggedResultsRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(specificTaggedResultsRadioButton)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(selectAllButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(deselectAllButton)
                        .addGap(136, 136, 136))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(1, 1, 1)
                        .addComponent(tagsScrollPane)
                        .addGap(5, 5, 5)
                        .addComponent(advancedButton)))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void selectAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllButtonActionPerformed
        setAllTaggedResultsSelected(true);
        wizPanel.setFinish(true);
    }//GEN-LAST:event_selectAllButtonActionPerformed

    private void deselectAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deselectAllButtonActionPerformed
        setAllTaggedResultsSelected(false);
        wizPanel.setFinish(false);
    }//GEN-LAST:event_deselectAllButtonActionPerformed

    private void advancedButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_advancedButtonActionPerformed
        artifactStates = dialog.display();
    }//GEN-LAST:event_advancedButtonActionPerformed

    private void allResultsRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allResultsRadioButtonActionPerformed
        setAllTaggedResultsSelected(false);
    }//GEN-LAST:event_allResultsRadioButtonActionPerformed

    private void allTaggedResultsRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allTaggedResultsRadioButtonActionPerformed
        setAllTaggedResultsSelected(true);
    }//GEN-LAST:event_allTaggedResultsRadioButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton advancedButton;
    private javax.swing.JRadioButton allResultsRadioButton;
    private javax.swing.JRadioButton allTaggedResultsRadioButton;
    private javax.swing.JLabel dataLabel;
    private javax.swing.JButton deselectAllButton;
    private javax.swing.ButtonGroup optionsButtonGroup;
    private javax.swing.JButton selectAllButton;
    private javax.swing.JRadioButton specificTaggedResultsRadioButton;
    private javax.swing.JList<String> tagsList;
    private javax.swing.JScrollPane tagsScrollPane;
    // End of variables declaration//GEN-END:variables

    private class TagsListModel implements ListModel<String> {

        @Override
        public int getSize() {
            return tags.size();
        }

        @Override
        public String getElementAt(int index) {
            return tags.get(index);
        }

        @Override
        public void addListDataListener(ListDataListener l) {
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
        }
    }

    // Render the Tags as JCheckboxes
    private class TagsListRenderer extends JCheckBox implements ListCellRenderer<String> {

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value != null) {
                setEnabled(list.isEnabled());
                setSelected(tagStates.get(value));
                setFont(list.getFont());
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                setText(value);
                return this;
            }
            return new JLabel();
        }
    }
}
