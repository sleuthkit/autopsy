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
package org.sleuthkit.autopsy.discovery;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility class for the various user interface elements used by Discovery.
 */
final class DiscoveryUiUtils {

    private final static Logger logger = Logger.getLogger(DiscoveryUiUtils.class.getName());
    private static final int BYTE_UNIT_CONVERSION = 1000;
    private static final int ICON_SIZE = 16;
    private static final String RED_CIRCLE_ICON_PATH = "org/sleuthkit/autopsy/images/red-circle-exclamation.png";
    private static final String YELLOW_CIRCLE_ICON_PATH = "org/sleuthkit/autopsy/images/yellow-circle-yield.png";
    private static final String DELETE_ICON_PATH = "org/sleuthkit/autopsy/images/file-icon-deleted.png";
    private static final String UNSUPPORTED_DOC_PATH = "org/sleuthkit/autopsy/images/image-extraction-not-supported.png";
    private static final ImageIcon INTERESTING_SCORE_ICON = new ImageIcon(ImageUtilities.loadImage(YELLOW_CIRCLE_ICON_PATH, false));
    private static final ImageIcon NOTABLE_SCORE_ICON = new ImageIcon(ImageUtilities.loadImage(RED_CIRCLE_ICON_PATH, false));
    private static final ImageIcon DELETED_ICON = new ImageIcon(ImageUtilities.loadImage(DELETE_ICON_PATH, false));
    private static final ImageIcon UNSUPPORTED_DOCUMENT_THUMBNAIL = new ImageIcon(ImageUtilities.loadImage(UNSUPPORTED_DOC_PATH, false));

    @NbBundle.Messages({"# {0} - fileSize",
        "# {1} - units",
        "DiscoveryUiUtility.sizeLabel.text=Size: {0} {1}",
        "DiscoveryUiUtility.bytes.text=bytes",
        "DiscoveryUiUtility.kiloBytes.text=KB",
        "DiscoveryUiUtility.megaBytes.text=MB",
        "DiscoveryUiUtility.gigaBytes.text=GB",
        "DiscoveryUiUtility.terraBytes.text=TB"})
    /**
     * Convert a size in bytes to a string with representing the size in the
     * largest units which represent the value as being greater than or equal to
     * one. Result will be rounded down to the nearest whole number of those
     * units.
     *
     * @param bytes Size in bytes.
     */
    static String getFileSizeString(long bytes) {
        long size = bytes;
        int unitsSwitchValue = 0;
        while (size > BYTE_UNIT_CONVERSION && unitsSwitchValue < 4) {
            size /= BYTE_UNIT_CONVERSION;
            unitsSwitchValue++;
        }
        String units;
        switch (unitsSwitchValue) {
            case 1:
                units = Bundle.DiscoveryUiUtility_kiloBytes_text();
                break;
            case 2:
                units = Bundle.DiscoveryUiUtility_megaBytes_text();
                break;
            case 3:
                units = Bundle.DiscoveryUiUtility_gigaBytes_text();
                break;
            case 4:
                units = Bundle.DiscoveryUiUtility_terraBytes_text();
                break;
            default:
                units = Bundle.DiscoveryUiUtility_bytes_text();
                break;
        }
        return Bundle.DiscoveryUiUtility_sizeLabel_text(size, units);
    }

    /**
     * Get the image to use when the document type does not support image
     * extraction.
     *
     * @return An image that indicates we don't know if there are images.
     */
    static ImageIcon getUnsupportedImageThumbnail() {
        return UNSUPPORTED_DOCUMENT_THUMBNAIL;
    }

    /**
     * Get the names of the sets which exist in the case database for the
     * specified artifact and attribute types.
     *
     * @param artifactType     The artifact type to get the list of sets for.
     * @param setNameAttribute The attribute type which contains the set names.
     *
     * @return A list of set names which exist in the case for the specified
     *         artifact and attribute types.
     *
     * @throws TskCoreException
     */
    static List<String> getSetNames(BlackboardArtifact.ARTIFACT_TYPE artifactType, BlackboardAttribute.ATTRIBUTE_TYPE setNameAttribute) throws TskCoreException {
        List<BlackboardArtifact> arts = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifacts(artifactType);
        List<String> setNames = new ArrayList<>();
        for (BlackboardArtifact art : arts) {
            for (BlackboardAttribute attr : art.getAttributes()) {
                if (attr.getAttributeType().getTypeID() == setNameAttribute.getTypeID()) {
                    String setName = attr.getValueString();
                    if (!setNames.contains(setName)) {
                        setNames.add(setName);
                    }
                }
            }
        }
        Collections.sort(setNames);
        return setNames;
    }

    /**
     * Helper method to see if point is on the icon.
     *
     * @param comp  The component to check if the cursor is over the icon of
     * @param point The point the cursor is at.
     *
     * @return True if the point is over the icon, false otherwise.
     */
    static boolean isPointOnIcon(Component comp, Point point) {
        return comp instanceof JComponent && point.x >= comp.getX() && point.x <= comp.getX() + ICON_SIZE && point.y >= comp.getY() && point.y <= comp.getY() + ICON_SIZE;
    }

    /**
     * Method to set the icon and tool tip text for a label to show deleted
     * status.
     *
     * @param isDeleted      True if the label should reflect deleted status,
     *                       false otherwise.
     * @param isDeletedLabel The label to set the icon and tooltip for.
     */
    static void setDeletedIcon(boolean isDeleted, javax.swing.JLabel isDeletedLabel) {
        if (isDeleted) {
            isDeletedLabel.setIcon(DELETED_ICON);
            isDeletedLabel.setToolTipText(Bundle.ImageThumbnailPanel_isDeleted_text());
        } else {
            isDeletedLabel.setIcon(null);
            isDeletedLabel.setToolTipText(null);
        }
    }

    /**
     * Method to set the icon and tool tip text for a label to show the score.
     *
     * @param resultFile The result file which the label should reflect the
     *                   score of.
     * @param scoreLabel The label to set the icon and tooltip for.
     */
    static void setScoreIcon(ResultFile resultFile, javax.swing.JLabel scoreLabel) {
        switch (resultFile.getScore()) {
            case NOTABLE_SCORE:
                scoreLabel.setIcon(NOTABLE_SCORE_ICON);
                break;
            case INTERESTING_SCORE:
                scoreLabel.setIcon(INTERESTING_SCORE_ICON);
                break;
            case NO_SCORE:  // empty case - this is interpreted as an intentional fall-through
            default:
                scoreLabel.setIcon(null);
                break;
        }
        scoreLabel.setToolTipText(resultFile.getScoreDescription());
    }

    /**
     * Get the size of the icons used by the UI.
     *
     * @return
     */
    static int getIconSize() {
        return ICON_SIZE;
    }

    /**
     * Helper method to display an error message when the results of the
     * Discovery Top component may be incomplete.
     */
    static void displayErrorMessage(DiscoveryDialog dialog) {
        //check if modules run and assemble message
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            Map<Long, DataSourceModulesWrapper> dataSourceIngestModules = new HashMap<>();
            for (DataSource dataSource : skCase.getDataSources()) {
                dataSourceIngestModules.put(dataSource.getId(), new DataSourceModulesWrapper(dataSource.getName()));
            }

            for (IngestJobInfo jobInfo : skCase.getIngestJobs()) {
                dataSourceIngestModules.get(jobInfo.getObjectId()).updateModulesRun(jobInfo);
            }
            String message = "";
            for (DataSourceModulesWrapper dsmodulesWrapper : dataSourceIngestModules.values()) {
                message += dsmodulesWrapper.getMessage();
            }
            if (!message.isEmpty()) {
                JScrollPane messageScrollPane = new JScrollPane();
                JTextPane messageTextPane = new JTextPane();
                messageTextPane.setText(message);
                messageTextPane.setVisible(true);
                messageTextPane.setEditable(false);
                messageTextPane.setCaretPosition(0);
                messageScrollPane.setMaximumSize(new Dimension(600, 100));
                messageScrollPane.setPreferredSize(new Dimension(600, 100));
                messageScrollPane.setViewportView(messageTextPane);
                JOptionPane.showMessageDialog(dialog, messageScrollPane, Bundle.OpenDiscoveryAction_resultsIncomplete_text(), JOptionPane.PLAIN_MESSAGE);
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "Exception while determining which modules have been run for Discovery", ex);
        }
        dialog.validateDialog();
    }

    /**
     * Private constructor for DiscoveryUiUtils utility class.
     */
    private DiscoveryUiUtils() {
        //private constructor in a utility class intentionally left blank
    }
}
