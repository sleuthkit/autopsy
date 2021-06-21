/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.taggedhashes;

import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.ListCellRenderer;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupSettingsPanel;
import org.sleuthkit.autopsy.tags.TagUtils;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class are used to configure the report module plug in that
 * provides a convenient way to add content hashes to hash set databases.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class SaveTaggedHashesToHashDbConfigPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private List<TagName> tagNames;
    private Map<TagName, Boolean> tagNameSelections = new LinkedHashMap<>();
    private DefaultListModel<TagName> tagsNamesListModel = new DefaultListModel<>();
    private TagsNamesListCellRenderer tagsNamesRenderer = new TagsNamesListCellRenderer();
    private String selectedHashSetName;
    private List<HashDb> updateableHashSets = new ArrayList<>();

    SaveTaggedHashesToHashDbConfigPanel() {
        initComponents();
        customizeComponents();

        // Set up the tag names JList component to be a collection of check boxes
        // for selecting tag names. The mouse click listener updates tagNameSelections
        // to reflect user choices.
        tagNamesListBox.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent evt) {
                if (jAllTagsCheckBox.isSelected()) {
                    return;
                }
                JList<?> list = (JList) evt.getSource();
                int index = list.locationToIndex(evt.getPoint());
                if (index > -1) {
                    TagName tagName = tagsNamesListModel.getElementAt(index);
                    tagNameSelections.put(tagName, !tagNameSelections.get(tagName));
                    list.repaint();
                }
            }
        });

        this.jAllTagsCheckBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                tagNamesListBox.setEnabled(!jAllTagsCheckBox.isSelected());
                selectAllButton.setEnabled(!jAllTagsCheckBox.isSelected());
                deselectAllButton.setEnabled(!jAllTagsCheckBox.isSelected());
                selectAllTags(jAllTagsCheckBox.isSelected());
            }
        });
    }

    HashesReportModuleSettings getConfiguration() {
        return new HashesReportModuleSettings(jAllTagsCheckBox.isSelected(), selectedHashSetName);
    }

    void setConfiguration(HashesReportModuleSettings settings) {
        // Need to reset tags. User may have opened a different case or
        // there may not be a case open any more (Command Line wizard).
        customizeComponents();

        // update tag selection
        jAllTagsCheckBox.setSelected(settings.isExportAllTags());
        if (settings.isExportAllTags()) {
            selectAllTags(true);
        }

        // update hash database selection
        if (settings.getHashDbName() != null) {
            populateHashSetComponents();
            hashSetsComboBox.setSelectedItem(settings.getHashDbName());
        }
    }

    private void customizeComponents() {
        tagsNamesListModel = new DefaultListModel<>();
        tagsNamesRenderer = new TagsNamesListCellRenderer();
        populateTagNameComponents();

        tagNamesListBox.setModel(tagsNamesListModel);
        tagNamesListBox.setCellRenderer(tagsNamesRenderer);
        tagNamesListBox.setVisibleRowCount(-1);

        populateHashSetComponents();
    }

    private void populateTagNameComponents() {
        // Get the tag names in use for the current case.
        tagNames = new ArrayList<>();
        Map<TagName, Boolean> updatedTagNameSelections = new LinkedHashMap<>();
        try {
            // There may not be a case open when configuring report modules for Command Line execution
            tagNames = Case.getCurrentCaseThrows().getServices().getTagsManager().getTagNamesInUse();
            for (TagName tagName : tagNames) {
                tagsNamesListModel.addElement(tagName);
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(SaveTaggedHashesToHashDbConfigPanel.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex);
        } catch (NoCurrentCaseException ex) {
            // There may not be a case open when configuring report modules for Command Line execution
            if (Case.isCaseOpen()) {
                Logger.getLogger(SaveTaggedHashesToHashDbConfigPanel.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex);
            }
        }

        // Preserve the previous selections. Note that tagNameSelections is a
        // LinkedHashMap so that order is preserved and the tagNames and tagNameSelections
        // containers are "parallel" containers.
        for (TagName tagName : tagNames) {
            if (tagNameSelections.get(tagName) != null && Objects.equals(tagNameSelections.get(tagName), Boolean.TRUE)) {
                updatedTagNameSelections.put(tagName, Boolean.TRUE);
            } else {
                updatedTagNameSelections.put(tagName, Boolean.FALSE);
            }
        }
        tagNameSelections = updatedTagNameSelections;
    }

    private void populateHashSetComponents() {
        // Clear the components because this method is called both during construction
        // and when the user changes the hash set configuration.
        hashSetsComboBox.removeAllItems();
        selectedHashSetName = "";

        // Get the updateable hash databases and add their hash set names to the
        // JComboBox component.
        updateableHashSets = HashDbManager.getInstance().getUpdateableHashSets();
        if (!updateableHashSets.isEmpty()) {
            for (HashDb hashDb : updateableHashSets) {
                hashSetsComboBox.addItem(hashDb.getHashSetName());
            }
            hashSetsComboBox.setEnabled(true);
        } else {
            hashSetsComboBox.setEnabled(false);
        }
    }

    /**
     * Gets the subset of the tag names in use selected by the user.
     *
     * @return A list, possibly empty, of TagName data transfer objects (DTOs).
     */
    List<TagName> getSelectedTagNames() {
        List<TagName> selectedTagNames = new ArrayList<>();
        for (TagName tagName : tagNames) {
            if (tagNameSelections.get(tagName)) {
                selectedTagNames.add(tagName);
            }
        }
        return selectedTagNames;
    }

    /**
     * Gets the hash set database selected by the user.
     *
     * @return A HashDb object representing the database or null.
     */
    HashDb getSelectedHashDatabase() {
        for (HashDb hashDb : updateableHashSets) {
            if (hashDb.getHashSetName().equals(selectedHashSetName)) {
                return hashDb;
            }
        }
        return null;
    }

    // This class renders the items in the tag names JList component as JCheckbox components.
    private class TagsNamesListCellRenderer extends JCheckBox implements ListCellRenderer<TagName> {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<? extends TagName> list, TagName value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value != null) {
                setEnabled(list.isEnabled());
                setSelected(tagNameSelections.get(value));
                setFont(list.getFont());
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                
                String text = TagUtils.getDecoratedTagDisplayName(value);
                setText(text);
                this.setToolTipText(text);
                return this;
            }
            return new JLabel();
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

        jScrollPane1 = new javax.swing.JScrollPane();
        tagNamesListBox = new javax.swing.JList<>();
        selectAllButton = new javax.swing.JButton();
        deselectAllButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        hashSetsComboBox = new javax.swing.JComboBox<>();
        configureHashDatabasesButton = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        jAllTagsCheckBox = new javax.swing.JCheckBox();

        jScrollPane1.setViewportView(tagNamesListBox);

        org.openide.awt.Mnemonics.setLocalizedText(selectAllButton, org.openide.util.NbBundle.getMessage(SaveTaggedHashesToHashDbConfigPanel.class, "SaveTaggedHashesToHashDbConfigPanel.selectAllButton.text")); // NOI18N
        selectAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deselectAllButton, org.openide.util.NbBundle.getMessage(SaveTaggedHashesToHashDbConfigPanel.class, "SaveTaggedHashesToHashDbConfigPanel.deselectAllButton.text")); // NOI18N
        deselectAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deselectAllButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(SaveTaggedHashesToHashDbConfigPanel.class, "SaveTaggedHashesToHashDbConfigPanel.jLabel1.text")); // NOI18N

        hashSetsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hashSetsComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(configureHashDatabasesButton, org.openide.util.NbBundle.getMessage(SaveTaggedHashesToHashDbConfigPanel.class, "SaveTaggedHashesToHashDbConfigPanel.configureHashDatabasesButton.text")); // NOI18N
        configureHashDatabasesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                configureHashDatabasesButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(SaveTaggedHashesToHashDbConfigPanel.class, "SaveTaggedHashesToHashDbConfigPanel.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jAllTagsCheckBox, org.openide.util.NbBundle.getMessage(SaveTaggedHashesToHashDbConfigPanel.class, "SaveTaggedHashesToHashDbConfigPanel.jAllTagsCheckBox.text")); // NOI18N
        jAllTagsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jAllTagsCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 359, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(deselectAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(selectAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jAllTagsCheckBox)
                            .addComponent(jLabel1)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(hashSetsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 159, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(configureHashDatabasesButton))
                            .addComponent(jLabel2))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {deselectAllButton, selectAllButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jAllTagsCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(selectAllButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(deselectAllButton))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 112, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel2)
                .addGap(4, 4, 4)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hashSetsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(configureHashDatabasesButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void selectAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllButtonActionPerformed
        selectAllTags(true);
    }//GEN-LAST:event_selectAllButtonActionPerformed

    private void hashSetsComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hashSetsComboBoxActionPerformed
        selectedHashSetName = (String) hashSetsComboBox.getSelectedItem();
    }//GEN-LAST:event_hashSetsComboBoxActionPerformed

    private void deselectAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deselectAllButtonActionPerformed
        selectAllTags(false);
    }//GEN-LAST:event_deselectAllButtonActionPerformed

    private void configureHashDatabasesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_configureHashDatabasesButtonActionPerformed
        HashLookupSettingsPanel configPanel = new HashLookupSettingsPanel();
        configPanel.load();
        if (JOptionPane.showConfirmDialog(this, configPanel, "Hash Set Configuration", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            configPanel.store();
            populateHashSetComponents();
        } else {
            configPanel.cancel();
            populateHashSetComponents();
        }
    }//GEN-LAST:event_configureHashDatabasesButtonActionPerformed

    private void selectAllTags(boolean select) {
        Boolean state = Boolean.TRUE;
        if (!select) {
            state = Boolean.FALSE;
        }
        for (TagName tagName : tagNames) {
            tagNameSelections.put(tagName, state);
        }
        tagNamesListBox.repaint();
    }
    private void jAllTagsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jAllTagsCheckBoxActionPerformed
        selectAllTags(true);
    }//GEN-LAST:event_jAllTagsCheckBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton configureHashDatabasesButton;
    private javax.swing.JButton deselectAllButton;
    private javax.swing.JComboBox<String> hashSetsComboBox;
    private javax.swing.JCheckBox jAllTagsCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton selectAllButton;
    private javax.swing.JList<TagName> tagNamesListBox;
    // End of variables declaration//GEN-END:variables
}
