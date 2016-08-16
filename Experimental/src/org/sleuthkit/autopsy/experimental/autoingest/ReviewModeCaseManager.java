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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.AddImageAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseNewAction;
import org.sleuthkit.autopsy.experimental.configuration.AutoIngestUserPreferences;
import org.sleuthkit.autopsy.experimental.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.experimental.coordinationservice.CoordinationService.CoordinationServiceException;

/**
 * Handles opening, locking, and unlocking cases in review mode. Instances of
 * this class are tightly coupled to the Autopsy "current case" concept and the
 * Autopsy UI, and cases must be opened by code executing in the event
 * dispatch thread (EDT). Because of the tight coupling to the UI, exception
 * messages are deliberately user-friendly.
 */
final class ReviewModeCaseManager implements PropertyChangeListener {

    /*
     * Provides uniform exceptions with user-friendly error messages.
     */
    final class ReviewModeCaseManagerException extends Exception {

        private static final long serialVersionUID = 1L;

        private ReviewModeCaseManagerException(String message) {
            super(message);
        }

        private ReviewModeCaseManagerException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    private static final Logger logger = Logger.getLogger(ReviewModeCaseManager.class.getName());
    private static ReviewModeCaseManager instance;
    private CoordinationService.Lock currentCaseLock;

    /**
     * Gets the review mode case manager.
     *
     * @return The review mode case manager singleton.
     */
    synchronized static ReviewModeCaseManager getInstance() {
        if (instance == null) {
            /*
             * Two stage construction is used here to avoid allowing "this"
             * reference to escape from the constructor via registering as an
             * PropertyChangeListener. This is to ensure that a partially
             * constructed manager is not published to other threads.
             */
            instance = new ReviewModeCaseManager();
            Case.addPropertyChangeListener(instance);
        }
        return instance;
    }

    /**
     * Constructs a review mode case manager to handles opening, locking, and
     * unlocking cases in review mode. Instances of this class are tightly
     * coupled to the Autopsy "current case" concept and the Autopsy UI,
     * and cases must be opened by code executing in the event dispatch thread
     * (EDT). Because of the tight coupling to the UI, exception messages are
     * deliberately user-friendly.
     *
     */
    private ReviewModeCaseManager() {
        /*
         * Disable the new case action because review mode is only for looking
         * at cases created by automated ingest.
         */
        CallableSystemAction.get(CaseNewAction.class).setEnabled(false);

        /*
         * Permanently delete the "Open Recent Cases" item in the "File" menu.
         * This is quite drastic, as it also affects Autopsy standalone mode on
         * this machine, but review mode is only for looking at cases created by
         * automated ingest.
         */
        FileObject root = FileUtil.getConfigRoot();
        FileObject openRecentCasesMenu = root.getFileObject("Menu/Case/OpenRecentCase");
        if (openRecentCasesMenu != null) {
            try {
                openRecentCasesMenu.delete();
            } catch (IOException ex) {
                ReviewModeCaseManager.logger.log(Level.WARNING, "Unable to remove Open Recent Cases file menu item", ex);
            }
        }
    }

    /*
     * Gets a list of the cases in the top level case folder used by automated
     * ingest.
     */
    List<AutoIngestCase> getCases() {
        List<AutoIngestCase> cases = new ArrayList<>();
        List<Path> caseFolders = PathUtils.findCaseFolders(Paths.get(AutoIngestUserPreferences.getAutoModeResultsFolder()));
        for (Path caseFolderPath : caseFolders) {
            cases.add(new AutoIngestCase(caseFolderPath));
        }
        return cases;
    }

    /**
     * Attempts to open a case as the current case. Assumes it is called by code
     * executing in the event dispatch thread (EDT).
     *
     * @param caseMetadataFilePath Path to the case metadata file.
     *
     * @throws ReviewModeCaseManagerException
     */
    /*
     * TODO (RC): With a little work, the lock acquisition/release could be done
     * by a thread in a single thread executor, removing the "do it in the EDT"
     * requirement
     */
    synchronized void openCaseInEDT(Path caseMetadataFilePath) throws ReviewModeCaseManagerException {
        Path caseFolderPath = caseMetadataFilePath.getParent();
        try {
            /*
             * Acquire a lock on the case folder. If the lock cannot be
             * acquired, the case cannot be opened.
             */
            currentCaseLock = CoordinationService.getInstance(CoordinationServiceNamespace.getRoot()).tryGetSharedLock(CoordinationService.CategoryNode.CASES, caseFolderPath.toString());
            if (null == currentCaseLock) {
                throw new ReviewModeCaseManagerException("Could not get shared access to multi-user case folder");
            }

            /*
             * Open the case.
             */
            Case.open(caseMetadataFilePath.toString());

            /**
             * Disable the add data source action in review mode. This has to be
             * done here because Case.open() calls Case.doCaseChange() and the
             * latter method enables the action. Since Case.doCaseChange()
             * enables the menus on EDT by calling SwingUtilities.invokeLater(),
             * we have to do the same thing here to maintain the order of
             * execution.
             */
            SwingUtilities.invokeLater(() -> {
                CallableSystemAction.get(AddImageAction.class).setEnabled(false);
            });

        } catch (CoordinationServiceException | ReviewModeCaseManagerException | CaseActionException ex) {
            /*
             * Release the coordination service lock on the case folder.
             */
            try {
                if (currentCaseLock != null) {
                    currentCaseLock.release();
                    currentCaseLock = null;
                }
            } catch (CoordinationService.CoordinationServiceException exx) {
                logger.log(Level.SEVERE, String.format("Error deleting legacy LOCKED state file for case at %s", caseFolderPath), exx);
            }

            if (ex instanceof CoordinationServiceException) {
                throw new ReviewModeCaseManagerException("Could not get access to the case folder from the coordination service, contact administrator", ex);
            } else if (ex instanceof IOException) {
                throw new ReviewModeCaseManagerException("Could not write to the case folder, contact adminstrator", ex);
            } else if (ex instanceof CaseActionException) {
                /*
                 * CaseActionExceptions have user friendly error messages.
                 */
                throw new ReviewModeCaseManagerException(String.format("Could not open the case (%s), contract administrator", ex.getMessage()), ex);
            } else if (ex instanceof ReviewModeCaseManagerException) {
                throw (ReviewModeCaseManagerException) ex;
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())
                && null != evt.getOldValue()
                && null == evt.getNewValue()) {
            /*
             * When a case is closed, release the coordination service lock on
             * the case folder. This must be done in the EDT because it was
             * acquired in the EDT via openCase().
             */
            if (null != currentCaseLock) {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            currentCaseLock.release();
                            currentCaseLock = null;
                        } catch (CoordinationService.CoordinationServiceException ex) {
                            logger.log(Level.SEVERE, String.format("Failed to release the coordination service lock with path %s", currentCaseLock.getNodePath()), ex);
                            currentCaseLock = null;
                        }
                    });
                } catch (InterruptedException | InvocationTargetException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to release the coordination service lock with path %s", currentCaseLock.getNodePath()), ex);
                    currentCaseLock = null;
                }
            }
        }
    }

}
