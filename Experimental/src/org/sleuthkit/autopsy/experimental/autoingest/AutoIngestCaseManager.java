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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.experimental.configuration.AutoIngestUserPreferences;

/**
 * Handles locating and opening cases created by auto ingest.
 */
final class AutoIngestCaseManager {

    private static final Logger LOGGER = Logger.getLogger(AutoIngestCaseManager.class.getName());
    private static AutoIngestCaseManager instance;
    
    private CoordinationService coordinationService;

    /**
     * Gets the auto ingest case manager.
     *
     * @return The auto ingest case manager singleton.
     */
    synchronized static AutoIngestCaseManager getInstance() throws AutoIngestCaseManager.AutoIngestCaseManagerException {
        if (null == instance) {
            instance = new AutoIngestCaseManager();
        }
        return instance;
    }

    /**
     * Constructs an object that handles locating and opening cases created by
     * auto ingest.
     */
    private AutoIngestCaseManager() throws AutoIngestCaseManagerException {
        try {
            init();
        } catch (AutoIngestCaseManager.AutoIngestCaseManagerException ex) {
            Logger.getLogger(AutoIngestCaseManager.class.getName()).log(Level.SEVERE, null, ex);
            throw ex;
        }
    }
    
    /**
     * Initialize the coordination service.
     * 
     * @throws org.sleuthkit.autopsy.experimental.autoingest.AutoIngestCaseManager.AutoIngestCaseManagerException 
     */
    private void init() throws AutoIngestCaseManager.AutoIngestCaseManagerException {
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationServiceException ex) {
            throw new AutoIngestCaseManager.AutoIngestCaseManagerException(ex.getMessage(), ex);
        }
    }

    /**
     * Gets a list of the cases in the top level case folder used by auto
     * ingest.
     */
    @Messages({
        "AutoIngestCaseManager.CasePathsError=An error occurred while trying to retrieve the list of case paths."
    })
    List<AutoIngestCase> getCases() {
        List<AutoIngestCase> cases = new ArrayList<>();
        List<Path> casePathList;
        try {
            casePathList = getCasePaths();
            for (Path casePath : casePathList) {
                cases.add(new AutoIngestCase(casePath));
            }
        } catch (AutoIngestCaseManagerException ex) {
            String errorMessage = NbBundle.getMessage(AutoIngestCaseManager.class, "AutoIngestCaseManager.CasePathsError");
            LOGGER.log(Level.SEVERE, errorMessage, ex);
            MessageNotifyUtil.Message.error(errorMessage);
        }
        return cases;
    }
    
    /**
     * Retrieve all of the case nodes and filter for only those that represent
     * case paths.
     * 
     * @return List of case paths.
     */
    private List<Path> getCasePaths() throws AutoIngestCaseManagerException {
        try {
            List<String> nodeList = coordinationService.getNodeList(CoordinationService.CategoryNode.CASES);
            List<Path> casePathList = new ArrayList<Path>(0);
            for (String node : nodeList) {
                if(node.indexOf('\\') >= 0 || node.indexOf('/') >= 0) {
                    /*
                     * This is not a case name lock (name specifies a path).
                     */
                    String nodeUpperCase = node.toUpperCase();
                    if(!nodeUpperCase.endsWith("_RESOURCES") && !nodeUpperCase.endsWith("AUTO_INGEST_LOG.TXT")) {
                        /*
                         * This is not a case resource lock. Collect the path.
                         */
                        casePathList.add(Paths.get(node));
                    }
                }
            }
            return casePathList;
            
        } catch (CoordinationServiceException ex) {
            throw new AutoIngestCaseManager.AutoIngestCaseManagerException("Failed to get node list from coordination service.", ex);
        }
    }

    /**
     * Opens an auto ingest case case.
     *
     * @param caseMetadataFilePath Path to the case metadata file.
     *
     * @throws CaseActionException
     */
    synchronized void openCase(Path caseMetadataFilePath) throws CaseActionException {
        /*
         * Open the case.
         */
        Case.openAsCurrentCase(caseMetadataFilePath.toString());
    }
    
    /**
     * Exception type thrown when there is an error completing an auto ingest
     * case manager operation.
     */
    static final class AutoIngestCaseManagerException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest case manager operation.
         *
         * @param message The exception message.
         */
        private AutoIngestCaseManagerException(String message) {
            super(message);
        }

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest case manager operation.
         *
         * @param message The exception message.
         * @param cause   A Throwable cause for the error.
         */
        private AutoIngestCaseManagerException(String message, Throwable cause) {
            super(message, cause);
        }

    }
}
