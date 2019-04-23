/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
 *
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
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + "'";
            
            // Get the count of each SET_NAME
            String query = "set_name, count(1) AS set_count FROM (" + innerSelect + ") set_names GROUP BY set_name";
            
            GetInterestingItemSetNamesCallback callback = new GetInterestingItemSetNamesCallback();
            Case.getCurrentCaseThrows().getSleuthkitCase().getCaseDbAccessManager().select(query, callback);
            setCounts = callback.getSetCountMap();
            setNames.addAll(setCounts.keySet());
            
            /*
            List<BlackboardArtifact> interestingItems = Case.getCurrentCaseThrows().getSleuthkitCase()
                    .getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
            interestingItems.addAll(Case.getCurrentCaseThrows().getSleuthkitCase()
                    .getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT));
            for(BlackboardArtifact art:interestingItems) {
                BlackboardAttribute setAttr = art.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
                if ((setAttr != null) &&
                    (setAttr.getValueString() != null) &&
                    ( !setAttr.getValueString().isEmpty()) &&
                    ( !setNames.contains(setAttr.getValueString()))){
                    setNames.add(setAttr.getValueString());
                }
            }*/
        } catch (TskCoreException ex) {
            Logger.getLogger(ReportWizardPortableCaseOptionsVisualPanel.class.getName()).log(Level.SEVERE, "Failed to get interesting item set names", ex);
            JOptionPane.showMessageDialog(this, Bundle.PortableCaseInterestingItemsListPanel_error_errorLoadingTags(), Bundle.PortableCaseInterestingItemsListPanel_error_errorTitle(), JOptionPane.ERROR_MESSAGE);
        } catch (NoCurrentCaseException ex) {
            Logger.getLogger(ReportWizardPortableCaseOptionsVisualPanel.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex);
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
                setText(value);
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
    
    static class GetInterestingItemSetNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

        private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GetInterestingItemSetNamesCallback.class.getName());
        private final Map<String, Long> setCounts = new HashMap<>();
        
        @Override
        public void process(ResultSet rs) {
            try {
                System.out.println("## In GetInterestingItemSetNamesCallback");
                while (rs.next()) {
                    try {
                        Long setCount = rs.getLong("set_count");
                        String setName = rs.getString("set_name");

                        setCounts.put(setName, setCount);
                        System.out.println("### Set: " + setName + "    Count: " + setCount);
                        
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "Unable to get data_source_obj_id or value from result set", ex);
                    }
                }
                System.out.println("## Done processing result set");
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to get next result for values by datasource", ex);
            }
        }   
        
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
        setNamesListBox.repaint();
    }//GEN-LAST:event_deselectButtonActionPerformed

    private void selectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectButtonActionPerformed
        for (String setName : setNames) {
            setNameSelections.put(setName, Boolean.TRUE);
        }
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
