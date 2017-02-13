/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest.runIngestModuleWizard;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponents.AdvancedConfigurationDialog;
import org.sleuthkit.autopsy.ingest.IngestOptionsPanel;
import org.sleuthkit.autopsy.ingest.IngestProfileMap;
import org.sleuthkit.autopsy.ingest.IngestProfileMap.IngestProfile;

/**
 * Visual panel for the choosing of ingest profiles by the user when running
 * ingest.
 */
final class IngestProfileSelectionPanel extends JPanel implements ItemListener {

    @Messages({"IngestProfileSelectionPanel.customSettings.name=Custom Settings", 
        "IngestProfileSelectionPanel.name=Ingest Profile Selection",
        "IngestProfileSelectionPanel.customSettings.description=configure individual module settings in next step of wizard"})
    
    private static final String CUSTOM_SETTINGS_DISPLAY_NAME = Bundle.IngestProfileSelectionPanel_customSettings_name();
    private static final String CUSTOM_SETTINGS_DESCRIPTION = Bundle.IngestProfileSelectionPanel_customSettings_description();  
    private final IngestProfileSelectionWizardPanel wizardPanel;
    private String selectedProfile;
    private List<IngestProfile> profiles = Collections.emptyList();

    /**
     * Creates new IngestProfileSelectionPanel
     *
     * @param panel               - the WizardPanel which contains this panel
     * @param lastSelectedProfile - the profile that will be selected initially
     */
    IngestProfileSelectionPanel(IngestProfileSelectionWizardPanel panel, String lastSelectedProfile) {
        initComponents();
        wizardPanel = panel;
        selectedProfile = lastSelectedProfile;
        populateListOfCheckboxes();
        this.setName(Bundle.IngestProfileSelectionPanel_name());
    }

    /**
     * Returns the profile that is currently selected in this panel
     *
     * @return selectedProfile
     */
    String getLastSelectedProfile() {
        return selectedProfile;
    }

    /**
     * Adds a radio button for custom settings as well as one for each profile
     * that has been created to the panel containing them.
     */
    private void populateListOfCheckboxes() {
        profiles = getProfiles();
        addRadioButton(CUSTOM_SETTINGS_DISPLAY_NAME, RunIngestModulesAction.getDefaultContext(), CUSTOM_SETTINGS_DESCRIPTION);
        for (IngestProfile profile : profiles) {
            addRadioButton(profile.toString(), profile.toString(), profile.getDescription());
        }
    }

    /**
     * Creates and configures a single radio button before adding it to both the
     * button group and the panel.
     *
     * @param profileDisplayName - the name of the profile the user should see
     * @param profileContextName - the name the profile will be recognized as
     *                           programmatically
     * @param profileDesc        - the description of the profile
     */
    private void addRadioButton(String profileDisplayName, String profileContextName, String profileDesc) {
        String displayText = profileDisplayName + " - " + profileDesc;
        JRadioButton myRadio = new JRadioButton(displayText);
        myRadio.setName(profileContextName);
        myRadio.setToolTipText(profileDesc);
        myRadio.addItemListener(this);
        if (profileContextName.equals(selectedProfile)) {
            myRadio.setSelected(true);
        }

        profileListButtonGroup.add(myRadio);
        profileListPanel.add(myRadio);

    }

    /**
     * Getter for the list of profiles
     * @return profiles
     */
    private List<IngestProfile> getProfiles() {
        if (profiles.isEmpty()) {
            fetchProfileList();
        }
        return profiles;
    }

    /**
     * Remove everything from the list of checkboxes.
     */
    private void clearListOfCheckBoxes() {
        profileListButtonGroup = new javax.swing.ButtonGroup();
        profileListPanel.removeAll();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        profileListButtonGroup = new javax.swing.ButtonGroup();
        ingestSettingsButton = new javax.swing.JButton();
        profileListScrollPane = new javax.swing.JScrollPane();
        profileListPanel = new javax.swing.JPanel();
        profileListLabel = new javax.swing.JLabel();

        setMaximumSize(new java.awt.Dimension(5750, 3000));
        setPreferredSize(new java.awt.Dimension(625, 450));

        org.openide.awt.Mnemonics.setLocalizedText(ingestSettingsButton, org.openide.util.NbBundle.getMessage(IngestProfileSelectionPanel.class, "IngestProfileSelectionPanel.ingestSettingsButton.text")); // NOI18N
        ingestSettingsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ingestSettingsButtonActionPerformed(evt);
            }
        });

        profileListScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        profileListPanel.setAutoscrolls(true);
        profileListPanel.setLayout(new javax.swing.BoxLayout(profileListPanel, javax.swing.BoxLayout.PAGE_AXIS));
        profileListScrollPane.setViewportView(profileListPanel);

        org.openide.awt.Mnemonics.setLocalizedText(profileListLabel, org.openide.util.NbBundle.getMessage(IngestProfileSelectionPanel.class, "IngestProfileSelectionPanel.profileListLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(profileListScrollPane)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ingestSettingsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(profileListLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 102, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 523, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(profileListLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(profileListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 385, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(ingestSettingsButton)
                .addGap(18, 18, 18))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Opens up a dialog with an IngestOptionsPanel so the user can modify any 
     * settings from that options panel. 
     * 
     * @param evt the button press
     */
    private void ingestSettingsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ingestSettingsButtonActionPerformed
        final AdvancedConfigurationDialog dialog = new AdvancedConfigurationDialog(true);
        IngestOptionsPanel ingestOptions = new IngestOptionsPanel();
        ingestOptions.load();
        dialog.addApplyButtonListener(
                (ActionEvent e) -> {
                    ingestOptions.store();
                    clearListOfCheckBoxes();
                    fetchProfileList();
                    profileListPanel.revalidate();
                    profileListPanel.repaint();
                    populateListOfCheckboxes();
                    dialog.close();
                }
        );
        dialog.display(ingestOptions);
    }//GEN-LAST:event_ingestSettingsButtonActionPerformed

    boolean isLastPanel = false;
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton ingestSettingsButton;
    private javax.swing.ButtonGroup profileListButtonGroup;
    private javax.swing.JLabel profileListLabel;
    private javax.swing.JPanel profileListPanel;
    private javax.swing.JScrollPane profileListScrollPane;
    // End of variables declaration//GEN-END:variables

    /**
     * Listens for changes and checks the currently selected radio button
     * if custom settings button is enabled it enables the next button,
     * otherwise it enables the Finish button.
     * 
     * @param e 
     */
    @Override
    public void itemStateChanged(ItemEvent e) {
        for (Component rButton : profileListPanel.getComponents()) {
            JRadioButton jrb = (JRadioButton) rButton;
            if (jrb.isSelected()) {
                selectedProfile = jrb.getName();
                break;
            }
        }
        boolean wasLastPanel = isLastPanel;
        isLastPanel = !selectedProfile.equals(RunIngestModulesAction.getDefaultContext());
        wizardPanel.fireChangeEvent();
        this.firePropertyChange("LAST_ENABLED", wasLastPanel, isLastPanel); //NON-NLS
    }

    /**
     * Get all the currently existing ingest profiles.
     */
    private void fetchProfileList() {
        profiles = new ArrayList<>();
        profiles.addAll(new IngestProfileMap().getIngestProfileMap().values());
    }
}
