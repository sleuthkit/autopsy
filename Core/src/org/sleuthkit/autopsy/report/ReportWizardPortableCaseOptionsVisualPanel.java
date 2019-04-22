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
package org.sleuthkit.autopsy.report;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.event.ListDataListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.report.PortableCaseReportModule.ChunkSize;
import org.sleuthkit.autopsy.report.PortableCaseReportModule.PortableCaseOptions;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * The UI portion of the Portable Case config panel
 */
class ReportWizardPortableCaseOptionsVisualPanel extends javax.swing.JPanel {

    private final ReportWizardPortableCaseOptionsPanel wizPanel;
    private List<TagName> tagNames;
    private final Map<String, Boolean> tagNameSelections = new LinkedHashMap<>();
    private final TagNamesListModel tagsNamesListModel = new TagNamesListModel();
    private final TagsNamesListCellRenderer tagsNamesRenderer = new TagsNamesListCellRenderer();
    private final PortableCaseOptions options = new PortableCaseOptions();
    
    /**
     * Creates new form ReportWizardPortableCaseOptionsVisualPanel
     */
    ReportWizardPortableCaseOptionsVisualPanel(ReportWizardPortableCaseOptionsPanel wizPanel) {
        this.wizPanel = wizPanel;
        initComponents();
        customizeComponents();
    }
    
    private void customizeComponents() {
        populateTagNameComponents();
        
        if ( ! PlatformUtil.isWindowsOS()) {
            errorLabel.setVisible(true);
            compressCheckbox.setEnabled(false);
        } else {
            errorLabel.setVisible(false);
        }
        
        for (ChunkSize chunkSize:ChunkSize.values()) {
            chunkSizeComboBox.addItem(chunkSize);
        }
        chunkSizeComboBox.setSelectedItem(ChunkSize.NONE);
        chunkSizeComboBox.setEnabled(false);
        chunkSizeLabel.setEnabled(false);
        options.updateCompression(false, ChunkSize.NONE);

        //interestingFilesCheckbox.setSelected(false);
        //interestingResultsCheckbox.setSelected(false);
        options.updateInterestingItems(false, false);
        
        
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.X_AXIS));
        listPanel.add(new PortableCaseTagsListPanel(wizPanel, options));
        listPanel.add(new PortableCaseTagsListPanel(wizPanel, options));
        
    }
    
    @NbBundle.Messages({
        "ReportWizardPortableCaseOptionsVisualPanel.error.errorTitle=Error getting tag names for case",
        "ReportWizardPortableCaseOptionsVisualPanel.error.noOpenCase=There is no case open",
        "ReportWizardPortableCaseOptionsVisualPanel.error.errorLoadingTags=Error loading tags",  
    })
    private void populateTagNameComponents() {
        // Get the tag names in use for the current case.
        try {
            tagNames = Case.getCurrentCaseThrows().getServices().getTagsManager().getTagNamesInUse();
        } catch (TskCoreException ex) {
            Logger.getLogger(ReportWizardPortableCaseOptionsVisualPanel.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex);
            JOptionPane.showMessageDialog(this, Bundle.ReportWizardPortableCaseOptionsVisualPanel_error_errorLoadingTags(), Bundle.ReportWizardPortableCaseOptionsVisualPanel_error_errorTitle(), JOptionPane.ERROR_MESSAGE);
        } catch (NoCurrentCaseException ex) {
            Logger.getLogger(ReportWizardPortableCaseOptionsVisualPanel.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex);
            JOptionPane.showMessageDialog(this, Bundle.ReportWizardPortableCaseOptionsVisualPanel_error_noOpenCase(), Bundle.ReportWizardPortableCaseOptionsVisualPanel_error_errorTitle(), JOptionPane.ERROR_MESSAGE);
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
                    updateTagList();
                }
            }
        });
    }
    
    @NbBundle.Messages({
        "ReportWizardPortableCaseOptionsVisualPanel.getName.title=Choose Portable Case settings",  
    })  
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
    
    private void updateInterestingItems() {
        //options.updateInterestingItems(interestingFilesCheckbox.isSelected(), interestingResultsCheckbox.isSelected());
        wizPanel.setFinish(options.isValid());
    }
    
    private void updateCompression() {
        options.updateCompression(compressCheckbox.isSelected(), getChunkSize());
        wizPanel.setFinish(options.isValid());
    }
    
    private void updateTagList() {
        options.updateTagNames(getSelectedTagNames());
        wizPanel.setFinish(options.isValid());
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
     * Get the user-selected settings.
     *
     * @return
     */
    PortableCaseOptions getPortableCaseReportOptions() {
        return options;
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

        jPanel1 = new javax.swing.JPanel();
        tagsSelectAllButton = new javax.swing.JButton();
        tagsDeselectAllButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        tagNamesListBox = new javax.swing.JList<>();
        tagLabel = new javax.swing.JLabel();
        chunkSizeComboBox = new javax.swing.JComboBox<>();
        compressCheckbox = new javax.swing.JCheckBox();
        chunkSizeLabel = new javax.swing.JLabel();
        errorLabel = new javax.swing.JLabel();
        resultsLabel = new javax.swing.JLabel();
        listPanel = new javax.swing.JPanel();

        org.openide.awt.Mnemonics.setLocalizedText(tagsSelectAllButton, org.openide.util.NbBundle.getMessage(ReportWizardPortableCaseOptionsVisualPanel.class, "ReportWizardPortableCaseOptionsVisualPanel.tagsSelectAllButton.text")); // NOI18N
        tagsSelectAllButton.setMaximumSize(new java.awt.Dimension(99, 23));
        tagsSelectAllButton.setMinimumSize(new java.awt.Dimension(99, 23));
        tagsSelectAllButton.setPreferredSize(new java.awt.Dimension(99, 23));
        tagsSelectAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tagsSelectAllButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(tagsDeselectAllButton, org.openide.util.NbBundle.getMessage(ReportWizardPortableCaseOptionsVisualPanel.class, "ReportWizardPortableCaseOptionsVisualPanel.tagsDeselectAllButton.text")); // NOI18N
        tagsDeselectAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tagsDeselectAllButtonActionPerformed(evt);
            }
        });

        jScrollPane1.setViewportView(tagNamesListBox);

        org.openide.awt.Mnemonics.setLocalizedText(tagLabel, org.openide.util.NbBundle.getMessage(ReportWizardPortableCaseOptionsVisualPanel.class, "ReportWizardPortableCaseOptionsVisualPanel.tagLabel.text")); // NOI18N

        chunkSizeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chunkSizeComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(compressCheckbox, org.openide.util.NbBundle.getMessage(ReportWizardPortableCaseOptionsVisualPanel.class, "ReportWizardPortableCaseOptionsVisualPanel.compressCheckbox.text")); // NOI18N
        compressCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compressCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(chunkSizeLabel, org.openide.util.NbBundle.getMessage(ReportWizardPortableCaseOptionsVisualPanel.class, "ReportWizardPortableCaseOptionsVisualPanel.chunkSizeLabel.text")); // NOI18N

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(ReportWizardPortableCaseOptionsVisualPanel.class, "ReportWizardPortableCaseOptionsVisualPanel.errorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(resultsLabel, org.openide.util.NbBundle.getMessage(ReportWizardPortableCaseOptionsVisualPanel.class, "ReportWizardPortableCaseOptionsVisualPanel.resultsLabel.text")); // NOI18N

        javax.swing.GroupLayout listPanelLayout = new javax.swing.GroupLayout(listPanel);
        listPanel.setLayout(listPanelLayout);
        listPanelLayout.setHorizontalGroup(
            listPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        listPanelLayout.setVerticalGroup(
            listPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(listPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 328, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tagsSelectAllButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(tagsDeselectAllButton, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(16, 16, 16))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(compressCheckbox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(errorLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(chunkSizeLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chunkSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(resultsLabel)
                                    .addComponent(tagLabel))
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addContainerGap())))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(resultsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tagLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(compressCheckbox)
                            .addComponent(errorLabel)
                            .addComponent(chunkSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(chunkSizeLabel)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(tagsSelectAllButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(tagsDeselectAllButton)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(listPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 463, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 259, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void tagsSelectAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tagsSelectAllButtonActionPerformed
        for (TagName tagName : tagNames) {
            tagNameSelections.put(tagName.getDisplayName(), Boolean.TRUE);
        }
        tagNamesListBox.repaint();
    }//GEN-LAST:event_tagsSelectAllButtonActionPerformed

    private void tagsDeselectAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tagsDeselectAllButtonActionPerformed
        for (TagName tagName : tagNames) {
            tagNameSelections.put(tagName.getDisplayName(), Boolean.FALSE);
        }
        tagNamesListBox.repaint();
    }//GEN-LAST:event_tagsDeselectAllButtonActionPerformed

    private void chunkSizeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chunkSizeComboBoxActionPerformed
        updateCompression();
    }//GEN-LAST:event_chunkSizeComboBoxActionPerformed

    private void compressCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compressCheckboxActionPerformed
        chunkSizeComboBox.setEnabled(compressCheckbox.isSelected());
        chunkSizeLabel.setEnabled(compressCheckbox.isSelected());
        updateCompression();
    }//GEN-LAST:event_compressCheckboxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<ChunkSize> chunkSizeComboBox;
    private javax.swing.JLabel chunkSizeLabel;
    private javax.swing.JCheckBox compressCheckbox;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPanel listPanel;
    private javax.swing.JLabel resultsLabel;
    private javax.swing.JLabel tagLabel;
    private javax.swing.JList<String> tagNamesListBox;
    private javax.swing.JButton tagsDeselectAllButton;
    private javax.swing.JButton tagsSelectAllButton;
    // End of variables declaration//GEN-END:variables
}
