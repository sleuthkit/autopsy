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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.TskData;

/**
 * Node for layout dir
 */
public class VirtualDirectoryNode extends AbstractAbstractFileNode<VirtualDirectory> {

    private static Logger logger = Logger.getLogger(VirtualDirectoryNode.class.getName());
    //prefix for special VirtualDirectory root nodes grouping local files
    public final static String LOGICAL_FILE_SET_PREFIX = "LogicalFileSet";

    public static String nameForLayoutFile(VirtualDirectory ld) {
        return ld.getName();
    }

    public VirtualDirectoryNode(VirtualDirectory ld) {
        super(ld);

        this.setDisplayName(nameForLayoutFile(ld));

        String name = ld.getName();

        //set icon for name, special case for some built-ins
        if (name.equals(VirtualDirectory.NAME_UNALLOC)) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/folder-icon-deleted.png");
        } else if (name.startsWith(LOGICAL_FILE_SET_PREFIX)) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/fileset-icon-16.png");
        } else if (name.equals(VirtualDirectory.NAME_CARVED)) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png"); //TODO
        } else {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png");
        }

    }

    /**
     * Right click action for this node
     *
     * @param popup
     * @return
     */
    @Override
    public Action[] getActions(boolean popup) {
        List<Action> actions = new ArrayList<>();
        actions.add(new NewWindowViewAction(
                NbBundle.getMessage(this.getClass(), "VirtualDirectoryNode.getActions.viewInNewWin.text"), this));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions.toArray(new Action[0]);
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
        fillPropertyMap(map, content);

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VirtualDirectoryNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(),
                "VirtualDirectoryNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "VirtualDirectoryNode.createSheet.name.desc"),
                getName()));

        final String NO_DESCR = NbBundle.getMessage(this.getClass(), "VirtualDirectoryNode.createSheet.noDesc");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            ss.put(new NodeProperty<>(entry.getKey(), entry.getKey(), NO_DESCR, entry.getValue()));
        }

        return s;
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
        return true;
    }

    /**
     * Convert meta flag long to user-readable string / label
     *
     * @param metaFlag to convert
     * @return string formatted meta flag representation
     */
    public static String metaFlagToString(short metaFlag) {

        String result = "";

        short allocFlag = TskData.TSK_FS_META_FLAG_ENUM.ALLOC.getValue();
        short unallocFlag = TskData.TSK_FS_META_FLAG_ENUM.UNALLOC.getValue();

        if ((metaFlag & allocFlag) == allocFlag) {
            result = TskData.TSK_FS_META_FLAG_ENUM.ALLOC.toString();
        }
        if ((metaFlag & unallocFlag) == unallocFlag) {
            result = TskData.TSK_FS_META_FLAG_ENUM.UNALLOC.toString();
        }

        return result;
    }
}
