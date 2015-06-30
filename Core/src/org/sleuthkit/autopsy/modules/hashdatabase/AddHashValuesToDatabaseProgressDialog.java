/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.datamodel.HashEntry;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author sidhesh
 */
public class AddHashValuesToDatabaseProgressDialog extends javax.swing.JDialog {

    private final AddHashValuesToDatabaseDialog parentRef;
    private boolean disposeParent = false;
    private final HashDb hashDb;
    private final List<HashEntry> hashes;
    private final List<String> invalidHashes;
    private final Pattern md5Pattern;
    private String errorMessage;
    private final String text;

    /**
     * Creates new form AddHashValuesToDatabaseProgressDialog
     *
     * @param parent
     * @param hashDb
     * @param text
     */
    AddHashValuesToDatabaseProgressDialog(AddHashValuesToDatabaseDialog parent, HashDb hashDb, String text) {
        super(parent);
        initComponents();
        display();
        this.hashes = new ArrayList<>();
        this.invalidHashes = new ArrayList<>();
        this.md5Pattern = Pattern.compile("^[a-fA-F0-9]{32}$"); // NON-NLS
        this.parentRef = parent;
        this.hashDb = hashDb;
        this.text = text;
    }

    private void display() {
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenDimension.width - getSize().width) / 2, (screenDimension.height - getSize().height) / 2);
        setVisible(true);
    }

    /**
     * Executes a SwingWorker which performs addition of hashes into the database.
     */
    final void addHashValuesToDatabase() {
        parentRef.enableAddHashValuesToDatabaseDialog(false);
        new SwingWorker<Object, Void>() {

            @Override
            protected Object doInBackground() throws Exception {
                // parse the text for md5 hashes.
                statusLabel.setText(NbBundle.getMessage(AddHashValuesToDatabaseProgressDialog.class, "AddHashValuesToDatabaseProgressDialog.addHashValuesToDatabase.parsing"));
                getHashesFromTextArea(text);

                // Perform checks for invalid input. Then perform insertion 
                // of hashes in the database.
                if (!invalidHashes.isEmpty()) {
                    statusLabel.setText(NbBundle.getMessage(AddHashValuesToDatabaseProgressDialog.class, "AddHashValuesToDatabaseProgressDialog.addHashValuesToDatabase.invalidHash"));
                    finish(false);
                    errorMessage = NbBundle.getMessage(AddHashValuesToDatabaseProgressDialog.class, "AddHashValuesToDatabaseProgressDialog.addHashValuesToDatabase.invaliHash.msg");
                    for (String invalidHash : invalidHashes) {
                        errorMessage = errorMessage + invalidHash + "\n"; // NON-NLS
                    }
                    showErrorsButton.setVisible(true);
                } else if (hashes.isEmpty()) {
                    statusLabel.setText(NbBundle.getMessage(AddHashValuesToDatabaseProgressDialog.class, "AddHashValuesToDatabaseProgressDialog.addHashValuesToDatabase.noHashesToAdd"));
                    finish(false);
                } else {
                    try {
                        hashDb.addHashes(hashes);
                        statusLabel.setText(NbBundle.getMessage(AddHashValuesToDatabaseProgressDialog.class, "AddHashValuesToDatabaseProgressDialog.addHashValuesToDatabase.success", hashes.size()));
                        finish(true);
                        disposeParent = true;
                    } catch (TskCoreException ex) {
                        statusLabel.setText(NbBundle.getMessage(AddHashValuesToDatabaseProgressDialog.class, "AddHashValuesToDatabaseProgressDialog.addHashValuesToDatabase.errorAddingValidHash"));
                        finish(false);
                        errorMessage = NbBundle.getMessage(AddHashValuesToDatabaseProgressDialog.class, "AddHashValuesToDatabaseProgressDialog.addHashValuesToDatabase.errorAddingValidHash.msg", ex.toString());
                        showErrorsButton.setVisible(true);
                    }
                }
                return null;
            }
        }.execute();
    }

    /**
     * Sets the progressbar to maximum value, change colors accordingly, and
     * enables OK button.
     * @param success 
     */
    private void finish(boolean success) {
        okButton.setEnabled(true);
        addingHashesToDatabaseProgressBar.setIndeterminate(false);
        addingHashesToDatabaseProgressBar.setValue(addingHashesToDatabaseProgressBar.getMaximum());
        if (success) {
            addingHashesToDatabaseProgressBar.setForeground(Color.green);
        } else {
            addingHashesToDatabaseProgressBar.setBackground(Color.red);
            addingHashesToDatabaseProgressBar.setForeground(Color.red);
        }

    }

    /**
     * Parses for String for MD5 hashes and adds new HashEntry objects into the
     * list of hashes. It also populates the invalidHashes list for user-feedback.
     * @param text 
     */
    private void getHashesFromTextArea(String text) {
        String[] linesInTextArea = text.split("\\r?\\n"); // NON-NLS
        // These entries may be of <MD5> or <MD5, comment> format
        for (String hashEntry : linesInTextArea) {
            hashEntry = hashEntry.trim();
            Matcher m = md5Pattern.matcher(hashEntry);
            if (m.find()) {
                // more information can be added to the HashEntry - sha-1, sha-512, comment
                hashes.add(new HashEntry(null, m.group(0), null, null, null));
            } else {
                if (!hashEntry.isEmpty()) {
                    invalidHashes.add(hashEntry);
                }
            }
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

        addingHashesToDatabaseProgressBar = new javax.swing.JProgressBar();
        okButton = new javax.swing.JButton();
        statusLabel = new javax.swing.JLabel();
        showErrorsButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle(org.openide.util.NbBundle.getMessage(AddHashValuesToDatabaseProgressDialog.class, "AddHashValuesToDatabaseProgressDialog.title")); // NOI18N

        addingHashesToDatabaseProgressBar.setIndeterminate(true);

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(AddHashValuesToDatabaseProgressDialog.class, "AddHashValuesToDatabaseProgressDialog.okButton.text")); // NOI18N
        okButton.setEnabled(false);
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(statusLabel, org.openide.util.NbBundle.getMessage(AddHashValuesToDatabaseProgressDialog.class, "AddHashValuesToDatabaseProgressDialog.statusLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(showErrorsButton, org.openide.util.NbBundle.getMessage(AddHashValuesToDatabaseProgressDialog.class, "AddHashValuesToDatabaseProgressDialog.showErrorsButton.text")); // NOI18N
        showErrorsButton.setVisible(false);
        showErrorsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showErrorsButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(addingHashesToDatabaseProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, 311, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(okButton))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(statusLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(showErrorsButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(addingHashesToDatabaseProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(okButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(statusLabel)
                    .addComponent(showErrorsButton))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        parentRef.enableAddHashValuesToDatabaseDialog(true);
        if (disposeParent) {
            parentRef.dispose();
        }
        this.dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    private void showErrorsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showErrorsButtonActionPerformed
        JTextArea textArea = new JTextArea(errorMessage);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(250, 100));
        JOptionPane.showMessageDialog(this, scrollPane, "Error:\n", JOptionPane.OK_OPTION); // NON-NLS
    }//GEN-LAST:event_showErrorsButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JProgressBar addingHashesToDatabaseProgressBar;
    private javax.swing.JButton okButton;
    private javax.swing.JButton showErrorsButton;
    private javax.swing.JLabel statusLabel;
    // End of variables declaration//GEN-END:variables
}
