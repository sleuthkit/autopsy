/*
 * 
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
package org.sleuthkit.autopsy.corecomponents;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import org.openide.explorer.view.Visualizer;
import org.openide.nodes.Node;

/**
 * A tree expansion listener that will trigger a recreation of childs through
 * its child factory on re-expansion of a node (causes to recreate the
 * ChildFactory for this purpose.).
 */
public final class DelayedLoadChildNodesOnTreeExpansion implements TreeExpansionListener {

    /**
     * A flag for avoiding endless recursion inside the expansion listener that
     * could trigger collapsing and (re-)expanding nodes again.
     * @param event
     */
    //private boolean inRecursion = false;

    @Override
    public synchronized void treeCollapsed(final TreeExpansionEvent event) {
        Node eventNode = Visualizer.findNode(event.getPath().getLastPathComponent());
//        if (eventNode instanceof CommonFilesNode) { // avoid endless //!inRecursion && 
//            // recursion
//            final CommonFilesNode node = (CommonFilesNode) eventNode;
//            node.setCleanRefreshNeeded(true);
//        }
    }

    @Override
    public synchronized void treeExpanded(final TreeExpansionEvent event) {
        Node eventNode = Visualizer.findNode(event.getPath().getLastPathComponent());
        if (eventNode instanceof MultiLayerTableFilterNode) { //!inRecursion && 
            final MultiLayerTableFilterNode node = (MultiLayerTableFilterNode) eventNode;
            node.refresh();
//            if (!outlineView.isExpanded(node)) {
//                // Seems that the refresh caused to collapse, re-expand again and
//                // avoid recursion in this listener!
//                inRecursion = true;
//                try {
//                    outlineView.expandNode(node);
//                } finally {
//                    inRecursion = false;
//                }
//            }
        }

    }
}
