/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.logicalimager.configuration;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Logical Imager Rule Set
 */
class LogicalImagerRuleSet {

    @SerializedName("set-name")
    @Expose(serialize = true)
    final private String setName;

    @SerializedName("rules")
    @Expose(serialize = true)
    private final List<LogicalImagerRule> rules;

    LogicalImagerRuleSet(String setName, List<LogicalImagerRule> rules) {
        this.setName = setName;
        this.rules = rules;
    }

    String getSetName() {
        return setName;
    }

    List<LogicalImagerRule> getRules() {
        return rules;
    }

    /*
     * Find a rule with the given name. Return null if not found.
     */
    LogicalImagerRule find(String name) {
        for (LogicalImagerRule rule : rules) {
            if (rule.getName().equals(name)) {
                return rule;
            }
        }
        return null;
    }

}
