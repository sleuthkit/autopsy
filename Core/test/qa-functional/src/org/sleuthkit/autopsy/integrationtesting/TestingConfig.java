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

import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Configuration for which integration test suites to run.
 */
public class TestingConfig {

    private final List<String> excludeAllExcept;
    private final List<String> includeAllExcept;


    public TestingConfig(List<String> excludeAllExcept, List<String> includeAllExcept) {
        this.excludeAllExcept = excludeAllExcept;
        this.includeAllExcept = includeAllExcept;
    }

    /**
     * @return The test suites to be run. If not specified, getIncludeAllExcept
     *         will be used. If that is not specified, all tests will be run.
     */
    public List<String> getExcludeAllExcept() {
        return excludeAllExcept;
    }

    /**
     * @return The test suites explicitly to exclude. If not specified,
     *         getExcludeAllExcept will be used. If that is not specified, all
     *         tests will be run.
     */
    public List<String> getIncludeAllExcept() {
        return includeAllExcept;
    }

    /**
     * Whether or not the current settings contain the current test.
     * @param itemType The fully qualified name of the test suite.
     * @return True if this test should be run.
     */
    public boolean hasIncludedTest(String itemType) {
        if (itemType == null) {
            return false;
        }

        if (!CollectionUtils.isEmpty(includeAllExcept)) {
            if (includeAllExcept.stream().anyMatch((test) -> StringUtils.equalsIgnoreCase(test, itemType))) {
                return false;
            }
        }

        if (!CollectionUtils.isEmpty(excludeAllExcept)) {
            return excludeAllExcept.stream().anyMatch((test) -> StringUtils.equalsIgnoreCase(test, itemType));
        }

        return true;
    }
}
