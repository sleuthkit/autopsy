/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.configurelogicalimager;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.Map;

/**
 * Logical Imager Configuration file JSON
 */
public class LogicalImagerConfig {

    @SerializedName("finalize-image-writer")
    @Expose(serialize = true) 
    private boolean finalizeImageWriter;

    @SerializedName("rule-set")
    @Expose(serialize = true) 
    private Map<String, LogicalImagerRule> ruleSet;
    
    public LogicalImagerConfig() {
        this.finalizeImageWriter = false;
        this.ruleSet = new HashMap<>();
    }
    
    public LogicalImagerConfig(
            boolean finalizeImageWriter,
            Map<String, LogicalImagerRule> ruleSet
    ) {
        this.finalizeImageWriter = finalizeImageWriter;
        this.ruleSet = ruleSet;
    }

    public boolean isFinalizeImageWriter() {
        return finalizeImageWriter;
    }

    public void setFinalizeImageWriter(boolean finalizeImageWriter) {
        this.finalizeImageWriter = finalizeImageWriter;
    }

    public Map<String, LogicalImagerRule> getRuleSet() {
        return ruleSet;
    }

    public void setRuleSet(Map<String, LogicalImagerRule> ruleSet) {
        this.ruleSet = ruleSet;
    }    
}
