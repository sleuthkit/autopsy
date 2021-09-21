/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import javax.swing.Action;
import java.util.ArrayList;
import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestMonitor.AutoIngestNodeState;

/**
 * A node which represents all AutoIngestNodes. Each AutoIngestNode will have a
 * child node representing it and its status.
 */
final class AinStatusNode extends AbstractNode {

    /**
     * Construct a new AinStatusNode.
     */
    AinStatusNode(AutoIngestMonitor monitor) {
        super(Children.create(new AinStatusChildren(monitor), true));
    }

    /**
     * A ChildFactory for generating StatusNodes.
     */
    static class AinStatusChildren extends ChildFactory<AutoIngestNodeState> {

        private final AutoIngestMonitor monitor;

        /**
         * Create children nodes for the AutoIngestNodeState which will each
         * represent a single node state
         *
         * @param autoIngestMonitor the monitor which contains the node states
         */
        AinStatusChildren(AutoIngestMonitor autoIngestMonitor) {
            monitor = autoIngestMonitor;
        }

        @Override
        protected boolean createKeys(List<AutoIngestNodeState> list) {
            list.addAll(monitor.getNodeStates());
            return true;
        }

        @Override
        protected Node createNodeForKey(AutoIngestNodeState key) {
            return new StatusNode(key);
        }
    }

    /**
     * A node which represents a single AutoIngestNode and its status.
     */
    static final class StatusNode extends AbstractNode {

        private final AutoIngestNodeState nodeState;

        /**
         * Construct a new StatusNode to represent an AutoIngestNode and its
         * status.
         *
         * @param nodeState - the AutoIngestNodeState being represented by this
         *                  node
         */
        StatusNode(AutoIngestNodeState nodeState) {
            super(Children.LEAF);
            this.nodeState = nodeState;
            setName(nodeState.getName());
            setDisplayName(nodeState.getName());
        }

        @Override
        @Messages({"AinStatusNode.hostName.title=Host Name",
            "AinStatusNode.status.title=Status",
            "AinStatusNode.status.running=Running",
            "AinStatusNode.status.pauseRequested=Pause Requested",
            "AinStatusNode.status.pausedByUser=Paused By User",
            "AinStatusNode.status.pausedForError=Paused Due to System Error",
            "AinStatusNode.status.startingup=Starting Up",
            "AinStatusNode.status.shuttingdown=Shutting Down",
            "AinStatusNode.status.unknown=Unknown"
        })
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }
            ss.put(new NodeProperty<>(Bundle.AinStatusNode_hostName_title(), Bundle.AinStatusNode_hostName_title(), Bundle.AinStatusNode_hostName_title(),
                    nodeState.getName()));
            String status = Bundle.AinStatusNode_status_unknown();
            switch (nodeState.getState()) {
                case RUNNING:
                    status = Bundle.AinStatusNode_status_running();
                    break;
                case STARTING_UP:
                    status = Bundle.AinStatusNode_status_startingup();
                    break;
                case SHUTTING_DOWN:
                    status = Bundle.AinStatusNode_status_shuttingdown();
                    break;
                case PAUSED_BY_REQUEST:
                    status = Bundle.AinStatusNode_status_pausedByUser();
                    break;
                case PAUSED_DUE_TO_SYSTEM_ERROR:
                    status = Bundle.AinStatusNode_status_pausedForError();
                    break;
                case PAUSE_REQUESTED:
                    status = Bundle.AinStatusNode_status_pauseRequested();
                    break;
                default:
                    break;
            }
            ss.put(new NodeProperty<>(Bundle.AinStatusNode_status_title(), Bundle.AinStatusNode_status_title(), Bundle.AinStatusNode_status_title(),
                    status));
            return s;
        }

        @Override
        public Action[] getActions(boolean context) {
            List<Action> actions = new ArrayList<>();
            if (AutoIngestDashboard.isAdminAutoIngestDashboard()) {
                if (nodeState.getState() == AutoIngestNodeState.State.PAUSED_BY_REQUEST
                        || nodeState.getState() == AutoIngestNodeState.State.PAUSE_REQUESTED
                        || nodeState.getState() == AutoIngestNodeState.State.PAUSED_DUE_TO_SYSTEM_ERROR
                        || nodeState.getState() == AutoIngestNodeState.State.RUNNING) {
                    actions.add(new AutoIngestAdminActions.AutoIngestNodeControlAction.PauseResumeAction(nodeState));
                }
                actions.add(new AutoIngestAdminActions.AutoIngestNodeControlAction.ShutdownAction(nodeState)); 
                actions.add(new AutoIngestAdminActions.AutoIngestNodeControlAction.GenerateThreadDumpControlAction(nodeState));
            }
            return actions.toArray(new Action[actions.size()]);
        }
    }

}
