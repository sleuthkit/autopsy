/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.url.analytics.domaincategorization;

import java.util.Set;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.url.analytics.DomainCategory;

/**
 * Dialog for adding or editing a custom domain suffix category.
 */
@Messages({
    "AddEditCategoryDialog_Edit=Edit Entry",
    "AddEditCategoryDialog_Add=Add Entry"
})
class AddEditCategoryDialog extends javax.swing.JDialog {

    private boolean changed = false;
    private final Set<String> currentSuffixes;
    private final DomainCategory currentDomainCategory;

    // listens for document updates
    private final DocumentListener updateListener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            onValueUpdate(domainSuffixTextField.getText(), categoryTextField.getText());
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            onValueUpdate(domainSuffixTextField.getText(), categoryTextField.getText());
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            onValueUpdate(domainSuffixTextField.getText(), categoryTextField.getText());
        }
    };

    /**
     * Main constructor if adding a new domain suffix.
     *
     * @param parent The parent frame for this dialog.
     * @param currentSuffixes The current domain suffixes.
     */
    AddEditCategoryDialog(java.awt.Frame parent, Set<String> currentSuffixes) {
        this(parent, currentSuffixes, null);
    }

    /**
     * Main constructor if editing a domain suffix.
     *
     * @param parentThe parent frame for this dialog.
     * @param currentSuffixes The current domain suffixes.
     * @param currentDomainCategory The domain category being edited. If null,
     * it will be assumed that a new domain suffix is being added.
     */
    AddEditCategoryDialog(java.awt.Frame parent, Set<String> currentSuffixes, DomainCategory currentDomainCategory) {
        super(parent, true);
        initComponents();
        this.currentSuffixes = currentSuffixes;
        this.currentDomainCategory = currentDomainCategory;

        // set title based on whether or not we are editing or adding
        // also don't allow editing of domain suffix if editing
        if (currentDomainCategory == null) {
            setTitle(Bundle.AddEditCategoryDialog_Add());
            domainSuffixTextField.setEditable(true);
            domainSuffixTextField.setEnabled(true);
            onValueUpdate(null, null);
        } else {
            setTitle(Bundle.AddEditCategoryDialog_Edit());
            domainSuffixTextField.setEditable(false);
            domainSuffixTextField.setEnabled(false);
            onValueUpdate(currentDomainCategory.getHostSuffix(), currentDomainCategory.getCategory());
        }

        validationLabel.setText("");

        categoryTextField.getDocument().addDocumentListener(updateListener);
        domainSuffixTextField.getDocument().addDocumentListener(updateListener);
    }

    /**
     * Returns the string value for the name in the input field if Ok pressed or
     * null if not.
     *
     * @return The string value for the name in the input field if Ok pressed or
     * null if not.
     */
    DomainCategory getValue() {
        return new DomainCategory(domainSuffixTextField.getText(), categoryTextField.getText());
    }

    /**
     * Returns whether or not the value has been changed and saved by the user.
     *
     * @return Whether or not the value has been changed and saved by the user.
     */
    boolean isChanged() {
        return changed;
    }

    /**
     * When the text field is updated, this method is called.
     *
     * @param suffixStr The current domain suffix string in the input.
     * @param categoryStr The current category string in the input.
     */
    @Messages({
        "# {0} - maxSuffixLen",
        "AddEditCategoryDialog_onValueUpdate_badSuffix=Please provide a domain suffix that is no more than {0} characters that includes at least one period.",
        "# {0} - maxCategoryLen",
        "AddEditCategoryDialog_onValueUpdate_badCategory=Please provide a category that is no more than {0} characters.",
        "AddEditCategoryDialog_onValueUpdate_suffixRepeat=Please provide a unique domain suffix.",
        "AddEditCategoryDialog_onValueUpdate_sameCategory=Please provide a new category for this domain suffix.",})
    void onValueUpdate(String suffixStr, String categoryStr) {

        String safeSuffixStr = suffixStr == null ? "" : suffixStr;
        String normalizedSuffix = WebCategoriesDataModel.getNormalizedSuffix(safeSuffixStr);
        String safeCategoryStr = categoryStr == null ? "" : categoryStr;
        String normalizedCategory = WebCategoriesDataModel.getNormalizedCategory(safeCategoryStr);

        // update input text field if it is not the same.
        if (!safeCategoryStr.equals(categoryTextField.getText())) {
            categoryTextField.setText(safeCategoryStr);
        }

        if (!safeSuffixStr.equals(domainSuffixTextField.getText())) {
            domainSuffixTextField.setText(safeSuffixStr);
        }

        String validationMessage = null;
        if (normalizedSuffix.length() == 0
                || normalizedSuffix.length() > WebCategoriesDataModel.getMaxDomainSuffixLength()
                || normalizedSuffix.indexOf('.') < 0) {
            
            validationMessage = Bundle.AddEditCategoryDialog_onValueUpdate_badSuffix(WebCategoriesDataModel.getMaxCategoryLength());

        } else if (normalizedCategory.length() == 0 || normalizedCategory.length() > WebCategoriesDataModel.getMaxCategoryLength()) {
            validationMessage = Bundle.AddEditCategoryDialog_onValueUpdate_badCategory(WebCategoriesDataModel.getMaxCategoryLength());

        } else if (currentSuffixes.contains(normalizedSuffix)
                && (currentDomainCategory == null
                || !normalizedSuffix.equals(currentDomainCategory.getHostSuffix()))) {

            validationMessage = Bundle.AddEditCategoryDialog_onValueUpdate_suffixRepeat();

        } else if (currentDomainCategory != null
                && currentDomainCategory.getCategory() != null
                && normalizedCategory.equals(WebCategoriesDataModel.getNormalizedCategory(currentDomainCategory.getCategory()))) {

            validationMessage = Bundle.AddEditCategoryDialog_onValueUpdate_sameCategory();
        }

        saveButton.setEnabled(validationMessage == null);
        validationLabel.setText(validationMessage == null ? "" : String.format("<html>%s</html>", validationMessage));
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        categoryTextField = new javax.swing.JTextField();
        domainSuffixTextField = new javax.swing.JTextField();
        javax.swing.JLabel categoryLabel = new javax.swing.JLabel();
        javax.swing.JLabel domainSuffixLabel = new javax.swing.JLabel();
        validationLabel = new javax.swing.JLabel();
        javax.swing.JButton cancelButton = new javax.swing.JButton();
        saveButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        categoryLabel.setText(org.openide.util.NbBundle.getMessage(AddEditCategoryDialog.class, "AddEditCategoryDialog.categoryLabel.text")); // NOI18N

        domainSuffixLabel.setText(org.openide.util.NbBundle.getMessage(AddEditCategoryDialog.class, "AddEditCategoryDialog.domainSuffixLabel.text")); // NOI18N

        validationLabel.setForeground(java.awt.Color.RED);
        validationLabel.setText(" ");
        validationLabel.setToolTipText("");

        cancelButton.setText(org.openide.util.NbBundle.getMessage(AddEditCategoryDialog.class, "AddEditCategoryDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        saveButton.setText(org.openide.util.NbBundle.getMessage(AddEditCategoryDialog.class, "AddEditCategoryDialog.saveButton.text")); // NOI18N
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
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
                        .addComponent(saveButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addComponent(validationLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(domainSuffixLabel)
                            .addComponent(categoryLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(categoryTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 276, Short.MAX_VALUE)
                            .addComponent(domainSuffixTextField))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(domainSuffixTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(domainSuffixLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(categoryTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(categoryLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(validationLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cancelButton)
                    .addComponent(saveButton))
                .addContainerGap(8, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
        this.changed = true;
        dispose();
    }//GEN-LAST:event_saveButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        this.changed = false;
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField categoryTextField;
    private javax.swing.JTextField domainSuffixTextField;
    private javax.swing.JButton saveButton;
    private javax.swing.JLabel validationLabel;
    // End of variables declaration//GEN-END:variables
}
