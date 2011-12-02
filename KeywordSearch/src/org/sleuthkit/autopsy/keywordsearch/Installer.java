/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import org.openide.modules.ModuleInstall;
import org.sleuthkit.autopsy.casemodule.Case;

public class Installer extends ModuleInstall{

    @Override
    public void restored() {
        Case.addPropertyChangeListener(new KeywordSearch.CaseChangeListener());
    }
    
    // TODO: need logic for starting & shutting down the server.
    // startup should be robust enough to deal with a still running server
    // from a previous crash.
   
}
