/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

/**
 * Provides a root node for the results views with a single child node that
 * displays a message as the sole item in its property sheet, useful for
 * displaying explanatory text in the result views when there is a node with no
 * children in the tree view.
 */
public final class EmptyNode extends AbstractNode {

    /**
     * Provides a root node for the results views with a single child node that
     * displays a message as the sole item in its property sheet, useful for
     * displaying explanatory text in the result views when there is a node with
     * no children in the tree view.
     *
     * @param displayedMessage The text for the property sheet of the child
     *                         node.
     */
    public EmptyNode(String displayedMessage) {
        super(Children.create(new EmptyNodeChildren(displayedMessage), true));
    }

    static class EmptyNodeChildren extends ChildFactory<String> {

        String displayedMessage;

        private EmptyNodeChildren(String displayedMessage) {
            this.displayedMessage = displayedMessage;
        }

        @Override
        protected boolean createKeys(List<String> keys) {
            keys.add(displayedMessage);
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new MessageNode(key);
        }

    }

    /**
     * The single child node of an EmptyNode, responsible for displaying a
     * message as the sole item in its property sheet.
     */
    static class MessageNode extends DisplayableItemNode {

        MessageNode(String name) {
            super(Children.LEAF);
            super.setName(name);
            setName(name);
            setDisplayName(name);
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }
}
