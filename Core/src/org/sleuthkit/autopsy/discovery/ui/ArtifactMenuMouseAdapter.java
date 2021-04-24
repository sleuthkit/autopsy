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
package org.sleuthkit.autopsy.discovery.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.actionhelpers.ExtractActionHelper;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * MouseAdapter to display a context menu on the specified
 * AbstractArtifactListPanel when the user right clicks.
 */
class ArtifactMenuMouseAdapter extends java.awt.event.MouseAdapter {

    private final AbstractArtifactListPanel listPanel;
    private static final Logger logger = Logger.getLogger(ArtifactMenuMouseAdapter.class.getName());

    /**
     * Create a new ArtifactMenMouseAdapter.
     *
     * @param listPanel The panel which the menu should be in regards to.
     */
    ArtifactMenuMouseAdapter(AbstractArtifactListPanel listPanel) {
        this.listPanel = listPanel;
    }

    @Override
    public void mouseClicked(java.awt.event.MouseEvent evt) {
        if (!evt.isPopupTrigger() && SwingUtilities.isRightMouseButton(evt) && listPanel != null && !listPanel.isEmpty()) {
            if (listPanel.selectAtPoint(evt.getPoint())) {
                showPopupMenu(evt);
            }
        }
    }

    /**
     * If an artifact is selected display a JPopupMenu which has available
     * actions.
     *
     * @param event The mouseEvent being responded to.
     */
    private void showPopupMenu(java.awt.event.MouseEvent event) {
        BlackboardArtifact artifact = listPanel.getSelectedArtifact();
        if (artifact == null) {
            return;
        }
        try {
            JMenuItem[] items = getMenuItems(artifact);
            JPopupMenu popupMenu = new JPopupMenu();
            for (JMenuItem menu : items) {
                if (menu != null) {
                    popupMenu.add(menu);
                } else {
                    popupMenu.add(new JSeparator());
                }
            }
            listPanel.showPopupMenu(popupMenu, event.getPoint());
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get source content of artifact with ID: " + artifact.getArtifactID(), ex);
        }
    }

    /**
     * Returns a list of JMenuItems for the artifact. The list list may contain
     * nulls which should be removed or replaced with JSeparators.
     *
     * @param artifact The artifact to get menu items for.
     *
     * @return List of menu items.
     *
     * @throws TskCoreException
     */
    @NbBundle.Messages({"ArtifactMenuMouseAdapter.noFile.text=File does not exist."})
    private JMenuItem[] getMenuItems(BlackboardArtifact artifact) throws TskCoreException {
        List<JMenuItem> menuItems = new ArrayList<>();
        BlackboardAttribute pathIdAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID));
        Long contentId;
        if (pathIdAttr != null) {
            contentId = pathIdAttr.getValueLong();
        } else if (artifact.getArtifactTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID() && artifact.getArtifactTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()) {
            contentId = artifact.getObjectID();
        } else {
            contentId = null;
            JMenuItem noFile = new JMenuItem();
            noFile.setText(Bundle.ArtifactMenuMouseAdapter_noFile_text());
            noFile.setEnabled(false);
            noFile.setForeground(Color.RED);
            menuItems.add(noFile);
        }
        menuItems.addAll(getTimelineMenuItems(artifact));
        if (contentId != null) {
            Content content = artifact.getSleuthkitCase().getContentById(contentId);
            menuItems.addAll(getDataModelActionFactoryMenuItems(artifact, content));
            menuItems.add(DeleteFileContentTagAction.getInstance().getMenuForFiles(Arrays.asList((AbstractFile) content)));
        } else {
           menuItems.add(AddBlackboardArtifactTagAction.getInstance().getMenuForContent(Arrays.asList(artifact)));
        }
        menuItems.add(DeleteFileBlackboardArtifactTagAction.getInstance().getMenuForArtifacts(Arrays.asList(artifact)));
        return menuItems.toArray(new JMenuItem[0]);
    }

    /**
     * Gets the Timeline Menu Items for this artifact.
     *
     * @param artifact The artifact to get menu items for.
     *
     * @return List of timeline menu items.
     */
    private List<JMenuItem> getTimelineMenuItems(BlackboardArtifact artifact) {
        List<JMenuItem> menuItems = new ArrayList<>();
        //if this artifact has a time stamp add the action to view it in the timeline
        try {
            if (ViewArtifactInTimelineAction.hasSupportedTimeStamp(artifact)) {
                menuItems.add(new JMenuItem(new ViewArtifactInTimelineAction(artifact)));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error getting arttribute(s) from blackboard artifact %d.", artifact.getArtifactID()), ex); //NON-NLS
        }

        return menuItems;
    }

    /**
     * Use the DateModelActionsFactory to get some of the basic actions for the
     * artifact. The advantage to using the DataModelActionsFactory is that the
     * menu items can be put in a consistent order with other parts of the UI.
     *
     * @param artifact The artifact to get menu items for.
     * @param content  The file the artifact is in regards to.
     *
     * @return List of JMenuItems for the DataModelActionFactory actions.
     */
    @NbBundle.Messages({
        "ArtifactMenuMouseAdapter_ExternalViewer_label=Open in external viewer"
    })
    private List<JMenuItem> getDataModelActionFactoryMenuItems(BlackboardArtifact artifact, Content content) {
        List<JMenuItem> menuItems = new ArrayList<>();
        List<Action> actions = DataModelActionsFactory.getActions(content, true);
        for (Action action : actions) {
            if (action == null) {
                menuItems.add(null);
            } else if (action instanceof ExportCSVAction) {
                // Do nothing we don't need this menu item.
            } else if (action instanceof AddContentTagAction) {
                menuItems.add(((AddContentTagAction) action).getMenuForContent(Arrays.asList((AbstractFile) content)));
            } else if (action instanceof AddBlackboardArtifactTagAction) {
                menuItems.add(((AddBlackboardArtifactTagAction) action).getMenuForContent(Arrays.asList(artifact)));
            } else if (action instanceof ExternalViewerShortcutAction) {
                // Replace with an ExternalViewerAction
                ExternalViewerAction newAction = new ExternalViewerAction(Bundle.ArtifactMenuMouseAdapter_ExternalViewer_label(), new FileNode((AbstractFile) content));
                menuItems.add(new JMenuItem(newAction));
            } else if (action instanceof ExtractAction) {
                menuItems.add(new JMenuItem(new ExtractFileAction((AbstractFile) content)));
            } else {
                menuItems.add(new JMenuItem(action));
            }
        }
        return menuItems;
    }

    /**
     * An action class for extracting the related file.
     */
    @NbBundle.Messages({
        "ArtifactMenuMouseAdapter_label=Extract Files"
    })
    private final class ExtractFileAction extends AbstractAction {

        private static final long serialVersionUID = 1L;
        final private AbstractFile file;

        /**
         * Construct a new ExtractFileAction.
         *
         * @param file The AbstractFile to be extracted.
         */
        private ExtractFileAction(AbstractFile file) {
            super(Bundle.ArtifactMenuMouseAdapter_label());
            this.file = file;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ExtractActionHelper helper = new ExtractActionHelper();
            helper.extract(e, Arrays.asList(file));
        }
    }
}
