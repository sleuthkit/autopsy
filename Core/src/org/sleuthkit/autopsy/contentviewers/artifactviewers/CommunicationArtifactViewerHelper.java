/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.artifactviewers;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.contentviewers.layout.ContentViewerDefaults;

/**
 *
 * A class to help display a communication artifact in a panel using a
 * gridbaglayout.
 */
final class CommunicationArtifactViewerHelper {

    // Number of columns in the gridbag layout.
    private final static int MAX_COLS = 4;

    /**
     * Empty private constructor
     */
    private CommunicationArtifactViewerHelper() {

    }

    /**
     * Adds a new heading to the panel.
     *
     * @param panel         Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints   Constraints to use.
     * @param spacing       Spacing to add to top insets (in pixels).
     * @param headerString  Heading string to display.
     *
     * @return JLabel Heading label added.
     */
    static JLabel addHeader(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, int topSpacing, String headerString) {

        Insets savedInsets = constraints.insets;

        // create label for heading
        javax.swing.JLabel headingLabel = new javax.swing.JLabel();


        constraints.gridy++;
        constraints.gridx = 0;

        // let the header span all of the row
        constraints.gridwidth = MAX_COLS;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.fill = GridBagConstraints.NONE;
        
        constraints.insets = new Insets(topSpacing, 0, ContentViewerDefaults.getLineSpacing(), 0);

        // set text
        headingLabel.setText(headerString.trim());

        // make it large and bold
        headingLabel.setFont(ContentViewerDefaults.getHeaderFont());

        // add to panel
        gridbagLayout.setConstraints(headingLabel, constraints);
        panel.add(headingLabel);

        // reset constraints to normal
        constraints.gridwidth = 1;

        // add line end glue
        addLineEndGlue(panel, gridbagLayout, constraints);

        //restore insets
        constraints.insets = savedInsets;

        return headingLabel;
    }

    /**
     * Add a key value row to the specified panel with the specified layout and
     * constraints.
     *
     *
     * @param panel         Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints   Constraints to use.
     * @param keyString     Key name to display.
     * @param valueString   Value string to display.
     *
     */
    static void addNameValueRow(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, String keyString, String valueString) {
        addKey(panel, gridbagLayout, constraints, keyString);
        addValue(panel, gridbagLayout, constraints, valueString);
    }

    /**
     * Adds the given component to the panel.
     *
     * Caller must know what it's doing and set up all the constraints properly.
     *
     * @param panel         Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints   Constraints to use.
     * @param component     Component to add.
     */
    static void addComponent(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, JComponent component) {

        // add to panel
        gridbagLayout.setConstraints(component, constraints);
        panel.add(component);
    }

    /**
     * Adds a filler/glue at the end of the line to keep the other columns
     * aligned, in case the panel is resized.
     *
     * @param panel         Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints   Constraints to use.
     */
    static void addLineEndGlue(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints) {
        // Place the filler just past the last column.
        constraints.gridx = MAX_COLS;

        double savedWeightX = constraints.weightx;
        int savedFill = constraints.fill;

        constraints.weightx = 1.0; // take up all the horizontal space
        constraints.fill = GridBagConstraints.HORIZONTAL;

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
     * @param panel         Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints   Constraints to use.
     */
    static void addPageEndGlue(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints) {

        constraints.gridx = 0;
        constraints.gridy++;

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
     * Adds a label/key to the panel at col 0.
     *
     * @param panel         Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints   Constraints to use.
     * @param keyString     Key name to display.
     *
     * @return Label added.
     */
    static JLabel addKey(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, String keyString) {
        return addKeyAtCol(panel, gridbagLayout, constraints, keyString, 0);
    }

    /**
     * Adds a label/key to the panel at specified column.
     *
     * @param panel         Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints   Constraints to use.
     * @param keyString     Key name to display.
     * @param gridx         column index, must be less than MAX_COLS - 1.
     *
     * @return Label added.
     */
    static JLabel addKeyAtCol(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, String keyString, int gridx) {

        // create label
        javax.swing.JLabel keyLabel = new javax.swing.JLabel();

        constraints.gridy++;
        constraints.gridx = gridx < MAX_COLS - 1 ? gridx : MAX_COLS - 2;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(0, ContentViewerDefaults.getSectionIndent(), ContentViewerDefaults.getLineSpacing(), 0);

        // set text
        String preppedKeyString = keyString == null ? null : keyString.trim() + ":";
        keyLabel.setText(preppedKeyString);

        // add to panel
        gridbagLayout.setConstraints(keyLabel, constraints);
        panel.add(keyLabel);

        return keyLabel;
    }

    /**
     * Adds a value string to the panel at col 1.
     *
     * @param panel         Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints   Constraints to use.
     * @param valueString   Value string to display.
     *
     * @return Label added.
     */
    static JTextPane addValue(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, String valueString) {
        return addValueAtCol(panel, gridbagLayout, constraints, valueString, 1);
    }

    /**
     * Adds a value string to the panel at specified column.
     *
     * @param panel         Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints   Constraints to use.
     * @param keyString     Value string to display.
     * @param gridx         Column index, must be less than MAX_COLS;
     *
     * @return Label added.
     */
    static JTextPane addValueAtCol(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, String valueString, int gridx) {
        // create label,
        JTextPane valueField = new JTextPane();
        valueField.setEditable(false);
        valueField.setOpaque(false);
        valueField.setMargin(new Insets(0,0,0,0));

        constraints.gridx = gridx < MAX_COLS ? gridx : MAX_COLS - 1;

        GridBagConstraints cloneConstraints = (GridBagConstraints) constraints.clone();

        // let the value span 2 cols
        cloneConstraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.LINE_START;
        cloneConstraints.insets = new Insets(0, ContentViewerDefaults.getColumnSpacing(), ContentViewerDefaults.getLineSpacing(), 0);

        // set text
        valueField.setText(valueString);

        // attach a right click menu with Copy option
        valueField.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                valueLabelMouseClicked(evt, valueField);
            }
        });

        // add label to panel
        gridbagLayout.setConstraints(valueField, cloneConstraints);
        panel.add(valueField);

        // end the line
        addLineEndGlue(panel, gridbagLayout, constraints);

        return valueField;
    }

    /**
     * Displays a message string, starting at column 0, and spanning the entire
     * row.
     *
     * @param panel         Panel to show.
     * @param gridbagLayout Layout to use.
     * @param constraints   Constraints to use.
     * @param insets        The insets to be used for the grid bag layout constraints.  If null, default insets are assumed.
     * @param messageString Message to display.
     *
     * @return Label for message added.
     */
    static JLabel addMessageRow(JPanel panel, GridBagLayout gridbagLayout, Insets insets, GridBagConstraints constraints, String messageString) {
        return addMessageRow(panel, gridbagLayout, constraints, insets, messageString, 0);
    }

    /**
     * Displays a message string, starting at specified column, and spanning the
     * entire row.
     *
     * @param panel         Panel to show.
     * @param gridbagLayout Layout to use.
     * @param insets        The insets to be used for the grid bag layout constraints.
     * @param constraints   Constraints to use.
     * @param insets        The insets to be used for the grid bag layout constraints.  If null, default insets are assumed.
     * @param messageString Message to display.
     * @param gridx         The grid x location to use.
     *
     * @return Label for message added.
     */
    static JLabel addMessageRow(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, Insets insets, String messageString, int gridx) {

        // create label
        javax.swing.JLabel messageLabel = new javax.swing.JLabel();

        constraints.gridy++;
        constraints.gridx = gridx < MAX_COLS - 1 ? gridx : MAX_COLS - 2;
        constraints.insets = insets == null 
                ? new Insets(0, 0, ContentViewerDefaults.getLineSpacing(), 0) :
                insets;
        constraints.anchor = GridBagConstraints.LINE_START;
        
        int savedGridwidth = constraints.gridwidth;

        constraints.gridwidth = 3;

        // set text
        messageLabel.setText(messageString == null ? null : messageString.trim());
        messageLabel.setFont(ContentViewerDefaults.getMessageFont());

        // add to panel
        gridbagLayout.setConstraints(messageLabel, constraints);
        panel.add(messageLabel);

        addLineEndGlue(panel, gridbagLayout, constraints);

        // restore constraints
        constraints.gridwidth = savedGridwidth;

        return messageLabel;
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
     * @param panel             Panel to update.
     * @param gridbagLayout     Layout to use.
     * @param constraints       Constraints to use.
     * @param accountIdentifier Account identifier to search the persona.
     *
     * @return List of AccountPersonaSearcherData objects.
     */
    @NbBundle.Messages({
        "CommunicationArtifactViewerHelper_persona_label=Persona: ",
        "CommunicationArtifactViewerHelper_persona_searching=Searching...",
        "CommunicationArtifactViewerHelper_persona_unknown=Unknown",
        "CommunicationArtifactViewerHelper_persona_button_view=View",
        "CommunicationArtifactViewerHelper_persona_button_create=Create"
    })

    static List<AccountPersonaSearcherData> addPersonaRow(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, String accountIdentifier) {
        List<AccountPersonaSearcherData> dataList = new ArrayList<>();

        constraints.gridy++;
        constraints.gridx = 1;

        Insets savedInsets = constraints.insets;

        // extra Indent in
        constraints.insets = new java.awt.Insets(0, ContentViewerDefaults.getColumnSpacing(), ContentViewerDefaults.getLineSpacing(), 0);
        constraints.anchor = GridBagConstraints.LINE_START;
        
        // create label
        javax.swing.JLabel personaLabel = new javax.swing.JLabel();
        String personaLabelText = Bundle.CommunicationArtifactViewerHelper_persona_label();
        personaLabelText = personaLabelText.concat(CentralRepository.isEnabled()
                ? Bundle.CommunicationArtifactViewerHelper_persona_searching()
                : Bundle.CommunicationArtifactViewerHelper_persona_unknown());

        personaLabel.setText(personaLabelText == null ? null : personaLabelText.trim());
        
        // add to panel
        gridbagLayout.setConstraints(personaLabel, constraints);
        panel.add(personaLabel);

        constraints.gridx++;

        // Place a button as place holder. It will be enabled when persona is available. 
        javax.swing.JButton personaButton = new javax.swing.JButton();
        personaButton.setText(Bundle.CommunicationArtifactViewerHelper_persona_button_view());
        personaButton.setMargin(new Insets(0, 5, 0, 5));
        personaButton.setEnabled(false);

        gridbagLayout.setConstraints(personaButton, constraints);
        panel.add(personaButton);

        if (CentralRepository.isEnabled()) {
            // kick off a task to find the persona for this account
            dataList.add(new AccountPersonaSearcherData(accountIdentifier, personaLabel, personaButton));
        } else {
            personaLabel.setEnabled(false);
        }
        
        // restore constraint        
        constraints.insets = savedInsets;

        addLineEndGlue(panel, gridbagLayout, constraints);

        return dataList;
    }

    /**
     * Adds a contact row to the panel.
     *
     * @param panel         Panel to update.
     * @param gridbagLayout Layout to use.
     * @param constraints   Constraints to use.
     * @param contactId     Contact name to display.
     *
     * @return A JLabel with the contact information.
     */
    @NbBundle.Messages({
        "# {0} - contact name",
        "CommunicationArtifactViewerHelper_contact_label=Contact: {0}",
        "CommunicationArtifactViewerHelper_contact_label_unknown=Unknown"
    })
    static JComponent addContactRow(JPanel panel, GridBagLayout gridbagLayout, GridBagConstraints constraints, String contactId) {
        // Increase the y value because we are not calling the addKey
        constraints.gridy++;
        //Don't change the origian constraints, just make a copy to modify
        GridBagConstraints indentedConstraints = (GridBagConstraints) constraints.clone();

        // Add an indent to match persona labels
        indentedConstraints.insets = new java.awt.Insets(0, ContentViewerDefaults.getSectionIndent(), ContentViewerDefaults.getLineSpacing(), 0);

        String contactInfo = Bundle.CommunicationArtifactViewerHelper_contact_label(contactId != null && !contactId.isEmpty() ? contactId : Bundle.CommunicationArtifactViewerHelper_contact_label_unknown());

        return addValueAtCol(panel, gridbagLayout, indentedConstraints, contactInfo, 1);
    }

    /**
     * Event handler for mouse click event. Attaches a 'Copy' menu item to right
     * click.
     *
     * @param evt        Event to check.
     * @param valueLabel Label to attach the menu item to.
     */
    @NbBundle.Messages({
        "CommunicationArtifactViewerHelper_menuitem_copy=Copy"
    })
    private static void valueLabelMouseClicked(java.awt.event.MouseEvent evt, JTextPane valueLabel) {
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
