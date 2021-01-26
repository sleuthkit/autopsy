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
package org.sleuthkit.autopsy.integrationtesting.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;

/**
 * The case types to create for this case.
 */
public enum IntegrationCaseType {
    @JsonProperty("multiUser")
    MULTI_USER, 
    
    @JsonProperty("singleUser")
    SINGLE_USER, 
    
    @JsonProperty("both")
    BOTH;
    
    /**
     * Creates a list of Case.CaseType objects that represent the IntegrationCaseType.
     * @param integrationCaseType The type(s) of cases to create for this case (single user, multi user, both).
     * @return The Case.CaseType objects associated with choice.
     */
    public static List<CaseType> getCaseTypes(IntegrationCaseType integrationCaseType) {
        if (integrationCaseType == null) {
            return Collections.emptyList();
        }
        
        switch (integrationCaseType) {
            case MULTI_USER: return Arrays.asList(CaseType.MULTI_USER_CASE);
            case SINGLE_USER: return Arrays.asList(CaseType.SINGLE_USER_CASE);
            case BOTH: return Arrays.asList(CaseType.MULTI_USER_CASE, CaseType.SINGLE_USER_CASE);
            default: throw new IllegalArgumentException("Unknown integration case type: " + integrationCaseType);
        }
    }
}
