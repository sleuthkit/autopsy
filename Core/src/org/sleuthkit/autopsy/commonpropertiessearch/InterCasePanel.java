/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import org.sleuthkit.autopsy.guiutils.DataSourceComboBoxModel;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import javax.swing.ComboBoxModel;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * UI controls for Common Files Search scenario where the user intends to find
 * common files between cases in addition to the present case.
 */
public final class InterCasePanel extends javax.swing.JPanel {

    private final static Logger logger = Logger.getLogger(InterCasePanel.class.getName());
    private static final long serialVersionUID = 1L;
    private final Observable fileTypeFilterObservable;
    static final int NO_CASE_SELECTED = -1;

    private ComboBoxModel<String> casesList = new DataSourceComboBoxModel();

    private final Map<Integer, String> caseMap;

    private Map<String, CorrelationAttributeInstance.Type> correlationTypeFilters;

    /**
     * Creates new form InterCasePanel
     */
    public InterCasePanel() {
        initComponents();
        this.caseMap = new HashMap<>();
        fileTypeFilterObservable = new Observable() {
            @Override
            public void notifyObservers() {
                //set changed before notify observers
                //we want this observerable to always cause the observer to update when notified
                this.setChanged();
                super.notifyObservers();
            }
        };
    }

    /**
     * Add an Observer to the Observable portion of this panel so that it can be
     * notified of changes to this panel.
     *
     * @param observer the object which is observing this panel
     */
    void addObserver(Observer observer) {
        fileTypeFilterObservable.addObserver(observer);
    }

    /**
     * If the user has selected to show only results of specific file types.
     *
     * @return if the selected file categories button is enabled AND selected,
     *         true for enabled AND selected false for not selected OR not
     *         enabled
     */
    boolean fileCategoriesButtonIsSelected() {
        return selectedFileCategoriesButton.isEnabled() && selectedFileCategoriesButton.isSelected();
    }

    /**
     * If the user has selected selected to show Picture and Video files as part
     * of the filtered results.
     *
     * @return if the pictures and video checkbox is enabled AND selected, true
     *         for enabled AND selected false for not selected OR not enabled
     */
    boolean pictureVideoCheckboxIsSelected() {
        return pictureVideoCheckbox.isEnabled() && pictureVideoCheckbox.isSelected();
    }

    /**
     * If the user has selected selected to show Document files as part of the
     * filtered results.
     *
     * @return if the documents checkbox is enabled AND selected, true for
     *         enabled AND selected false for not selected OR not enabled
     */
    boolean documentsCheckboxIsSelected() {
        return documentsCheckbox.isEnabled() && documentsCheckbox.isSelected();
    }

    /**
     * If the EamDB is enabled, the UI will populate the correlation type
     * ComboBox with available types in the CR.
     */
    void setupCorrelationTypeFilter() {
        this.correlationTypeFilters = new HashMap<>();
        try {
            List<CorrelationAttributeInstance.Type> types = CentralRepository.getInstance().getDefinedCorrelationTypes();
            Collections.sort(types, new Comparator<CorrelationAttributeInstance.Type>() {
                //The types should be sorted so that the File type is the first item in the combo box.
                @Override
                public int compare(CorrelationAttributeInstance.Type type1, CorrelationAttributeInstance.Type type2) {
                    return Integer.compare(type1.getId(), type2.getId());
                }
            });
            for (CorrelationAttributeInstance.Type type : types) {
                if (! type.getDbTableName().contains("os_account")) {
                    correlationTypeFilters.put(type.getDisplayName(), type);
                    this.correlationTypeComboBox.addItem(type.getDisplayName());
                }
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.WARNING, "Error getting correlation types", ex);
        }
        this.correlationTypeComboBox.setSelectedIndex(0);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup = new javax.swing.ButtonGroup();
        caseComboBox = new javax.swing.JComboBox<>();
        correlationComboBoxLabel = new javax.swing.JLabel();
        correlationTypeComboBox = new javax.swing.JComboBox<>();
        categoriesLabel = new javax.swing.JLabel();
        allFileCategoriesRadioButton = new javax.swing.JRadioButton();
        selectedFileCategoriesButton = new javax.swing.JRadioButton();
        pictureVideoCheckbox = new javax.swing.JCheckBox();
        documentsCheckbox = new javax.swing.JCheckBox();
        specificCentralRepoCaseCheckbox = new javax.swing.JCheckBox();

        caseComboBox.setModel(casesList);
        caseComboBox.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(correlationComboBoxLabel, org.openide.util.NbBundle.getMessage(InterCasePanel.class, "InterCasePanel.correlationComboBoxLabel.text")); // NOI18N

        correlationTypeComboBox.setSelectedItem(null);
        correlationTypeComboBox.setToolTipText(org.openide.util.NbBundle.getMessage(InterCasePanel.class, "InterCasePanel.correlationTypeComboBox.toolTipText")); // NOI18N
        correlationTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                correlationTypeComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(categoriesLabel, org.openide.util.NbBundle.getMessage(InterCasePanel.class, "InterCasePanel.categoriesLabel.text")); // NOI18N
        categoriesLabel.setEnabled(false);
        categoriesLabel.setName(""); // NOI18N

        buttonGroup.add(allFileCategoriesRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(allFileCategoriesRadioButton, org.openide.util.NbBundle.getMessage(InterCasePanel.class, "InterCasePanel.allFileCategoriesRadioButton.text")); // NOI18N
        allFileCategoriesRadioButton.setToolTipText(org.openide.util.NbBundle.getMessage(InterCasePanel.class, "InterCasePanel.allFileCategoriesRadioButton.toolTipText")); // NOI18N
        allFileCategoriesRadioButton.setEnabled(false);
        allFileCategoriesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allFileCategoriesRadioButtonActionPerformed(evt);
            }
        });

        buttonGroup.add(selectedFileCategoriesButton);
        selectedFileCategoriesButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(selectedFileCategoriesButton, org.openide.util.NbBundle.getMessage(InterCasePanel.class, "InterCasePanel.selectedFileCategoriesButton.text")); // NOI18N
        selectedFileCategoriesButton.setToolTipText(org.openide.util.NbBundle.getMessage(InterCasePanel.class, "InterCasePanel.selectedFileCategoriesButton.toolTipText")); // NOI18N
        selectedFileCategoriesButton.setEnabled(false);
        selectedFileCategoriesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectedFileCategoriesButtonActionPerformed(evt);
            }
        });

        pictureVideoCheckbox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(pictureVideoCheckbox, org.openide.util.NbBundle.getMessage(InterCasePanel.class, "InterCasePanel.pictureVideoCheckbox.text")); // NOI18N
        pictureVideoCheckbox.setEnabled(false);
        pictureVideoCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pictureVideoCheckboxActionPerformed(evt);
            }
        });

        documentsCheckbox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(documentsCheckbox, org.openide.util.NbBundle.getMessage(InterCasePanel.class, "InterCasePanel.documentsCheckbox.text")); // NOI18N
        documentsCheckbox.setEnabled(false);
        documentsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                documentsCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(specificCentralRepoCaseCheckbox, org.openide.util.NbBundle.getMessage(InterCasePanel.class, "InterCasePanel.specificCentralRepoCaseCheckbox.text")); // NOI18N
        specificCentralRepoCaseCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                specificCentralRepoCaseCheckboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(specificCentralRepoCaseCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addComponent(correlationComboBoxLabel)
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(categoriesLabel)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(19, 19, 19)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(allFileCategoriesRadioButton)
                                    .addComponent(selectedFileCategoriesButton)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGap(21, 21, 21)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(documentsCheckbox)
                                            .addComponent(pictureVideoCheckbox))))))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(21, 21, 21)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(caseComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(correlationTypeComboBox, 0, 353, Short.MAX_VALUE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(specificCentralRepoCaseCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(caseComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(correlationComboBoxLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(correlationTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(categoriesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(allFileCategoriesRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectedFileCategoriesButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pictureVideoCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(documentsCheckbox)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void allFileCategoriesRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allFileCategoriesRadioButtonActionPerformed
        //When the allFileCategoriesRadioButton is selected disable the options 
        //related to selected file categories and notify observers that the panel has changed
        //incase the current settings are invalid
        pictureVideoCheckbox.setEnabled(false);
        documentsCheckbox.setEnabled(false);
        fileTypeFilterObservable.notifyObservers();
    }//GEN-LAST:event_allFileCategoriesRadioButtonActionPerformed

    private void selectedFileCategoriesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectedFileCategoriesButtonActionPerformed
        //When the selectedFileCategoriesButton is selected enable its related options
        //and notify observers that the panel has changed incase the current settings are invalid
        pictureVideoCheckbox.setEnabled(true);
        documentsCheckbox.setEnabled(true);
        fileTypeFilterObservable.notifyObservers();
    }//GEN-LAST:event_selectedFileCategoriesButtonActionPerformed

    private void specificCentralRepoCaseCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specificCentralRepoCaseCheckboxActionPerformed
        //When the specificCentralRepoCaseCheckbox is clicked update its related options
        this.caseComboBox.setEnabled(specificCentralRepoCaseCheckbox.isSelected());
        if (specificCentralRepoCaseCheckbox.isSelected()) {
            this.caseComboBox.setSelectedIndex(0);
        }
    }//GEN-LAST:event_specificCentralRepoCaseCheckboxActionPerformed


    private void correlationTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_correlationTypeComboBoxActionPerformed
        boolean enableFileTypesFilter = this.correlationTypeComboBox.getSelectedItem().equals("Files");
        categoriesLabel.setEnabled(enableFileTypesFilter);
        allFileCategoriesRadioButton.setEnabled(enableFileTypesFilter);
        selectedFileCategoriesButton.setEnabled(enableFileTypesFilter);
        boolean enableIndividualFilters = (enableFileTypesFilter && selectedFileCategoriesButton.isSelected());
        pictureVideoCheckbox.setEnabled(enableIndividualFilters);
        documentsCheckbox.setEnabled(enableIndividualFilters);
    }//GEN-LAST:event_correlationTypeComboBoxActionPerformed

    private void pictureVideoCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pictureVideoCheckboxActionPerformed
        //notify observers that the panel has changed incase the current settings are invalid
        fileTypeFilterObservable.notifyObservers();
    }//GEN-LAST:event_pictureVideoCheckboxActionPerformed

    private void documentsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_documentsCheckboxActionPerformed
        //notify observers that the panel has changed incase the current settings are invalid
        fileTypeFilterObservable.notifyObservers();
    }//GEN-LAST:event_documentsCheckboxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allFileCategoriesRadioButton;
    private javax.swing.ButtonGroup buttonGroup;
    private javax.swing.JComboBox<String> caseComboBox;
    private javax.swing.JLabel categoriesLabel;
    private javax.swing.JLabel correlationComboBoxLabel;
    private javax.swing.JComboBox<String> correlationTypeComboBox;
    private javax.swing.JCheckBox documentsCheckbox;
    private javax.swing.JCheckBox pictureVideoCheckbox;
    private javax.swing.JRadioButton selectedFileCategoriesButton;
    private javax.swing.JCheckBox specificCentralRepoCaseCheckbox;
    // End of variables declaration//GEN-END:variables

    /**
     * Get the map of cases which was used to populate the combo box on this
     * panel.
     *
     * @return an unmodifiable copy of the map of cases
     */
    Map<Integer, String> getCaseMap() {
        return Collections.unmodifiableMap(this.caseMap);
    }

    /**
     * Set the datamodel for the combo box which displays the cases in the
     * central repository
     *
     * @param dataSourceComboBoxModel the DataSourceComboBoxModel to use
     */
    void setCaseComboboxModel(DataSourceComboBoxModel dataSourceComboBoxModel) {
        this.casesList = dataSourceComboBoxModel;
        this.caseComboBox.setModel(dataSourceComboBoxModel);
    }

    /**
     * Update the map of cases that this panel allows the user to select from
     *
     * @param caseMap A map of cases
     */
    void setCaseMap(Map<Integer, String> caseMap) {
        this.caseMap.clear();
        this.caseMap.putAll(caseMap);
    }

    /**
     * Whether or not the central repository has multiple cases in it.
     *
     * @return true when the central repository has 2 or more case, false if it
     *         has 0 or 1 case in it.
     */
    boolean centralRepoHasMultipleCases() {
        return this.caseMap.size() >= 2;
    }

    /**
     * Get the ID for the selected case
     *
     * @return the ID of the selected case or InterCasePanel.NO_CASE_SELECTED
     *         when none selected
     */
    Integer getSelectedCaseId() {
        if (specificCentralRepoCaseCheckbox.isSelected()) {
            for (Entry<Integer, String> entry : this.caseMap.entrySet()) {
                if (entry.getValue().equals(this.caseComboBox.getSelectedItem())) {
                    return entry.getKey();
                }
            }
        }
        return InterCasePanel.NO_CASE_SELECTED;
    }

    /**
     * Returns the selected Correlation Type by getting the Type from the stored
     * HashMap.
     *
     * @return Type the selected Correlation Type to query for.
     */
    CorrelationAttributeInstance.Type getSelectedCorrelationType() {
        return correlationTypeFilters.get(this.correlationTypeComboBox.getSelectedItem().toString());
    }
}
