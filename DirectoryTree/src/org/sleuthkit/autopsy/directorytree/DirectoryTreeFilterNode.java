/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Volume;

/**
 * This class sets the actions for the nodes in the directory tree and creates
 * the children filter so that files and such are hidden from the tree. 
 *
 */
public class DirectoryTreeFilterNode extends FilterNode {

    private static final Action collapseAll = new CollapseAction("Collapse All");

    /** the constructor */
    public DirectoryTreeFilterNode(Node arg) {
        super(arg, DirectoryTreeFilterChildren.createInstance(arg));
    }

    // TODO This seems bad.  We should have this return the real original and modify code somewhere else to wrap it
    @Override
    public Node getOriginal() {
        return new DataResultFilterNode(super.getOriginal());
    }

    /**
     * Right click action for the nodes in the directory tree.
     *
     * @param popup
     * @return
     */
    @Override
    public Action[] getActions(boolean popup) {
        List<Action> actions = new ArrayList<Action>();

        Content content = super.getOriginal().getLookup().lookup(Content.class);
        if (content != null) {
            actions.addAll(DirectoryTreeFilterNode.getActions(content));
            actions.add(collapseAll);
        }

        return actions.toArray(new Action[actions.size()]);
    }

    private static List<Action> getActions(Content c) {
        List<Action> actions = new ArrayList<Action>();

        actions.addAll(ShowDetailActionVisitor.getActions(c));

        return actions;
    }
}
