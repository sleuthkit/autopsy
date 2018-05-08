/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.contentviewers.PListViewer.PropKeyValue;
import org.sleuthkit.autopsy.contentviewers.PListViewer.PropertyType;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * Factory class to create nodes for Plist table view
 */

class PListRowFactory extends ChildFactory<Integer> {

    private final List<PropKeyValue> rows;

    PListRowFactory(final List<PropKeyValue> rows) {
        this.rows = rows;
    }

    /**
     * Creates keys
     * 
     * @param keys
     * @return true 
     */
    @Override
    protected boolean createKeys(final List<Integer> keys) {
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                keys.add(i);
            }
        }
        return true;
    }

    /**
     * Creates node for the given key
     * @param key
     * @return node for the given key, null if the key is invalid or node doesn't exist
     */
    @Override
    protected Node createNodeForKey(final Integer key) {
        if (Objects.isNull(rows) || rows.isEmpty() || key >= rows.size()) {
            return null;
        }
        return new PListNode(rows.get(key));
    }
}

/**
 * Node for a Plist key
*/
class PListNode extends AbstractNode {

    private final PropKeyValue propKeyVal;

    PListNode(final PropKeyValue propKeyVal) {
        
        super(propKeyVal.getChildren() == null ? Children.LEAF : new PListNodeChildren(propKeyVal.getChildren()));

        this.propKeyVal = propKeyVal;

        super.setName(propKeyVal.getKey());
        super.setDisplayName(propKeyVal.getKey());
        if (propKeyVal.getType() == PropertyType.ARRAY) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keychain-16.png");
        } else if (propKeyVal.getType() == PropertyType.DICTIONARY) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keys-dict-16.png");
        } else  {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/key-16.png");
        }
       
    }

    /**
     * Creates property sheet for the node
     */
    @Override
    protected Sheet createSheet() {

        final Sheet sheet = super.createSheet();
        Sheet.Set properties = sheet.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            sheet.put(properties);
        }

        properties.put(new NodeProperty<>(Bundle.PListNode_TypeCol(),
                Bundle.PListNode_TypeCol(),
                Bundle.PListNode_TypeCol(),
                propKeyVal.getType().name())); // NON-NLS

        properties.put(new NodeProperty<>(Bundle.PListNode_ValueCol(),
                Bundle.PListNode_ValueCol(),
                Bundle.PListNode_ValueCol(),
                (propKeyVal.getChildren() == null) ? propKeyVal.getValue() : "")); // NON-NLS

        return sheet;
    }

    /**
     *  Creates children nodes for a compound PList key
     */
    private static class PListNodeChildren extends Children.Keys<PropKeyValue> {

        private final List<PropKeyValue> children;

        PListNodeChildren(final PropKeyValue... children) {
            super();
            this.children = Arrays.asList(children);
        }

        @Override
        protected void addNotify() {
            super.setKeys(this.children);
        }

        @Override
        protected Node[] createNodes(final PropKeyValue propKeyVal) {
            return new Node[]{new PListNode(propKeyVal)};
        }

    }

}
