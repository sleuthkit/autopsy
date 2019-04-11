/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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

import org.sleuthkit.autopsy.coordinationservice.CoordinationService;

/**
 * A utility class supplying helper methods for case deletion.
 */
final class DeleteCaseUtils {

    private static final String NO_NODE_ERROR_MSG_FRAGMENT = "KeeperErrorCode = NoNode";
        
    /**
     * Examines a coordination service exception to try to determine if it is a
     * no node exception.
     *
     * @param ex A coordination service exception.
     *
     * @return True or false.
     */
    static boolean isNoNodeException(CoordinationService.CoordinationServiceException ex) {
        boolean isNodeNodeEx = false;
        Throwable cause = ex.getCause();
        if (cause != null) {
            String causeMessage = cause.getMessage();
            isNodeNodeEx = causeMessage.contains(NO_NODE_ERROR_MSG_FRAGMENT);
        }
        return isNodeNodeEx;
    }    
    
    /**
     * A private constructor to prevent instantiation.
     */
    private DeleteCaseUtils() {

    }

}
