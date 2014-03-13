/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

/*
 * CasePropertiesForm.java
 *
 * Created on Mar 14, 2011, 1:48:20 PM
 */

package org.sleuthkit.autopsy.casemodule;

import java.awt.event.ActionListener;
import java.io.File;
import java.util.Map;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.actions.CallableSystemAction;

/**
 * The form where user can change / update the properties of the current case.
 *
 * @author jantonius
 */
class CasePropertiesForm extends javax.swing.JPanel{

    Case current = null;
    private static JPanel caller;    // panel for error
    
    // Shrink a path to fit in targetLength (if necessary), by replaceing part
    // of the path with "...". Ex: "C:\Users\bob\...\folder\other\Image.img"
    private String shrinkPath(String path, int targetLength) {
        if(path.length() > targetLength){
            String fill = "...";
            
            int partsLength = targetLength - fill.length();
            
            String front = path.substring(0, partsLength/4);
            int frontSep = front.lastIndexOf(File.separatorChar);
            if (frontSep != -1) {
                front = front.substring(0, frontSep+1);
            }
            
            String back = path.substring(partsLength*3/4);
            int backSep = back.indexOf(File.separatorChar);
            if (backSep != -1) {
                back = back.substring(backSep);
            }
            return back + fill + front;
        } else {
            return path;
        }
    }
    
    
    /** Creates new form CasePropertiesForm */
    CasePropertiesForm(Case currentCase, String crDate, String caseDir, Map<Long, String> imgPaths) {
        initComponents();
        caseNameTextField.setText(currentCase.getName());
        caseNumberTextField.setText(currentCase.getNumber());
        examinerTextField.setText(currentCase.getExaminer());
        crDateTextField.setText(crDate);
        caseDirTextArea.setText(caseDir);

        current = currentCase;

        int totalImages = imgPaths.size();
       
        // create the headers and add all the rows
        String[] headers = {"Path"};
        String[][] rows = new String[totalImages][];
        
        int i = 0;
        for(long key : imgPaths.keySet()){
            String path = imgPaths.get(key);
            String shortenPath = shrinkPath(path, 70);
            rows[i++] = new String[]{shortenPath};
        }

        // create the table inside with the imgPaths information
        DefaultTableModel model = new DefaultTableModel(rows, headers)
        {
            @Override
            // make the cells in the FileContentTable "read only"
            public boolean isCellEditable(int row, int column){
                return false;
                //return column == lastColumn; // make the last column (Remove button), only the editable
            }
        };
        imagesTable.setModel(model);
        
//        // set the size of the remove column
//        TableColumn removeCol = imagesTable.getColumnModel().getColumn(lastColumn);
//        removeCol.setPreferredWidth(75);
//        removeCol.setMaxWidth(75);
//        removeCol.setMinWidth(75);
//        removeCol.setResizable(false);

//        // create the delete action to remove the image from the current case
//        Action delete = new AbstractAction()
//        {
//            @Override
//            public void actionPerformed(ActionEvent e)
//            {
//                // get the image path
//                JTable table = (JTable)e.getSource();
//                int modelRow = Integer.valueOf(e.getActionCommand());
//                String removeColumn = table.getValueAt(modelRow, lastColumn).toString();
//                // get the image ID
//                int selectedID = Integer.parseInt(removeColumn.substring(0, removeColumn.indexOf('|')));
//                String imagePath = removeColumn.substring(removeColumn.indexOf('|') + 1);
//
//                // throw the confirmation first
//                String confMsg = "Are you sure want to remove image \"" + imagePath + "\" from this case?";
//                NotifyDescriptor d = new NotifyDescriptor.Confirmation(confMsg, "Create directory", NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE);
//                d.setValue(NotifyDescriptor.NO_OPTION);
//
//                Object res = DialogDisplayer.getDefault().notify(d);
//                // if user select "Yes"
//                if(res != null && res == DialogDescriptor.YES_OPTION){
//                    // remove the image in the case class and in the xml config file
//                    try {
//                        current.removeImage(selectedID, imagePath);
//                    } catch (Exception ex) {
//                        Logger.getLogger(CasePropertiesForm.class.getName()).log(Level.WARNING, "Error: couldn't remove image.", ex);
//                    }
//                    // remove the row of the image path
//                    ((DefaultTableModel)table.getModel()).removeRow(modelRow);
//                }
//            }
//        };
//
//        ButtonColumn buttonColumn = new ButtonColumn(imagesTable, delete, 1, "Remove");
//        buttonColumn.setMnemonic(KeyEvent.VK_D);
    }

    /** This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        casePropLabel = new javax.swing.JLabel();
        caseNameLabel = new javax.swing.JLabel();
        crDateLabel = new javax.swing.JLabel();
        caseDirLabel = new javax.swing.JLabel();
        crDateTextField = new javax.swing.JTextField();
        caseNameTextField = new javax.swing.JTextField();
        updateCaseNameButton = new javax.swing.JButton();
        genInfoLabel = new javax.swing.JLabel();
        imgInfoLabel = new javax.swing.JLabel();
        OKButton = new javax.swing.JButton();
        imagesTableScrollPane = new javax.swing.JScrollPane();
        imagesTable = new javax.swing.JTable();
        jScrollPane2 = new javax.swing.JScrollPane();
        caseDirTextArea = new javax.swing.JTextArea();
        deleteCaseButton = new javax.swing.JButton();
        caseNumberLabel = new javax.swing.JLabel();
        examinerLabel = new javax.swing.JLabel();
        caseNumberTextField = new javax.swing.JTextField();
        examinerTextField = new javax.swing.JTextField();

        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane1.setViewportView(jTextArea1);

        casePropLabel.setFont(new java.awt.Font("Tahoma", 1, 24));
        casePropLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        casePropLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.casePropLabel.text")); // NOI18N

        caseNameLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.caseNameLabel.text")); // NOI18N

        crDateLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.crDateLabel.text")); // NOI18N

        caseDirLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.caseDirLabel.text")); // NOI18N

        crDateTextField.setEditable(false);
        crDateTextField.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.crDateTextField.text")); // NOI18N

        caseNameTextField.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.caseNameTextField.text")); // NOI18N

        updateCaseNameButton.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.updateCaseNameButton.text")); // NOI18N
        updateCaseNameButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateCaseNameButtonActionPerformed(evt);
            }
        });

        genInfoLabel.setFont(new java.awt.Font("Tahoma", 1, 14));
        genInfoLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.genInfoLabel.text")); // NOI18N

        imgInfoLabel.setFont(new java.awt.Font("Tahoma", 1, 14));
        imgInfoLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.imgInfoLabel.text")); // NOI18N

        OKButton.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.OKButton.text")); // NOI18N

        imagesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Path", "Remove"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, true
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        imagesTable.setShowHorizontalLines(false);
        imagesTable.setShowVerticalLines(false);
        imagesTable.getTableHeader().setReorderingAllowed(false);
        imagesTable.setUpdateSelectionOnSort(false);
        imagesTableScrollPane.setViewportView(imagesTable);

        caseDirTextArea.setBackground(new java.awt.Color(240, 240, 240));
        caseDirTextArea.setColumns(20);
        caseDirTextArea.setEditable(false);
        caseDirTextArea.setRows(1);
        caseDirTextArea.setRequestFocusEnabled(false);
        jScrollPane2.setViewportView(caseDirTextArea);

        deleteCaseButton.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.deleteCaseButton.text")); // NOI18N
        deleteCaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteCaseButtonActionPerformed(evt);
            }
        });

        caseNumberLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.caseNumberLabel.text")); // NOI18N

        examinerLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.examinerLabel.text")); // NOI18N

        caseNumberTextField.setEditable(false);
        caseNumberTextField.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.caseNumberTextField.text")); // NOI18N

        examinerTextField.setEditable(false);
        examinerTextField.setText(org.openide.util.NbBundle.getMessage(CasePropertiesForm.class, "CasePropertiesForm.examinerTextField.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(casePropLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 440, Short.MAX_VALUE)
                    .addComponent(genInfoLabel)
                    .addComponent(imgInfoLabel)
                    .addComponent(imagesTableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 440, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(181, 181, 181)
                        .addComponent(OKButton, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(caseNameLabel)
                                    .addComponent(caseNumberLabel))
                                .addGap(25, 25, 25)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(caseNameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 246, Short.MAX_VALUE)
                                    .addComponent(caseNumberTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 246, Short.MAX_VALUE)))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(examinerLabel)
                                .addGap(45, 45, 45)
                                .addComponent(examinerTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 246, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(caseDirLabel)
                                    .addComponent(crDateLabel))
                                .addGap(18, 18, 18)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 246, Short.MAX_VALUE)
                                    .addComponent(crDateTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 246, Short.MAX_VALUE))))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(updateCaseNameButton)
                            .addComponent(deleteCaseButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(casePropLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(genInfoLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(caseNameLabel)
                    .addComponent(caseNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(updateCaseNameButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(caseNumberLabel)
                    .addComponent(caseNumberTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(examinerLabel)
                    .addComponent(examinerTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 19, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(crDateLabel)
                    .addComponent(crDateTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(caseDirLabel)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(39, 39, 39)
                        .addComponent(imgInfoLabel))
                    .addComponent(deleteCaseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(imagesTableScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 170, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(OKButton)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Updates the case name.
     *
     * @param evt  The action event
     */
    private void updateCaseNameButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateCaseNameButtonActionPerformed
        String oldCaseName = Case.getCurrentCase().getName();
        String newCaseName = caseNameTextField.getText();
        //String oldPath = caseDirTextArea.getText() + File.separator + oldCaseName + ".aut";
        //String newPath = caseDirTextArea.getText() + File.separator + newCaseName + ".aut";

        // check if the old and new case name is not equal
        if(!oldCaseName.equals(newCaseName)){

            // check if the case name is empty
            if(newCaseName.trim().equals("")){
                JOptionPane.showMessageDialog(caller,
                                              NbBundle.getMessage(this.getClass(),
                                                                  "CasePropertiesForm.updateCaseName.msgDlg.empty.msg"),
                                              NbBundle.getMessage(this.getClass(),
                                                                  "CasePropertiesForm.updateCaseName.msgDlg.empty.title"),
                                              JOptionPane.ERROR_MESSAGE);
            }
            else{
                // check if case Name contain one of this following symbol:
                //  \ / : * ? " < > |
                if(newCaseName.contains("\\") || newCaseName.contains("/") || newCaseName.contains(":") ||
                   newCaseName.contains("*") || newCaseName.contains("?") || newCaseName.contains("\"") ||
                   newCaseName.contains("<") || newCaseName.contains(">") || newCaseName.contains("|")){
                    String errorMsg = NbBundle
                            .getMessage(this.getClass(), "CasePropertiesForm.updateCaseName.msgDlg.invalidSymbols.msg");
                    JOptionPane.showMessageDialog(caller, errorMsg,
                                                  NbBundle.getMessage(this.getClass(),
                                                                      "CasePropertiesForm.updateCaseName.msgDlg.invalidSymbols.title"),
                                                  JOptionPane.ERROR_MESSAGE);
                }
                else{
                    // ask for the confirmation first
                    String confMsg = NbBundle
                            .getMessage(this.getClass(), "CasePropertiesForm.updateCaseName.confMsg.msg", oldCaseName,
                                        newCaseName);
                    NotifyDescriptor d = new NotifyDescriptor.Confirmation(confMsg,
                                                                           NbBundle.getMessage(this.getClass(),
                                                                                               "CasePropertiesForm.updateCaseName.confMsg.title"),
                                                                           NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE);
                    d.setValue(NotifyDescriptor.NO_OPTION);

                    Object res = DialogDisplayer.getDefault().notify(d);
                    if(res != null && res == DialogDescriptor.YES_OPTION){
                        // if user select "Yes"
                        String oldPath = current.getConfigFilePath();
                        try {
                            current.updateCaseName(oldCaseName, oldPath , newCaseName, oldPath);
                        } catch (Exception ex) {
                            Logger.getLogger(CasePropertiesForm.class.getName()).log(Level.WARNING, "Error: problem updating case name.", ex);
                        }
                    }
                }
            }
        }
    }//GEN-LAST:event_updateCaseNameButtonActionPerformed

    private void deleteCaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteCaseButtonActionPerformed
        CallableSystemAction.get(CaseDeleteAction.class).actionPerformed(evt);
    }//GEN-LAST:event_deleteCaseButtonActionPerformed


    /**
     * Sets the listener for the OK button
     *
     * @param e  The action listener
     */
    public void setOKButtonActionListener(ActionListener e){
        OKButton.addActionListener(e);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton OKButton;
    private javax.swing.JLabel caseDirLabel;
    private javax.swing.JTextArea caseDirTextArea;
    private javax.swing.JLabel caseNameLabel;
    private javax.swing.JTextField caseNameTextField;
    private javax.swing.JLabel caseNumberLabel;
    private javax.swing.JTextField caseNumberTextField;
    private javax.swing.JLabel casePropLabel;
    private javax.swing.JLabel crDateLabel;
    private javax.swing.JTextField crDateTextField;
    private javax.swing.JButton deleteCaseButton;
    private javax.swing.JLabel examinerLabel;
    private javax.swing.JTextField examinerTextField;
    private javax.swing.JLabel genInfoLabel;
    private javax.swing.JTable imagesTable;
    private javax.swing.JScrollPane imagesTableScrollPane;
    private javax.swing.JLabel imgInfoLabel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JButton updateCaseNameButton;
    // End of variables declaration//GEN-END:variables

}
