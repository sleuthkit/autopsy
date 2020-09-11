/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * An interface to provide the current SleuthkitCase object. This is to allow
 * for SleuthkitCase objects to be created and injected in a testing scenario
 * outside of the context of Case.
 *
 * By default, this uses Case.getCurrentCaseThrows().getSleuthkitCase().
 */
public interface SleuthkitCaseProvider {

    /**
     * Exception thrown in the event that the SleuthkitCase object cannot be
     * provided.
     */
    class SleuthkitCaseProviderException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Main constructor.
         *
         * @param string The message for the exception.
         */
        public SleuthkitCaseProviderException(String string) {
            super(string);
        }

        /**
         * Main constructor.
         *
         * @param string The message for the exception.
         * @param thrwbl The inner exception.
         */
        public SleuthkitCaseProviderException(String string, Throwable thrwbl) {
            super(string, thrwbl);
        }
    }

    /**
     * The default SleuthkitCaseProvider. This uses
     * Case.getCurrentCaseThrows().getSleuthkitCase().
     */
    SleuthkitCaseProvider DEFAULT = () -> {
        try {
            return Case.getCurrentCaseThrows().getSleuthkitCase();
        } catch (NoCurrentCaseException e) {
            throw new SleuthkitCaseProviderException("No currently open case.", e);
        }
    };

    /**
     * @return Returns the current SleuthkitCase object.
     *
     * @throws SleuthkitCaseProviderException Thrown if there is an error
     *                                        providing the case.
     */
    SleuthkitCase get() throws SleuthkitCaseProviderException;
}
