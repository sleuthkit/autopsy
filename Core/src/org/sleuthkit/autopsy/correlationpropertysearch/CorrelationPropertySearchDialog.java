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
package org.sleuthkit.autopsy.correlationpropertysearch;

import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.commonfilesearch.CommonAttributesSearchResultsViewerTable;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.corecomponents.TextPrompt;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.EmptyNode;

@Messages({
    "CorrelationPropertySearchDialog.title.text=Correlation Property Search",
    "CorrelationPropertySearchDialog.results.text=Correlation Properties",
    "CorrelationPropertySearchDialog.emptyNode.text=No results found.",
    "CorrelationPropertySearchDialog.validation.invalidHash=The supplied value is not a valid MD5 hash."
})
/**
 * The Correlation Property Search dialog allows users to search for specific
 * types of correlation properties in the Central Repository.
 */
final class CorrelationPropertySearchDialog extends javax.swing.JDialog {
    private static final Logger logger = Logger.getLogger(CorrelationPropertySearchDialog.class.getName());
    
    private static final String FILES_CORRELATION_TYPE = "Files";
    
    private final List<CorrelationAttributeInstance.Type> correlationTypes;
    private TextPrompt correlationValueTextFieldPrompt;

    /**
     * Creates a new instance of the Correlation Property Search dialog.
     */
    CorrelationPropertySearchDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(), Bundle.CorrelationPropertySearchDialog_title_text(), true);
        this.correlationTypes = new ArrayList<>();
        initComponents();
        customizeComponents();
    }
    
    /**
     * Perform the correlation property search.
     */
    private void search() {
        new SwingWorker<List<CorrelationAttributeInstance>, Void>() {
            
            @Override
            protected List<CorrelationAttributeInstance> doInBackground() {
                List<CorrelationAttributeInstance.Type> correlationTypes;
                List<CorrelationAttributeInstance> correlationInstances = new ArrayList<>();
                
                try {
                    correlationTypes = EamDb.getInstance().getDefinedCorrelationTypes();
                    for (CorrelationAttributeInstance.Type type : correlationTypes) {
                        if (type.getDisplayName().equals((String) correlationTypeComboBox.getSelectedItem())) {
                            correlationInstances = EamDb.getInstance().getArtifactInstancesByTypeValue(type, correlationValueTextField.getText());
                            break;
                        }
                    }
                } catch (EamDbException ex) {
                    logger.log(Level.SEVERE, "Unable to connect to the Central Repository database.", ex);
                } catch (CorrelationAttributeNormalizationException ex) {
                    logger.log(Level.SEVERE, "Unable to retrieve data from the Central Repository.", ex);
                }

                return correlationInstances;
            }

            @Override
            protected void done() {
                try {
                    super.done();
                    List<CorrelationAttributeInstance> correlationInstances = this.get();
                    DataResultViewerTable table = new CommonAttributesSearchResultsViewerTable();
                    Collection<DataResultViewer> viewers = new ArrayList<>(1);
                    viewers.add(table);
                    
                    CorrelationPropertySearchNode searchNode = new CorrelationPropertySearchNode(correlationInstances);
                    CorrelationPropertyFilterNode tableFilterNode = new CorrelationPropertyFilterNode(searchNode, true, searchNode.getName());
                    
                    String resultsText = String.format("%s (%s; \"%s\")",
                            Bundle.CorrelationPropertySearchDialog_results_text(),
                            (String) correlationTypeComboBox.getSelectedItem(),
                            correlationValueTextField.getText());
                    final TopComponent searchResultWin;
                    if (correlationInstances.isEmpty()) {
                        Node emptyNode = new TableFilterNode(
                                new EmptyNode(Bundle.CorrelationPropertySearchDialog_emptyNode_text()), true);
                        searchResultWin = DataResultTopComponent.createInstance(
                                resultsText, Bundle.CorrelationPropertySearchDialog_title_text(), emptyNode, 0);
                    } else {
                        searchResultWin = DataResultTopComponent.createInstance(
                                resultsText, Bundle.CorrelationPropertySearchDialog_title_text(), tableFilterNode, HIDE_ON_CLOSE, viewers);
                    }
                    searchResultWin.requestActive(); // make it the active top component
                } catch (ExecutionException | InterruptedException ex) {
                    logger.log(Level.SEVERE, "Unable to get CorrelationAttributeInstances.", ex);
                }
            }
        }.execute();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        correlationValueLabel = new javax.swing.JLabel();
        correlationValueTextField = new javax.swing.JTextField();
        searchButton = new javax.swing.JButton();
        correlationTypeComboBox = new javax.swing.JComboBox<>();
        correlationTypeLabel = new javax.swing.JLabel();
        errorLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        org.openide.awt.Mnemonics.setLocalizedText(correlationValueLabel, org.openide.util.NbBundle.getMessage(CorrelationPropertySearchDialog.class, "CorrelationPropertySearchDialog.correlationValueLabel.text")); // NOI18N

        correlationValueTextField.setText(org.openide.util.NbBundle.getMessage(CorrelationPropertySearchDialog.class, "CorrelationPropertySearchDialog.correlationValueTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(CorrelationPropertySearchDialog.class, "CorrelationPropertySearchDialog.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(correlationTypeLabel, org.openide.util.NbBundle.getMessage(CorrelationPropertySearchDialog.class, "CorrelationPropertySearchDialog.correlationTypeLabel.text")); // NOI18N

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(CorrelationPropertySearchDialog.class, "CorrelationPropertySearchDialog.errorLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(errorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(searchButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(correlationValueLabel)
                            .addComponent(correlationTypeLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(correlationTypeComboBox, 0, 289, Short.MAX_VALUE)
                            .addComponent(correlationValueTextField))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(correlationTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(correlationTypeLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(correlationValueLabel)
                    .addComponent(correlationValueTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(searchButton)
                    .addComponent(errorLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        searchButton.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(CorrelationPropertySearchDialog.class, "CorrelationPropertySearchDialog.searchButton.AccessibleContext.accessibleName")); // NOI18N
        searchButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(CorrelationPropertySearchDialog.class, "CorrelationPropertySearchDialog.searchButton.AccessibleContext.accessibleDescription")); // NOI18N

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        if (validateInputs()) {
            /*
             * Just in case, we'll lock down the type and value components to
             * avoid the possibly of a race condition.
             */
            correlationTypeComboBox.setEnabled(false);
            correlationValueTextField.setEnabled(false);
            
            search();
            dispose();
        } else {
            searchButton.setEnabled(false);
            errorLabel.setText(Bundle.CorrelationPropertySearchDialog_validation_invalidHash());
            correlationValueTextField.grabFocus();
        }
    }//GEN-LAST:event_searchButtonActionPerformed

    /**
     * Further customize the components beyond the standard initialization.
     */
    private void customizeComponents() {
        searchButton.setEnabled(false);
        
        /*
         * Add correlation types to the combo-box.
         */
        try {
            EamDb dbManager = EamDb.getInstance();
            correlationTypes.clear();
            correlationTypes.addAll(dbManager.getDefinedCorrelationTypes());
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
        }

        for (CorrelationAttributeInstance.Type type : correlationTypes) {
            // We only support the "Files" type for now.
            if (type.getDisplayName().equals(FILES_CORRELATION_TYPE)) {
                correlationTypeComboBox.addItem(type.getDisplayName());
            }
        }
        correlationTypeComboBox.setSelectedIndex(0);
        
        correlationTypeComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                updateSearchButton();
            }
        });
        
        /*
         * Create listener for text input.
         */
        correlationValueTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSearchButton();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSearchButton();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSearchButton();
            }
        });
        
        updateCorrelationValueTextFieldPrompt();
    }
    
    @Messages({
        "CorrelationPropertySearchDialog.correlationValueTextField.filesExample=Example: \"f0e1d2c3b4a5968778695a4b3c2d1e0f\""
    })
    /**
     * Update the text prompt of the name text field based on the input type
     * selection.
     */
    private void updateCorrelationValueTextFieldPrompt() {
        /**
         * Add text prompt to the text field.
         */
        String text = Bundle.CorrelationPropertySearchDialog_correlationValueTextField_filesExample();
        correlationValueTextFieldPrompt = new TextPrompt(text, correlationValueTextField);
        
        /**
         * Sets the foreground color and transparency of the text prompt.
         */
        correlationValueTextFieldPrompt.setForeground(Color.LIGHT_GRAY);
        correlationValueTextFieldPrompt.changeAlpha(0.9f); // Mostly opaque
        
        validate();
        repaint();
    }
    
    /**
     * Enable or disable the Search button depending on whether or not text has
     * been provided for the correlation property value.
     */
    private void updateSearchButton() {
        searchButton.setEnabled(correlationValueTextField.getText().isEmpty() == false);
    }
    
    /**
     * Validate the value input.
     * 
     * @return True if the input is valid for the selected type; otherwise false.
     */
    private boolean validateInputs() {
        Pattern md5Pattern = Pattern.compile("^[a-fA-F0-9]{32}$"); // NON-NLS
        Matcher matcher = md5Pattern.matcher(correlationValueTextField.getText().trim());
        if (matcher.find()) {
            return true;
        }
        return false;
    }

    /**
     * Display the Correlation Property Search dialog.
     */
    public void display() {
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> correlationTypeComboBox;
    private javax.swing.JLabel correlationTypeLabel;
    private javax.swing.JLabel correlationValueLabel;
    private javax.swing.JTextField correlationValueTextField;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JButton searchButton;
    // End of variables declaration//GEN-END:variables
}
