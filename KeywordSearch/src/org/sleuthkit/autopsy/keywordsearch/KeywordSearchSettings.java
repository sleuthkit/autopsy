/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.StringExtract;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchIngestModule.UpdateFrequency;


//This file contains constants and settings for KeywordSearch
public class KeywordSearchSettings {
    public static final String MODULE_NAME = "KeywordSearch";
    static final String PROPERTIES_OPTIONS = MODULE_NAME+"_Options";
    static final String PROPERTIES_NSRL = MODULE_NAME+"_NSRL";
    static final String PROPERTIES_SCRIPTS = MODULE_NAME+"_Scripts";
    private static boolean skipKnown = true;
    private static final Logger logger = Logger.getLogger(KeywordSearchSettings.class.getName());
    private static UpdateFrequency UpdateFreq = UpdateFrequency.AVG;
    static List<StringExtract.StringExtractUnicodeTable.SCRIPT> stringExtractScripts = new ArrayList<StringExtract.StringExtractUnicodeTable.SCRIPT>();
    static Map<String,String> stringExtractOptions = new HashMap<String,String>();
    

           
    /**
     * Gets the update Frequency from  KeywordSearch_Options.properties
     * @return KeywordSearchIngestModule's update frequency
     */ 
    static UpdateFrequency getUpdateFrequency(){
        if(ModuleSettings.getConfigSetting(PROPERTIES_OPTIONS, "UpdateFrequency") != null){
         return UpdateFrequency.valueOf(ModuleSettings.getConfigSetting(PROPERTIES_OPTIONS, "UpdateFrequency"));
        }
        //if it failed, return the default/last known value
        logger.log(Level.WARNING, "Could not read property for UpdateFrequency, returning backup value.");
        return UpdateFreq;
    }
    
    
    /**
     * Sets the update frequency and writes to KeywordSearch_Options.properties
     * @param freq Sets KeywordSearchIngestModule to this value.
     */
    static void setUpdateFrequency(UpdateFrequency freq){
        ModuleSettings.setConfigSetting(PROPERTIES_OPTIONS, "UpdateFrequency", freq.name());
        UpdateFreq = freq;
    }
    
    /**
     * Sets whether or not to skip adding known good files to the search during index.
     * @param skip 
     */
    static void setSkipKnown(boolean skip) {
        ModuleSettings.setConfigSetting(PROPERTIES_NSRL, "SkipKnown", Boolean.toString(skip));
        skipKnown = skip;
    }
    
   /**
     * Gets the setting for whether or not this ingest is skipping adding known good files to the index.
     * @return skip setting
     */
    static boolean getSkipKnown() {
       if(ModuleSettings.getConfigSetting(PROPERTIES_NSRL, "SkipKnown") != null){
            return Boolean.parseBoolean(ModuleSettings.getConfigSetting(PROPERTIES_NSRL, "SkipKnown"));
        }
       //if it fails, return the default/last known value
       logger.log(Level.WARNING, "Could not read property for SkipKnown, returning backup value.");
       return skipKnown;
    }
   

    
    /**
     * Sets what scripts to extract during ingest
     * @param scripts List of scripts to extract
     */
    static void setStringExtractScripts(List<StringExtract.StringExtractUnicodeTable.SCRIPT> scripts) {
        stringExtractScripts.clear();
        stringExtractScripts.addAll(scripts);
        
        for(String s : ModuleSettings.getConfigSettings(PROPERTIES_SCRIPTS).keySet()){
            if (! scripts.contains(StringExtract.StringExtractUnicodeTable.SCRIPT.valueOf(s))){
                ModuleSettings.setConfigSetting(PROPERTIES_SCRIPTS, s, "false");
            }
        }
        for(StringExtract.StringExtractUnicodeTable.SCRIPT s : stringExtractScripts){
            ModuleSettings.setConfigSetting(PROPERTIES_SCRIPTS, s.name(), "true");
        }

    }
    
   /**
     * Set / override string extract option
     * @param key option name to set
     * @param val option value to set
     */
     static void setStringExtractOption(String key, String val) {
        stringExtractOptions.put(key, val);
        ModuleSettings.setConfigSetting(PROPERTIES_OPTIONS, key, val);
    }
     
     /**
     * gets the currently set scripts to use
     *
     * @return the list of currently used script
     */
   static  List<SCRIPT> getStringExtractScripts(){
        if(ModuleSettings.getConfigSettings(PROPERTIES_SCRIPTS) != null && !ModuleSettings.getConfigSettings(PROPERTIES_SCRIPTS).isEmpty()){
            List<SCRIPT> scripts = new ArrayList<SCRIPT>();
            for(Map.Entry<String,String> kvp : ModuleSettings.getConfigSettings(PROPERTIES_SCRIPTS).entrySet()){
                if(kvp.getValue().equals("true")){
                    scripts.add(SCRIPT.valueOf(kvp.getKey()));
                }
            }
            return scripts;
        }
        //if it failed, try to return the built-in list maintained by the singleton.
        logger.log(Level.WARNING, "Could not read properties for extracting scripts, returning backup values.");
        return new ArrayList<SCRIPT>(stringExtractScripts);
    }
    
 
    
    /**
     * get string extract option for the key
     * @param key option name
     * @return option string value, or empty string if the option is not set
     */
    static String getStringExtractOption(String key) {
        if (ModuleSettings.getConfigSetting(PROPERTIES_OPTIONS, key) != null){
            return ModuleSettings.getConfigSetting(PROPERTIES_OPTIONS, key);
        }
        else {
            logger.log(Level.WARNING, "Could not read property for Key "+ key + ", returning backup value.");
            return stringExtractOptions.get(key);
            
        }
    }
    /**
     * Sets the default values of the KeywordSearch properties files if none already exist.
     */
    static void setDefaults(){
        logger.log(Level.INFO, "Detecting default settings.");
             //setting default NSRL
     if(!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_NSRL, "SkipKnown")){
         logger.log(Level.INFO, "No configuration for NSRL not found, generating default...");
          KeywordSearchSettings.setSkipKnown(true);
       }
     //setting default Update Frequency
     if(!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_OPTIONS, "UpdateFrequency")){
         logger.log(Level.INFO, "No configuration for Update Frequency not found, generating default...");
         KeywordSearchSettings.setUpdateFrequency(UpdateFrequency.AVG);
      }
     //setting default Extract UTF8
     if(!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_OPTIONS, AbstractFileExtract.ExtractOptions.EXTRACT_UTF8.toString())){
         logger.log(Level.INFO, "No configuration for UTF8 not found, generating default...");
         KeywordSearchSettings.setStringExtractOption(AbstractFileExtract.ExtractOptions.EXTRACT_UTF8.toString(), Boolean.TRUE.toString());
         }
        //setting default Extract UTF16
     if(!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_OPTIONS, AbstractFileExtract.ExtractOptions.EXTRACT_UTF16.toString())){
         logger.log(Level.INFO, "No configuration for UTF16 not found, generating defaults...");
         KeywordSearchSettings.setStringExtractOption(AbstractFileExtract.ExtractOptions.EXTRACT_UTF16.toString(), Boolean.TRUE.toString());
       }
        //setting default Latin-1 Script
     if(!ModuleSettings.settingExists(KeywordSearchSettings.PROPERTIES_SCRIPTS, SCRIPT.LATIN_1.name())){
         logger.log(Level.INFO, "No configuration for Scripts not found, generating defaults...");
         ModuleSettings.setConfigSetting(KeywordSearchSettings.PROPERTIES_SCRIPTS, SCRIPT.LATIN_1.name(), Boolean.toString(true));
        }
    }
    
       
    
}
