/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository;

import javax.swing.text.AbstractDocument;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;

/**
 * Dialog to allow Central Repository file instance comments to be added and
 * modified.
 */
@Messages({"CentralRepoCommentDialog.title.addEditCentralRepoComment=Add/Edit Central Repository Comment"})
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class CentralRepoCommentDialog extends javax.swing.JDialog {

    private final CorrelationAttributeInstance correlationAttributeInstance;
    private boolean commentUpdated = false;
    private String currentComment = "";

    /**
     * Create an instance.
     *
     * @param correlationAttributeInstance The correlation attribute to be
     *                                     modified.
     */
    CentralRepoCommentDialog(CorrelationAttributeInstance correlationAttributeInstance) {
        super(WindowManager.getDefault().getMainWindow(), Bundle.CentralRepoCommentDialog_title_addEditCentralRepoComment());

        initComponents();

        CorrelationAttributeInstance instance = correlationAttributeInstance;

        // Store the original comment
        if (instance.getComment() != null) {
            currentComment = instance.getComment();
        }

        //Truncate legacy comments to be MAX_CHARACTERS characters, pressing 'okay'
        //once the editted comment is loaded in will truncate it in the database as 
        //well.
        commentTextArea.setText(instance.getComment());

        this.correlationAttributeInstance = correlationAttributeInstance;
    }

    /**
     * Display the dialog.
     */
    void display() {
        setModal(true);
        setSize(getPreferredSize());
        setLocationRelativeTo(this.getParent());
        setAlwaysOnTop(false);
        pack();
        setVisible(true);
    }

    /**
     * Has the comment been updated?
     *
     * @return True if the comment has been updated; otherwise false.
     */
    boolean isCommentUpdated() {
        return commentUpdated;
    }

    /**
     * Get the current comment. If the user hit OK, this will be the new
     * comment. If the user canceled, this will be the original comment.
     *
     * @return the comment
     */
    String getComment() {
        return currentComment;
    }

    /**
     * Limits the number of characters that can go into the Comment JTextArea of
     * this Dialog box.
     */
    private class CentralRepoCommentLengthFilter extends DocumentFilter {

        private final Integer MAX_CHARACTERS = 500;
        private Integer remainingCharacters = MAX_CHARACTERS;

        public CentralRepoCommentLengthFilter() {
            updateLabel();
        }

        /**
         * Truncates the insert string if its addition in the Comment dialog box
         * will cause it to go past MAX_CHARACTERS in length.
         *
         * @param filter  FilterBypass that can be used to mutate Document
         * @param offset  the offset into the document to insert the content >=
         *                0. All positions that track change at or after the
         *                given location will move.
         * @param input   the string to insert
         * @param attrSet the attributes to associate with the inserted content.
         *                This may be null if there are no attributes.
         *
         * @throws BadLocationException the given insert position is not a valid
         *                              position within the document
         */
        public void insertString(FilterBypass filter, int offset, String input,
                AttributeSet attrSet) throws BadLocationException {
            //Truncate the insert if its too long
            this.replace(filter, offset, 0, input, attrSet);
        }

        /**
         * Remove the number of characters from the Comment Text box and add
         * back to our remaining count.
         *
         * @param filter FilterBypass that can be used to mutate Document
         * @param offset the offset from the beginning >= 0
         * @param length the number of characters to remove >= 0
         *
         * @throws BadLocationException some portion of the removal range was
         *                              not a valid part of the document. The
         *                              location in the exception is the first
         *                              bad position encountered.
         */
        public void remove(FilterBypass filter, int offset, int length)
                throws BadLocationException {
            super.remove(filter, offset, length);
            remainingCharacters += length;
            updateLabel();
        }

        /**
         * Replace the current text at the offset position with the inputted
         * text. If the offset is the end of the text box, then this functions
         * like an append. Truncate this input if its addition will cause the
         * Comment text box to be > MAX_CHARACTERS in length.
         *
         * @param filter  FilterBypass that can be used to mutate Document
         * @param offset  Location in Document
         * @param length  Length of text to delete
         * @param input   Text to insert, null indicates no text to insert
         * @param attrSet AttributeSet indicating attributes of inserted text,
         *                null is legal.
         *
         * @throws BadLocationException the given insert position is not a valid
         *                              position within the document
         */
        public void replace(FilterBypass filter, int offset, int length, String input,
                AttributeSet attrSet) throws BadLocationException {
            //Truncate the replace if its too long
            String truncatedText = input;
            if ((filter.getDocument().getLength() + input.length() - length) > MAX_CHARACTERS) {
                truncatedText = input.substring(0, MAX_CHARACTERS - 
                        filter.getDocument().getLength() - length);
            }
            super.replace(filter, offset, length, truncatedText, attrSet);
            remainingCharacters -= truncatedText.length() - length;
            updateLabel();
        }

        /**
         * Updates the remainingCharactersLabel to reflect the current state.
         * If there are no more characters left, a red 0 is displayed in the 
         * UI.
         */
        private void updateLabel() {
            if (remainingCharacters == 0) {
                remainingCharactersLabel.setText(String.format(
                        "<html><font color=\"red\">%d</font> %s</html>",
                    remainingCharacters, "characters remaining")); 
            } else {
                remainingCharactersLabel.setText(String.format(
                        "<html><font color=\"black\">%d</font> %s</html>",
                    remainingCharacters, "characters remaining")); 
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

        jScrollPane1 = new javax.swing.JScrollPane();
        commentTextArea = new javax.swing.JTextArea();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        commentLabel = new javax.swing.JLabel();
        remainingCharactersLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setSize(getPreferredSize());

        commentTextArea.setColumns(20);
        commentTextArea.setLineWrap(true);
        commentTextArea.setRows(5);
        commentTextArea.setTabSize(4);
        commentTextArea.setWrapStyleWord(true);
	((AbstractDocument)commentTextArea.getDocument())
                .setDocumentFilter(new CentralRepoCommentLengthFilter());
        jScrollPane1.setViewportView(commentTextArea);

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(CentralRepoCommentDialog.class, "CentralRepoCommentDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(CentralRepoCommentDialog.class, "CentralRepoCommentDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(commentLabel, org.openide.util.NbBundle.getMessage(CentralRepoCommentDialog.class, "CentralRepoCommentDialog.commentLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(commentLabel)
                        .addGap(0, 451, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(remainingCharactersLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(okButton, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(commentLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(cancelButton)
                        .addComponent(okButton))
                    .addComponent(remainingCharactersLabel))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        currentComment = commentTextArea.getText();
        correlationAttributeInstance.setComment(currentComment);
        commentUpdated = true;

        dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel commentLabel;
    private javax.swing.JTextArea commentTextArea;
    private javax.swing.JLabel remainingCharactersLabel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton okButton;
    // End of variables declaration//GEN-END:variables
}
