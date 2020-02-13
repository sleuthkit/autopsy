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
package org.sleuthkit.autopsy.allcasessearch;

import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizer;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.corecomponents.TextPrompt;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.EmptyNode;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

@Messages({
    "AllCasesSearchDialog.dialogTitle.text=Search All Cases",
    "AllCasesSearchDialog.resultsTitle.text=All Cases",
    "AllCasesSearchDialog.resultsDescription.text=All Cases Search",
    "AllCasesSearchDialog.emptyNode.text=No results found.",
    "AllCasesSearchDialog.validation.invalidHash=The supplied value is not a valid MD5 hash.",
    "AllCasesSearchDialog.validation.invalidEmail=The supplied value is not a valid e-mail address.",
    "AllCasesSearchDialog.validation.invalidDomain=The supplied value is not a valid domain.",
    "AllCasesSearchDialog.validation.invalidPhone=The supplied value is not a valid phone number.",
    "AllCasesSearchDialog.validation.invalidSsid=The supplied value is not a valid wireless network.",
    "AllCasesSearchDialog.validation.invalidMac=The supplied value is not a valid MAC address.",
    "AllCasesSearchDialog.validation.invalidImei=The supplied value is not a valid IMEI number.",
    "AllCasesSearchDialog.validation.invalidImsi=The supplied value is not a valid IMSI number.",
    "AllCasesSearchDialog.validation.invalidIccid=The supplied value is not a valid ICCID number.",
    "AllCasesSearchDialog.validation.genericMessage=The supplied value is not valid.",
    "# {0} - number of cases",
    "AllCasesSearchDialog.caseLabel.text=The current Central Repository contains {0} case(s)."
})
/**
 * The Search All Cases dialog allows users to search for specific types of
 * correlation properties in the Central Repository.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class AllCasesSearchDialog extends javax.swing.JDialog {

    private static final Logger logger = Logger.getLogger(AllCasesSearchDialog.class.getName());
    private static final long serialVersionUID = 1L;

    private final List<CorrelationAttributeInstance.Type> correlationTypes;
    private CorrelationAttributeInstance.Type selectedCorrelationType;
    private TextPrompt correlationValueTextFieldPrompt;

    /**
     * Creates a new instance of the Search Other Cases dialog.
     */
    AllCasesSearchDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(), Bundle.AllCasesSearchDialog_dialogTitle_text(), true);
        this.correlationTypes = new ArrayList<>();
        initComponents();
        customizeComponents();
    }

    /**
     * Perform the other cases search.
     *
     * @param type  The correlation type.
     * @param value The value to be matched.
     */
    private void search(CorrelationAttributeInstance.Type type, String value) {
        new SwingWorker<List<CorrelationAttributeInstance>, Void>() {

            @Override
            protected List<CorrelationAttributeInstance> doInBackground() {
                List<CorrelationAttributeInstance> correlationInstances = new ArrayList<>();

                try {
                    correlationInstances = CentralRepository.getInstance().getArtifactInstancesByTypeValue(type, value);
                } catch (CentralRepoException ex) {
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
                    DataResultViewerTable table = new DataResultViewerTable();
                    Collection<DataResultViewer> viewers = new ArrayList<>(1);
                    viewers.add(table);

                    AllCasesSearchNode searchNode = new AllCasesSearchNode(correlationInstances);
                    TableFilterNode tableFilterNode = new TableFilterNode(searchNode, true, searchNode.getName());

                    String resultsText = String.format("%s (%s; \"%s\")",
                            Bundle.AllCasesSearchDialog_resultsTitle_text(), type.getDisplayName(), value);
                    final TopComponent searchResultWin;
                    if (correlationInstances.isEmpty()) {
                        Node emptyNode = new TableFilterNode(
                                new EmptyNode(Bundle.AllCasesSearchDialog_emptyNode_text()), true);
                        searchResultWin = DataResultTopComponent.createInstance(
                                resultsText, Bundle.AllCasesSearchDialog_resultsDescription_text(), emptyNode, 0);
                    } else {
                        searchResultWin = DataResultTopComponent.createInstance(
                                resultsText, Bundle.AllCasesSearchDialog_resultsDescription_text(), tableFilterNode, correlationInstances.size(), viewers);
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
        descriptionLabel = new javax.swing.JLabel();
        casesLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        org.openide.awt.Mnemonics.setLocalizedText(correlationValueLabel, org.openide.util.NbBundle.getMessage(AllCasesSearchDialog.class, "AllCasesSearchDialog.correlationValueLabel.text")); // NOI18N

        correlationValueTextField.setText(org.openide.util.NbBundle.getMessage(AllCasesSearchDialog.class, "AllCasesSearchDialog.correlationValueTextField.text")); // NOI18N
        correlationValueTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                valueFieldKeyReleaseListener(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(AllCasesSearchDialog.class, "AllCasesSearchDialog.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        correlationTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                correlationTypeComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(correlationTypeLabel, org.openide.util.NbBundle.getMessage(AllCasesSearchDialog.class, "AllCasesSearchDialog.correlationTypeLabel.text")); // NOI18N

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(AllCasesSearchDialog.class, "AllCasesSearchDialog.errorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(descriptionLabel, org.openide.util.NbBundle.getMessage(AllCasesSearchDialog.class, "AllCasesSearchDialog.descriptionLabel.text")); // NOI18N

        casesLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(casesLabel, org.openide.util.NbBundle.getMessage(AllCasesSearchDialog.class, "AllCasesSearchDialog.casesLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(descriptionLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 430, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(correlationValueLabel)
                            .addComponent(correlationTypeLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(correlationTypeComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(correlationValueTextField)
                            .addComponent(errorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(casesLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(searchButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(descriptionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(correlationTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(correlationTypeLabel))
                .addGap(15, 15, 15)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(correlationValueTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(correlationValueLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(errorLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 20, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(searchButton)
                    .addComponent(casesLabel))
                .addContainerGap())
        );

        searchButton.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(AllCasesSearchDialog.class, "AllCasesSearchDialog.searchButton.AccessibleContext.accessibleName")); // NOI18N
        searchButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(AllCasesSearchDialog.class, "AllCasesSearchDialog.searchButton.AccessibleContext.accessibleDescription")); // NOI18N

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        CorrelationAttributeInstance.Type correlationType = selectedCorrelationType;
        String correlationValue = correlationValueTextField.getText().trim();

        if (validateInputs(correlationType, correlationValue)) {
            search(correlationType, correlationValue);
            dispose();
        } else {
            String validationMessage;
            switch (correlationType.getId()) {
                case CorrelationAttributeInstance.FILES_TYPE_ID:
                    validationMessage = Bundle.AllCasesSearchDialog_validation_invalidHash();
                    break;
                case CorrelationAttributeInstance.DOMAIN_TYPE_ID:
                    validationMessage = Bundle.AllCasesSearchDialog_validation_invalidDomain();
                    break;
                case CorrelationAttributeInstance.EMAIL_TYPE_ID:
                    validationMessage = Bundle.AllCasesSearchDialog_validation_invalidEmail();
                    break;
                case CorrelationAttributeInstance.PHONE_TYPE_ID:
                    validationMessage = Bundle.AllCasesSearchDialog_validation_invalidPhone();
                    break;
                case CorrelationAttributeInstance.SSID_TYPE_ID:
                    validationMessage = Bundle.AllCasesSearchDialog_validation_invalidSsid();
                    break;
                case CorrelationAttributeInstance.MAC_TYPE_ID:
                    validationMessage = Bundle.AllCasesSearchDialog_validation_invalidMac();
                    break;
                case CorrelationAttributeInstance.IMEI_TYPE_ID:
                    validationMessage = Bundle.AllCasesSearchDialog_validation_invalidImei();
                    break;
                case CorrelationAttributeInstance.IMSI_TYPE_ID:
                    validationMessage = Bundle.AllCasesSearchDialog_validation_invalidImsi();
                    break;
                case CorrelationAttributeInstance.ICCID_TYPE_ID:
                    validationMessage = Bundle.AllCasesSearchDialog_validation_invalidIccid();
                    break;
                default:
                    validationMessage = Bundle.AllCasesSearchDialog_validation_genericMessage();
                    break;

            }
            errorLabel.setText(validationMessage);
            searchButton.setEnabled(false);
            correlationValueTextField.grabFocus();
        }
    }//GEN-LAST:event_searchButtonActionPerformed

    private void correlationTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_correlationTypeComboBoxActionPerformed
        //make error message go away when combo box is selected
        errorLabel.setText("");
    }//GEN-LAST:event_correlationTypeComboBoxActionPerformed

    private void valueFieldKeyReleaseListener(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_valueFieldKeyReleaseListener
        //make error message go away when the user enters anything in the value field
        errorLabel.setText("");
    }//GEN-LAST:event_valueFieldKeyReleaseListener

    /**
     * Validate the supplied input.
     *
     * @param type  The correlation type.
     * @param value The value to be validated.
     *
     * @return True if the input is valid for the given type; otherwise false.
     */
    private boolean validateInputs(CorrelationAttributeInstance.Type type, String value) {
        try {
            CorrelationAttributeNormalizer.normalize(type, value);
        } catch (CorrelationAttributeNormalizationException ex) {
            // No need to log this.
            return false;
        }

        return true;
    }

    /**
     * Further customize the components beyond the standard initialization.
     */
    private void customizeComponents() {
        searchButton.setEnabled(false);

        /*
         * Add correlation types to the combo-box.
         */
        try {
            CentralRepository dbManager = CentralRepository.getInstance();
            correlationTypes.clear();
            correlationTypes.addAll(dbManager.getDefinedCorrelationTypes());
            int numberOfCases = dbManager.getCases().size();
            casesLabel.setText(Bundle.AllCasesSearchDialog_caseLabel_text(numberOfCases));
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Unable to connect to the Central Repository database.", ex);
        }

        for (CorrelationAttributeInstance.Type type : correlationTypes) {
            correlationTypeComboBox.addItem(type.getDisplayName());
        }
        correlationTypeComboBox.setSelectedIndex(0);

        correlationTypeComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                updateSelectedType();
                updateCorrelationValueTextFieldPrompt();
                updateSearchButton();
            }
        });

        updateSelectedType();

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
        "AllCasesSearchDialog.correlationValueTextField.filesExample=Example: \"f0e1d2c3b4a5968778695a4b3c2d1e0f\"",
        "AllCasesSearchDialog.correlationValueTextField.domainExample=Example: \"domain.com\"",
        "AllCasesSearchDialog.correlationValueTextField.emailExample=Example: \"user@host.com\"",
        "AllCasesSearchDialog.correlationValueTextField.phoneExample=Example: \"(800)123-4567\"",
        "AllCasesSearchDialog.correlationValueTextField.usbExample=Example: \"4&1234567&0\"",
        "AllCasesSearchDialog.correlationValueTextField.ssidExample=Example: \"WirelessNetwork-5G\"",
        "AllCasesSearchDialog.correlationValueTextField.macExample=Example: \"0C-14-F2-01-AF-45\"",
        "AllCasesSearchDialog.correlationValueTextField.imeiExample=Example: \"351756061523999\"",
        "AllCasesSearchDialog.correlationValueTextField.imsiExample=Example: \"310150123456789\"",
        "AllCasesSearchDialog.correlationValueTextField.iccidExample=Example: \"89 91 19 1299 99 329451 0\""
    })
    /**
     * Update the text prompt of the name text field based on the input type
     * selection.
     */
    private void updateCorrelationValueTextFieldPrompt() {
        /**
         * Add text prompt to the text field.
         */
        String text;
        switch (selectedCorrelationType.getId()) {
            case CorrelationAttributeInstance.FILES_TYPE_ID:
                text = Bundle.AllCasesSearchDialog_correlationValueTextField_filesExample();
                break;
            case CorrelationAttributeInstance.DOMAIN_TYPE_ID:
                text = Bundle.AllCasesSearchDialog_correlationValueTextField_domainExample();
                break;
            case CorrelationAttributeInstance.EMAIL_TYPE_ID:
                text = Bundle.AllCasesSearchDialog_correlationValueTextField_emailExample();
                break;
            case CorrelationAttributeInstance.PHONE_TYPE_ID:
                text = Bundle.AllCasesSearchDialog_correlationValueTextField_phoneExample();
                break;
            case CorrelationAttributeInstance.USBID_TYPE_ID:
                text = Bundle.AllCasesSearchDialog_correlationValueTextField_usbExample();
                break;
            case CorrelationAttributeInstance.SSID_TYPE_ID:
                text = Bundle.AllCasesSearchDialog_correlationValueTextField_ssidExample();
                break;
            case CorrelationAttributeInstance.MAC_TYPE_ID:
                text = Bundle.AllCasesSearchDialog_correlationValueTextField_macExample();
                break;
            case CorrelationAttributeInstance.IMEI_TYPE_ID:
                text = Bundle.AllCasesSearchDialog_correlationValueTextField_imeiExample();
                break;
            case CorrelationAttributeInstance.IMSI_TYPE_ID:
                text = Bundle.AllCasesSearchDialog_correlationValueTextField_imsiExample();
                break;
            case CorrelationAttributeInstance.ICCID_TYPE_ID:
                text = Bundle.AllCasesSearchDialog_correlationValueTextField_iccidExample();
                break;
            default:
                text = "";
                break;
        }
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
     * Update the 'selectedCorrelationType' value to match the selected type
     * from the combo-box.
     */
    private void updateSelectedType() {
        for (CorrelationAttributeInstance.Type type : correlationTypes) {
            if (type.getDisplayName().equals((String) correlationTypeComboBox.getSelectedItem())) {
                selectedCorrelationType = type;
                break;
            }
        }
    }

    /**
     * Enable or disable the Search button depending on whether or not text has
     * been provided for the correlation property value.
     */
    private void updateSearchButton() {
        searchButton.setEnabled(correlationValueTextField.getText().isEmpty() == false);
    }

    /**
     * Display the Search Other Cases dialog.
     */
    public void display() {
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel casesLabel;
    private javax.swing.JComboBox<String> correlationTypeComboBox;
    private javax.swing.JLabel correlationTypeLabel;
    private javax.swing.JLabel correlationValueLabel;
    private javax.swing.JTextField correlationValueTextField;
    private javax.swing.JLabel descriptionLabel;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JButton searchButton;
    // End of variables declaration//GEN-END:variables
}
