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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

/**
 * Configuration for which integration test suites to run.
 */
public class TestingConfig {

    private final Map<String, ParameterizedResourceConfig> excludeAllExcept;
    private final Set<String> includeAllExcept;

    @JsonCreator
    public TestingConfig(
            @JsonProperty("excludeAllExcept") List<ParameterizedResourceConfig> excludeAllExcept, 
            @JsonProperty("includeAllExcept") List<String> includeAllExcept) {

        List<ParameterizedResourceConfig> safeExcludeAllExcept = ((excludeAllExcept == null) ? Collections.emptyList() : excludeAllExcept);
        this.excludeAllExcept = safeExcludeAllExcept
                .stream()
                .collect(Collectors.toMap(
                        (res) ->  res.getResource() == null ? "" : res.getResource().toUpperCase(),
                        (res) -> res,
                        (res1, res2) -> {
                            Map<String, Object> mergedArgs = new HashMap<>();
                            mergedArgs.putAll(res1.getParameters());
                            mergedArgs.putAll(res2.getParameters());
                            return new ParameterizedResourceConfig(res1.getResource(), mergedArgs);
                        })
        );

        List<String> safeIncludeAllExcept = ((includeAllExcept == null) ? Collections.emptyList() : includeAllExcept);
        this.includeAllExcept = safeIncludeAllExcept
                .stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    /**
     * @return The test suites to be run. If not specified, getIncludeAllExcept
     * will be used. If that is not specified, all tests will be run.
     */
    public Set<ParameterizedResourceConfig> getExcludeAllExcept() {
        return new HashSet<>(excludeAllExcept.values());
    }

    /**
     * @return The test suites explicitly to exclude. If not specified,
     * getExcludeAllExcept will be used. If that is not specified, all tests
     * will be run.
     */
    public Set<String> getIncludeAllExcept() {
        return includeAllExcept;
    }
    
    public Map<String, Object> getParameters(String itemType) {
        ParameterizedResourceConfig resource = (itemType == null) ? null : excludeAllExcept.get(itemType.toUpperCase());
        return resource == null ? Collections.emptyMap() : new HashMap<String, Object>(resource.getParameters());
    }

    /**
     * Whether or not the current settings contain the current test.
     *
     * @param itemType The fully qualified name of the test suite.
     * @return True if this test should be run.
     */
    public boolean hasIncludedTest(String itemType) {
        if (itemType == null) {
            return false;
        }

        if (!CollectionUtils.isEmpty(includeAllExcept)) {
            if (includeAllExcept.contains(itemType.toUpperCase())) {
                return false;
            }
        }

        if (!MapUtils.isEmpty(excludeAllExcept)) {
            return excludeAllExcept.containsKey(itemType.toUpperCase());
        }

        return true;
    }
}
