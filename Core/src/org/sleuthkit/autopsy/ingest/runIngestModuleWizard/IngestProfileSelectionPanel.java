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
package org.sleuthkit.autopsy.ingest.runIngestModuleWizard;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import javax.swing.Box;
import static javax.swing.Box.createVerticalGlue;
import javax.swing.ButtonModel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponents.AdvancedConfigurationDialog;
import org.sleuthkit.autopsy.ingest.IngestOptionsPanel;
import org.sleuthkit.autopsy.ingest.IngestProfiles;
import org.sleuthkit.autopsy.ingest.IngestProfiles.IngestProfile;

/**
 * Visual panel for the choosing of ingest profiles by the user when running
 * ingest.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class IngestProfileSelectionPanel extends JPanel {

    @Messages({"IngestProfileSelectionPanel.customSettings.name=Custom Settings",
        "IngestProfileSelectionPanel.customSettings.description=configure individual module settings in next step of wizard"})

    private static final long serialVersionUID = 1L;
    private static final String CUSTOM_SETTINGS_DISPLAY_NAME = Bundle.IngestProfileSelectionPanel_customSettings_name();
    private static final String CUSTOM_SETTINGS_DESCRIPTION = Bundle.IngestProfileSelectionPanel_customSettings_description();
    private final IngestProfileSelectionWizardPanel wizardPanel;
    private String selectedProfile;
    private List<IngestProfile> profiles = Collections.emptyList();
    boolean isLastPanel = false;

    //Listener for profile button selection
    ActionListener buttonGroupActionListener = (ActionEvent e) -> {
        updateSelectedProfile();
    };
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
        isLastPanel = !selectedProfile.equals(wizardPanel.getDefaultContext());

        populateProfilesList();
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
     * Handles the change to selected profile 
     * if custom settings button is selected, it enables the next button, otherwise
     * it enables the Finish button.
     */
    private void updateSelectedProfile() {
        
        ButtonModel selectedButton = profileListButtonGroup.getSelection();
        selectedProfile = selectedButton.getActionCommand();
        
        boolean wasLastPanel = isLastPanel;
        isLastPanel = !selectedProfile.equals(wizardPanel.getDefaultContext());
        wizardPanel.fireChangeEvent();
        this.firePropertyChange("LAST_ENABLED", wasLastPanel, isLastPanel); //NON-NLS
    }
    
    /**
     * Adds a button for custom settings as well as one for each profile
     * that has been created to the panel containing them.
     */
    private void populateProfilesList() {
        profiles = getProfiles();
        
        GridBagLayout gridBagLayout = new GridBagLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weighty = .0;
        constraints.anchor = GridBagConstraints.LINE_START;

        addButton(CUSTOM_SETTINGS_DISPLAY_NAME, wizardPanel.getDefaultContext(), CUSTOM_SETTINGS_DESCRIPTION, gridBagLayout, constraints);
        
        profiles.forEach((profile) -> {
            constraints.weightx = 0;
            constraints.gridy++;
            constraints.gridx = 0;
            
            addButton(profile.toString(), profile.toString(), profile.getDescription(), gridBagLayout, constraints);
        });
        //Add vertical glue at the bottom of the scroll panel so spacing 
        //between elements is less dependent on the number of elements
        constraints.gridy++;
        constraints.gridx = 0;
        constraints.weighty = 1;
        Component vertGlue = createVerticalGlue();
        profileListPanel.add(vertGlue);
        gridBagLayout.setConstraints(vertGlue, constraints);
        profileListPanel.setLayout(gridBagLayout);
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
    private void addButton(String profileDisplayName, String profileContextName, String profileDesc, GridBagLayout layout, GridBagConstraints constraints) {
        
        //Add a spacer
        Dimension spacerBlockDimension = new Dimension(6, 4); // Space between left edge and button, Space between rows 
        Box.Filler spacer = new Box.Filler(spacerBlockDimension, spacerBlockDimension, spacerBlockDimension);
        constraints.weightx = 1;
        layout.setConstraints(spacer, constraints);
        profileListPanel.add(spacer);
        constraints.gridx++;
        constraints.gridy++;
        

        JToggleButton profileButton = new JToggleButton();     
        profileButton.setMaximumSize(new java.awt.Dimension(48, 48));
        profileButton.setMinimumSize(new java.awt.Dimension(48, 48));
        profileButton.setPreferredSize(new java.awt.Dimension(48, 48));
        
        profileButton.setName(profileContextName);
        profileButton.setActionCommand(profileContextName);
        
        profileButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/timeline/images/magnifier-zoom-in-green.png")));
        profileButton.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/checkbox24.png")));
        profileButton.setFocusable(false);
        profileButton.setFocusPainted(false);
        profileButton.addActionListener(buttonGroupActionListener);
         
        if (profileContextName.equals(selectedProfile)) {
            profileButton.setSelected(true);
        }
        
        profileListButtonGroup.add(profileButton);
        profileListPanel.add(profileButton);
        layout.setConstraints(profileButton, constraints);
        constraints.gridx++;
        constraints.weightx = 1;
        
        //Using a JTextArea as though it is a label in order to get multi-line support
        String displayText = profileDisplayName;
        if (!profileDesc.isEmpty()) {
            displayText += " - " + profileDesc;
        }
        JTextArea myLabel = new JTextArea(displayText);
        Color gray = new Color(240, 240, 240);  //matches background of panel
        myLabel.setBackground(gray);
        myLabel.setEditable(false);
        myLabel.setWrapStyleWord(true);
        myLabel.setLineWrap(true);
       
        //Add space between the button and text 
        Box.Filler buttonTextSpacer = new Box.Filler(spacerBlockDimension, spacerBlockDimension, spacerBlockDimension);
        layout.setConstraints(buttonTextSpacer, constraints);
        profileListPanel.add(buttonTextSpacer);
        constraints.gridx++;
            
        //Add the text area serving as a label to the right of the button
        profileListPanel.add(myLabel);
        layout.setConstraints(myLabel, constraints);

    }

    /**
     * Getter for the list of profiles
     *
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
     * Get all the currently existing ingest profiles.
     */
    private void fetchProfileList() {
        profiles = IngestProfiles.getIngestProfiles();
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
        profileListPanel.setLayout(new java.awt.GridBagLayout());
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
                    populateProfilesList();
                    dialog.close();
                }
        );
        dialog.display(ingestOptions);
    }//GEN-LAST:event_ingestSettingsButtonActionPerformed
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton ingestSettingsButton;
    private javax.swing.ButtonGroup profileListButtonGroup;
    private javax.swing.JLabel profileListLabel;
    private javax.swing.JPanel profileListPanel;
    private javax.swing.JScrollPane profileListScrollPane;
    // End of variables declaration//GEN-END:variables

 }
