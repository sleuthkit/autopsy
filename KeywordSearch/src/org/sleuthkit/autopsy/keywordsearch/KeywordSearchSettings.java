/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.StringExtract;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchIngestModule.StringsExtractOptions;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchIngestModule.UpdateFrequency;

//This file contains constants and settings for KeywordSearch
class KeywordSearchSettings {

    public static final String MODULE_NAME = NbBundle.getMessage(KeywordSearchSettings.class, "KeywordSearchSettings.moduleName.text");
    static final String PROPERTIES_OPTIONS = NbBundle.getMessage(KeywordSearchSettings.class, "KeywordSearchSettings.properties_options.text", MODULE_NAME);
    static final String PROPERTIES_NSRL = NbBundle.getMessage(KeywordSearchSettings.class, "KeywordSearchSettings.propertiesNSRL.text", MODULE_NAME);
    static final String PROPERTIES_SCRIPTS = NbBundle.getMessage(KeywordSearchSettings.class, "KeywordSearchSettings.propertiesScripts.text", MODULE_NAME);
    static final String SHOW_SNIPPETS = "showSnippets"; //NON-NLS
    static final boolean DEFAULT_SHOW_SNIPPETS = true;
    static final String OCR_ENABLED = "ocrEnabled"; //NON-NLS
    static final String LIMITED_OCR_ENABLED = "limitedOcrEnabled"; //NON-NLS
    static final boolean OCR_ENABLED_DEFAULT = false; // NON-NLS
    static final boolean LIMITED_OCR_ENABLED_DEFAULT = false;
    private static boolean skipKnown = true;
    private static final Logger logger = Logger.getLogger(KeywordSearchSettings.class.getName());
    private static UpdateFrequency UpdateFreq = UpdateFrequency.DEFAULT;
    private static List<StringExtract.StringExtractUnicodeTable.SCRIPT> stringExtractScripts = new ArrayList<>();
    private static Map<String, String> stringExtractOptions = new HashMap<>();

    /**
     * Gets the update Frequency from KeywordSearch_Options.properties
     *
     * @return KeywordSearchIngestModule's update frequency
     */
    static UpdateFrequency getUpdateFrequency() {
        if (ModuleSettings.getConfigSetting(PROPERTIES_OPTIONS, "UpdateFrequency") != null) { //NON-NLS
            return UpdateFrequency.valueOf(ModuleSettings.getConfigSetting(PROPERTIES_OPTIONS, "UpdateFrequency")); //NON-NLS
        }
        //if it failed, return the default/last known value
        logger.log(Level.WARNING, "Could not read property for UpdateFrequency, returning backup value."); //NON-NLS
        return UpdateFrequency.DEFAULT;
    }

    /**
     * Sets the update frequency and writes to KeywordSearch_Options.properties
     *
     * @param freq Sets KeywordSearchIngestModule to this value.
     */
    static void setUpdateFrequency(UpdateFrequency freq) {
        ModuleSettings.setConfigSetting(PROPERTIES_OPTIONS, "UpdateFrequency", freq.name()); //NON-NLS
        UpdateFreq = freq;
    }

    /**
     * Sets whether or not to skip adding known good files to the search during
     * index.
     *
     * @param skip
     */
    static void setSkipKnown(boolean skip) {
        ModuleSettings.setConfigSetting(PROPERTIES_NSRL, "SkipKnown", Boolean.toString(skip)); //NON-NLS
        skipKnown = skip;
    }

    /**
     * Gets the setting for whether or not this ingest is skipping adding known
     * good files to the index.
     *
     * @return skip setting
     */
    static boolean getSkipKnown() {
        if (ModuleSettings.getConfigSetting(PROPERTIES_NSRL, "SkipKnown") != null) { //NON-NLS
            return Boolean.parseBoolean(ModuleSettings.getConfigSetting(PROPERTIES_NSRL, "SkipKnown")); //NON-NLS
        }
        //if it fails, return the default/last known value
        logger.log(Level.WARNING, "Could not read property for SkipKnown, returning backup value."); //NON-NLS
        return skipKnown;
    }

    /**
     * Sets what scripts to extract during ingest
     *
     * @param scripts List of scripts to extract
     */
    static void setStringExtractScripts(List<StringExtract.StringExtractUnicodeTable.SCRIPT> scripts) {
        stringExtractScripts.clear();
        stringExtractScripts.addAll(scripts);

        //Disabling scripts that weren't selected
        for (String s : ModuleSettings.getConfigSettings(PROPERTIES_SCRIPTS).keySet()) {
            if (!scripts.contains(StringExtract.StringExtractUnicodeTable.SCRIPT.valueOf(s))) {
                ModuleSettings.setConfigSetting(PROPERTIES_SCRIPTS, s, "false"); //NON-NLS
            }
        }
        //Writing and enabling selected scripts
        for (StringExtract.StringExtractUnicodeTable.SCRIPT s : stringExtractScripts) {
            ModuleSettings.setConfigSetting(PROPERTIES_SCRIPTS, s.name(), "true"); //NON-NLS
        }

    }

    /**
     * Set / override string extract option
     *
     * @param key option name to set
     * @param val option value to set
     */
    static void setStringExtractOption(String key, String val) {
        stringExtractOptions.put(key, val);
        ModuleSettings.setConfigSetting(PROPERTIES_OPTIONS, key, val);
    }

    /**
     * Get OCR setting from permanent storage
     *
     * @return Is OCR enabled?
     *
     * @deprecated Please use KeywordSearchJobSettings instead.
     */
    @Deprecated
    static boolean getOcrOption() {
        if (ModuleSettings.settingExists(PROPERTIES_OPTIONS, OCR_ENABLED)) {
            return ModuleSettings.getConfigSetting(PROPERTIES_OPTIONS, OCR_ENABLED).equals("true"); //NON-NLS
        } else {
            return OCR_ENABLED_DEFAULT;
        }
    }

    /**
     * Gets the limited OCR flag to indicate if OCR should be limited to larger
     * images and images which were extracted from documents.
     *
     * @return Flag indicating if limited OCR is enabled. True if OCR should be
     *         limited, false otherwise.
     *
     * @deprecated Please use KeywordSearchJobSettings instead.
     */
    @Deprecated
    static boolean getLimitedOcrOption() {
        if (ModuleSettings.settingExists(PROPERTIES_OPTIONS, LIMITED_OCR_ENABLED)) {
            return ModuleSettings.getConfigSetting(PROPERTIES_OPTIONS, LIMITED_OCR_ENABLED).equals("true"); //NON-NLS
        } else {
            return LIMITED_OCR_ENABLED_DEFAULT;
        }
    }

    static void setShowSnippets(boolean showSnippets) {
        ModuleSettings.setConfigSetting(PROPERTIES_OPTIONS, SHOW_SNIPPETS, (showSnippets ? "true" : "false")); //NON-NLS
    }

    static boolean getShowSnippets() {
        if (ModuleSettings.settingExists(PROPERTIES_OPTIONS, SHOW_SNIPPETS)) {
            return ModuleSettings.getConfigSetting(PROPERTIES_OPTIONS, SHOW_SNIPPETS).equals("true"); //NON-NLS
        } else {
            return DEFAULT_SHOW_SNIPPETS;
        }
    }

    /**
     * gets the currently set scripts to use
     *
     * @return the list of currently used script
     */
    static List<SCRIPT> getStringExtractScripts() {
        if (ModuleSettings.getConfigSettings(PROPERTIES_SCRIPTS) != null && !ModuleSettings.getConfigSettings(PROPERTIES_SCRIPTS).isEmpty()) {
            List<SCRIPT> scripts = new ArrayList<>();
            for (Map.Entry<String, String> kvp : ModuleSettings.getConfigSettings(PROPERTIES_SCRIPTS).entrySet()) {
                if (kvp.getValue().equals("true")) { //NON-NLS
                    scripts.add(SCRIPT.valueOf(kvp.getKey()));
                }
            }
            return scripts;
        }
        //if it failed, try to return the built-in list maintained by the singleton.
        logger.log(Level.WARNING, "Could not read properties for extracting scripts, returning backup values."); //NON-NLS
        return new ArrayList<>(stringExtractScripts);
    }

    /**
     * get string extract option for the key
     *
     * @param key option name
     *
     * @return option string value, or empty string if the option is not set
     */
    static String getStringExtractOption(String key) {
        if (ModuleSettings.getConfigSetting(PROPERTIES_OPTIONS, key) != null) {
            return ModuleSettings.getConfigSetting(PROPERTIES_OPTIONS, key);
        } else {
            logger.log(Level.WARNING, "Could not read property for key {0}, returning backup value.", key); //NON-NLS
            return stringExtractOptions.get(key);
        }
    }

    /**
     * get the map of string extract options.
     *
     * @return Map<String,String> of extract options.
     */
    static Map<String, String> getStringExtractOptions() {
        Map<String, String> settings = ModuleSettings.getConfigSettings(PROPERTIES_OPTIONS);
        if (settings == null) {
            Map<String, String> settingsv2 = new HashMap<>();
            logger.log(Level.WARNING, "Could not read properties for {0}.properties, returning backup values", PROPERTIES_OPTIONS); //NON-NLS
            settingsv2.putAll(stringExtractOptions);
            return settingsv2;
        } else {
            return settings;
        }
    }

    /**
     * Sets the default values of the KeywordSearch properties files if none
     * already exist.
     */
    static void setDefaults() {
        logger.log(Level.INFO, "Detecting default settings."); //NON-NLS
        //setting default NSRL
        if (!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_NSRL, "SkipKnown")) { //NON-NLS
            logger.log(Level.INFO, "No configuration for NSRL found, generating default..."); //NON-NLS
            KeywordSearchSettings.setSkipKnown(true);
        }
        //setting default Update Frequency
        if (!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_OPTIONS, "UpdateFrequency")) { //NON-NLS
            logger.log(Level.INFO, "No configuration for Update Frequency found, generating default..."); //NON-NLS
            KeywordSearchSettings.setUpdateFrequency(UpdateFrequency.DEFAULT);
        }
        //setting default Extract UTF8
        if (!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_OPTIONS, StringsExtractOptions.EXTRACT_UTF8.toString())) {
            logger.log(Level.INFO, "No configuration for UTF8 found, generating default..."); //NON-NLS
            KeywordSearchSettings.setStringExtractOption(StringsExtractOptions.EXTRACT_UTF8.toString(), Boolean.TRUE.toString());
        }
        //setting default Extract UTF16
        if (!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_OPTIONS, StringsExtractOptions.EXTRACT_UTF16.toString())) {
            logger.log(Level.INFO, "No configuration for UTF16 found, generating defaults..."); //NON-NLS
            KeywordSearchSettings.setStringExtractOption(StringsExtractOptions.EXTRACT_UTF16.toString(), Boolean.TRUE.toString());
        }
        //setting default Latin-1 Script
        if (!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_SCRIPTS, SCRIPT.LATIN_1.name())) {
            logger.log(Level.INFO, "No configuration for Scripts found, generating defaults..."); //NON-NLS
            ModuleSettings.setConfigSetting(KeywordSearchSettings.PROPERTIES_SCRIPTS, SCRIPT.LATIN_1.name(), Boolean.toString(true));
        }
    }
}
