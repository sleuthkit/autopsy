/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponentinterfaces;

import javax.annotation.concurrent.ThreadSafe;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 *
 */
public interface AutopsyServiceProvider {

    /**
     *
     * @param context
     *
     * @throws
     * org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyServiceProvider.AutopsyServiceProviderException
     */
    default void openCaseResources(Context context) throws AutopsyServiceProviderException {
        /*
         * Autopsy service providers may not have case-level resources.
         */
    }

    /**
     *
     * @param context
     *
     * @throws
     * org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyServiceProvider.AutopsyServiceProviderException
     */
    default void closeCaseResources(Context context) throws AutopsyServiceProviderException {
        /*
         * Autopsy service providers may not have case-level resources.
         */
    }

    /**
     * 
     */
    @ThreadSafe
    public final static class Context {
        private final Case currentCase;
        private final ProgressIndicator progressIndicator;
        private volatile boolean cancelRequested;
        
        /**
         * 
         * @param currentCase 
         */
        public Context(Case currentCase, ProgressIndicator progressIndicator) {
            this.currentCase = currentCase;
            this.progressIndicator = progressIndicator;
            this.cancelRequested = false;
        }

        /**
         * 
         * @return 
         */
        public Case getCase() {
            return this.currentCase;
        }
        
        /**
         * 
         * @return 
         */
        public ProgressIndicator getProgressIndicator() {
            return this.progressIndicator;
        }
        
        /**
         * 
         */
        public void requestCancel() {
            this.cancelRequested = true;
        }
        
        /**
         * 
         * @return 
         */
        public boolean cancelRequested() {
            return this.cancelRequested;
        }
        
    }

    /**
     * Exception thrown by autopsy service provider methods.
     */
    public final static class AutopsyServiceProviderException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception to be thrown by an autopsy service provider
         * method.
         *
         * @param message Exception message.
         */
        AutopsyServiceProviderException(String message) {
            super(message);
        }

        /**
         * Constructs an exception to be thrown by an autopsy service provider
         * method.
         *
         * @param message   Exception message.
         * @param throwable Exception cause.
         */
        AutopsyServiceProviderException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }
}
