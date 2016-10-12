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
package org.sleuthkit.autopsy.casemodule.services;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.NbBundle;

final class NewTagNameDialog extends javax.swing.JDialog {

    private static final long serialVersionUID = 1L;
    private String userTagDisplayName;
    private BUTTON_PRESSED result;

    enum BUTTON_PRESSED {
        OK, CANCEL;
    }

    /**
     * Creates a new NewUserTagNameDialog dialog.
     */
    NewTagNameDialog() {
        super(new JFrame(NbBundle.getMessage(NewTagNameDialog.class, "NewTagNameDialog.title.text")),
                NbBundle.getMessage(NewTagNameDialog.class, "NewTagNameDialog.title.text"), true);
        initComponents();
        this.display();
    }

    /**
     * Sets display settings for the dialog and adds appropriate listeners.
     */
    private void display() {
        setLayout(new BorderLayout());

        /*
         * Center the dialog 
         */
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        int width = this.getSize().width;
        int height = this.getSize().height;
        setLocation((screenDimension.width - width) / 2, (screenDimension.height - height) / 2);

        /*
         * Add a handler for when the dialog window is closed directly.
         */
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doButtonAction(false);
            }
        });

        /*
         * Add a listener to enable the OK button when the text field changes.
         */
        tagNameTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                fire();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                fire();
            }
            @Override
            public void insertUpdate(DocumentEvent e) {
                fire();
            }
            private void fire() {
                enableOkButton();
            }
        });

        enableOkButton();

        /*
         * Used to show the dialog.
         */
        setResizable(false);
        setVisible(true);
    }

    /**
     * Called when a button is pressed or when the dialog is closed.
     * @param okPressed whether the OK button was pressed.
     */
    private void doButtonAction(boolean okPressed) {
        if (okPressed) {
            String newTagDisplayName = tagNameTextField.getText().trim();
            if (newTagDisplayName.isEmpty()) {
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(NewTagNameDialog.class, "NewTagNameDialog.JOptionPane.tagNameEmpty.message"),
                        NbBundle.getMessage(NewTagNameDialog.class, "NewTagNameDialog.JOptionPane.tagNameEmpty.title"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (TagsManager.containsIllegalCharacters(newTagDisplayName)) {
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(NewTagNameDialog.class, "NewTagNameDialog.JOptionPane.tagNameIllegalCharacters.message"),
                        NbBundle.getMessage(NewTagNameDialog.class, "NewTagNameDialog.JOptionPane.tagNameIllegalCharacters.title"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            userTagDisplayName = newTagDisplayName;
            result = BUTTON_PRESSED.OK;
        } else {
            result = BUTTON_PRESSED.CANCEL;
        }
            setVisible(false);
    }

    /**
     * Returns the tag name entered by the user.
     * 
     * @return a new user tag name 
     */
    String getTagName() {
        return userTagDisplayName;
    }

    /**
     * Returns information about which button was pressed.
     * 
     * @return BUTTON_PRESSED (OK, CANCEL)
     */
    BUTTON_PRESSED getResult() {
        return result;
    }

    /**
     * Enable the OK button if the tag name text field is not empty.
     * Sets the enter button as default, so user can press enter to activate
     * an okButton press and add the tag name.
     */
    private void enableOkButton() {
        okButton.setEnabled(!tagNameTextField.getText().isEmpty());
        getRootPane().setDefaultButton(okButton);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        newTagNameLabel = new javax.swing.JLabel();
        tagNameTextField = new javax.swing.JTextField();
        cancelButton = new javax.swing.JButton();
        okButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(newTagNameLabel, org.openide.util.NbBundle.getMessage(NewTagNameDialog.class, "NewTagNameDialog.newTagNameLabel.text")); // NOI18N

        tagNameTextField.setText(org.openide.util.NbBundle.getMessage(NewTagNameDialog.class, "NewTagNameDialog.tagNameTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(NewTagNameDialog.class, "NewTagNameDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(NewTagNameDialog.class, "NewTagNameDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tagNameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(okButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addComponent(newTagNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(newTagNameLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tagNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(50, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(okButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        doButtonAction(true);
    }//GEN-LAST:event_okButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        doButtonAction(false);
    }//GEN-LAST:event_cancelButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel newTagNameLabel;
    private javax.swing.JButton okButton;
    private javax.swing.JTextField tagNameTextField;
    // End of variables declaration//GEN-END:variables
}
