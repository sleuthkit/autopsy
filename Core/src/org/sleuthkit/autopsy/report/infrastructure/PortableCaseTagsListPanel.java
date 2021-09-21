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
import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.event.ListDataListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * The subpanel showing the tags in use.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class PortableCaseTagsListPanel extends javax.swing.JPanel {

    private List<TagName> tagNames;
    private final Map<String, Boolean> tagNameSelections = new LinkedHashMap<>();
    private final TagNamesListModel tagsNamesListModel = new TagNamesListModel();
    private final TagsNamesListCellRenderer tagsNamesRenderer = new TagsNamesListCellRenderer();
    private final Map<String, Long> tagCounts = new HashMap<>();
    
    private final ReportWizardPortableCaseOptionsPanel wizPanel;
    private final PortableCaseReportModuleSettings settings;
    private final boolean useCaseSpecificData;
    
    /**
     * Creates new form PortableCaseListPanel
     */
    PortableCaseTagsListPanel(ReportWizardPortableCaseOptionsPanel wizPanel, PortableCaseReportModuleSettings options, boolean useCaseSpecificData) {
        this.wizPanel = wizPanel;
        this.useCaseSpecificData = useCaseSpecificData;
        this.settings = options;
        initComponents();
        customizeComponents();
        // update tag selection
        jAllTagsCheckBox.setSelected(settings.areAllTagsSelected());
        tagNamesListBox.setEnabled(!jAllTagsCheckBox.isSelected());
        selectButton.setEnabled(!jAllTagsCheckBox.isSelected());
        deselectButton.setEnabled(!jAllTagsCheckBox.isSelected());
        selectAllTags(jAllTagsCheckBox.isSelected());

        this.jAllTagsCheckBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                tagNamesListBox.setEnabled(!jAllTagsCheckBox.isSelected());
                selectButton.setEnabled(!jAllTagsCheckBox.isSelected());
                deselectButton.setEnabled(!jAllTagsCheckBox.isSelected());
                selectAllTags(jAllTagsCheckBox.isSelected());
            }
        });
    }

    @NbBundle.Messages({
        "PortableCaseTagsListPanel.error.errorTitle=Error getting tag names for case",
        "PortableCaseTagsListPanel.error.noOpenCase=There is no case open",
        "PortableCaseTagsListPanel.error.errorLoadingTags=Error loading tags",  
    })    
    private void customizeComponents() {
        // Get the tag names in use for the current case.
        tagNames = new ArrayList<>();
        Map<Long, Long> tagCountsByID = new HashMap<>();
        try {
            // only try to load tag names if we are displaying case specific data, otherwise
            // we will be displaying case specific data in command line wizard if there is 
            // a case open in the background
            if (useCaseSpecificData) {
                tagNames = Case.getCurrentCaseThrows().getServices().getTagsManager().getTagNamesInUse();

                // Get the counts for each tag ID
                String query = "tag_name_id, count(1) AS tag_count "
                        + "FROM ("
                        + "SELECT tag_name_id FROM content_tags UNION ALL SELECT tag_name_id FROM blackboard_artifact_tags"
                        + ") tag_ids GROUP BY tag_name_id"; // NON-NLS
                GetTagCountsCallback callback = new GetTagCountsCallback();
                Case.getCurrentCaseThrows().getSleuthkitCase().getCaseDbAccessManager().select(query, callback);
                tagCountsByID = callback.getTagCountMap();
            }

            // Mark the tag names as unselected. Note that tagNameSelections is a
            // LinkedHashMap so that order is preserved and the tagNames and tagNameSelections
            // containers are "parallel" containers.
            for (TagName tagName : tagNames) {
                tagNameSelections.put(tagName.getDisplayName(), Boolean.FALSE);
                if (tagCountsByID.containsKey(tagName.getId())) {
                    tagCounts.put(tagName.getDisplayName(), tagCountsByID.get(tagName.getId()));
                } else {
                    tagCounts.put(tagName.getDisplayName(), 0L);
                }
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(ReportWizardPortableCaseOptionsVisualPanel.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); // NON-NLS
        } catch (NoCurrentCaseException ex) {
            // There may not be a case open when configuring report modules for Command Line execution
            Logger.getLogger(ReportWizardPortableCaseOptionsVisualPanel.class.getName()).log(Level.WARNING, "Exception while getting open case.", ex); // NON-NLS
        }

        // Set up the tag names JList component to be a collection of check boxes
        // for selecting tag names. The mouse click listener updates tagNameSelections
        // to reflect user choices.
        tagNamesListBox.setModel(tagsNamesListModel);
        tagNamesListBox.setCellRenderer(tagsNamesRenderer);
        tagNamesListBox.setVisibleRowCount(-1);
        tagNamesListBox.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent evt) {
                JList<?> list = (JList) evt.getSource();
                int index = list.locationToIndex(evt.getPoint());
                if (index > -1) {
                    String value = tagsNamesListModel.getElementAt(index);
                    tagNameSelections.put(value, !tagNameSelections.get(value));
                    list.repaint();
                    updateTagList();
                }
            }
        });
    }
    
    /**
     * Save the current selections and enabled/disable the finish button as needed.
     */
    private void updateTagList() {
        settings.updateTagNames(getSelectedTagNames());
        settings.setAllTagsSelected(jAllTagsCheckBox.isSelected());
        wizPanel.setFinish(settings.isValid());
    }    
    
    /**
     * This class is a list model for the tag names JList component.
     */
    private class TagNamesListModel implements ListModel<String> {

        @Override
        public int getSize() {
            return tagNames.size();
        }

        @Override
        public String getElementAt(int index) {
            return tagNames.get(index).getDisplayName();
        }

        @Override
        public void addListDataListener(ListDataListener l) {
            // Nothing to do
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
            // Nothing to do
        }
    }

    /**
     * This class renders the items in the tag names JList component as JCheckbox components.
     */
    private class TagsNamesListCellRenderer extends JCheckBox implements ListCellRenderer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value != null) {
                setEnabled(list.isEnabled());
                setSelected(tagNameSelections.get(value));
                setFont(list.getFont());
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                String text = value + " (" + tagCounts.get(value) + ")";
                setText(text);
                setToolTipText(text);
                return this;
            }
            return new JLabel();
        }
    }      
    
    /**
     * Gets the subset of the tag names in use selected by the user.
     *
     * @return A list, possibly empty, of TagName data transfer objects (DTOs).
     */
    private List<TagName> getSelectedTagNames() {
        List<TagName> selectedTagNames = new ArrayList<>();
        for (TagName tagName : tagNames) {
            if (tagNameSelections.get(tagName.getDisplayName())) {
                selectedTagNames.add(tagName);
            }
        }
        return selectedTagNames;
    }    
    
    /**
     * Processes the result sets from the tag count query.
     */    
    static class GetTagCountsCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

        private static final Logger logger = Logger.getLogger(GetTagCountsCallback.class.getName());
        private final Map<Long, Long> tagCounts = new HashMap<>();
        
        @Override
        public void process(ResultSet rs) {
            try {
                while (rs.next()) {
                    try {
                        Long tagCount = rs.getLong("tag_count"); // NON-NLS
                        Long tagID = rs.getLong("tag_name_id"); // NON-NLS

                        tagCounts.put(tagID, tagCount);
                        
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "Unable to get data_source_obj_id or value from result set", ex); // NON-NLS
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to get next result for values by datasource", ex); // NON-NLS
            }
        }   
        
        /**
         * Get a map of the tag name IDs to the number of usages.
         * 
         * @return Map of tag name ID to number of items tagged with it
         */
        Map<Long, Long> getTagCountMap() {
            return tagCounts;
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

        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        tagNamesListBox = new javax.swing.JList<>();
        descLabel = new javax.swing.JLabel();
        selectButton = new javax.swing.JButton();
        deselectButton = new javax.swing.JButton();
        jAllTagsCheckBox = new javax.swing.JCheckBox();

        jScrollPane1.setViewportView(tagNamesListBox);

        org.openide.awt.Mnemonics.setLocalizedText(descLabel, org.openide.util.NbBundle.getMessage(PortableCaseTagsListPanel.class, "PortableCaseTagsListPanel.descLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(selectButton, org.openide.util.NbBundle.getMessage(PortableCaseTagsListPanel.class, "PortableCaseTagsListPanel.selectButton.text")); // NOI18N
        selectButton.setMaximumSize(new java.awt.Dimension(87, 23));
        selectButton.setMinimumSize(new java.awt.Dimension(87, 23));
        selectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deselectButton, org.openide.util.NbBundle.getMessage(PortableCaseTagsListPanel.class, "PortableCaseTagsListPanel.deselectButton.text")); // NOI18N
        deselectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deselectButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jAllTagsCheckBox, org.openide.util.NbBundle.getMessage(PortableCaseTagsListPanel.class, "PortableCaseTagsListPanel.jAllTagsCheckBox.text")); // NOI18N
        jAllTagsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jAllTagsCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(descLabel)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(selectButton, javax.swing.GroupLayout.PREFERRED_SIZE, 96, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(deselectButton))
                            .addComponent(jAllTagsCheckBox))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(descLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jAllTagsCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 163, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(selectButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(deselectButton)))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void deselectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deselectButtonActionPerformed
        selectAllTags(false);
    }//GEN-LAST:event_deselectButtonActionPerformed

    private void selectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectButtonActionPerformed
        selectAllTags(true);
    }//GEN-LAST:event_selectButtonActionPerformed

    private void jAllTagsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jAllTagsCheckBoxActionPerformed
        selectAllTags(true);
    }//GEN-LAST:event_jAllTagsCheckBoxActionPerformed

    private void selectAllTags(boolean select) {
        Boolean state = Boolean.TRUE;
        if (!select) {
            state = Boolean.FALSE;
        }
        for (TagName tagName : tagNames) {
            tagNameSelections.put(tagName.getDisplayName(), state);
        }
        updateTagList();
        tagNamesListBox.repaint();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel descLabel;
    private javax.swing.JButton deselectButton;
    private javax.swing.JCheckBox jAllTagsCheckBox;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton selectButton;
    private javax.swing.JList<String> tagNamesListBox;
    // End of variables declaration//GEN-END:variables
}
