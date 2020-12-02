/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.yara.rules;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 *
 * Yara Rule Set Manager. Manages the creation, deletion of yara rule sets.
 */
public class RuleSetManager {

    private final static String BASE_FOLDER = "yara";
    private final static String RULE_SET_FOLDER = "ruleSets";

    /**
     * Create a new Yara rule set with the given set name.
     *
     * @param name Name of new rule set
     *
     * @return Newly created RuleSet
     *
     * @throws RuleSetException RuleSet with given name already exists.
     */
    public RuleSet createRuleSet(String name) throws RuleSetException {

        if(name == null || name.isEmpty()) {
            throw new RuleSetException("YARA rule set name cannot be null or empty string" );
        }
        
        if (isRuleSetExists(name)) {
            throw new RuleSetException(String.format("Yara rule set with name %s already exits.", name));
        }

        Path basePath = getRuleSetPath();
        Path setPath = Paths.get(basePath.toString(), name);

        setPath.toFile().mkdir();

        return new RuleSet(name, setPath);
    }

    /**
     * Returns a list of all of the existing yara rule sets.
     *
     * @return
     */
    public List<RuleSet> getRuleSetList() {
        List<RuleSet> ruleSets = new ArrayList<>();
        Path basePath = getRuleSetPath();

        String[] ruleSetNames = basePath.toFile().list();

        for (String setName : ruleSetNames) {
            ruleSets.add(new RuleSet(setName, Paths.get(basePath.toString(), setName)));
        }

        return ruleSets;
    }

    /**
     * Check if a yara rule set of the given name exists.
     *
     * @param name Yara rule set name
     *
     * @return True if the rule set exist.
     */
    public boolean isRuleSetExists(String name) {
        Path basePath = getRuleSetPath();
        Path setPath = Paths.get(basePath.toString(), name);

        return setPath.toFile().exists();
    }

    /**
     * Returns the Path to get RuleSet directory. If it does not exist it is
     * created.
     *
     * @return Yara rule set directory path.
     */
    private Path getRuleSetPath() {
        Path basePath = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath(), BASE_FOLDER, RULE_SET_FOLDER);
        File baseFile = basePath.toFile();

        if (!baseFile.exists()) {
            baseFile.mkdirs();
        }

        return basePath;
    }

}
