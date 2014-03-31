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

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.directorytree.ExplorerNodeActionVisitor;
import org.sleuthkit.autopsy.directorytree.FileSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.datamodel.Image;

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
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/hard-drive-icon.jpg");
    }

    /**
     * Right click action for this node
     *
     * @param context
     * @return
     */
    @Override
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<Action>();

        actionsList.add(new NewWindowViewAction(
                NbBundle.getMessage(this.getClass(), "ImageNode.getActions.viewInNewWin.text"), this));
        actionsList.add(new FileSearchAction(
                NbBundle.getMessage(this.getClass(), "ImageNode.getActions.openFileSearchByAttr.text")));
        actionsList.addAll(ExplorerNodeActionVisitor.getActions(content));

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
                getName()));

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
}
