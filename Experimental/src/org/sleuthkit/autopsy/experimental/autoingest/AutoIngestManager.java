/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import java.io.Serializable;

/**
 *
 * @author elivis
 */
public class AutoIngestManager {

    /*
     * Events published by an auto ingest manager. The events are published
     * locally to auto ingest manager clients that register as observers and are
     * broadcast to other auto ingest nodes. // RJCTODO: Is this true?
     */
    enum Event {

        INPUT_SCAN_COMPLETED,
        JOB_STARTED,
        JOB_STATUS_UPDATED,
        JOB_COMPLETED,
        CASE_PRIORITIZED,
        CASE_DELETED,
        PAUSED_BY_REQUEST,
        PAUSED_FOR_SYSTEM_ERROR,
        RESUMED
    }

    /**
     * The outcome of a case deletion operation.
     */
    public static final class CaseDeletionResult implements Serializable {

        private static final long serialVersionUID = 1L;

        /*
         * A case may be completely deleted, partially deleted, or not deleted
         * at all.
         */
        enum Status {

            /**
             * The case folder could not be either physically or logically
             * (DELETED state file written) deleted.
             */
            FAILED,
            /**
             * The case folder was deleted, but one or more of the image folders
             * for the case could not be either physically or logically (DELETED
             * state file written) deleted.
             */
            PARTIALLY_COMPLETED,
            /**
             * The case folder and all of its image folders were either
             * physically or logically (DELETED state file written) deleted.
             */
            COMPLETED;
        }    
    }
    
    static final class AutoIngestManagerStartupException extends Exception {

        private static final long serialVersionUID = 1L;

        private AutoIngestManagerStartupException(String message) {
            super(message);
        }

        private AutoIngestManagerStartupException(String message, Throwable cause) {
            super(message, cause);
        }

    }
}
