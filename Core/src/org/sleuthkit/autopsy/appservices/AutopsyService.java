/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.appservices;

import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * An interface for services that report status and may manage case and
 * application resources such a text index, a database, etc. A service provider
 * may have resources of both types, of only one type, or no resources at all.
 */
public interface AutopsyService {

    /**
     * Gets the service name.
     *
     * @return The service name.
     */
    String getServiceName();

//    Status getStatus() {
//        
//    }
//    default void openAppResources(ApplicationContext context) throws AutopsyServiceException {
//        /*
//         * Autopsy services may not have application-level resources.
//         */
//    }
    /**
     * Creates, opens or upgrades any case-level resources managed by the
     * service.
     *
     * @param context The case context which includes things such as the case, a
     *                progress indicator for the operation, a cancellation
     *                request flag, etc.
     *
     * @throws
     * org.sleuthkit.autopsy.framework.AutopsyService.AutopsyServiceException
     */
    default void openCaseResources(CaseContext context) throws AutopsyServiceException {
        /*
         * Autopsy services may not have case-level resources.
         */
    }

    /**
     * Closes any case-level resources managed by the service.
     *
     * @param context The case context which includes things such as the case, a
     *                progress indicator for the operation, a cancellation
     *                request flag, etc.
     *
     * @throws
     * org.sleuthkit.autopsy.framework.AutopsyService.AutopsyServiceException
     */
    default void closeCaseResources(CaseContext context) throws AutopsyServiceException {
        /*
         * Autopsy services may not have case-level resources.
         */
    }

//    default void closeAppResources(ApplicationContext context) throws AutopsyServiceException {
//        /*
//         * Autopsy services may not have case-level resources.
//         */
//    }
    /**
     * The context for the creation/opening/upgrading of case-level resources by
     * a service.
     */
    public static class CaseContext {

        private final Case theCase;
        private final ProgressIndicator progressIndicator;
        private volatile boolean cancelRequested;
        private final boolean isNewCase;

        /**
         * Constructs the context for the creation/opening/upgrading of
         * case-level resources by a service.
         *
         * @param theCase           The case.
         * @param progressIndicator A progress indicator for the opening of the
         *                          case-level resources
         */
        public CaseContext(Case theCase, ProgressIndicator progressIndicator) {
            this(theCase, progressIndicator, false);
        }
        
        /**
         * Constructs the context for the creation/opening/upgrading of
         * case-level resources by a service.
         *
         * @param theCase           The case.
         * @param progressIndicator A progress indicator for the opening of the
         *                          case-level resources.
         * @param  isNewCase        True if theCase is a new case.
         */
        public CaseContext(Case theCase, ProgressIndicator progressIndicator, boolean isNewCase) {
            this.theCase = theCase;
            this.progressIndicator = progressIndicator;
            this.cancelRequested = false;
            this.isNewCase = isNewCase;
        }

        /**
         * Gets the case for the creation/opening/upgrading of case-level
         * resources by a service.
         *
         * @return The case.
         */
        public Case getCase() {
            return this.theCase;
        }

        /**
         * Gets the progress indicator for the creation/opening/upgrading of
         * case-level resources by a service. IMPORTANT: The service should only
         * call progress() on the progress indicator. Calling start() and
         * finish() are the responsibility of the case providing the context.
         *
         * @return The progress indicator.
         */
        public ProgressIndicator getProgressIndicator() {
            return this.progressIndicator;
        }

        /**
         * Requests cancellation of the creation/opening/upgrading of case-level
         * resources by a service. The request is not guaranteed to be honored.
         */
        public void requestCancel() {
            this.cancelRequested = true;
        }

        /**
         * Indicates whether or not cancellation of the
         * creation/opening/upgrading of case-level resources by a service has
         * been requested.
         *
         * @return True or false.
         */
        public boolean cancelRequested() {
            return this.cancelRequested;
        }
        
        /**
         * Indicates whether or the case is a new case in the process of being
         * created.
         * 
         * @return True if it is a new case.
         */
        public boolean isNewCase() {
            return this.isNewCase;
        }
    }

    /**
     * Exception thrown by autopsy service methods.
     */
    public static class AutopsyServiceException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception to be thrown by an autopsy service provider
         * method.
         *
         * @param message Exception message.
         */
        public AutopsyServiceException(String message) {
            super(message);
        }

        /**
         * Constructs an exception to be thrown by an autopsy service provider
         * method.
         *
         * @param message Exception message.
         * @param cause   Exception cause.
         */
        public AutopsyServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
