/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

/**
 * EmptyNode Class made for edge case where no mime exist in the database yet.
 * Creates a node to display information on why the tree is empty.
 *
 * Swapped for the FileTypesByMimeType node in
 * DirectoryTreeTopComponent.respondSelection
 */
public final class EmptyNode extends AbstractNode {

    public EmptyNode(String displayedMessage) {
        super(Children.create(new EmptyChildFactory(displayedMessage), true));

    }

    /**
     * Method to check if the node in question is a FileTypesByMimeTypeNode which is empty. 
     * 
     * @param originNode the Node which you wish to check.  
     * @return True if originNode is an instance of FileTypesByMimeTypeNode and is empty, false otherwise.
     */
    public static boolean isEmptyMimeTypeNode(Node originNode) {
        boolean isEmptyMimeNode = false;
        if (originNode instanceof FileTypesByMimeType.FileTypesByMimeTypeNode && ((FileTypesByMimeType.FileTypesByMimeTypeNode) originNode).isEmpty()) {
            isEmptyMimeNode = true;
        }
        return isEmptyMimeNode;
    }

    static class EmptyChildFactory extends ChildFactory<String> {

        String fileIdMsg; //NON-NLS

        private EmptyChildFactory(String displayedMessage) {
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
