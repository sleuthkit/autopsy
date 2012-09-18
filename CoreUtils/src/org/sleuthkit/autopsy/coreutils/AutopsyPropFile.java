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
import java.util.Properties;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.modules.Places;
/**
 * This class contains the framework to read, add, update, and remove
 * from the properties file located at %USERDIR%/autopsy.properties
 * 
 * @author dfickling
 */
public class AutopsyPropFile {

    // The directory where the properties file is lcoated
    private final static String userDirPath = Places.getUserDirectory().getAbsolutePath();
    private final static String propFilePath = userDirPath + File.separator + "autopsy.properties";

    // The AutopsyPropFile singleton
    private static final AutopsyPropFile INSTANCE = new AutopsyPropFile();
    
    private Properties properties;
    
    /**
     * Get the instance of the AutopsyPropFile singleton
     */
    public static AutopsyPropFile getInstance(){
        return INSTANCE;
    }
    
    /** the constructor */
    private AutopsyPropFile() {
        properties = new Properties();
        try {
            // try to load all the properties from the properties file in the home directory
            InputStream inputStream = new FileInputStream(propFilePath);
            properties.load(inputStream);
            inputStream.close();
        } catch (Exception ignore) {
            // if cannot load it, we create a new properties file without any data inside it
            try {
                // create the directory and property file to store it
                File output = new File(propFilePath);
                if (!output.exists()) {
                    File parent = new File(output.getParent());
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    output.createNewFile();
                    FileOutputStream fos = new FileOutputStream(output);
                    properties.store(fos, "");
                    fos.close();
                } else {
                    // if the property file already exist, throw an error that says cannot load that file
                    Logger.getLogger(AutopsyPropFile.class.getName()).log(Level.WARNING, "Error: Could not load the property file.", new Exception("The properties file already exist and can't load that file."));
                }
            } catch (IOException ex2) {
                Logger.getLogger(AutopsyPropFile.class.getName()).log(Level.WARNING, "Error: Could not create the property file.", ex2);
            }
        }
    }
    
    
    private void storeProperties() throws IOException {
        FileOutputStream fos = new FileOutputStream(new File(propFilePath));
        properties.store(fos, "");
        fos.close();
    }
    
    /**
     * Gets the properties file paths.
     *
     * @return propertyPath
     */
    public static String getPropertiesFilePath() {
        return propFilePath;
    }
    
    public void setProperty(String key, String val){
        properties.setProperty(key, val);
        try {
            storeProperties();
        } catch (Exception ex) {
            Logger.getLogger(AutopsyPropFile.class.getName()).log(Level.WARNING, "Error: Could not update the properties file.", ex);
        }
    }
    
    public String getProperty(String key){
        return properties.getProperty(key);
    }
    
    public void removeProperty(String key){
        properties.setProperty(key, null);
        try {
            storeProperties();
        } catch (Exception ex) {
            Logger.getLogger(AutopsyPropFile.class.getName()).log(Level.WARNING, "Error: Could not update the properties file.", ex);
        }
    }
    
    /**
     * Gets the property file where the user properties such as Recent Cases
     * and selected Hash Databases are stored.
     * @return A new file handle
     */
    public static File getPropertyFile() {
        return new File(propFilePath);
    }
    
    public static File getUserDirPath() {
        return new File(userDirPath);
    }
}
