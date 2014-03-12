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

import java.util.Map;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Node that contains a KeyValue object. The node also has that KeyValue object
 * set to its lookup so that when the node is passed to the content viewers its
 * string will be displayed.
 */
public class KeyValueNode extends AbstractNode {

    private KeyValue data;

    public KeyValueNode(KeyValue thing, Children children) {
        super(children, Lookups.singleton(thing));
        this.setName(thing.getName());
        this.data = thing;

        setIcon();
    }

    public KeyValueNode(KeyValue thing, Children children, Lookup lookup) {
        super(children, lookup);
        this.setName(thing.getName());
        this.data = thing;

        setIcon();
    }

    private void setIcon() {
        //if file/dir, set icon
        AbstractFile af = Lookup.getDefault().lookup(AbstractFile.class);
        if (af != null) {
            // set name, display name, and icon
            if (af.isDir()) {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png");
            } else {
                this.setIconBaseWithExtension(FileNode.getIconForFileType(af));
            }

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

        // table view drops first column of properties under assumption
        // that it contains the node's name
        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "KeyValueNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "KeyValueNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "KeyValueNode.createSheet.name.desc"),
                data.getName()));

        for (Map.Entry<String, Object> entry : data.getMap().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            ss.put(new NodeProperty<>(key,
                    key,
                    NbBundle.getMessage(this.getClass(), "KeyValueNode.createSheet.map.desc"),
                    value));
        }

        return s;
    }
}
