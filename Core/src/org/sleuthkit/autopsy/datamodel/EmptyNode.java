/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
 * EmptyNode Class made for when you need to display a node with with text in the table view
 * but no children in the tree view.
 *
 */
public final class EmptyNode extends AbstractNode {

    /**
     * Creates an EmptyNode
     * 
     * @param displayedMessage the text you would like displayed in the table view, this will not appear as a child node. 
     */
    public EmptyNode(String displayedMessage) {
        super(Children.create(new EmptyNodeChildren(displayedMessage), true));

    }

    static class EmptyNodeChildren extends ChildFactory<String> {

        String fileIdMsg; //NON-NLS

        private EmptyNodeChildren(String displayedMessage) {
            fileIdMsg = displayedMessage;
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.add(fileIdMsg);
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new MessageNode(key);
        }

    }

    /**
     * MessageNode is is the info message that displays in the table view, by
     * also extending a DisplayableItemNode type, rather than an AbstractNode
     * type it doesn't throw an error when right clicked.
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
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }
}
