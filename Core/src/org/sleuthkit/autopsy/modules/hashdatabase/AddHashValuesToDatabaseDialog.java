/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.HashEntry;

/**
 *
 * @author sidhesh
 */
public class AddHashValuesToDatabaseDialog extends javax.swing.JDialog {
    
    Pattern md5Pattern = Pattern.compile("[a-f0-9]{32}");
    List<HashEntry> hashes = new ArrayList<>();

    /**
     * Displays a dialog that allows a user to add hash values to the selected database.
     */    
    AddHashValuesToDatabaseDialog(String databaseName) {
        super(new JFrame(),
              "Add Hash Values to - " + databaseName,
              true);
        initComponents();
        display();
    }
    
    
    
    private void display() {
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenDimension.width - getSize().width) / 2, (screenDimension.height - getSize().height) / 2);
        setVisible(true);                        
    }
    
    List<HashEntry> getHashValuesAddedToDatabase() {
        return hashes;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPopupMenu1 = new javax.swing.JPopupMenu();
        jScrollPane1 = new javax.swing.JScrollPane();
        hashValuesTextArea = new javax.swing.JTextArea();
        AddValuesToHashDatabaseButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        pasteFromClipboardButton = new javax.swing.JButton();
        instructionLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        hashValuesTextArea.setColumns(20);
        hashValuesTextArea.setRows(5);
        hashValuesTextArea.setToolTipText(org.openide.util.NbBundle.getMessage(AddHashValuesToDatabaseDialog.class, "AddHashValuesToDatabaseDialog.hashValuesTextArea.toolTipText")); // NOI18N
        hashValuesTextArea.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                hashValuesTextAreaMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(hashValuesTextArea);

        org.openide.awt.Mnemonics.setLocalizedText(AddValuesToHashDatabaseButton, org.openide.util.NbBundle.getMessage(AddHashValuesToDatabaseDialog.class, "AddHashValuesToDatabaseDialog.AddValuesToHashDatabaseButton.text")); // NOI18N
        AddValuesToHashDatabaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddValuesToHashDatabaseButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(AddHashValuesToDatabaseDialog.class, "AddHashValuesToDatabaseDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(pasteFromClipboardButton, org.openide.util.NbBundle.getMessage(AddHashValuesToDatabaseDialog.class, "AddHashValuesToDatabaseDialog.pasteFromClipboardButton.text")); // NOI18N
        pasteFromClipboardButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pasteFromClipboardButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(instructionLabel, org.openide.util.NbBundle.getMessage(AddHashValuesToDatabaseDialog.class, "AddHashValuesToDatabaseDialog.instructionLabel.text")); // NOI18N
        instructionLabel.setToolTipText(org.openide.util.NbBundle.getMessage(AddHashValuesToDatabaseDialog.class, "AddHashValuesToDatabaseDialog.instructionLabel.toolTipText")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(instructionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 250, Short.MAX_VALUE)
                    .addComponent(jScrollPane1))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(AddValuesToHashDatabaseButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pasteFromClipboardButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cancelButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(instructionLabel)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 276, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(pasteFromClipboardButton)
                        .addGap(18, 18, 18)
                        .addComponent(AddValuesToHashDatabaseButton)
                        .addGap(18, 18, 18)
                        .addComponent(cancelButton)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        this.dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void AddValuesToHashDatabaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddValuesToHashDatabaseButtonActionPerformed
        // validate the user input.
        String userInput = hashValuesTextArea.getText();
        String[] listOfHashEntries = userInput.split("\\r?\\n");
        // These entries may be of <MD5> or <MD5, comment> format
        int numberOfHashesAdded = 0;
        for(String hashEntry : listOfHashEntries) {
            Matcher m = md5Pattern.matcher(hashEntry);
            if(m.find()) {
                // more information can be added to the HashEntry - sha-1, sha-512, comment
                hashes.add(new HashEntry(null, m.group(0), null, null, null));
                numberOfHashesAdded++;
            }
        }
        JOptionPane.showMessageDialog(this, NbBundle.getMessage(this.getClass(), "AddHashValuesToDatabaseDialog.hashesAdded.msg", numberOfHashesAdded));
        this.dispose();
    }//GEN-LAST:event_AddValuesToHashDatabaseButtonActionPerformed

    private void pasteFromClipboardButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteFromClipboardButtonActionPerformed
        hashValuesTextArea.paste();
        hashValuesTextArea.append("\n");
    }//GEN-LAST:event_pasteFromClipboardButtonActionPerformed

    private void hashValuesTextAreaMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_hashValuesTextAreaMouseClicked
        if(SwingUtilities.isRightMouseButton(evt)) {
            JPopupMenu popup = new JPopupMenu();

            JMenuItem cutMenu = new JMenuItem("Cut");
            cutMenu.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    hashValuesTextArea.cut();
                }
            });

            JMenuItem copyMenu = new JMenuItem("Copy");
            copyMenu.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    hashValuesTextArea.copy();
                }
            });

            JMenuItem pasteMenu = new JMenuItem("Paste");
            pasteMenu.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    hashValuesTextArea.paste();
                    hashValuesTextArea.append("\n");
                }
            });

            popup.add(cutMenu);
            popup.add(copyMenu);
            popup.add(pasteMenu);
            popup.show(hashValuesTextArea, evt.getX(), evt.getY());
        }
    }//GEN-LAST:event_hashValuesTextAreaMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton AddValuesToHashDatabaseButton;
    private javax.swing.JButton cancelButton;
    private javax.swing.JTextArea hashValuesTextArea;
    private javax.swing.JLabel instructionLabel;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton pasteFromClipboardButton;
    // End of variables declaration//GEN-END:variables
}

