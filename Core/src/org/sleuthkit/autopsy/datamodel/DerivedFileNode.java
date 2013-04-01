/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.HashSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.TagAction;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.LayoutFile;

/**
 * A Node for a DerivedFile content object.
 *
 * TODO should be able to extend FileNode after FileNode extends
 * AbstractFsContentNode<AbstractFile>
 */
public class DerivedFileNode extends AbstractAbstractFileNode<DerivedFile> {

    public static String nameForLayoutFile(LayoutFile lf) {
        return lf.getName();
    }

    public DerivedFileNode(DerivedFile df) {
        super(df);

        this.setDisplayName(df.getName());

        // set name, display name, and icon
        if (df.isDir()) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png");
        } else {
            this.setIconBaseWithExtension(FileNode.getIconForFileType(df));
        }

    }

    @Override
    public TYPE getDisplayableItemNodeType() {
        return TYPE.CONTENT;
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        Map<String, Object> map = new LinkedHashMap<String, Object>();
        fillPropertyMap(map, content);

        ss.put(new NodeProperty("Name", "Name", "no description", getName()));

        final String NO_DESCR = "no description";
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            ss.put(new NodeProperty(entry.getKey(), entry.getKey(), NO_DESCR, entry.getValue()));
        }
        // @@@ add more properties here...

        return s;
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<Action>();

        actionsList.add(new NewWindowViewAction("View in New Window", this));
        actionsList.add(new ExternalViewerAction("Open in External Viewer", this));
        actionsList.add(null); // creates a menu separator
        actionsList.add(new ExtractAction("Extract", content)); //might not need this actions - already local file
        actionsList.add(new HashSearchAction("Search for files with the same MD5 hash", this));
        actionsList.add(null); // creates a menu separator
        actionsList.add(new TagAction(content));

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
        return true; //!this.hasContentChildren();
    }
}
