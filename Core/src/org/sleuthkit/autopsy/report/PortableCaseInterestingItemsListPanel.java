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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * The subpanel showing the interesting item sets
 */
class PortableCaseInterestingItemsListPanel extends javax.swing.JPanel {

    private List<String> setNames;
    private final Map<String, Boolean> setNameSelections = new LinkedHashMap<>();
    private final SetNamesListModel setNamesListModel = new SetNamesListModel();
    private final SetNamesListCellRenderer setNamesRenderer = new SetNamesListCellRenderer();
    private Map<String, Long> setCounts;
    
    private final ReportWizardPortableCaseOptionsPanel wizPanel;
    private final PortableCaseReportModule.PortableCaseOptions options;
    
    /**
     * Creates new form PortableCaseListPanel
     */
    PortableCaseInterestingItemsListPanel(ReportWizardPortableCaseOptionsPanel wizPanel, PortableCaseReportModule.PortableCaseOptions options) {
        this.wizPanel = wizPanel;
        this.options = options;
        initComponents();
        customizeComponents();
    }

    @NbBundle.Messages({
        "PortableCaseInterestingItemsListPanel.error.errorTitle=Error getting intesting item set names for case",
        "PortableCaseInterestingItemsListPanel.error.noOpenCase=There is no case open",
        "PortableCaseInterestingItemsListPanel.error.errorLoadingTags=Error loading interesting item set names",  
    })    
    private void customizeComponents() {
        
        // Get the set names in use for the current case.
        setNames = new ArrayList<>();
        try {
            // Get all SET_NAMEs from interesting item artifacts
            String innerSelect = "SELECT (value_text) AS set_name FROM blackboard_attributes WHERE (artifact_type_id = '" + 
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID() + "' OR artifact_type_id = '" +
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID() + "') AND attribute_type_id = '" + 
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + "'"; // NON-NLS
            
            // Get the count of each SET_NAME
            String query = "set_name, count(1) AS set_count FROM (" + innerSelect + ") set_names GROUP BY set_name"; // NON-NLS
            
            GetInterestingItemSetNamesCallback callback = new GetInterestingItemSetNamesCallback();
            Case.getCurrentCaseThrows().getSleuthkitCase().getCaseDbAccessManager().select(query, callback);
            setCounts = callback.getSetCountMap();
            setNames.addAll(setCounts.keySet());
        } catch (TskCoreException ex) {
            Logger.getLogger(ReportWizardPortableCaseOptionsVisualPanel.class.getName()).log(Level.SEVERE, "Failed to get interesting item set names", ex); // NON-NLS
            JOptionPane.showMessageDialog(this, Bundle.PortableCaseInterestingItemsListPanel_error_errorLoadingTags(), Bundle.PortableCaseInterestingItemsListPanel_error_errorTitle(), JOptionPane.ERROR_MESSAGE);
        } catch (NoCurrentCaseException ex) {
            Logger.getLogger(ReportWizardPortableCaseOptionsVisualPanel.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
            JOptionPane.showMessageDialog(this, Bundle.PortableCaseInterestingItemsListPanel_error_noOpenCase(), Bundle.PortableCaseInterestingItemsListPanel_error_errorTitle(), JOptionPane.ERROR_MESSAGE);
        }
        Collections.sort(setNames);

        // Mark the set names as unselected. Note that setNameSelections is a
        // LinkedHashMap so that order is preserved and the setNames and setNameSelections
        // containers are "parallel" containers.
        for (String setName : setNames) {
            setNameSelections.put(setName, Boolean.FALSE);
        }

        // Set up the tag names JList component to be a collection of check boxes
        // for selecting tag names. The mouse click listener updates setNameSelections
        // to reflect user choices.
        setNamesListBox.setModel(setNamesListModel);
        setNamesListBox.setCellRenderer(setNamesRenderer);
        setNamesListBox.setVisibleRowCount(-1);
        setNamesListBox.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent evt) {
                JList<?> list = (JList) evt.getSource();
                int index = list.locationToIndex(evt.getPoint());
                if (index > -1) {
                    String value = setNamesListModel.getElementAt(index);
                    setNameSelections.put(value, !setNameSelections.get(value));
                    list.repaint();
                    updateSetNameList();
                }
            }
        });
    }
    
    /**
     * Save the current selections and enabled/disable the finish button as needed.
     */
    private void updateSetNameList() {
        options.updateSetNames(getSelectedSetNames());
        wizPanel.setFinish(options.isValid());
    }    

    // This class is a list model for the tag names JList component.
    private class SetNamesListModel implements ListModel<String> {

        @Override
        public int getSize() {
            return setNames.size();
        }

        @Override
        public String getElementAt(int index) {
            return setNames.get(index);
        }

        @Override
        public void addListDataListener(ListDataListener l) {
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
        }
    }

    // This class renders the items in the tag names JList component as JCheckbox components.
    private class SetNamesListCellRenderer extends JCheckBox implements ListCellRenderer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value != null) {
                setEnabled(list.isEnabled());
                setSelected(setNameSelections.get(value));
                setFont(list.getFont());
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                setText(value + " (" + setCounts.get(value) + ")"); // NON-NLS
                return this;
            }
            return new JLabel();
        }
    }      
    
    /**
     * Gets the subset of the interesting item set names in use selected by the user.
     *
     * @return A list, possibly empty, of String data transfer objects (DTOs).
     */
    private List<String> getSelectedSetNames() {
        List<String> selectedSetNames = new ArrayList<>();
        for (String setName : setNames) {
            if (setNameSelections.get(setName)) {
                selectedSetNames.add(setName);
            }
        }
        return selectedSetNames;
    }    
    
    /**
     * Processes the result sets from the interesting item set name query.
     */
    private static class GetInterestingItemSetNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

        private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GetInterestingItemSetNamesCallback.class.getName());
        private final Map<String, Long> setCounts = new HashMap<>();
        
        @Override
        public void process(ResultSet rs) {
            try {
                while (rs.next()) {
                    try {
                        Long setCount = rs.getLong("set_count"); // NON-NLS
                        String setName = rs.getString("set_name"); // NON-NLS

                        setCounts.put(setName, setCount);
                        
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "Unable to get data_source_obj_id or value from result set", ex); // NON-NLS
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to get next result for values by datasource", ex); // NON-NLS
            }
        }   
        
        /**
         * Gets the counts for each interesting items set
         * 
         * @return A map from each set name to the number of items in it
         */
        Map<String, Long> getSetCountMap() {
            return setCounts;
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
        setNamesListBox = new javax.swing.JList<>();
        jLabel1 = new javax.swing.JLabel();
        selectButton = new javax.swing.JButton();
        deselectButton = new javax.swing.JButton();

        jScrollPane1.setViewportView(setNamesListBox);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(PortableCaseInterestingItemsListPanel.class, "PortableCaseInterestingItemsListPanel.jLabel1.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(selectButton, org.openide.util.NbBundle.getMessage(PortableCaseInterestingItemsListPanel.class, "PortableCaseInterestingItemsListPanel.selectButton.text")); // NOI18N
        selectButton.setMaximumSize(new java.awt.Dimension(87, 23));
        selectButton.setMinimumSize(new java.awt.Dimension(87, 23));
        selectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deselectButton, org.openide.util.NbBundle.getMessage(PortableCaseInterestingItemsListPanel.class, "PortableCaseInterestingItemsListPanel.deselectButton.text")); // NOI18N
        deselectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deselectButtonActionPerformed(evt);
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
                            .addComponent(jLabel1)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(selectButton, javax.swing.GroupLayout.PREFERRED_SIZE, 96, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(deselectButton)))
                        .addGap(0, 8, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 179, Short.MAX_VALUE)
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
        for (String setName : setNames) {
            setNameSelections.put(setName, Boolean.FALSE);
        }
        updateSetNameList();
        setNamesListBox.repaint();
    }//GEN-LAST:event_deselectButtonActionPerformed

    private void selectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectButtonActionPerformed
        for (String setName : setNames) {
            setNameSelections.put(setName, Boolean.TRUE);
        }
        updateSetNameList();
        setNamesListBox.repaint();
    }//GEN-LAST:event_selectButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deselectButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton selectButton;
    private javax.swing.JList<String> setNamesListBox;
    // End of variables declaration//GEN-END:variables
}
