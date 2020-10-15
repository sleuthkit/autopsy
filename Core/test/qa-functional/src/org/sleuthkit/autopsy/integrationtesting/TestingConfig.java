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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Configuration for which integration test suites to run.
 */
public class TestingConfig {
    private final Set<String> excludeAllExcept;
    private final Set<String> includeAllExcept;

    private static Set<String> convert(List<String> orig) {
        if (orig == null) {
            return Collections.emptySet();
        }
        
        return orig.stream()
                .map((item) -> item.toUpperCase())
                .collect(Collectors.toSet());
    }
    
    public TestingConfig(List<String> excludeAllExcept, List<String> includeAllExcept) {
        this.excludeAllExcept = convert(excludeAllExcept);
        this.includeAllExcept = convert(includeAllExcept);
    }

    public Set<String> getExcludeAllExcept() {
        return excludeAllExcept;
    }

    public Set<String> getIncludeAllExcept() {
        return includeAllExcept;
    }   
    
    public boolean hasIncludedTest(String itemType) {
        if (itemType == null) {
            return false;
        }
        
        if (!CollectionUtils.isEmpty(includeAllExcept)) {
            if (includeAllExcept.contains(itemType.toUpperCase())) {
                return false;
            }
        }
        
        if (!CollectionUtils.isEmpty(excludeAllExcept)) {
            return excludeAllExcept.contains(itemType.toUpperCase());
        }
        
        return true;
    }
}
