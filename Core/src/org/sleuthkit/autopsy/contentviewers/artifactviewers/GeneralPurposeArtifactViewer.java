/*
 * Autopsy
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
package org.sleuthkit.autopsy.contentviewers.artifactviewers;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.discovery.ui.AbstractArtifactDetailsPanel;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel to display the details for a Web History Artifact.
 */
@ServiceProvider(service = ArtifactContentViewer.class)
public class GeneralPurposeArtifactViewer extends AbstractArtifactDetailsPanel implements ArtifactContentViewer {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(GeneralPurposeArtifactViewer.class.getName());
    // Number of columns in the gridbag layout.
    private final static int MAX_COLS = 4;
    private final static Insets ROW_INSETS = new java.awt.Insets(0, 12, 0, 0);
    private final static Insets HEADER_INSETS = new java.awt.Insets(0, 0, 0, 0);
    private final static double GLUE_WEIGHT_X = 1.0;
    private final static double TEXT_WEIGHT_X = 0.0;
    private final static int LABEL_COLUMN = 0;
    private final static int VALUE_COLUMN = 1;
    private final static int VALUE_WIDTH = 2;
    private final static int LABEL_WIDTH = 1;
    private final GridBagLayout gridBagLayout = new GridBagLayout();
    private final GridBagConstraints gridBagConstraints = new GridBagConstraints();
    private String dataSourceName;
    private String sourceFileName;
    private final Map<Integer, List<BlackboardAttribute>> attributeMap = new HashMap<>();

    /**
     * Creates new form GeneralPurposeArtifactViewer.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public GeneralPurposeArtifactViewer() {
        initComponents();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Override
    public void setArtifact(BlackboardArtifact artifact) {
        resetComponent();
        if (artifact != null) {
            try {
                extractArtifactData(artifact);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to get attributes for artifact " + artifact.getArtifactID(), ex);
            }
            updateView();
        }
        this.setLayout(this.gridBagLayout);
        this.revalidate();
        this.repaint();
    }

    /**
     * Extracts data from the artifact to be displayed in the panel.
     *
     * @param artifact Artifact to show.
     *
     * @throws TskCoreException
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void extractArtifactData(BlackboardArtifact artifact) throws TskCoreException {
        // Get all the attributes and group them by the attributeType 
        for (BlackboardAttribute bba : artifact.getAttributes()) {
            List<BlackboardAttribute> attrList = attributeMap.get(bba.getAttributeType().getTypeID());
            if (attrList == null) {
                attrList = new ArrayList<>();
            }
            attrList.add(bba);
            attributeMap.put(bba.getAttributeType().getTypeID(), attrList);
        }
        dataSourceName = artifact.getDataSource().getName();
        sourceFileName = artifact.getParent().getName();
    }

    /**
     * Reset the panel so that it is empty.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void resetComponent() {
        // clear the panel 
        this.removeAll();
        gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridx = LABEL_COLUMN;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.weightx = TEXT_WEIGHT_X;    // keep components fixed horizontally.
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = ROW_INSETS;
        dataSourceName = null;
        sourceFileName = null;
        attributeMap.clear();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Override
    public Component getComponent() {
        // Slap a vertical scrollbar on the panel.
        return new JScrollPane(this, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Override
    public boolean isSupported(BlackboardArtifact artifact) {
        return (artifact != null)
                && (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()
                || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()
                || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()
                || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID());
    }

    @NbBundle.Messages({"GeneralPurposeArtifactViewer.details.attrHeader=Attributes",
        "GeneralPurposeArtifactViewer.details.sourceHeader=Source",
        "GeneralPurposeArtifactViewer.details.dataSource=Data Source",
        "GeneralPurposeArtifactViewer.details.file=File"})
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Update the view to reflect the current artifact's details.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void updateView() {
        addHeader(Bundle.GeneralPurposeArtifactViewer_details_attrHeader());
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE);
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED);
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED);
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START);
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END);
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN);
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL);
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER);
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME);
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE);
        moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT);
        for (int key : attributeMap.keySet()) {
            for (BlackboardAttribute bba : attributeMap.get(key)) {
                addNameValueRow(bba.getAttributeType().getDisplayName(), bba.getDisplayString());
            }
        }
        addHeader(Bundle.GeneralPurposeArtifactViewer_details_sourceHeader());
        addNameValueRow(Bundle.GeneralPurposeArtifactViewer_details_dataSource(), dataSourceName);
        addNameValueRow(Bundle.GeneralPurposeArtifactViewer_details_file(), sourceFileName);
        // add veritcal glue at the end
        addPageEndGlue();
    }

    /**
     * Remove attributes of the specified type from the map and add them to the
     * panel.
     *
     * @param type The type of BlackboardAttribute to remove from the map and
     *             add to the panel.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void moveAttributesFromMapToPanel(BlackboardAttribute.ATTRIBUTE_TYPE type) {
        List<BlackboardAttribute> attrList = attributeMap.remove(type.getTypeID());
        if (attrList != null) {
            for (BlackboardAttribute bba : attrList) {
                addNameValueRow(bba.getAttributeType().getDisplayName(), bba.getDisplayString());
            }
        }
    }

    /**
     * Adds a new heading to the panel.
     *
     * @param headerString Heading string to display.
     *
     * @return JLabel Heading label added.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private JLabel addHeader(String headerString) {
        // create label for heading
        javax.swing.JLabel headingLabel = new javax.swing.JLabel();
        // add a blank line before the start of new section, unless it's 
        // the first section
        if (gridBagConstraints.gridy != 0) {
            gridBagConstraints.gridy++;
            add(new javax.swing.JLabel(" "), gridBagConstraints);
            addLineEndGlue();
        }
        gridBagConstraints.gridy++;
        gridBagConstraints.gridx = LABEL_COLUMN;;
        // let the header span all of the row
        gridBagConstraints.gridwidth = MAX_COLS;
        gridBagConstraints.insets = HEADER_INSETS;
        // set text
        headingLabel.setText(headerString);
        // make it large and bold
        headingLabel.setFont(headingLabel.getFont().deriveFont(Font.BOLD, headingLabel.getFont().getSize() + 2));
        // add to panel
        add(headingLabel, gridBagConstraints);
        // reset constraints to normal
        gridBagConstraints.gridwidth = LABEL_WIDTH;
        // add line end glue
        addLineEndGlue();
        gridBagConstraints.insets = ROW_INSETS;
        return headingLabel;
    }

    /**
     * Add a key value row to the specified panel with the specified layout and
     * constraints.
     *
     * @param keyString   Key name to display.
     * @param valueString Value string to display.
     */
    private void addNameValueRow(String keyString, String valueString) {
        addKeyAtCol(keyString);
        addValueAtCol(valueString);
    }

    /**
     * Adds a filler/glue at the end of the line to keep the other columns
     * aligned, in case the panel is resized.
     */
    private void addLineEndGlue() {
        // Place the filler just past the last column.
        gridBagConstraints.gridx = MAX_COLS;
        gridBagConstraints.weightx = GLUE_WEIGHT_X; // take up all the horizontal space
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        javax.swing.Box.Filler horizontalFiller = new javax.swing.Box.Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(32767, 0));
        add(horizontalFiller, gridBagConstraints);
        // restore fill & weight
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.weightx = TEXT_WEIGHT_X;
    }

    /**
     * Adds a filler/glue at the bottom of the panel to keep the data rows
     * aligned, in case the panel is resized.
     */
    private void addPageEndGlue() {
        gridBagConstraints.weighty = 1.0; // take up all the vertical space
        gridBagConstraints.fill = GridBagConstraints.VERTICAL;
        javax.swing.Box.Filler vertFiller = new javax.swing.Box.Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(0, 32767));
        add(vertFiller, gridBagConstraints);
    }

    /**
     * Adds a label/key to the panel.
     *
     * @param keyString Key name to display.
     *
     * @return Label added.
     */
    private JLabel addKeyAtCol(String keyString) {
        // create label
        javax.swing.JLabel keyLabel = new javax.swing.JLabel();
        gridBagConstraints.gridy++;
        gridBagConstraints.gridx = LABEL_COLUMN;
        gridBagConstraints.gridwidth = LABEL_WIDTH;
        // set text
        keyLabel.setText(keyString + ": ");
        // add to panel
        add(keyLabel, gridBagConstraints);
        return keyLabel;
    }

    /**
     * Adds a value string to the panel at specified column.
     *
     * @param valueString Value string to display.
     *
     * @return Label added.
     */
    private JTextPane addValueAtCol(String valueString) {
        // create label,
        JTextPane valueField = new JTextPane();
        valueField.setEditable(false);
        valueField.setOpaque(false);
        gridBagConstraints.gridx = VALUE_COLUMN;
        GridBagConstraints cloneConstraints = (GridBagConstraints) gridBagConstraints.clone();
        // let the value span 2 cols
        cloneConstraints.gridwidth = VALUE_WIDTH;
        cloneConstraints.fill = GridBagConstraints.BOTH;
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
        add(valueField, cloneConstraints);
        // end the line
        addLineEndGlue();
        return valueField;
    }

    /**
     * Event handler for mouse click event. Attaches a 'Copy' menu item to right
     * click.
     *
     * @param evt        Event to check.
     * @param valueLabel Label to attach the menu item to.
     */
    @NbBundle.Messages({
        "GeneralPurposeArtifactViewer_menuitem_copy=Copy"
    })
    private void valueLabelMouseClicked(java.awt.event.MouseEvent evt, JTextPane valueLabel) {
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


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
