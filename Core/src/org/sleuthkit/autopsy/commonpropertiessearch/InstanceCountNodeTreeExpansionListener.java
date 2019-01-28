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
package org.sleuthkit.autopsy.commonpropertiessearch;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import org.openide.explorer.view.Visualizer;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;

/**
 * A tree expansion listener used to do lazy creation of the Childfren of an
 * InstanceCountNode when the node is expanded.
 */
final class InstanceCountNodeTreeExpansionListener implements TreeExpansionListener {

    @Override
    public synchronized void treeCollapsed(final TreeExpansionEvent event) {
    }

    @Override
    public synchronized void treeExpanded(final TreeExpansionEvent event) {
        final Node eventNode = Visualizer.findNode(event.getPath().getLastPathComponent());
        if (eventNode instanceof TableFilterNode) {
            final TableFilterNode tableFilterNode = (TableFilterNode) eventNode;
            final DataResultFilterNode dataResultFilterNode = tableFilterNode.getLookup().lookup(DataResultFilterNode.class);
            if (dataResultFilterNode != null) {
                final InstanceCountNode instanceCountNode = dataResultFilterNode.getLookup().lookup(InstanceCountNode.class);
                if (instanceCountNode != null) {
                    instanceCountNode.createChildren();
                }
                final InstanceDataSourceNode instanceDataSourceNode = dataResultFilterNode.getLookup().lookup(InstanceDataSourceNode.class);
                if (instanceDataSourceNode != null) {
                    instanceDataSourceNode.createChildren();
                }
            }
        }
    }

}
