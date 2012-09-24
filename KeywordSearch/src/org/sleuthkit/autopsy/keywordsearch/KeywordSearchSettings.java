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
    static List<StringExtract.StringExtractUnicodeTable.SCRIPT> stringExtractScripts = new ArrayList<StringExtract.StringExtractUnicodeTable.SCRIPT>();
    static Map<String,String> stringExtractOptions = new HashMap<String,String>();
    

    
    /**
     * 
     * @return IngestModule singleton
     */
    static KeywordSearchIngestModule getDefault(){
        return KeywordSearchIngestModule.getDefault();
    }
    
    /**
     * 
     * @return IngestModule's update frequency
     */
    static UpdateFrequency getUpdateFrequency(){
        return KeywordSearchIngestModule.getDefault().getUpdateFrequency();
    }
    
    /**
     * Sets the ingest module's update frequency.
     * @param c Update frequency to set.
     */
    static void setUpdateFrequency(UpdateFrequency c){
        KeywordSearchIngestModule.getDefault().setUpdateFrequency(c);
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
            return stringExtractOptions.get(key);
        }
    }
    
    /**
     * Gets the setting for whether or not this ingest is skipping adding known good files to the index.
     * @return skip setting
     */
    static boolean getSkipKnown() {
        try{
        if(ModuleSettings.getConfigSetting(PROPERTIES_NSRL, "SkipKnown") != null){
            skipKnown = Boolean.parseBoolean(ModuleSettings.getConfigSetting(PROPERTIES_NSRL, "SkipKnown"));
        }
         }
          catch(Exception e ){
              Logger.getLogger(KeywordSearchIngestModule.class.getName()).log(Level.WARNING, "Could not parse boolean value from properties file.", e);
          }
        return skipKnown;
    }
       
    
}
