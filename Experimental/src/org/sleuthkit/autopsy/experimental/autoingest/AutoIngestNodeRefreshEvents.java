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

/**
 * Class which contains events to identify what should be refreshed in the
 * AutoIngestJobsNode
 */
class AutoIngestNodeRefreshEvents {

    /**
     * The base class for all refresh events.
     */
    static class AutoIngestRefreshEvent {

        private final AutoIngestMonitor monitor;

        AutoIngestRefreshEvent(AutoIngestMonitor monitor) {
            this.monitor = monitor;
        }

        /**
         * Get the monitor which will provide access to the state of
         * the jobs.
         *
         * @return
         */
        AutoIngestMonitor getMonitor() {
            return this.monitor;
        }
    }

    /**
     * An event to denote that the children of the AutoIngestJobsNode should be
     * refreshed but no specific nodes need their properties refreshed.
     */
    static final class RefreshChildrenEvent extends AutoIngestRefreshEvent {

        /**
         * Constructs a RefreshChildrenEvent.
         */
        RefreshChildrenEvent(AutoIngestMonitor monitor) {
            super(monitor);
        }
    }

    /**
     * An event to denote that all nodes which represent jobs which are part of
     * the specified case should be refreshed.
     */
    static final class RefreshCaseEvent extends AutoIngestRefreshEvent {

        private final String caseName;

        /**
         * Constructs a RefreshCaseEvent
         *
         * @param monitor The monitor that will provide access to the current state of the jobs lists.
         * @param name The name of the case whose nodes should be refreshed.
         */
        RefreshCaseEvent(AutoIngestMonitor monitor, String name) {
            super(monitor);
            caseName = name;
        }

        /**
         * Get the case name which should have all it's jobs have their node
         * refreshed.
         *
         * @return caseName - the case which contains the jobs which should have
         *         their nodes refreshed
         */
        String getCaseToRefresh() {
            return caseName;
        }

    }

    /**
     * An event to denote that a node for a specific job should be refreshed.
     */
    static final class RefreshJobEvent extends AutoIngestRefreshEvent {

        private final AutoIngestJob autoIngestJob;

        /**
         * Constructs a RefreshJobEvent.
         *
         * @param monitor The monitor which will provide access to the current state of the jobs lists.
         * @param job  The job which should be refreshed.
         */
        RefreshJobEvent(AutoIngestMonitor monitor, AutoIngestJob job) {
            super(monitor);
            autoIngestJob = job;
        }

        /**
         * Get the AutoIngestJob which should have it's node refreshed.
         *
         * @return autoIngestJob - the AutoIngestJob which should have it's node
         *         refreshed
         */
        AutoIngestJob getJobToRefresh() {
            return autoIngestJob;
        }
    }
}
