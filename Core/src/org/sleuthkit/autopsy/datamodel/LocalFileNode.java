/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2017 Basis Technology Corp.
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.HashSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * A Node for a LocalFile or DerivedFile content object.
 */
public class LocalFileNode extends AbstractAbstractFileNode<AbstractFile> {
    
    private static final Logger LOGGER = Logger.getLogger(LocalFileNode.class.getName());

    public LocalFileNode(AbstractFile af) {
        super(af);

        this.setDisplayName(af.getName());

        // set name, display name, and icon
        if (af.isDir()) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png"); //NON-NLS
        } else {
            this.setIconBaseWithExtension(FileNode.getIconForFileType(af));
        }

    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, getContent());

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "LocalFileNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "LocalFileNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "LocalFileNode.createSheet.name.desc"),
                getName()));

        final String NO_DESCR = NbBundle.getMessage(this.getClass(), "LocalFileNode.createSheet.noDescr.text");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            ss.put(new NodeProperty<>(entry.getKey(), entry.getKey(), NO_DESCR, entry.getValue()));
        }

        // add tags property to the sheet
        addTagProperty(ss);

        return s;
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.addAll(Arrays.asList(super.getActions(true)));
        actionsList.add(new ViewContextAction(NbBundle.getMessage(this.getClass(), "LocalFileNode.viewFileInDir.text"), this.content));
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(
                NbBundle.getMessage(this.getClass(), "LocalFileNode.getActions.viewInNewWin.text"), this));
        actionsList.add(new ExternalViewerAction(
                NbBundle.getMessage(this.getClass(), "LocalFileNode.getActions.openInExtViewer.text"), this));
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(new HashSearchAction(
                NbBundle.getMessage(this.getClass(), "LocalFileNode.getActions.searchFilesSameMd5.text"), this));
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());
        
        final Collection<AbstractFile> selectedFilesList =
                new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if(selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList.toArray(new Action[0]);
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        // This seems wrong, but it also seems that it is never called
        // because the visitor to figure out if there are children or 
        // not will check if it has children using the Content API
        return true; //!this.hasContentChildren();
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }
}
