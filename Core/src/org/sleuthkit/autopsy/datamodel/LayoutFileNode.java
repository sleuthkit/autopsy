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
package org.sleuthkit.autopsy.datamodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.TskData;

/**
 * Node for layout file
 */
public class LayoutFileNode extends AbstractAbstractFileNode<LayoutFile> {

    @Deprecated
    public static enum LayoutContentPropertyType {

        PARTS {
            @Override
            public String toString() {
                return NbBundle.getMessage(this.getClass(), "LayoutFileNode.propertyType.parts");
            }
        }
    }

    public static String nameForLayoutFile(LayoutFile lf) {
        return lf.getName();
    }

    public LayoutFileNode(LayoutFile lf) {
        super(lf);

        this.setDisplayName(nameForLayoutFile(lf));

        if (lf.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.CARVED)) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/carved-file-icon-16.png"); //NON-NLS
        } else {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png"); //NON-NLS
        }
    }

    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.addAll(Arrays.asList(super.getActions(true)));
        actionsList.add(new NewWindowViewAction(
                NbBundle.getMessage(this.getClass(), "LayoutFileNode.getActions.viewInNewWin.text"), this));
        actionsList.add(new ExternalViewerAction(
                NbBundle.getMessage(this.getClass(), "LayoutFileNode.getActions.openInExtViewer.text"), this));
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());

        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }

        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList.toArray(new Action[actionsList.size()]);
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

}
