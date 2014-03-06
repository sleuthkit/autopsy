/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;
import javax.swing.JMenuItem;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A simple UI for finding text after ingest
 */
public class DropdownSearchPanel extends AbstractKeywordSearchPerformer {
    private static final Logger logger = Logger.getLogger(DropdownSearchPanel.class.getName());
    private static DropdownSearchPanel instance = null;
//    private boolean entered = false;
    
    /**
     * Creates new form DropdownSearchPanel
     */
    public DropdownSearchPanel() {
        initComponents();
        customizeComponents();
    }
    
    private void customizeComponents() {
        keywordTextField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
//                if (keywordTextField.getText()
//                             .equals(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class,
//                                                                          "KeywordSearchPanel.keywordTextField.text"))) {
//                    keywordTextField.setText("");
//                    keywordTextField.setForeground(Color.BLACK);
//                    entered = true;
//                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (keywordTextField.getText().equals("")) {
                    resetSearchBox();
                }
            }
        });
        
        keywordTextField.setComponentPopupMenu(rightClickMenu);
        ActionListener actList = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem jmi = (JMenuItem) e.getSource();
                if (jmi.equals(cutMenuItem)) {
                    keywordTextField.cut();
                } else if (jmi.equals(copyMenuItem)) {
                    keywordTextField.copy();
                } else if (jmi.equals(pasteMenuItem)) {
//                    if (keywordTextField.getText()
//                                 .equals(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class,
//                                                                              "KeywordSearchPanel.keywordTextField.text"))) {
//                        keywordTextField.setText("");
//                        keywordTextField.setForeground(Color.BLACK);
//                        entered = true;
//                    }
                    keywordTextField.paste();
                } else if (jmi.equals(selectAllMenuItem)) {
                    keywordTextField.selectAll();
                }
            }
        };
        cutMenuItem.addActionListener(actList);
        copyMenuItem.addActionListener(actList);
        pasteMenuItem.addActionListener(actList);
        selectAllMenuItem.addActionListener(actList);        
    }
    
    public static synchronized DropdownSearchPanel getDefault() {
        if (instance == null) {
            instance = new DropdownSearchPanel();
        }
        return instance;
    }
    
    void addSearchButtonActionListener(ActionListener actionListener) {
        searchButton.addActionListener(actionListener);
    }    
       
    public void resetSearchBox() {
        keywordTextField.setText("");
//        keywordTextField.setEditable(true);
//        keywordTextField.setText(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class,
//                                                               "KeywordSearchPanel.keywordTextField.text"));
//        keywordTextField.setForeground(Color.LIGHT_GRAY);
        //entered = false;
    }       
    
    @Override
    public String getQueryText() {
        return keywordTextField.getText();
    }

    @Override
    public boolean isRegExQuerySelected() {
        return regexRadioButton.isSelected();
    }

    @Override
    public boolean isWholewordQuerySelected() {
        return exactRadioButton.isSelected();
    }
    
    @Override
    public boolean isMultiwordQuery() {
        return false;
    }

    @Override
    public List<Keyword> getQueryList() {
        throw new UnsupportedOperationException("No list for single-keyword search");
    }    

    @Override
    protected void postFilesIndexedChange() {
        //nothing to update
    }    
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        queryTypeButtonGroup = new javax.swing.ButtonGroup();
        rightClickMenu = new javax.swing.JPopupMenu();
        cutMenuItem = new javax.swing.JMenuItem();
        copyMenuItem = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        selectAllMenuItem = new javax.swing.JMenuItem();
        keywordTextField = new javax.swing.JTextField();
        searchButton = new javax.swing.JButton();
        exactRadioButton = new javax.swing.JRadioButton();
        substringRadioButton = new javax.swing.JRadioButton();
        regexRadioButton = new javax.swing.JRadioButton();

        org.openide.awt.Mnemonics.setLocalizedText(cutMenuItem, org.openide.util.NbBundle.getMessage(DropdownSearchPanel.class, "DropdownSearchPanel.cutMenuItem.text")); // NOI18N
        rightClickMenu.add(cutMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(copyMenuItem, org.openide.util.NbBundle.getMessage(DropdownSearchPanel.class, "DropdownSearchPanel.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(pasteMenuItem, org.openide.util.NbBundle.getMessage(DropdownSearchPanel.class, "DropdownSearchPanel.pasteMenuItem.text")); // NOI18N
        rightClickMenu.add(pasteMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(selectAllMenuItem, org.openide.util.NbBundle.getMessage(DropdownSearchPanel.class, "DropdownSearchPanel.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        keywordTextField.setFont(new java.awt.Font("Monospaced", 0, 14)); // NOI18N
        keywordTextField.setText(org.openide.util.NbBundle.getMessage(DropdownSearchPanel.class, "DropdownSearchPanel.keywordTextField.text")); // NOI18N
        keywordTextField.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(192, 192, 192), 1, true));
        keywordTextField.setMinimumSize(new java.awt.Dimension(2, 25));
        keywordTextField.setPreferredSize(new java.awt.Dimension(2, 25));
        keywordTextField.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                keywordTextFieldMouseClicked(evt);
            }
        });
        keywordTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                keywordTextFieldActionPerformed(evt);
            }
        });

        searchButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/search-icon.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(DropdownSearchPanel.class, "DropdownSearchPanel.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        queryTypeButtonGroup.add(exactRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(exactRadioButton, org.openide.util.NbBundle.getMessage(DropdownSearchPanel.class, "DropdownSearchPanel.exactRadioButton.text")); // NOI18N

        queryTypeButtonGroup.add(substringRadioButton);
        substringRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(substringRadioButton, org.openide.util.NbBundle.getMessage(DropdownSearchPanel.class, "DropdownSearchPanel.substringRadioButton.text")); // NOI18N

        queryTypeButtonGroup.add(regexRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(regexRadioButton, org.openide.util.NbBundle.getMessage(DropdownSearchPanel.class, "DropdownSearchPanel.regexRadioButton.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(keywordTextField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(searchButton))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(exactRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(substringRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(regexRadioButton)
                        .addGap(0, 27, Short.MAX_VALUE)))
                .addGap(5, 5, 5))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(keywordTextField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(searchButton, javax.swing.GroupLayout.DEFAULT_SIZE, 26, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exactRadioButton)
                    .addComponent(substringRadioButton)
                    .addComponent(regexRadioButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        keywordTextFieldActionPerformed(evt);
    }//GEN-LAST:event_searchButtonActionPerformed

    private void keywordTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keywordTextFieldActionPerformed
//        if (!entered) {
//            return;
//        }
        //getRootPane().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            search();
        } finally {
            //getRootPane().setCursor(null);
        }
    }//GEN-LAST:event_keywordTextFieldActionPerformed

    private void keywordTextFieldMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_keywordTextFieldMouseClicked
        if(evt.isPopupTrigger()) {
            rightClickMenu.show(evt.getComponent(), evt.getX(), evt.getY());
        }
    }//GEN-LAST:event_keywordTextFieldMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JMenuItem cutMenuItem;
    private javax.swing.JRadioButton exactRadioButton;
    private javax.swing.JTextField keywordTextField;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.ButtonGroup queryTypeButtonGroup;
    private javax.swing.JRadioButton regexRadioButton;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JButton searchButton;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JRadioButton substringRadioButton;
    // End of variables declaration//GEN-END:variables
  
 
}