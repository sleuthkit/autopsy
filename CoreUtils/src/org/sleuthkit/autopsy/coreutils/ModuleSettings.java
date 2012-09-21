/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

package org.sleuthkit.autopsy.coreutils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
/**
 * This class contains the framework to read, add, update, and remove
 * from the property files located at %USERDIR%/Config/x.properties
 */
public class ModuleSettings {

    // The directory where the properties file is lcoated
    private final static String userDirPath = PlatformUtil.getUserDirectory().getAbsolutePath();
    private final static String moduleDirPath = userDirPath + File.separator + "config";
    public static final String MAIN_SETTINGS="Autopsy";

    
    /** the constructor */
    private ModuleSettings() {
      
    }
    
    
    /**
     * Makes a new config file of the specified name. Do not include the extension.
     * @param moduleName - The name of the config file to make
     * @return True if successfully created, false if already exists or an error is thrown. 
     */
    public static boolean makeConfigFile(String moduleName){
        if(!configExists(moduleName)){
            File propPath = new File(moduleDirPath + File.separator + moduleName + ".properties");
            File parent = new File(propPath.getParent());
            if(! parent.exists()){
                parent.mkdirs();
            }
            Properties props = new Properties();
            try{
            propPath.createNewFile();
            FileOutputStream fos = new FileOutputStream(propPath);
            props.store(fos, "");
            fos.close();
            }
            catch(IOException e){
                Logger.getLogger(ModuleSettings.class.getName()).log(Level.WARNING, "Was not able to create a new properties file.", e);
                return false;
            }
            return true;
        }
        return false;
    }
    
    
    /**
     * Determines if a given properties file exists or not.
     * @param moduleName - The name of the config file to evaluate
     * @return true if the config exists, false otherwise.
     */
    public static boolean configExists(String moduleName){
        File f = new File(moduleDirPath + File.separator + moduleName + ".properties");
        return f.exists();
    }
    
    /**
     * Returns the path of the given properties file.
     * @param moduleName - The name of the config file to evaluate
     * @return The path of the given config file. Returns null if the config file doesn't exist.
     */
    private static String getPropertyPath(String moduleName){
        if(configExists(moduleName)){
            return moduleDirPath + File.separator + moduleName + ".properties";
        }
        
        return null;
    }
   
    /**
     * Returns the given properties file's  setting as specific by settingName.
     * @param moduleName - The name of the config file to read from.
     * @param settingName - The setting name to retrieve. 
     * @return - the value associated with the setting.
     * @throws IOException 
     */
    public static String getConfigSetting(String moduleName, String settingName){
        if(configExists(moduleName)){
            try{
            InputStream inputStream = new FileInputStream(getPropertyPath(moduleName));
            Properties props = new Properties();
            props.load(inputStream);
            inputStream.close();
            return props.getProperty(settingName);
            }
            
           catch(IOException e){
                Logger.getLogger(ModuleSettings.class.getName()).log(Level.WARNING, "Could not read config file [" + moduleName + "]", e);
                return null;
            }

        }
        else{ 
            makeConfigFile(moduleName);
           return getConfigSetting(moduleName, settingName);
        }
    }
       
    
    /**
     * Returns the given properties file's map of settings.
     * @param moduleName - the name of the config file to read from.
     * @return - the map of all key:value pairs representing the settings of the config.
     * @throws IOException 
     */
    public static Map< String, String> getConfigSettings(String moduleName) {

        if (configExists(moduleName)) {
            try{
            InputStream inputStream = new FileInputStream(getPropertyPath(moduleName));
            Properties props = new Properties();
            props.load(inputStream);
            inputStream.close();
            

            Set<String> keys = props.stringPropertyNames();
            Map<String, String> map = new HashMap<String, String>();
            
            for (String s : keys) {
                map.put(s, props.getProperty(s));
            }
            
            return map;
            }
            catch(IOException e){
                Logger.getLogger(ModuleSettings.class.getName()).log(Level.WARNING, "Could not read config file [" + moduleName + "]", e);
                return null;
            }
        } 
        else {
            makeConfigFile(moduleName);
            return getConfigSettings(moduleName);
        }
    }
    
    /**
     * Sets the given properties file to the given setting map.
     * @param moduleName - The name of the module to be written to.
     * @param settings - The mapping of all key:value pairs of settings to add to the config.
     */
    public static void setConfigSettings(String moduleName, Map<String,String> settings){
        if(configExists(moduleName)){
          try{
            InputStream inputStream = new FileInputStream(getPropertyPath(moduleName));
            Properties props = new Properties();
            props.load(inputStream);
            inputStream.close();

            for(Map.Entry<String,String> kvp : settings.entrySet()){
                props.setProperty(kvp.getKey(), kvp.getValue());
            }
            
            File path = new File(getPropertyPath(moduleName));
            FileOutputStream fos = new FileOutputStream(path);
            props.store(fos, "Changed config settings");
            fos.close();
          }
          catch(IOException e ){
              Logger.getLogger(ModuleSettings.class.getName()).log(Level.WARNING, "Property file exists for [" + moduleName + "] at [" + getPropertyPath(moduleName) + "] but could not be loaded.", e);
          }
        }
        else{
            makeConfigFile(moduleName);
            setConfigSettings(moduleName, settings);
        }
    }
    
    /**
     * Sets the given properties file to the given settings.
     * @param moduleName - The name of the module to be written to.
     * @param settingName - The name of the setting to be modified.
     * @param settingVal - the value to set the setting to.
     */
    public static void setConfigSetting(String moduleName, String settingName, String settingVal) {
        if(configExists(moduleName)){
          try{
            InputStream inputStream = new FileInputStream(getPropertyPath(moduleName));
            Properties props = new Properties();
            props.load(inputStream);
            inputStream.close();
          
            props.setProperty(settingName, settingVal);
            
             File path = new File(getPropertyPath(moduleName));
            FileOutputStream fos = new FileOutputStream(path);
            props.store(fos, "Changed config settings");
            fos.close();
          }
          catch(IOException e ){
              Logger.getLogger(ModuleSettings.class.getName()).log(Level.WARNING, "Property file exists for [" + moduleName + "] at [" + getPropertyPath(moduleName) + "] but could not be loaded.", e);
          }
        }
        
        else{
         //throw new FileNotFoundException("No property file found for [" + moduleName + "]");
            makeConfigFile(moduleName);
            Logger.getLogger(ModuleSettings.class.getName()).log(Level.INFO, "File did not exist. Created file [" + moduleName + ".properties]");
            setConfigSetting(moduleName, settingName, settingVal);
        }
    }
    

    /**
     * Removes the given key from the given properties file.
     * @param moduleName - The name of the properties file to be modified.
     * @param key - the name of the key to remove.
     */
    
    public static void removeProperty(String moduleName, String key){
        try{
            if(getConfigSetting(moduleName, key) != null){
               // setConfigSetting(moduleName, key, "");
            InputStream inputStream = new FileInputStream(getPropertyPath(moduleName));
            Properties props = new Properties();
            props.load(inputStream);
            inputStream.close();
            props.remove(key);
            File path = new File(getPropertyPath(moduleName));
            FileOutputStream fos = new FileOutputStream(path);
            props.store(fos, "Removed " + key);
            fos.close();
            }
        }
        catch(IOException e ){
            Logger.getLogger(ModuleSettings.class.getName()).log(Level.WARNING, "Could not remove property from file, file not found", e);
        }
    }
    
    /**
     * Gets the property file as specified.
     * @param moduleName
     * @return A new file handle
     */
    public static File getPropertyFile(String moduleName) {
        return new File(getPropertyPath(moduleName));
    }
    
}
