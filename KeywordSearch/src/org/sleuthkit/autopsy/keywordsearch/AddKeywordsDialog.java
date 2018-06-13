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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

/**
 * Dialog to add one or more keywords to a list
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class AddKeywordsDialog extends javax.swing.JDialog {

    List<String> newKeywords = new ArrayList<>();
    private javax.swing.JTextArea keywordTextArea;
    /**
     * Creates new form AddKeywordsDialog. Note that this does not display the
     * dialog - call display() after creation.
     *
     * @param initialKeywords Keywords to populate the list with
     * @param type            Starting keyword type
     */
    AddKeywordsDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                NbBundle.getMessage(AddKeywordsDialog.class, "AddKeywordsDialog.addKeywordsTitle.text"),
                true);
        initComponents();
        // Set the add button to only be active when there is text in the text area
        addButton.setEnabled(false);
        initKeywordTextArea();      
    }

    /**
     * Display the dialog
     */
    void display() {
        newKeywords.clear();
        setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    private void initKeywordTextArea() {
        keywordTextArea = new javax.swing.JTextArea() {
            //Override the paste action for this jtext area to always append pasted text with a new line if necessary
            @Override
            public void paste() {
                //if the cursor position is not at the start of a new line add the new line symbol before the pasted text
                if (!(keywordTextArea.getDocument().getLength()==0) && !keywordTextArea.getText().endsWith("\n")) {
                    keywordTextArea.append(System.getProperty("line.separator"));
                }
                keywordTextArea.setCaretPosition(keywordTextArea.getDocument().getLength());
                super.paste();           
            }
        };
        keywordTextArea.setColumns(
                20);
        keywordTextArea.setRows(
                5);
        keywordTextArea.addMouseListener(
                new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt
            ) {
                keywordTextAreaMouseClicked(evt);
            }
        }
        );
        jScrollPane1.setViewportView(keywordTextArea);

        keywordTextArea.getDocument()
                .addDocumentListener(new DocumentListener() {
                    @Override
                    public void changedUpdate(DocumentEvent e
                    ) {
                        fire();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e
                    ) {
                        fire();
                    }

                    @Override
                    public void insertUpdate(DocumentEvent e
                    ) {
                        fire();
                    }

                    private void fire() {
                        enableButtons();
                    }
                }
                );
    }

    /**
     * Set the initial contents of the text box. Intended to be used to
     * redisplay any keywords that contained errors
     *
     * @param initialKeywords
     */
    void setInitialKeywordList(String initialKeywords, boolean isLiteral, boolean isWholeWord) {
        keywordTextArea.setText(initialKeywords);
        if (!isLiteral) {
            regexRadioButton.setSelected(true);
        } else if (isWholeWord) {
            exactRadioButton.setSelected(true);
        } else {
            substringRadioButton.setSelected(true);
        }
    }

    private void enableButtons() {
        addButton.setEnabled(!keywordTextArea.getText().isEmpty());
    }

    /**
     * Get the list of keywords from the text area
     *
     * @return list of keywords
     */
    List<String> getKeywords() {
        return newKeywords;
    }

    /**
     * Get whether the regex option is selected
     *
     * @return true if the regex radio button is selected
     */
    boolean isKeywordRegex() {
        return regexRadioButton.isSelected();
    }

    /**
     * Get whether the exact match option is selected
     *
     * @return true if the exact match radio button is selected
     */
    boolean isKeywordExact() {
        return exactRadioButton.isSelected();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        keywordTypeButtonGroup = new javax.swing.ButtonGroup();
        exactRadioButton = new javax.swing.JRadioButton();
        substringRadioButton = new javax.swing.JRadioButton();
        regexRadioButton = new javax.swing.JRadioButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        enterKeywordsLabel = new javax.swing.JLabel();
        keywordTypeLabel = new javax.swing.JLabel();
        addButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        pasteButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        keywordTypeButtonGroup.add(exactRadioButton);
        exactRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(exactRadioButton, org.openide.util.NbBundle.getMessage(AddKeywordsDialog.class, "AddKeywordsDialog.exactRadioButton.text")); // NOI18N

        keywordTypeButtonGroup.add(substringRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(substringRadioButton, org.openide.util.NbBundle.getMessage(AddKeywordsDialog.class, "AddKeywordsDialog.substringRadioButton.text")); // NOI18N

        keywordTypeButtonGroup.add(regexRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(regexRadioButton, org.openide.util.NbBundle.getMessage(AddKeywordsDialog.class, "AddKeywordsDialog.regexRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(enterKeywordsLabel, org.openide.util.NbBundle.getMessage(AddKeywordsDialog.class, "AddKeywordsDialog.enterKeywordsLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(keywordTypeLabel, org.openide.util.NbBundle.getMessage(AddKeywordsDialog.class, "AddKeywordsDialog.keywordTypeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(addButton, org.openide.util.NbBundle.getMessage(AddKeywordsDialog.class, "AddKeywordsDialog.addButton.text")); // NOI18N
        addButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(AddKeywordsDialog.class, "AddKeywordsDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(pasteButton, org.openide.util.NbBundle.getMessage(AddKeywordsDialog.class, "AddKeywordsDialog.pasteButton.text")); // NOI18N
        pasteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pasteButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(enterKeywordsLabel)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 249, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pasteButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(addButton, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(keywordTypeLabel)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(substringRadioButton)
                            .addComponent(exactRadioButton)
                            .addComponent(regexRadioButton))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(enterKeywordsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(keywordTypeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(exactRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(substringRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(regexRadioButton)
                        .addGap(194, 194, 194))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 278, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(5, 5, 5)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(addButton)
                        .addComponent(cancelButton))
                    .addComponent(pasteButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void pasteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteButtonActionPerformed
        keywordTextArea.paste();
    }//GEN-LAST:event_pasteButtonActionPerformed

    private void addButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addButtonActionPerformed
        // Save the values from the list
        newKeywords.addAll(Arrays.asList(keywordTextArea.getText().split("\\r?\\n")));

        setVisible(false);
        dispose();
    }//GEN-LAST:event_addButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        setVisible(false);
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void keywordTextAreaMouseClicked(java.awt.event.MouseEvent evt) {
        if (SwingUtilities.isRightMouseButton(evt)) {
            JPopupMenu popup = new JPopupMenu();

            JMenuItem cutMenu = new JMenuItem("Cut"); // NON-NLS
            cutMenu.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    keywordTextArea.cut();
                }
            });

            JMenuItem copyMenu = new JMenuItem("Copy"); // NON-NLS
            copyMenu.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    keywordTextArea.copy();
                }
            });

            JMenuItem pasteMenu = new JMenuItem("Paste"); // NON-NLS
            pasteMenu.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    keywordTextArea.paste();
                }
            });

            popup.add(cutMenu);
            popup.add(copyMenu);
            popup.add(pasteMenu);
            popup.show(keywordTextArea, evt.getX(), evt.getY());
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addButton;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel enterKeywordsLabel;
    private javax.swing.JRadioButton exactRadioButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.ButtonGroup keywordTypeButtonGroup;
    private javax.swing.JLabel keywordTypeLabel;
    private javax.swing.JButton pasteButton;
    private javax.swing.JRadioButton regexRadioButton;
    private javax.swing.JRadioButton substringRadioButton;
    // End of variables declaration//GEN-END:variables
}
