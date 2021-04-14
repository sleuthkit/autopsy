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
package org.sleuthkit.autopsy.casemodule;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.hosts.HostNameValidator;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel to be displayed as a part of the add datasource wizard. Provides the
 * ability to select current host.
 */
@Messages({
    "AddImageWizardSelectHostVisual_title=Select Host"
})
class AddImageWizardSelectHostVisual extends javax.swing.JPanel {

    /**
     * A combo box item for a host (or null for default).
     */
    private static class HostListItem {

        private final Host host;

        /**
         * Main constructor.
         *
         * @param host The host.
         */
        HostListItem(Host host) {
            this.host = host;
        }

        /**
         * @return The host.
         */
        Host getHost() {
            return host;
        }

        @Override
        public String toString() {
            if (host == null || host.getName() == null) {
                return "";
            } else {
                return host.getName();
            }
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 41 * hash + Objects.hashCode(this.host == null ? 0 : this.host.getHostId());
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final HostListItem other = (HostListItem) obj;
            if (!Objects.equals(
                    this.host == null ? 0 : this.host.getHostId(),
                    other.host == null ? 0 : other.host.getHostId())) {

                return false;
            }
            return true;
        }

    }

    private static final Logger logger = Logger.getLogger(AddImageWizardSelectHostVisual.class.getName());

    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private Set<String> sanitizedHostSet = null;
    
    /**
     * Creates new form SelectHostPanel
     */
    AddImageWizardSelectHostVisual() {
        initComponents();

        specifyNewHostTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }
        });

        existingHostList.addListSelectionListener((evt) -> refresh());

        loadHostData();
        refresh();
    }

    /**
     * Add listener for validation change events.
     *
     * @param pcl The property change listener.
     */
    void addListener(PropertyChangeListener pcl) {
        changeSupport.addPropertyChangeListener(pcl);
    }

    /**
     * Remove listener from validation change events.
     *
     * @param pcl The property change listener.
     */
    void removeListener(PropertyChangeListener pcl) {
        changeSupport.removePropertyChangeListener(pcl);
    }

    /**
     * @return The currently selected host or null if no selection. This will
     * generate a new host if 'Specify New Host Name'
     */
    Host getSelectedHost() {
        if (specifyNewHostRadio.isSelected() && StringUtils.isNotEmpty(specifyNewHostTextField.getText())) {
            String newHostName = specifyNewHostTextField.getText();
            try {
                return Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().newHost(newHostName);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Unable to create host '%s'.", newHostName), ex);
                return null;
            }
        } else if (useExistingHostRadio.isSelected()
                && existingHostList.getSelectedValue() != null
                && existingHostList.getSelectedValue().getHost() != null) {

            return existingHostList.getSelectedValue().getHost();
        } else {
            return null;
        }
    }

    /**
     * Loads hosts from database and displays in combo box.
     */
    private void loadHostData() {
        try {
            Collection<Host> hosts = Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().getAllHosts();
            sanitizedHostSet = HostNameValidator.getSanitizedHostNames(hosts);
            
            Vector<HostListItem> hostListItems = hosts.stream()
                    .filter(h -> h != null)
                    .sorted((a, b) -> getNameOrEmpty(a).compareToIgnoreCase(getNameOrEmpty(b)))
                    .map((h) -> new HostListItem(h))
                    .collect(Collectors.toCollection(Vector::new));

            existingHostList.setListData(hostListItems);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to display host items with no current case.", ex);
        }
    }

    /**
     * Returns the name of the host or an empty string if the host or host name
     * is null.
     *
     * @param host The host.
     * @return The host name or empty string.
     */
    private String getNameOrEmpty(Host host) {
        return host == null || host.getName() == null ? "" : host.getName();
    }

    private void refresh() {
        specifyNewHostTextField.setEnabled(specifyNewHostRadio.isSelected());
        existingHostList.setEnabled(useExistingHostRadio.isSelected());

        String prevValidationMessage = validationMessage.getText();
        String newValidationMessage = getValidationMessage();
        validationMessage.setText(newValidationMessage);
        // if validation message changed (empty to non-empty or vice-versa) fire validation update
        if (StringUtils.isBlank(prevValidationMessage) != StringUtils.isBlank(newValidationMessage)) {
            changeSupport.firePropertyChange("validation", prevValidationMessage, newValidationMessage);
        }
    }

    @Messages({
        "AddImageWizardSelectHostVisual_getValidationMessage_noHostSelected=Please select an existing host.",})
    private String getValidationMessage() {
        if (specifyNewHostRadio.isSelected()) {
            // if problematic new name for host
            return HostNameValidator.getValidationMessage(specifyNewHostTextField.getText(), null, sanitizedHostSet);
            
            // or use existing host and no host is selected
        } else if (useExistingHostRadio.isSelected()
                && (existingHostList.getSelectedValue() == null
                || existingHostList.getSelectedValue().getHost() == null)) {
            return Bundle.AddImageWizardSelectHostVisual_getValidationMessage_noHostSelected();
        }

        return null;
    }

    @Override
    public String getName() {
        return Bundle.AddImageWizardSelectHostVisual_title();
    }

    boolean hasValidData() {
        return StringUtils.isBlank(validationMessage.getText());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.ButtonGroup radioButtonGroup = new javax.swing.ButtonGroup();
        generateNewRadio = new javax.swing.JRadioButton();
        specifyNewHostRadio = new javax.swing.JRadioButton();
        specifyNewHostTextField = new javax.swing.JTextField();
        useExistingHostRadio = new javax.swing.JRadioButton();
        javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
        existingHostList = new javax.swing.JList<>();
        hostDescription = new javax.swing.JLabel();
        validationMessage = new javax.swing.JLabel();

        radioButtonGroup.add(generateNewRadio);
        generateNewRadio.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(generateNewRadio, org.openide.util.NbBundle.getMessage(AddImageWizardSelectHostVisual.class, "AddImageWizardSelectHostVisual.generateNewRadio.text")); // NOI18N
        generateNewRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generateNewRadioActionPerformed(evt);
            }
        });

        radioButtonGroup.add(specifyNewHostRadio);
        org.openide.awt.Mnemonics.setLocalizedText(specifyNewHostRadio, org.openide.util.NbBundle.getMessage(AddImageWizardSelectHostVisual.class, "AddImageWizardSelectHostVisual.specifyNewHostRadio.text")); // NOI18N
        specifyNewHostRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                specifyNewHostRadioActionPerformed(evt);
            }
        });

        specifyNewHostTextField.setText(org.openide.util.NbBundle.getMessage(AddImageWizardSelectHostVisual.class, "AddImageWizardSelectHostVisual.specifyNewHostTextField.text")); // NOI18N

        radioButtonGroup.add(useExistingHostRadio);
        org.openide.awt.Mnemonics.setLocalizedText(useExistingHostRadio, org.openide.util.NbBundle.getMessage(AddImageWizardSelectHostVisual.class, "AddImageWizardSelectHostVisual.useExistingHostRadio.text")); // NOI18N
        useExistingHostRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useExistingHostRadioActionPerformed(evt);
            }
        });

        existingHostList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(existingHostList);

        org.openide.awt.Mnemonics.setLocalizedText(hostDescription, org.openide.util.NbBundle.getMessage(AddImageWizardSelectHostVisual.class, "AddImageWizardSelectHostVisual.hostDescription.text")); // NOI18N

        validationMessage.setForeground(java.awt.Color.RED);
        org.openide.awt.Mnemonics.setLocalizedText(validationMessage, org.openide.util.NbBundle.getMessage(AddImageWizardSelectHostVisual.class, "AddImageWizardSelectHostVisual.validationMessage.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(validationMessage, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(generateNewRadio)
                            .addComponent(useExistingHostRadio)
                            .addComponent(hostDescription)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(21, 21, 21)
                                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 270, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(specifyNewHostRadio)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(specifyNewHostTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 270, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 13, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(hostDescription)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(generateNewRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(specifyNewHostRadio)
                    .addComponent(specifyNewHostTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(useExistingHostRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(10, 10, 10)
                .addComponent(validationMessage)
                .addContainerGap(18, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void generateNewRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateNewRadioActionPerformed
        refresh();
    }//GEN-LAST:event_generateNewRadioActionPerformed

    private void specifyNewHostRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specifyNewHostRadioActionPerformed
        refresh();
    }//GEN-LAST:event_specifyNewHostRadioActionPerformed

    private void useExistingHostRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useExistingHostRadioActionPerformed
        refresh();
    }//GEN-LAST:event_useExistingHostRadioActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<HostListItem> existingHostList;
    private javax.swing.JRadioButton generateNewRadio;
    private javax.swing.JLabel hostDescription;
    private javax.swing.JRadioButton specifyNewHostRadio;
    private javax.swing.JTextField specifyNewHostTextField;
    private javax.swing.JRadioButton useExistingHostRadio;
    private javax.swing.JLabel validationMessage;
    // End of variables declaration//GEN-END:variables
}
