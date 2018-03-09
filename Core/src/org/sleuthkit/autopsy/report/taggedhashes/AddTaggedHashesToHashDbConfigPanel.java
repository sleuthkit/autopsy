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
package org.sleuthkit.autopsy.report.taggedhashes;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.event.ListDataListener;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupSettingsPanel;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class are used to configure the report module plug in that
 * provides a convenient way to add content hashes to hash set databases.
 */
class AddTaggedHashesToHashDbConfigPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private List<TagName> tagNames;
    private final Map<String, Boolean> tagNameSelections = new LinkedHashMap<>();
    private final TagNamesListModel tagsNamesListModel = new TagNamesListModel();
    private final TagsNamesListCellRenderer tagsNamesRenderer = new TagsNamesListCellRenderer();
    private HashDb selectedHashSet = null;

    AddTaggedHashesToHashDbConfigPanel() {
        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {
        populateTagNameComponents();
        populateHashSetComponents();
    }

    private void populateTagNameComponents() {
        // Get the tag names in use for the current case.
        try {
            tagNames = Case.getOpenCase().getServices().getTagsManager().getTagNamesInUse();
        } catch (TskCoreException ex) {
            Logger.getLogger(AddTaggedHashesToHashDbConfigPanel.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex);
            JOptionPane.showMessageDialog(this, "Error getting tag names for case.", "Tag Names Not Found", JOptionPane.ERROR_MESSAGE);
        } catch (NoCurrentCaseException ex) {
            Logger.getLogger(AddTaggedHashesToHashDbConfigPanel.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex);
            JOptionPane.showMessageDialog(this, "Error getting tag names for case.", "Exception while getting open case.", JOptionPane.ERROR_MESSAGE);
        }

        // Mark the tag names as unselected. Note that tagNameSelections is a
        // LinkedHashMap so that order is preserved and the tagNames and tagNameSelections
        // containers are "parallel" containers.
        for (TagName tagName : tagNames) {
            tagNameSelections.put(tagName.getDisplayName(), Boolean.FALSE);
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
                }
            }
        });
    }

    private void populateHashSetComponents() {
        // Clear the components because this method is called both during construction
        // and when the user changes the hash set configuration.
        hashSetsComboBox.removeAllItems();

        // Get the updateable hash databases and add their hash set names to the
        // JComboBox component.
        List<HashDb> updateableHashSets = HashDbManager.getInstance().getUpdateableHashSets();
        if (!updateableHashSets.isEmpty()) {
            for (HashDb hashDb : updateableHashSets) {
                hashSetsComboBox.addItem(hashDb);
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
            if (tagNameSelections.get(tagName.getDisplayName())) {
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
        return selectedHashSet;
    }

    // This class is a list model for the tag names JList component.
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
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
        }
    }

    // This class renders the items in the tag names JList component as JCheckbox components.
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
                setText(value);
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

        jScrollPane1.setViewportView(tagNamesListBox);

        org.openide.awt.Mnemonics.setLocalizedText(selectAllButton, org.openide.util.NbBundle.getMessage(AddTaggedHashesToHashDbConfigPanel.class, "AddTaggedHashesToHashDbConfigPanel.selectAllButton.text")); // NOI18N
        selectAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deselectAllButton, org.openide.util.NbBundle.getMessage(AddTaggedHashesToHashDbConfigPanel.class, "AddTaggedHashesToHashDbConfigPanel.deselectAllButton.text")); // NOI18N
        deselectAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deselectAllButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(AddTaggedHashesToHashDbConfigPanel.class, "AddTaggedHashesToHashDbConfigPanel.jLabel1.text")); // NOI18N

        hashSetsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hashSetsComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(configureHashDatabasesButton, org.openide.util.NbBundle.getMessage(AddTaggedHashesToHashDbConfigPanel.class, "AddTaggedHashesToHashDbConfigPanel.configureHashDatabasesButton.text")); // NOI18N
        configureHashDatabasesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                configureHashDatabasesButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(AddTaggedHashesToHashDbConfigPanel.class, "AddTaggedHashesToHashDbConfigPanel.jLabel2.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2)
                    .addComponent(jLabel1)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(hashSetsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 159, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(configureHashDatabasesButton)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(deselectAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(selectAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {deselectAllButton, selectAllButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(selectAllButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(deselectAllButton))
                    .addComponent(jScrollPane1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel2)
                .addGap(4, 4, 4)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hashSetsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(configureHashDatabasesButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void selectAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllButtonActionPerformed
        for (TagName tagName : tagNames) {
            tagNameSelections.put(tagName.getDisplayName(), Boolean.TRUE);
        }
        tagNamesListBox.repaint();
    }//GEN-LAST:event_selectAllButtonActionPerformed

    private void hashSetsComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hashSetsComboBoxActionPerformed
        selectedHashSet = (HashDb)hashSetsComboBox.getSelectedItem();
    }//GEN-LAST:event_hashSetsComboBoxActionPerformed

    private void deselectAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deselectAllButtonActionPerformed
        for (TagName tagName : tagNames) {
            tagNameSelections.put(tagName.getDisplayName(), Boolean.FALSE);
        }
        tagNamesListBox.repaint();
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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton configureHashDatabasesButton;
    private javax.swing.JButton deselectAllButton;
    private javax.swing.JComboBox<HashDb> hashSetsComboBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton selectAllButton;
    private javax.swing.JList<String> tagNamesListBox;
    // End of variables declaration//GEN-END:variables
}
