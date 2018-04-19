/*
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
package org.sleuthkit.autopsy.experimental.autoingest;


import javax.swing.Action;

import java.util.ArrayList;

import java.util.List;

import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;

import org.sleuthkit.autopsy.datamodel.NodeProperty;


/**
 * A node which represents all AutoIngestJobs of a given AutoIngestJobStatus.
 * Each job with the specified status will have a child node representing it.
 */
final class AinStatusNode extends AbstractNode {
    /**
     * Construct a new AutoIngestJobsNode.
     */
    AinStatusNode(AutoIngestMonitor monitor) {
        super(Children.create(new AinStatusChildren(monitor), false));
    }

    /**
     * A ChildFactory for generating JobNodes.
     */
    static class AinStatusChildren extends ChildFactory<AutoIngestJob> {

        private final AutoIngestMonitor monitor;

        /**
         * Create children nodes for the AutoIngestJobsNode which will each
         * represent a single AutoIngestJob
         *
         * @param autoIngestMonitor the monitor which contains the AutoIngestJobs
         */
        AinStatusChildren(AutoIngestMonitor autoIngestMonitor) {
            monitor = autoIngestMonitor;
        }

        @Override
        protected boolean createKeys(List<AutoIngestJob> list) {
            //get keys from monitor
            //add keys to List
            list.addAll(monitor.getJobsSnapshot().getPendingJobs());
            return true;
        }

        @Override
        protected Node createNodeForKey(AutoIngestJob key) {
            return new StatusNode(key);
        }

    }

    /**
     * A node which represents a single auto ingest job.
     */
    static final class StatusNode extends AbstractNode {

        private final AutoIngestJob autoIngestJob;

        /**
         * Construct a new JobNode to represent an AutoIngestJob and its status.
         *
         * @param job    - the AutoIngestJob being represented by this node
         */
        StatusNode(AutoIngestJob job) {
            super(Children.LEAF);
            autoIngestJob = job;
        }

        /**
         * Get the AutoIngestJob which this node represents.
         *
         * @return autoIngestJob
         */
        AutoIngestJob getAutoIngestJob() {
            return autoIngestJob;
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }
            ss.put(new NodeProperty<>("Host Name", "Host Name", "Host Name", //host name
                    "TODO - ADD HOST NAME"));
            ss.put(new NodeProperty<>("Status","Status","Status","TODO - ADD NODE STATUS"));            
            return s;
        }

        @Override
        public Action[] getActions(boolean context) {
            List<Action> actions = new ArrayList<>();
            if (AutoIngestDashboard.isAdminAutoIngestDashboard()) {
               //Add actions
            }
            return actions.toArray(new Action[actions.size()]);
        }
    }
}
