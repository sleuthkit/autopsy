/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.directorytree.ExplorerNodeActionVisitor;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.FileSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.ingest.RunIngestModulesDialog;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * This class is used to represent the "Node" for the image. The children of
 * this node are volumes.
 */
public class ImageNode extends AbstractContentNode<Image> {

    /**
     * Helper so that the display name and the name used in building the path
     * are determined the same way.
     *
     * @param i Image to get the name of
     *
     * @return short name for the Image
     */
    static String nameForImage(Image i) {
        return i.getName();
    }

    /**
     * @param img
     */
    public ImageNode(Image img) {
        super(img);

        // set name, display name, and icon
        String imgName = nameForImage(img);
        this.setDisplayName(imgName);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/hard-drive-icon.jpg"); //NON-NLS
    }

    /**
     * Right click action for this node
     *
     * @param context
     *
     * @return
     */
    @Override
    @Messages({"ImageNode.action.runIngestMods.text=Run Ingest Modules",
        "ImageNode.action.openFileSrcByAttr.text=Open File Search by Attributes",})
    public Action[] getActions(boolean context) {

        List<Action> actionsList = new ArrayList<Action>();
        actionsList.addAll(ExplorerNodeActionVisitor.getActions(content));
        actionsList.add(new FileSearchAction(
                NbBundle.getMessage(this.getClass(), "ImageNode.getActions.openFileSearchByAttr.text")));

        //extract dir action
        Directory dir = this.getLookup().lookup(Directory.class);
        if (dir != null) {
            actionsList.add(ExtractAction.getInstance());
            actionsList.add(new AbstractAction(
                    Bundle.ImageNode_action_runIngestMods_text()) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    final RunIngestModulesDialog ingestDialog = new RunIngestModulesDialog(dir);
                    ingestDialog.display();
                }
            });
        }
        final Image img = this.getLookup().lookup(Image.class);

        VirtualDirectory virtualDirectory = this.getLookup().lookup(VirtualDirectory.class);
        // determine if the virtualDireory is at root-level (Logical File Set).
        boolean isRootVD = false;
        if (virtualDirectory != null) {
            try {
                if (virtualDirectory.getParent() == null) {
                    isRootVD = true;
                }
            } catch (TskCoreException ex) {
                //logger.log(Level.WARNING, "Error determining the parent of the virtual directory", ex); // NON-NLS
            }
        }

        // 'run ingest' action and 'file search' action are added only if the
        // selected node is img node or a root level virtual directory.
        if (img != null || isRootVD) {
            actionsList.add(new AbstractAction(
                    Bundle.ImageNode_action_runIngestMods_text()) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    final RunIngestModulesDialog ingestDialog = new RunIngestModulesDialog(Collections.<Content>singletonList(content));
                    ingestDialog.display();
                }
            });
        }

        actionsList.add(new NewWindowViewAction(
                NbBundle.getMessage(this.getClass(), "ImageNode.getActions.viewInNewWin.text"), this));
        return actionsList.toArray(new Action[0]);
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ImageNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "ImageNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "ImageNode.createSheet.name.desc"),
                getDisplayName()));

        return s;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    /*
     * TODO (AUT-1849): Correct or remove peristent column reordering code
     *
     * Added to support this feature.
     */
//    @Override
//    public String getItemType() {
//        return "Image"; //NON-NLS
//    }
}
