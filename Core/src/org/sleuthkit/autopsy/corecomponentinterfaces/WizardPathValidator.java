/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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

import org.sleuthkit.autopsy.casemodule.Case;

/*
 * Defines an interface used by the Add DataSource wizard to validate path for selected
 * case and/or data source. 
 * 
 * Different Autopsy implementations and modes may have its unique attributes and 
 * may need to be processed differently.
 * 
 * The WizardPathValidator interface defines a uniform mechanism for the Autopsy UI
 * to:
 *  - Validate path for selected case.
 *  - Validate path for selected data source.
 */
public interface WizardPathValidator {

    /**
     * Validates case path.
     *
     * @param path Absolute path to case file.
     * @param caseType Case type
     * @return String Error message if path is invalid, empty string otherwise.
     *
     */
    String validateCasePath(String path, Case.CaseType caseType);

    /**
     * Validates data source path.
     *
     * @param path Absolute path to data source file.
     * @param caseType Case type
     * @return String Error message if path is invalid, empty string otherwise.
     *
     */
    String validateDataSourcePath(String path, Case.CaseType caseType);
}
