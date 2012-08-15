/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.hashdatabase;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.KeyValueNode;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.HashSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;

/**
 * Node for KeyValueContent, following the simple design of a KeyValueNode,
 * but allowing for added right click actions.
 */
public class KeyValueFileNode extends KeyValueNode {
    
    KeyValueContent kvContent;
    Content content;
    
    public KeyValueFileNode(KeyValueContent kvContent, Children children) {
        super(kvContent, children, Lookups.singleton(kvContent));
        this.setName(kvContent.getName());
        this.kvContent = kvContent;
        this.content = kvContent.getContent();
    }
    
    public KeyValueFileNode(KeyValueContent kvContent, Children children, Lookup lookup) {
         super(kvContent, children, lookup);
         this.setName(kvContent.getName());
         this.kvContent = kvContent;
         this.content = kvContent.getContent();
     }

    /**
     * Right click action for the nodes that we want to pass to the directory
     * table and the output view.
     *
     * @param popup
     * @return actions
     */
    @Override
    public Action[] getActions(boolean popup) {

        List<Action> actions = new ArrayList<Action>();

        actions.addAll(content.accept(new KeyValueFileNode.GetPopupActionsContentVisitor()));

        return actions.toArray(new Action[actions.size()]);
    }

    private class GetPopupActionsContentVisitor extends ContentVisitor.Default<List<Action>> {

        @Override
        public List<Action> visit(File f) {
            List<Action> actions = new ArrayList<Action>();
            actions.add(new NewWindowViewAction("View in New Window", new FileNode(f)));
            actions.add(new ExternalViewerAction("Open in External Viewer", new FileNode(f)));
            actions.add(null); // creates a menu separator
            actions.add(new ExtractAction("Extract File", new FileNode(f)));
            actions.add(new HashSearchAction("Search for similar MD5", new FileNode(f)));
            return actions;
        }
        
        @Override
        public List<Action> visit(Directory f) {
            List<Action> actions = new ArrayList<Action>();
            actions.add(new NewWindowViewAction("View in New Window", new DirectoryNode(f)));
            actions.add(new ExternalViewerAction("Open in External Viewer", new DirectoryNode(f)));
            actions.add(null); // creates a menu separator
            actions.add(new ExtractAction("Extract File", new DirectoryNode(f)));
            return actions;
        }

        @Override
        protected List<Action> defaultVisit(Content c) {
            return new ArrayList<Action>();
        }
    }
}
