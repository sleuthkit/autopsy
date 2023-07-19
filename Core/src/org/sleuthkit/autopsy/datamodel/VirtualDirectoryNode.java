/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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

import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * Node for a virtual directory
 */
public class VirtualDirectoryNode extends SpecialDirectoryNode {

    private static final Logger logger = Logger.getLogger(VirtualDirectoryNode.class.getName());
    //prefix for special VirtualDirectory root nodes grouping local files
    public final static String LOGICAL_FILE_SET_PREFIX = "LogicalFileSet"; //NON-NLS

    public static String nameForVirtualDirectory(VirtualDirectory ld) {
        return ld.getName();
    }

    public VirtualDirectoryNode(VirtualDirectory ld) {
        super(ld);

        this.setDisplayName(nameForVirtualDirectory(ld));
        
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/folder-icon-virtual.png"); //TODO NON-NLS
    }

    @Override
    protected Sheet createSheet() {
        Sheet defaultSheet = super.createSheet();
        Sheet.Set defaultSheetSet = defaultSheet.get(Sheet.PROPERTIES);
        
        //Pick out the location column
        //This path should not show because VDs are not part of the data source
        String locationCol = NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.locationColLbl");
        for (Property<?> p : defaultSheetSet.getProperties()) {
            if(locationCol.equals(p.getName())) {
                defaultSheetSet.remove(p.getName());
            }
        }
        
        return defaultSheet;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
