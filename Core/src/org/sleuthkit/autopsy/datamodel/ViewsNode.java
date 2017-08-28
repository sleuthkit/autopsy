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
        super(new RootContentChildren(Arrays.asList(
                new FileTypes(sleuthkitCase),
                // June '15: Recent Files was removed because it was not useful w/out filtering
                // add it back in if we can filter the results to a more managable size. 
                // new RecentFiles(sleuthkitCase),
                new DeletedContent(sleuthkitCase),
                new FileSize(sleuthkitCase))),
                Lookups.singleton(NAME));
        setName(NAME);
        setDisplayName(NAME);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/views.png"); //NON-NLS
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ViewsNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "ViewsNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "ViewsNode.createSheet.name.desc"),
                NAME));
        return s;
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }
}
