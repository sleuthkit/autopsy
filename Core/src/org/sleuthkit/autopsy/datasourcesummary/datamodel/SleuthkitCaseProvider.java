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
 * An interface to provide the current SleuthkitCase object. By default, this
 * uses Case.getCurrentCaseThrows().getSleuthkkitCase().
 */
public interface SleuthkitCaseProvider {

    /**
     * The default SleuthkitCaseProvider. This uses
     * Case.getCurrentCaseThrows().getSleuthkitCase().
     */
    SleuthkitCaseProvider DEFAULT = () -> Case.getCurrentCaseThrows().getSleuthkitCase();

    /**
     * @return Returns the current SleuthkitCase object.
     *
     * @throws NoCurrentCaseException Thrown if no case is open.
     */
    SleuthkitCase get() throws NoCurrentCaseException;
}
