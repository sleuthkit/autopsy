/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * A <code>Children</code> implementation for a 
 * <code>TableFilterNode</code>. A 
 * <code>TableFilterNode</code> creates at most one layer of child 
 * nodes for the node it wraps. It is designed to be used in the results view 
 * to ensure the individual viewers display only the first layer of child nodes.
 */
class TableFilterChildren extends FilterNode.Children {

    private int numberOfNodesCreated;
    private static volatile boolean maxResultsDialogShown = false;

    /**
     * Creates a Children object for a TableFilterNode. A TableFilterNode
     * creates at most one layer of child nodes for the node it wraps. It is
     * designed to be used in the results view to ensure the individual viewers
     * display only the first layer of child nodes.
     *
     * @param wrappedNode    The node wrapped by the TableFilterNode.
     * @param createChildren True if a children (child factory) object should be
     *                       created for the wrapped node.
     *
     * @return A children (child factory) object for a node wrapped by a
     *         TableFilterNode.
     */
    public static Children createInstance(Node wrappedNode, boolean createChildren) {

        if (createChildren) {
            return new TableFilterChildren(wrappedNode);
        } else {
            return Children.LEAF;
        }
    }

    /**
     * Constructs a children (child factory) implementation for a
     * <code>TableFilterNode</code>. A <code>TableFilterNode</code> creates at
     * most one layer of child nodes for the node it wraps. It is designed to be
     * used for nodes displayed in Autopsy table views.
     *
     * @param wrappedNode The node wrapped by the TableFilterNode.
     */
    TableFilterChildren(Node wrappedNode) {
        super(wrappedNode);
        numberOfNodesCreated = 0;
    }

    /**
     * Copies a TableFilterNode, with the create children (child factory) flag
     * set to false.
     *
     * @param nodeToCopy The TableFilterNode to copy.
     *
     * @return A copy of a TableFilterNode.
     */
    @Override
    protected Node copyNode(Node nodeToCopy) {
        return new TableFilterNode(nodeToCopy, false);
    }

    /**
     * Creates the child nodes represented by this children (child factory)
     * object.
     *
     * @param key The key, i.e., the node, for which to create the child nodes.
     *
     * @return
     */
    @Override
    @NbBundle.Messages({"# {0} - The results limit",
        "TableFilterChildren.createNodes.limitReached.msg="
        + "The limit on the number of results to display has been reached."
        + " Only the first {0} results will be shown."
        + " The limit can be modified under Tools, Options, View."})
    protected Node[] createNodes(Node key) {
        int maxNodesToCreate = UserPreferences.getMaximumNumberOfResults();

        if (maxNodesToCreate == 0 || numberOfNodesCreated < maxNodesToCreate) {
            // We either haven't hit the limit yet, or we don't have a limit.

            /**
             * We only want to apply the limit to "our" nodes (i.e. not the
             * wait node). If we don't do this the "Please wait..."
             * node causes the number of results in the table to be off by one.
             * Using the Bundle to get the value so that we are not tied to a
             * particular locale.
             */
            if (!key.getDisplayName().equalsIgnoreCase(NbBundle.getMessage(Node.class, "LBL_WAIT"))) {
                numberOfNodesCreated++;

                // If we have a limit and the creation of this node reaches it,
                // tell the user if they haven't already been told.
                if (numberOfNodesCreated == maxNodesToCreate && !maxResultsDialogShown) {
                    maxResultsDialogShown = true;

                    SwingUtilities.invokeLater(()
                            -> JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                                    Bundle.TableFilterChildren_createNodes_limitReached_msg(maxNodesToCreate))
                    );
                }
            }
            return new Node[]{this.copyNode(key)};
        } else {
            return new Node[]{};
        }
    }
}
