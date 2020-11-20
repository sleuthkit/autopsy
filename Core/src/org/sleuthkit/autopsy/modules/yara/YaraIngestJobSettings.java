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
package org.sleuthkit.autopsy.modules.yara;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.modules.yara.rules.RuleSet;

/**
 * IngestJobSettings for the YARA ingest module.
 */
public final class YaraIngestJobSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;

    private List<String> selectedRuleSetNames;
    private boolean onlyExecutableFiles;

    // Default constructor.
    YaraIngestJobSettings() {
        onlyExecutableFiles = true;
        selectedRuleSetNames = new ArrayList<>();
    }

    /**
     * Constructor.
     *
     * @param selected            List of selected rules.
     * @param onlyExecutableFiles Process only executable files.
     */
    public YaraIngestJobSettings(List<RuleSet> selected, boolean onlyExecutableFiles) {
        this.selectedRuleSetNames = new ArrayList<>();

        for (RuleSet set : selected) {
            selectedRuleSetNames.add(set.getName());
        }

        this.onlyExecutableFiles = onlyExecutableFiles;
    }

    /**
     * Return the list of rule name sets that were selected in the ingest
     * settings panel.
     *
     * @return List of selected RuleSet names.
     */
    public List<String> getSelectedRuleSetNames() {
        return Collections.unmodifiableList(selectedRuleSetNames);
    }

    /**
     * Set the list of selected rule names.
     *
     * @param selected List of selected rule Sets.
     */
    void setSelectedRuleSetNames(List<RuleSet> selected) {
        this.selectedRuleSetNames = new ArrayList<>();
        for (RuleSet set : selected) {
            selectedRuleSetNames.add(set.getName());
        }
    }
    
    /**
     * Returns if there are selected Rule Sets.
     * 
     * @return True if there is at least one selected rule.
     */
    boolean hasSelectedRuleSets() {
        return selectedRuleSetNames != null && !selectedRuleSetNames.isEmpty();
    }

    /**
     * Process only executable Files.
     *
     * @return If true the ingest module should process only executable files,
     *         if false process all files.
     */
    public boolean onlyExecutableFiles() {
        return onlyExecutableFiles;
    }

    /**
     * Set whether to process only executable files or all files.
     *
     * @param onlyExecutableFiles True if the ingest module should only process
     *                            executable files.
     */
    void setOnlyExecuteableFile(boolean onlyExecutableFiles) {
        this.onlyExecutableFiles = onlyExecutableFiles;
    }

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

}
