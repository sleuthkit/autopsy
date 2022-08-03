/*
 * Autopsy
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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.contentviewers.layout.ContentViewerDefaults;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.discovery.ui.AbstractArtifactDetailsPanel;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel to display the details for an Artifact.
 */
@ServiceProvider(service = ArtifactContentViewer.class)
public class GeneralPurposeArtifactViewer extends AbstractArtifactDetailsPanel implements ArtifactContentViewer {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(GeneralPurposeArtifactViewer.class.getName());
    // Number of columns in the gridbag layout.
    private final static int MAX_COLS = 4;
    private final static Insets ZERO_INSETS = new java.awt.Insets(0, 0, 0, 0);

    private final static Insets FIRST_HEADER_INSETS = ZERO_INSETS;
    private final static Insets HEADER_INSETS = new Insets(ContentViewerDefaults.getSectionSpacing(), 0, ContentViewerDefaults.getLineSpacing(), 0);
    private final static Insets VALUE_COLUMN_INSETS = new Insets(0, ContentViewerDefaults.getColumnSpacing(), ContentViewerDefaults.getLineSpacing(), 0);
    private final static Insets KEY_COLUMN_INSETS = new Insets(0, ContentViewerDefaults.getSectionIndent(), ContentViewerDefaults.getLineSpacing(), 0);

    private final static double GLUE_WEIGHT_X = 1.0;
    private final static double TEXT_WEIGHT_X = 0.0;
    private final static int LABEL_COLUMN = 0;
    private final static int VALUE_COLUMN = 1;
    private final static int VALUE_WIDTH = 2;
    private final static int LABEL_WIDTH = 1;
    private static final Integer[] DEFAULT_ORDERING = new Integer[]{BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(),
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(),
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID(),
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT.getTypeID(),
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(),
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_HEADERS.getTypeID()};
    private static final List<Integer> TYPES_WITH_DATE_SECTION = Arrays.asList(new Integer[]{BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()});
    private final GridBagLayout gridBagLayout = new GridBagLayout();
    private final GridBagConstraints gridBagConstraints = new GridBagConstraints();
    private final Map<Integer, Integer[]> orderingMap = new HashMap<>();
    private final javax.swing.JPanel detailsPanel = new javax.swing.JPanel();

    /**
     * Creates new form GeneralPurposeArtifactViewer.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public GeneralPurposeArtifactViewer() {
        addOrderings();
        initComponents();
        gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        detailsPanel.setLayout(gridBagLayout);
        detailsPanel.setBorder(new EmptyBorder(ContentViewerDefaults.getPanelInsets()));
    }

    /**
     * Private helper method to add the orderings used for each artifact type to
     * the map for lookup.
     */
    private void addOrderings() {
        orderingMap.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID(), new Integer[]{BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()});
        orderingMap.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE.getTypeID(), new Integer[]{BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_HEADERS.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()});
        orderingMap.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID(), new Integer[]{BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()});
        orderingMap.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID(), new Integer[]{BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()});
        orderingMap.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID(), new Integer[]{BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()});
        orderingMap.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID(), new Integer[]{BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()});
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @NbBundle.Messages({"GeneralPurposeArtifactViewer.unknown.text=Unknown"})
    @Override
    public void setArtifact(BlackboardArtifact artifact) {
        resetComponent();
        if (artifact != null) {
            String dataSourceName = Bundle.GeneralPurposeArtifactViewer_unknown_text();
            String hostName = Bundle.GeneralPurposeArtifactViewer_unknown_text();
            String sourceFileName = Bundle.GeneralPurposeArtifactViewer_unknown_text();
            Map<Integer, List<BlackboardAttribute>> attributeMap = new HashMap<>();
            try {
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

                hostName = Optional.ofNullable(Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().getHostByDataSource((DataSource) artifact.getDataSource()))
                        .map(h -> h.getName())
                        .orElse(null);

                sourceFileName = artifact.getParent().getUniquePath();
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to get attributes for artifact " + artifact.getArtifactID(), ex);
            }
            updateView(artifact, attributeMap, dataSourceName, hostName, sourceFileName);
        }
        detailsScrollPane.setViewportView(detailsPanel);
        detailsScrollPane.revalidate();
        revalidate();
    }

    /**
     * Reset the panel so that it is empty.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void resetComponent() {
        // clear the panel 
        detailsPanel.removeAll();
        detailsPanel.setLayout(gridBagLayout);
        detailsPanel.revalidate();
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridx = LABEL_COLUMN;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.weightx = TEXT_WEIGHT_X;    // keep components fixed horizontally.
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = ZERO_INSETS;
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Override
    public boolean isSupported(BlackboardArtifact artifact) {
        return (artifact != null)
                && (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()
                || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()
                || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()
                || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID()
                || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()
                || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE.getTypeID()
                || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_ACCOUNT_TYPE.getTypeID()
                || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_FORM_ADDRESS.getTypeID()
                || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_FORM_AUTOFILL.getTypeID());
    }

    @NbBundle.Messages({"GeneralPurposeArtifactViewer.details.attrHeader=Details",
        "GeneralPurposeArtifactViewer.details.sourceHeader=Source",
        "GeneralPurposeArtifactViewer.details.dataSource=Data Source",
        "GeneralPurposeArtifactViewer_details_host=Host",
        "GeneralPurposeArtifactViewer.details.file=File",
        "GeneralPurposeArtifactViewer.details.datesHeader=Dates"})
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        detailsScrollPane = new javax.swing.JScrollPane();

        detailsScrollPane.setPreferredSize(new java.awt.Dimension(300, 0));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(detailsScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(detailsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Update the view to reflect the current artifact's details.
     *
     * @param artifact       The artifact being displayed.
     * @param attributeMap   The map of attributes that exist for the artifact.
     * @param dataSourceName The name of the datasource that caused the creation
     *                       of the artifact.
     * @param hostName       The host name.
     * @param sourceFilePath The path of the file that caused the creation of
     *                       the artifact.
     */
    @NbBundle.Messages({"GeneralPurposeArtifactViewer.dates.created=Created",
        "GeneralPurposeArtifactViewer.dates.start=Start",
        "GeneralPurposeArtifactViewer.dates.end=End",
        "GeneralPurposeArtifactViewer.dates.time=Time",
        "GeneralPurposeArtifactViewer.term.label=Term",
        "GeneralPurposeArtifactViewer.details.otherHeader=Other",
        "GeneralPurposeArtifactViewer.noFile.text= (no longer exists)"})
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void updateView(BlackboardArtifact artifact, Map<Integer, List<BlackboardAttribute>> attributeMap, String dataSourceName, String hostName, String sourceFilePath) {
        final Integer artifactTypeId = artifact.getArtifactTypeID();
        if (!(artifactTypeId < 1 || artifactTypeId >= Integer.MAX_VALUE)) {
            JTextPane firstTextPane = addDetailsHeader(artifactTypeId);
            Integer[] orderingArray = orderingMap.get(artifactTypeId);
            if (orderingArray == null) {
                orderingArray = DEFAULT_ORDERING;
            }
            for (Integer attrId : orderingArray) {
                List<BlackboardAttribute> attrList = attributeMap.remove(attrId);
                if (attrList != null) {
                    for (BlackboardAttribute bba : attrList) {
                        if (bba.getAttributeType().getTypeName().startsWith("TSK_DATETIME")) {
                            if (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()) {
                                addNameValueRow(Bundle.GeneralPurposeArtifactViewer_dates_time(), TimeZoneUtils.getFormattedTime(bba.getValueLong()));
                            } else {
                                addNameValueRow(bba.getAttributeType().getDisplayName(), TimeZoneUtils.getFormattedTime(bba.getValueLong()));
                            }
                        } else if (bba.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT.getTypeID() && artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()) {
                            addNameValueRow(Bundle.GeneralPurposeArtifactViewer_term_label(), bba.getDisplayString());
                        } else if (bba.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID()) {
                            String displayString = bba.getDisplayString();
                            if (!attributeMap.containsKey(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID())) {
                                displayString += Bundle.GeneralPurposeArtifactViewer_noFile_text();
                            }
                            addNameValueRow(bba.getAttributeType().getDisplayName(), displayString);
                        } else {
                            addNameValueRow(bba.getAttributeType().getDisplayName(), bba.getDisplayString());
                        }
                    }
                }
            }
            if (TYPES_WITH_DATE_SECTION.contains(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID())) {
                boolean headerAdded = false;
                headerAdded = addDates(Bundle.GeneralPurposeArtifactViewer_dates_created(), attributeMap.remove(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID()), headerAdded);
                headerAdded = addDates(Bundle.GeneralPurposeArtifactViewer_dates_start(), attributeMap.remove(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID()), headerAdded);
                headerAdded = addDates(Bundle.GeneralPurposeArtifactViewer_dates_end(), attributeMap.remove(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID()), headerAdded);
                addDates(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getDisplayName(), attributeMap.remove(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()), headerAdded);
            }
            if (!attributeMap.keySet().isEmpty()) {
                addHeader(Bundle.GeneralPurposeArtifactViewer_details_otherHeader());
                for (int key : attributeMap.keySet()) {
                    for (BlackboardAttribute bba : attributeMap.get(key)) {
                        if (bba.getAttributeType().getTypeName().startsWith("TSK_DATETIME")) {
                            addNameValueRow(bba.getAttributeType().getDisplayName(), TimeZoneUtils.getFormattedTime(bba.getValueLong()));
                        } else {
                            addNameValueRow(bba.getAttributeType().getDisplayName(), bba.getDisplayString());
                        }
                    }
                }
            }
            addHeader(Bundle.GeneralPurposeArtifactViewer_details_sourceHeader());
            addNameValueRow(Bundle.GeneralPurposeArtifactViewer_details_host(), StringUtils.defaultString(hostName));
            addNameValueRow(Bundle.GeneralPurposeArtifactViewer_details_dataSource(), dataSourceName);
            addNameValueRow(Bundle.GeneralPurposeArtifactViewer_details_file(), sourceFilePath);
            // add veritcal glue at the end
            addPageEndGlue();
            if (firstTextPane != null) {
                firstTextPane.setCaretPosition(0);
            }
        }
        detailsPanel.revalidate();
    }

    /**
     * Private helper method to add all dates in a given attribute list.
     *
     * @param label        Specific String to use in place of attributes display
     *                     name.
     * @param attrList     List of attributes to add dates for.
     * @param headerExists If the "Dates" header has already been displayed.
     *
     * @return True if the "Dates" header has been displayed, false otherwise.
     */
    private boolean addDates(String label, List<BlackboardAttribute> attrList, boolean headerExists) {
        boolean headerAdded = headerExists;
        if (attrList != null) {
            if (!headerAdded) {
                addHeader(Bundle.GeneralPurposeArtifactViewer_details_datesHeader());
                headerAdded = true;
            }
            String labelToUse = label;
            for (BlackboardAttribute bba : attrList) {
                if (StringUtils.isBlank(label)) {
                    labelToUse = bba.getAttributeType().getDisplayName();
                }
                addNameValueRow(labelToUse, bba.getDisplayString());
            }
        }
        return headerAdded;
    }

    /**
     * Helper method to add an artifact specific details header.
     *
     * @param artifactTypeId ID of artifact type to add header for.
     */
    @NbBundle.Messages({"GeneralPurposeArtifactViewer.details.bookmarkHeader=Bookmark Details",
        "GeneralPurposeArtifactViewer.details.historyHeader=Visit Details",
        "GeneralPurposeArtifactViewer.details.downloadHeader=Downloaded File",
        "GeneralPurposeArtifactViewer.details.searchHeader=Web Search",
        "GeneralPurposeArtifactViewer.details.cachedHeader=Cached File",
        "GeneralPurposeArtifactViewer.details.cookieHeader=Cookie Details",})
    private JTextPane addDetailsHeader(int artifactTypeId) {
        String header;
        if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()) {
            header = Bundle.GeneralPurposeArtifactViewer_details_historyHeader();
        } else if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID()) {
            header = Bundle.GeneralPurposeArtifactViewer_details_bookmarkHeader();
        } else if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE.getTypeID()) {
            header = Bundle.GeneralPurposeArtifactViewer_details_cachedHeader();
        } else if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()) {
            header = Bundle.GeneralPurposeArtifactViewer_details_cookieHeader();
        } else if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()) {
            header = Bundle.GeneralPurposeArtifactViewer_details_downloadHeader();
        } else if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()) {
            header = Bundle.GeneralPurposeArtifactViewer_details_searchHeader();
        } else {
            header = Bundle.GeneralPurposeArtifactViewer_details_attrHeader();
        }
        return addHeader(header);
    }

    /**
     * Adds a new heading to the panel.
     *
     * @param headerString Heading string to display.
     *
     * @return JLabel Heading label added.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private JTextPane addHeader(String headerString) {
        // create label for heading
        javax.swing.JTextPane headingLabel = new javax.swing.JTextPane();
        headingLabel.setOpaque(false);
        headingLabel.setFocusable(false);
        headingLabel.setEditable(false);
        // add a blank line before the start of new section, unless it's 
        // the first section
        gridBagConstraints.insets = (gridBagConstraints.gridy == 0)
                ? FIRST_HEADER_INSETS
                : HEADER_INSETS;

        gridBagConstraints.gridy++;
        gridBagConstraints.gridx = LABEL_COLUMN;;
        // let the header span all of the row
        gridBagConstraints.gridwidth = MAX_COLS;
        // set text
        headingLabel.setText(headerString);
        // make it large and bold
        headingLabel.setFont(ContentViewerDefaults.getHeaderFont());
        headingLabel.setMargin(ZERO_INSETS);
        // add to panel
        addToPanel(headingLabel);
        // reset constraints to normal
        gridBagConstraints.gridwidth = LABEL_WIDTH;
        // add line end glue
        addLineEndGlue();
        gridBagConstraints.insets = ZERO_INSETS;
        return headingLabel;
    }

    /**
     * Add a key value row to the specified panel with the specified layout and
     * constraints.
     *
     * @param keyString   Key name to display.
     * @param valueString Value string to display.
     */
    private JTextPane addNameValueRow(String keyString, String valueString) {
        addKeyAtCol(keyString);
        return addValueAtCol(valueString);
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
        // add to panel
        addToPanel(horizontalFiller);
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
        // add to panel
        addToPanel(vertFiller);
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
        keyLabel.setFocusable(false);
        gridBagConstraints.gridy++;
        gridBagConstraints.insets = KEY_COLUMN_INSETS;
        gridBagConstraints.gridx = LABEL_COLUMN;
        gridBagConstraints.gridwidth = LABEL_WIDTH;
        // set text
        keyLabel.setText(keyString + ": ");
        // add to panel
        addToPanel(keyLabel);
        return keyLabel;
    }

    private void addToPanel(Component comp) {
        detailsPanel.add(comp, gridBagConstraints);
        detailsPanel.revalidate();
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
        valueField.setFocusable(false);
        valueField.setEditable(false);
        valueField.setOpaque(false);
        valueField.setMargin(ZERO_INSETS);
        gridBagConstraints.gridx = VALUE_COLUMN;
        gridBagConstraints.insets = VALUE_COLUMN_INSETS;
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
        // add label to panel with cloned contraintsF
        detailsPanel.add(valueField, cloneConstraints);
        revalidate();
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
    private javax.swing.JScrollPane detailsScrollPane;
    // End of variables declaration//GEN-END:variables
}
