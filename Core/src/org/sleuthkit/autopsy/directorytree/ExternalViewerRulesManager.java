/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * Manager for user's external viewer rules, used by the options panel and the
 * ExternalViewerAction. Reads from and writes to a preferences file.
 */
class ExternalViewerRulesManager {

    private static final String RULES_SETTINGS_NAME = "ExternalViewerRules"; //NON-NLS
    private static final String RULES_SETTINGS_KEY = "Rules"; //NON-NLS
    private static ExternalViewerRulesManager instance;
    private List<ExternalViewerRule> userRules = new ArrayList<>();

    /**
     * Gets the singleton manager of the external viewer rules defined by users.
     *
     * @return The external viewer rules manager singleton.
     */
    synchronized static ExternalViewerRulesManager getInstance() {
        if (instance == null) {
            instance = new ExternalViewerRulesManager();
            instance.loadUserDefinedRules();
        }
        return instance;
    }

    private ExternalViewerRulesManager() {
    }

    /**
     * Loads user defined rules from the configuration settings file.
     */
    private void loadUserDefinedRules() {
        String setting = ModuleSettings.getConfigSetting(RULES_SETTINGS_NAME, RULES_SETTINGS_KEY);
        if (setting != null && !setting.isEmpty()) {
            List<String> ruleTuples = Arrays.asList(setting.split("\\|"));
            for (String ruleTuple : ruleTuples) {
                String[] ruleParts = ruleTuple.split(">");
                userRules.add(new ExternalViewerRule(ruleParts[0], ruleParts[1],
                        ExternalViewerRule.RuleType.valueOf(ruleParts[2])));
            }
        }
    }

    /**
     * Writes a list of ExternalViewerRule objects to a configuration settings
     * file.
     *
     * @param rules to be written and saved.
     */
    synchronized void setUserRules(List<ExternalViewerRule> rules) {
        StringBuilder setting = new StringBuilder();
        for (ExternalViewerRule rule : rules) {
            if (setting.length() != 0) {
                setting.append("|");
            }
            setting.append(rule.getName()).append(">");
            setting.append(rule.getExePath()).append(">");
            setting.append(rule.getRuleType().name());
        }
        ModuleSettings.setConfigSetting(RULES_SETTINGS_NAME, RULES_SETTINGS_KEY, setting.toString());
        userRules = new ArrayList<>(rules);
    }

    /**
     * @return a list of the user's rules as ExternalViewerRule objects
     */
    synchronized List<ExternalViewerRule> getUserRules() {
        // ExternalViewerRule objects are immutable
        return new ArrayList<>(userRules);
    }

    /**
     * Finds the executable path associated with a rule name (MIME type or
     * extension). Returns an empty string if the rule name is not found.
     * @param name of MIME type or extension.
     * @return the associated executable absolute path.
     */
    synchronized String getExePathForName(String name) {
        for (ExternalViewerRule rule : userRules) {
            if (rule.getName().equals(name)) {
                return rule.getExePath();
            }
        }
        return "";
    }
}
