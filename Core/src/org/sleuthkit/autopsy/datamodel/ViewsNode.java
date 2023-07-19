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

import java.util.Arrays;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * Node for the views
 *
 */
public class ViewsNode extends DisplayableItemNode {

    public static final String NAME = NbBundle.getMessage(ViewsNode.class, "ViewsNode.name.text");

    public ViewsNode(SleuthkitCase sleuthkitCase) {
        this(sleuthkitCase, 0);
    }
    
    public ViewsNode(SleuthkitCase sleuthkitCase, long dsObjId) {
        
        super(  
                new RootContentChildren(Arrays.asList(
                    new FileTypes(dsObjId),
                    // June '15: Recent Files was removed because it was not useful w/out filtering
                    // add it back in if we can filter the results to a more managable size. 
                    // new RecentFiles(sleuthkitCase),
                    new DeletedContent(sleuthkitCase, dsObjId),
                    new FileSize(sleuthkitCase, dsObjId))
                ),
                Lookups.singleton(NAME)
            );
        setName(NAME);
        setDisplayName(NAME);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/views.png"); //NON-NLS
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
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ViewsNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "ViewsNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "ViewsNode.createSheet.name.desc"),
                NAME));
        return sheet;
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }
}
