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
import org.sleuthkit.autopsy.datamodel.KeyValue;
import org.sleuthkit.autopsy.datamodel.KeyValueNode;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;

/**
 * Node for KeyValueContent, allowing right click actions to be set.
 */
public class KeyValueFileNode extends KeyValueNode {
    
    KeyValue thing;
    Content content;
    
    public KeyValueFileNode(KeyValueContent thing, Children children) {
        super(thing, children, Lookups.singleton(thing));
        this.setName(thing.getName());
        this.thing = thing;
        this.content = thing.getContent();
    }
    
    public KeyValueFileNode(KeyValueContent thing, Children children, Lookup lookup) {
         super(thing, children, lookup);
         this.setName(thing.getName());
         this.thing = thing;
         this.content = thing.getContent();
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
            actions.add(new org.sleuthkit.autopsy.directorytree.HashSearchAction("Search for similar MD5", new FileNode(f)));
            return actions;
        }
        
        @Override
        public List<Action> visit(Directory f) {
            List<Action> actions = new ArrayList<Action>();
            actions.add(new NewWindowViewAction("View in New Window", new DirectoryNode(f)));
            actions.add(new ExternalViewerAction("Open in External Viewer", new DirectoryNode(f)));
            actions.add(null); // creates a menu separator
            actions.add(new ExtractAction("Extract File", new DirectoryNode(f)));
            actions.add(new org.sleuthkit.autopsy.directorytree.HashSearchAction("Search for similar MD5", new DirectoryNode(f)));
            return actions;
        }

        @Override
        protected List<Action> defaultVisit(Content c) {
            return new ArrayList<Action>();
        }
    }
}