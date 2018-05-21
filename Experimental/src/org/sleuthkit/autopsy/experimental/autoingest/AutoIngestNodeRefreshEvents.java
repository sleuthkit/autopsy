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
     * An empty interface for all refresh events to implement.
     */
    interface AutoIngestRefreshEvent {

    }

    /**
     * An event to denote that the children of the AutoIngestJobsNode should be
     * refreshed but no specific nodes need their properties refreshed.
     */
    static final class RefreshChildrenEvent implements AutoIngestRefreshEvent {

        /**
         * Constructs a RefreshChildrenEvent.
         */
        RefreshChildrenEvent() {

        }
    }

    /**
     * An event to denote that all nodes which represent jobs which are part of
     * the specified case should be refreshed.
     */
    static final class RefreshCaseEvent implements AutoIngestRefreshEvent {

        private final String caseName;

        /**
         * Constructs a RefreshCaseEvent.
         */
        RefreshCaseEvent(String name) {
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
    static final class RefreshJobEvent implements AutoIngestRefreshEvent {

        private final AutoIngestJob autoIngestJob;

        /**
         * Constructs a RefreshJobEvent.
         */
        RefreshJobEvent(AutoIngestJob job) {
            autoIngestJob = job;
        }

        /**
         * Get the AutoIngestJob which should have it's node refresheds.
         *
         * @return autoIngestJob - the AutoIngestJob which should have it's node
         *         refreshed
         */
        AutoIngestJob getJobToRefresh() {
            return autoIngestJob;
        }
    }

}
