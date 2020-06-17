/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.util.Optional;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 *
 * A class to help display a communication artifact in a panel using a
 * gridbaglayout.
 */
public final class CommunicationArtifactViewerHelper {

    // Number of columns in the gridbag layout.
    private final static int MAX_COLS = 4;

    private final static int LEFT_INDENT = 12;
    
    /**
     * Empty private constructor
     */
    private CommunicationArtifactViewerHelper() {

    }
    
    /**
     * Adds a new heading to the panel.
     *
     * @param panel Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints Constrains to use.
     * @param headerString Heading string to display.
     */
    static void addHeader(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, String headerString) {

        // add a blank line before the start of new section, unless it's 
        // the first section
        if (constraints.gridy != 0) {
            addBlankLine(panel, gridbagLayout, constraints);
        }
        constraints.gridy++;
        constraints.gridx = 0;

        // let the header span all of the row
        constraints.gridwidth = MAX_COLS;

        // create label for heading
        javax.swing.JLabel headingLabel = new javax.swing.JLabel();
        headingLabel.setText(headerString);

        // make it large and bold
        headingLabel.setFont(headingLabel.getFont().deriveFont(Font.BOLD, headingLabel.getFont().getSize() + 2));

        // add to panel
        gridbagLayout.setConstraints(headingLabel, constraints);
        panel.add(headingLabel);

        // reset constraints to normal
        constraints.gridwidth = 1;

        // add line end glue
        addLineEndGlue(panel, gridbagLayout, constraints);
    }

    /**
     * Adds the given component to the panel.
     * 
     * Caller must know what it's doing and set up all the constraints properly.
     *
     * @param panel Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints Constrains to use.
     * @param component Component to add.
     */
    static void addComponent(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, JComponent component) {
        
        // add to panel
        gridbagLayout.setConstraints(component, constraints);
        panel.add(component);

        // add line end glue
        addLineEndGlue(panel, gridbagLayout, constraints);
    }
    
    /**
     * Adds a filler/glue at the end of the line to keep the other columns
     * aligned, in case the panel is resized.
     *
     * @param panel Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints Constrains to use.
     */
    private static void addLineEndGlue(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints) {
        // Place the filler just past the last column.
        constraints.gridx = MAX_COLS;

        double savedWeightX = constraints.weightx;
        int savedFill = constraints.fill;

        constraints.weightx = 1.0; // take up all the horizontal space
        constraints.fill = GridBagConstraints.BOTH;

        javax.swing.Box.Filler horizontalFiller = new javax.swing.Box.Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(32767, 0));
        gridbagLayout.setConstraints(horizontalFiller, constraints);
        panel.add(horizontalFiller);

        // restore fill & weight
        constraints.fill = savedFill;
        constraints.weightx = savedWeightX;
    }

    /**
     * Adds a filler/glue at the bottom of the panel to keep the data rows
     * aligned, in case the panel is resized.
     *
     * @param panel Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints Constrains to use.
     */
    static void addPageEndGlue(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints) {

        constraints.gridx = 0;

        double savedWeighty = constraints.weighty;
        int savedFill = constraints.fill;

        constraints.weighty = 1.0; // take up all the vertical space
        constraints.fill = GridBagConstraints.VERTICAL;

        javax.swing.Box.Filler vertFiller = new javax.swing.Box.Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(0, 32767));
        gridbagLayout.setConstraints(vertFiller, constraints);
        panel.add(vertFiller, constraints);

        //Resore weight & fill
        constraints.weighty = savedWeighty;
        constraints.fill = savedFill;
    }

    /**
     * Adds a blank line to the panel.
     *
     * @param panel Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints Constrains to use.
     */
    private static void addBlankLine(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints) {
        constraints.gridy++;
        constraints.gridx = 0;

        javax.swing.JLabel filler = new javax.swing.JLabel(" ");
        gridbagLayout.setConstraints(filler, constraints);
        panel.add(filler);

        addLineEndGlue(panel, gridbagLayout, constraints);
    }

    /**
     * Adds a label/key to the panel.
     *
     * @param panel Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints Constrains to use.
     * @param keyString Key name to display.
     */
    static void addKey(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, String keyString) {

        constraints.gridy++;
        constraints.gridx = 0;

        Insets savedInsets = constraints.insets;

        // Set inset to indent in
        constraints.insets = new java.awt.Insets(0, LEFT_INDENT, 0, 0);

        // create label,
        javax.swing.JLabel keyLabel = new javax.swing.JLabel();
        keyLabel.setText(keyString + ": ");

        // add to panel
        gridbagLayout.setConstraints(keyLabel, constraints);
        panel.add(keyLabel);

        // restore inset
        constraints.insets = savedInsets;
    }

    /**
     * Adds a value string to the panel.
     *
     * @param panel Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints Constrains to use.
     * @param keyString Value string to display.
     */
    static void addValue(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, String valueString) {

        constraints.gridx = 1;

        int savedGridwidth = constraints.gridwidth;

        // let the value span 2 cols
        constraints.gridwidth = 2;

        // create label,
        javax.swing.JLabel valueField = new javax.swing.JLabel();
        valueField.setText(valueString);

        // attach a right click menu with Copy option
        valueField.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                valueLabelMouseClicked(evt, valueField);
            }
        });

        // add label to panel
        gridbagLayout.setConstraints(valueField, constraints);
        panel.add(valueField);

        // restore constraints
        constraints.gridwidth = savedGridwidth;

        // end the line
        addLineEndGlue(panel, gridbagLayout, constraints);
    }

    /**
     * Adds a Persona row to the panel.
     *
     * Adds a persona name label and a button to the panel. Kicks off a
     * background task to search for persona for the given account. Updates the
     * persona name and button when the task is done.
     *
     * If CentralRepostory is disabled, just displays 'Unknown' persona name.
     *
     * @param panel Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints Constrains to use.
     * @param accountIdentifier Account identifier to search the persona.
     *
     * @return Optional PersonaSearchAndDisplayTask started to search for
     * persona.
     */
    @NbBundle.Messages({
        "CommunicationArtifactViewerHelper_persona_label=Persona: ",
        "CommunicationArtifactViewerHelper_persona_searching=Searching...",
        "CommunicationArtifactViewerHelper_persona_unknown=Unknown",
        "CommunicationArtifactViewerHelper_persona_button_view=View",
        "CommunicationArtifactViewerHelper_persona_button_create=Create"
    })
    static Optional<PersonaSearchAndDisplayTask> addPersonaRow(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, String accountIdentifier) {

        PersonaSearchAndDisplayTask personaTask = null;

        constraints.gridy++;
        constraints.gridx = 1;

        Insets savedInsets = constraints.insets;

        // Indent in
        constraints.insets = new java.awt.Insets(0, LEFT_INDENT, 0, 0);

        // create label
        javax.swing.JLabel personaLabel = new javax.swing.JLabel();
        String personaLabelText = Bundle.CommunicationArtifactViewerHelper_persona_label();
        personaLabelText = personaLabelText.concat(CentralRepository.isEnabled()
                ? Bundle.CommunicationArtifactViewerHelper_persona_searching()
                : Bundle.CommunicationArtifactViewerHelper_persona_unknown());

        personaLabel.setText(personaLabelText);

        // add to panel
        gridbagLayout.setConstraints(personaLabel, constraints);
        panel.add(personaLabel);

        // restore constraint
        constraints.insets = savedInsets;

        constraints.gridx++;

        // Place a button as place holder. It will be enabled when persona is available. 
        javax.swing.JButton personaButton = new javax.swing.JButton();
        personaButton.setText(Bundle.CommunicationArtifactViewerHelper_persona_button_view());
        personaButton.setEnabled(false);
        

        gridbagLayout.setConstraints(personaButton, constraints);
        panel.add(personaButton);

        if (CentralRepository.isEnabled()) {
            // kick off a task to find the persona for this account
            personaTask = new PersonaSearchAndDisplayTask(panel, new AccountPersonaSearcherData(accountIdentifier, personaLabel, personaButton));
            personaTask.execute();
        } else {
            personaLabel.setEnabled(false);
        }

        addLineEndGlue(panel, gridbagLayout, constraints);

        return Optional.ofNullable(personaTask);
    }

    /**
     * Event handler for mouse click event. Attaches a 'Copy' menu item to right
     * click.
     *
     * @param evt Event to check.
     * @param valueLabel Label to attach the menu item to.
     */
    @NbBundle.Messages({
        "CommunicationArtifactViewerHelper_menuitem_copy=Copy"
    })
    private static void valueLabelMouseClicked(java.awt.event.MouseEvent evt, JLabel valueLabel) {
        if (SwingUtilities.isRightMouseButton(evt)) {
            JPopupMenu popup = new JPopupMenu();

            JMenuItem copyMenu = new JMenuItem(Bundle.CommunicationArtifactViewerHelper_menuitem_copy()); // NON-NLS
            copyMenu.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(valueLabel.getText()), null);

                }
            });

            popup.add(copyMenu);
            popup.show(valueLabel, evt.getX(), evt.getY());
        }
    }
}
