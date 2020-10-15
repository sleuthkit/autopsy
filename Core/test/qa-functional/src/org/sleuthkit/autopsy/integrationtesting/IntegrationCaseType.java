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
package org.sleuthkit.autopsy.integrationtesting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;

/**
 * The case types to create for this case.
 */
public enum IntegrationCaseType {
    multiUser, singleUser, both;
    
    public static List<CaseType> getCaseTypes(IntegrationCaseType integrationCaseType) {
        if (integrationCaseType == null) {
            return Collections.emptyList();
        }
        
        switch (integrationCaseType) {
            case multiUser: return Arrays.asList(CaseType.MULTI_USER_CASE);
            case singleUser: return Arrays.asList(CaseType.SINGLE_USER_CASE);
            case both: return Arrays.asList(CaseType.MULTI_USER_CASE, CaseType.SINGLE_USER_CASE);
            default: throw new IllegalArgumentException("Unknown integration case type: " + integrationCaseType);
        }
    }
}
