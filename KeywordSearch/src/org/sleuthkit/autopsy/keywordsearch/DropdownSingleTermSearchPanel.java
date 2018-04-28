/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JMenuItem;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A dropdown panel that provides GUI components that allow a user to do three
 * types of ad hoc single keyword searches. The first option is a standard
 * Lucene query for one or more terms, with or without wildcards and explicit
 * Boolean operators, or a phrase. The second option is a Lucene query for a
 * substring of a single term. The third option is a regex query using first the
 * terms component, followed by standard Lucene queries for any terms found.
 *
 * The toolbar uses a different font from the rest of the application,
 * Monospaced 14, due to the necessity to find a font that displays both Arabic
 * and Asian fonts at an acceptable size. The default, Tahoma 14, could not
 * perform this task at the desired size, and neither could numerous other
 * fonts.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class DropdownSingleTermSearchPanel extends AdHocSearchPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DropdownSingleTermSearchPanel.class.getName());
    private static DropdownSingleTermSearchPanel defaultInstance = null;

    /**
     * Gets the default instance of a dropdown panel that provides GUI
     * components that allow a user to do three types of ad hoc single keyword
     * searches.
     * @return the default instance of DropdownSingleKeywordSearchPanel
     */
    public static synchronized DropdownSingleTermSearchPanel getDefault() {
        if (null == defaultInstance) {
            defaultInstance = new DropdownSingleTermSearchPanel();
        }
        return defaultInstance;
    }

    /**
     * Constructs a dropdown panel that provides GUI components that allow a
     * user to do three types of ad hoc single keyword searches.
     */
    public DropdownSingleTermSearchPanel() {
        initComponents();
        customizeComponents();
    }

    /**
     * Does additional initialization of the GUI components created by the
     * initComponents method.
     */
    private void customizeComponents() {
        keywordTextField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (keywordTextField.getText().isEmpty()) {
                    clearSearchBox();
                }
            }
        });

        keywordTextField.setComponentPopupMenu(rightClickMenu);
        ActionListener actList = (ActionEvent e) -> {
            JMenuItem jmi = (JMenuItem) e.getSource();
            if (jmi.equals(cutMenuItem)) {
                keywordTextField.cut();
            } else if (jmi.equals(copyMenuItem)) {
                keywordTextField.copy();
            } else if (jmi.equals(pasteMenuItem)) {
                keywordTextField.paste();
            } else if (jmi.equals(selectAllMenuItem)) {
                keywordTextField.selectAll();
            }
        };
        cutMenuItem.addActionListener(actList);
        copyMenuItem.addActionListener(actList);
        pasteMenuItem.addActionListener(actList);
        selectAllMenuItem.addActionListener(actList);
    }

    /**
     * Add an action listener to the Search buttom component of the panel.
     *
     * @param actionListener The actin listener.
     */
    void addSearchButtonActionListener(ActionListener actionListener) {
        searchButton.addActionListener(actionListener);
    }

    /**
     * Clears the text in the query text field, i.e., sets it to the emtpy
     * string.
     */
    void clearSearchBox() {
        keywordTextField.setText("");
    }

    void setRegexSearchEnabled(boolean enabled) {
        exactRadioButton.setSelected(true);
        regexRadioButton.setEnabled(enabled);
    }
    
    /**
     * Gets a single keyword list consisting of a single keyword encapsulating
     * the input term(s)/phrase/substring/regex.
     *
     * @return The keyword list.
     */
    @NbBundle.Messages({"DropdownSingleTermSearchPanel.warning.title=Warning",
    "DropdownSingleTermSearchPanel.warning.text=Boundary characters ^ and $ do not match word boundaries. Consider\nreplacing with an explicit list of boundary characters, such as [ \\.,]"})
    @Override
    List<KeywordList> getKeywordLists() {
        
        if (regexRadioButton.isSelected()) {
            if((keywordTextField.getText() != null)  && 
                    (keywordTextField.getText().startsWith("^") || 
                    (keywordTextField.getText().endsWith("$") && ! keywordTextField.getText().endsWith("\\$")))) {

                KeywordSearchUtil.displayDialog(NbBundle.getMessage(this.getClass(), "DropdownSingleTermSearchPanel.warning.title"),
                        NbBundle.getMessage(this.getClass(), "DropdownSingleTermSearchPanel.warning.text"),
                        KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
            }
        }
        
        List<Keyword> keywords = new ArrayList<>();
        keywords.add(new Keyword(keywordTextField.getText(), !regexRadioButton.isSelected(), exactRadioButton.isSelected()));
        List<KeywordList> keywordLists = new ArrayList<>();
        keywordLists.add(new KeywordList(keywords));
        return keywordLists;
    }

    /**
     * Not implemented.
     */
    @Override
    protected void postFilesIndexedChange() {
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

        org.openide.awt.Mnemonics.setLocalizedText(cutMenuItem, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.cutMenuItem.text")); // NOI18N
        rightClickMenu.add(cutMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(copyMenuItem, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(pasteMenuItem, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.pasteMenuItem.text")); // NOI18N
        rightClickMenu.add(pasteMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(selectAllMenuItem, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        keywordTextField.setFont(new java.awt.Font("Monospaced", 0, 14)); // NOI18N
        keywordTextField.setText(org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.keywordTextField.text")); // NOI18N
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
        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        queryTypeButtonGroup.add(exactRadioButton);
        exactRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(exactRadioButton, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.exactRadioButton.text")); // NOI18N

        queryTypeButtonGroup.add(substringRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(substringRadioButton, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.substringRadioButton.text")); // NOI18N

        queryTypeButtonGroup.add(regexRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(regexRadioButton, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.regexRadioButton.text")); // NOI18N

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

    /**
     * Action performed by the action listener for the search button.
     *
     * @param evt The action event.
     */
    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        keywordTextFieldActionPerformed(evt);
    }//GEN-LAST:event_searchButtonActionPerformed

    /**
     * Action performed by the action listener for the keyword text field.
     *
     * @param evt The action event.
     */
    private void keywordTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keywordTextFieldActionPerformed
        try {
            search();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error performing ad hoc single keyword search", e); //NON-NLS
        }
    }//GEN-LAST:event_keywordTextFieldActionPerformed

    /**
     * Mouse event handler for the keyword text field.
     *
     * @param evt The mouse event.
     */
    private void keywordTextFieldMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_keywordTextFieldMouseClicked
        if (evt.isPopupTrigger()) {
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
