/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.newpackage;

import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.newpackage.FileSearch.GroupingAttributeType;
import org.sleuthkit.autopsy.newpackage.FileSearchData.FileType;
import org.sleuthkit.autopsy.newpackage.FileSearchData.FileSize;
import org.sleuthkit.autopsy.newpackage.FileSearchData.Frequency;
import org.sleuthkit.autopsy.newpackage.FileSearchFiltering.ParentSearchTerm;
import org.sleuthkit.autopsy.newpackage.FileSorter.SortingMethod;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.DataSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.DefaultListModel;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 *
 */
public class FileSearchDialog extends javax.swing.JDialog {

    private DefaultListModel<ParentSearchTerm> parentListModel;
    private ButtonPressed buttonPressed = ButtonPressed.SEARCH;
    
    /**
     * Creates new form FileSearchDialog
     */
    public FileSearchDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        customizeComponents();
    }
    
    private void customizeComponents() {
        
        int count = 0;
        DefaultListModel<FileType> fileTypeListModel = (DefaultListModel<FileType>)fileTypeList.getModel();
        for (FileType type : FileType.getOptionsForFiltering()) {
            fileTypeListModel.add(count, type);
            count++;
        }
        
        SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
        count = 0;
        try {
            DefaultListModel<DataSourceItem> dsListModel = (DefaultListModel<DataSourceItem>)dsList.getModel();
            for(DataSource ds : skCase.getDataSources()) {
                dsListModel.add(count, new DataSourceItem(ds));
            }
        } catch (Exception ex) {
            dsCheckBox.setEnabled(false);
            dsList.setEnabled(false);
        }
        
        count = 0;
        DefaultListModel<Frequency> frequencyListModel = (DefaultListModel<Frequency>)freqList.getModel();
        for (Frequency freq : Frequency.getOptionsForFiltering()) {
            frequencyListModel.add(count, freq);
        }
        
        count = 0;
        DefaultListModel<FileSize> sizeListModel = (DefaultListModel<FileSize>)sizeList.getModel();
        for (FileSize size : FileSize.values()) {
            sizeListModel.add(count, size);
        }
        
        count = 0;
        try {
            DefaultListModel<String> kwListModel = (DefaultListModel<String>)kwList.getModel();
            
            // TODO - use query
            List<BlackboardArtifact> arts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT);
            List<String> setNames = new ArrayList<>();
            for (BlackboardArtifact art : arts) {
                for (BlackboardAttribute attr : art.getAttributes()) {
                    if (attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()) {
                        String setName = attr.getValueString();
                        if ( ! setNames.contains(setName)) {
                            setNames.add(setName);
                        }
                    }
                }
            }
            Collections.sort(setNames);
            for(String name : setNames) {
                kwListModel.add(count, name);
            }
        } catch (Exception ex) {
            kwCheckBox.setEnabled(false);
            kwList.setEnabled(false);
        }       
        
        parentButtonGroup.add(parentFullRadioButton);
        parentButtonGroup.add(parentSubstringRadioButton);
        parentFullRadioButton.setSelected(true);
        parentListModel = (DefaultListModel<ParentSearchTerm>)parentList.getModel();
        
        for (GroupingAttributeType type : GroupingAttributeType.values()) {
            groupComboBox.addItem(type);
        }
        
        orderButtonGroup.add(orderAttrRadioButton);
        orderButtonGroup.add(orderSizeRadioButton);
        orderAttrRadioButton.setSelected(true);
        
        for (SortingMethod method : SortingMethod.values()) {
            fileOrderComboBox.addItem(method);
        }
    }
    
    List<FileSearchFiltering.FileFilter> getFilters() {
        List<FileSearchFiltering.FileFilter> filters = new ArrayList<>();
        
        // There will always be a file type selected
        filters.add(new FileSearchFiltering.FileTypeFilter(fileTypeList.getSelectedValuesList()));
        
        if (parentCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.ParentFilter(parentList.getSelectedValuesList()));
        }
        
        if (dsCheckBox.isSelected()) {
            List<DataSource> dataSources = dsList.getSelectedValuesList().stream().map(t -> t.ds).collect(Collectors.toList());
            filters.add(new FileSearchFiltering.DataSourceFilter(dataSources));
        }
        
        if (freqCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.FrequencyFilter(freqList.getSelectedValuesList()));
        }
        
        if (sizeCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.SizeFilter(sizeList.getSelectedValuesList()));
        }
        
        if (kwCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.KeywordListFilter(kwList.getSelectedValuesList()));
        }
        
        return filters;
    }
    
    FileSearch.AttributeType getGroupingAttribute() {
        GroupingAttributeType groupingAttrType = (GroupingAttributeType)groupComboBox.getSelectedItem();
        return groupingAttrType.getAttributeType();
    }
    
    FileGroup.GroupSortingAlgorithm getGroupSortingMethod() {
        if (orderAttrRadioButton.isSelected()) {
            return FileGroup.GroupSortingAlgorithm.BY_GROUP_KEY;
        }
        return FileGroup.GroupSortingAlgorithm.BY_GROUP_SIZE;
    }
    
    SortingMethod getFileSortingMethod() {
        return (SortingMethod)fileOrderComboBox.getSelectedItem();
    }
    
    
    private class DataSourceItem {
        private DataSource ds;
        
        DataSourceItem(DataSource ds) {
            this.ds = ds;
        }
        
        @Override
        public String toString() {
            return ds.getName() + " (ID: " + ds.getId() + ")";
        }
    }
    
    boolean searchCancelled() {
        return buttonPressed.equals(ButtonPressed.CANCEL);
    }
    
    private enum ButtonPressed {
        SEARCH,
        CANCEL
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        parentButtonGroup = new javax.swing.ButtonGroup();
        orderButtonGroup = new javax.swing.ButtonGroup();
        jLabel1 = new javax.swing.JLabel();
        cancelButton = new javax.swing.JButton();
        dsCheckBox = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        fileTypeList = new javax.swing.JList<>();
        jScrollPane2 = new javax.swing.JScrollPane();
        dsList = new javax.swing.JList<>();
        freqCheckBox = new javax.swing.JCheckBox();
        jScrollPane3 = new javax.swing.JScrollPane();
        freqList = new javax.swing.JList<>();
        jScrollPane4 = new javax.swing.JScrollPane();
        sizeList = new javax.swing.JList<>();
        sizeCheckBox = new javax.swing.JCheckBox();
        jScrollPane5 = new javax.swing.JScrollPane();
        kwList = new javax.swing.JList<>();
        kwCheckBox = new javax.swing.JCheckBox();
        jScrollPane6 = new javax.swing.JScrollPane();
        parentList = new javax.swing.JList<>();
        parentCheckBox = new javax.swing.JCheckBox();
        deleteParentButton = new javax.swing.JButton();
        addParentButton = new javax.swing.JButton();
        parentTextField = new javax.swing.JTextField();
        parentFullRadioButton = new javax.swing.JRadioButton();
        parentSubstringRadioButton = new javax.swing.JRadioButton();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        groupComboBox = new javax.swing.JComboBox<>();
        jLabel4 = new javax.swing.JLabel();
        orderAttrRadioButton = new javax.swing.JRadioButton();
        orderSizeRadioButton = new javax.swing.JRadioButton();
        jLabel5 = new javax.swing.JLabel();
        fileOrderComboBox = new javax.swing.JComboBox<>();
        searchButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.jLabel1.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(dsCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.dsCheckBox.text")); // NOI18N
        dsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dsCheckBoxActionPerformed(evt);
            }
        });

        fileTypeList.setModel(new DefaultListModel<FileType>());
        jScrollPane1.setViewportView(fileTypeList);

        dsList.setModel(new DefaultListModel<DataSourceItem>());
        dsList.setEnabled(false);
        jScrollPane2.setViewportView(dsList);

        org.openide.awt.Mnemonics.setLocalizedText(freqCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.freqCheckBox.text")); // NOI18N
        freqCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                freqCheckBoxActionPerformed(evt);
            }
        });

        freqList.setModel(new DefaultListModel<Frequency>());
        freqList.setEnabled(false);
        jScrollPane3.setViewportView(freqList);

        sizeList.setModel(new DefaultListModel<FileSize>());
        sizeList.setEnabled(false);
        jScrollPane4.setViewportView(sizeList);

        org.openide.awt.Mnemonics.setLocalizedText(sizeCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.sizeCheckBox.text")); // NOI18N
        sizeCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sizeCheckBoxActionPerformed(evt);
            }
        });

        kwList.setModel(new DefaultListModel<String>());
        kwList.setEnabled(false);
        jScrollPane5.setViewportView(kwList);

        org.openide.awt.Mnemonics.setLocalizedText(kwCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.kwCheckBox.text")); // NOI18N
        kwCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                kwCheckBoxActionPerformed(evt);
            }
        });

        parentList.setModel(new DefaultListModel<ParentSearchTerm>());
        parentList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        parentList.setEnabled(false);
        parentList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                parentListValueChanged(evt);
            }
        });
        jScrollPane6.setViewportView(parentList);

        org.openide.awt.Mnemonics.setLocalizedText(parentCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.parentCheckBox.text")); // NOI18N
        parentCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                parentCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deleteParentButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.deleteParentButton.text")); // NOI18N
        deleteParentButton.setEnabled(false);
        deleteParentButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteParentButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(addParentButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.addParentButton.text")); // NOI18N
        addParentButton.setEnabled(false);
        addParentButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addParentButtonActionPerformed(evt);
            }
        });

        parentTextField.setText(org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.parentTextField.text")); // NOI18N
        parentTextField.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(parentFullRadioButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.parentFullRadioButton.text")); // NOI18N
        parentFullRadioButton.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(parentSubstringRadioButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.parentSubstringRadioButton.text")); // NOI18N
        parentSubstringRadioButton.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.jLabel3.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.jLabel4.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(orderAttrRadioButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.orderAttrRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(orderSizeRadioButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.orderSizeRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel5, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.jLabel5.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(searchButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(dsCheckBox)
                            .addComponent(jLabel1)
                            .addComponent(freqCheckBox)
                            .addComponent(sizeCheckBox)
                            .addComponent(kwCheckBox)
                            .addComponent(parentCheckBox)
                            .addComponent(jLabel2))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(parentTextField)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(parentFullRadioButton)
                                        .addGap(18, 18, 18)
                                        .addComponent(parentSubstringRadioButton)
                                        .addGap(0, 0, Short.MAX_VALUE)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(deleteParentButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(addParentButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane4, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane5)
                            .addComponent(jScrollPane6, javax.swing.GroupLayout.Alignment.LEADING))
                        .addGap(35, 35, 35)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addComponent(jLabel4)
                            .addComponent(jLabel5))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(orderAttrRadioButton)
                            .addComponent(groupComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(orderSizeRadioButton)
                            .addComponent(fileOrderComboBox, 0, 144, Short.MAX_VALUE))
                        .addGap(0, 93, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel3)
                            .addComponent(groupComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(5, 5, 5)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel4)
                            .addComponent(orderAttrRadioButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(orderSizeRadioButton)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(dsCheckBox)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel5)
                        .addComponent(fileOrderComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(freqCheckBox)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 54, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sizeCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(kwCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(parentCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel2)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(deleteParentButton)
                    .addComponent(parentFullRadioButton)
                    .addComponent(parentSubstringRadioButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addParentButton)
                    .addComponent(parentTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 8, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(searchButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        setVisible(false);
        dispose();
        buttonPressed = ButtonPressed.CANCEL;
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void dsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dsCheckBoxActionPerformed
        dsList.setEnabled(dsCheckBox.isSelected());
    }//GEN-LAST:event_dsCheckBoxActionPerformed

    private void parentListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_parentListValueChanged
        if (parentList.getSelectedValuesList().isEmpty()) {
            deleteParentButton.setEnabled(false);
        } else {
            deleteParentButton.setEnabled(true);
        }
    }//GEN-LAST:event_parentListValueChanged

    private void deleteParentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteParentButtonActionPerformed
        int index = parentList.getSelectedIndex();
        parentListModel.remove(index);
    }//GEN-LAST:event_deleteParentButtonActionPerformed

    private void addParentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addParentButtonActionPerformed
        if ( ! parentTextField.getText().isEmpty()) {
            ParentSearchTerm searchTerm;
            if (parentFullRadioButton.isSelected()) {
                searchTerm = new ParentSearchTerm(parentTextField.getText(), true);
            } else {
                searchTerm = new ParentSearchTerm(parentTextField.getText(), false);
            }
            parentListModel.add(parentListModel.size(), searchTerm);
        }
    }//GEN-LAST:event_addParentButtonActionPerformed

    private void freqCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_freqCheckBoxActionPerformed
        freqList.setEnabled(freqCheckBox.isSelected());
    }//GEN-LAST:event_freqCheckBoxActionPerformed

    private void sizeCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sizeCheckBoxActionPerformed
        sizeList.setEnabled(sizeCheckBox.isSelected());
    }//GEN-LAST:event_sizeCheckBoxActionPerformed

    private void kwCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_kwCheckBoxActionPerformed
        kwList.setEnabled(kwCheckBox.isSelected());
    }//GEN-LAST:event_kwCheckBoxActionPerformed

    private void parentCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_parentCheckBoxActionPerformed
        parentList.setEnabled(parentCheckBox.isSelected());
        parentFullRadioButton.setEnabled(parentCheckBox.isSelected());
        parentSubstringRadioButton.setEnabled(parentCheckBox.isSelected());
        addParentButton.setEnabled(parentCheckBox.isSelected());
        deleteParentButton.setEnabled(parentCheckBox.isSelected());
    }//GEN-LAST:event_parentCheckBoxActionPerformed

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        buttonPressed = ButtonPressed.SEARCH;
        setVisible(false);
    }//GEN-LAST:event_searchButtonActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(FileSearchDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(FileSearchDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(FileSearchDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(FileSearchDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the dialog */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                FileSearchDialog dialog = new FileSearchDialog(new javax.swing.JFrame(), true);
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addParentButton;
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton deleteParentButton;
    private javax.swing.JCheckBox dsCheckBox;
    private javax.swing.JList<DataSourceItem> dsList;
    private javax.swing.JComboBox<SortingMethod> fileOrderComboBox;
    private javax.swing.JList<FileSearchData.FileType> fileTypeList;
    private javax.swing.JCheckBox freqCheckBox;
    private javax.swing.JList<Frequency> freqList;
    private javax.swing.JComboBox<GroupingAttributeType> groupComboBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JCheckBox kwCheckBox;
    private javax.swing.JList<String> kwList;
    private javax.swing.JRadioButton orderAttrRadioButton;
    private javax.swing.ButtonGroup orderButtonGroup;
    private javax.swing.JRadioButton orderSizeRadioButton;
    private javax.swing.ButtonGroup parentButtonGroup;
    private javax.swing.JCheckBox parentCheckBox;
    private javax.swing.JRadioButton parentFullRadioButton;
    private javax.swing.JList<ParentSearchTerm> parentList;
    private javax.swing.JRadioButton parentSubstringRadioButton;
    private javax.swing.JTextField parentTextField;
    private javax.swing.JButton searchButton;
    private javax.swing.JCheckBox sizeCheckBox;
    private javax.swing.JList<FileSize> sizeList;
    // End of variables declaration//GEN-END:variables
}
