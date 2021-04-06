/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.recentactivity;

import com.williballenthin.rejistry.RegistryHiveFile;
import com.williballenthin.rejistry.RegistryKey;
import com.williballenthin.rejistry.RegistryParseException;
import com.williballenthin.rejistry.RegistryValue;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 *
 * Parsers registry keys from a registry hive
 */
public class ParseRegistryHive {
   
    private final File registryHiveFile;
    private final RegistryHiveFile registryHive;
    final private static Logger logger = Logger.getLogger(ParseRegistryHive.class.getName());
    
    ParseRegistryHive(File registryHiveFile) throws IOException {
        this.registryHiveFile = registryHiveFile;
        registryHive = new RegistryHiveFile(this.registryHiveFile);
    }
    
    /**
     * 
     * @param registryKey Registry key to get the value for
     * @param registryValue Value of the registry key to get the data for
     * @return  String data from the registry key/value pair
     * 
     */
    public String getRegistryKeyValue(String registryKey, String registryValue) {
        
        RegistryKey currentKey = findRegistryKey(registryHive, registryKey);
        
        if (currentKey == null) {
            return null;
        }
    
        try {
            List<RegistryValue> parameterList = currentKey.getValueList();
                for (RegistryValue parameter : parameterList) {
                    if (parameter.getName().toLowerCase().equals(registryValue)) {
                        return parameter.getValue().getAsString();
                    }
                }
        } catch (RegistryParseException ex) {
            logger.log(Level.WARNING, String.format("Error reading registry file '%s'", registryHiveFile.getName()), ex); //NON-NLS

        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.WARNING, String.format("Unsupported Encoding Error for registry key '%s' and registry value '%s'", 
                        registryKey, registryValue), ex); //NON-NLS            
        }
    
        return null;
    
    }
    
    /**
     *  Gets the time that the registry key was written.
     *  @param registryKey Registry key to get the timestamp for
     *  @return  date/time that the key was written
     * 
     */
   public Calendar getRegistryKeyTime(String registryKey) {
        
        RegistryKey currentKey = findRegistryKey(registryHive, registryKey);
        
        if (currentKey == null) {
            return null;
        }
    
        return currentKey.getTimestamp();
    
    }
    
    /**
     * Gets the timestamp of the Registry key
     * 
     * @param registryHiveFile Hive to parse
     * @param registryKey registry key to find in hive
     * @return registry key or null if it cannot be found
     */
    private RegistryKey findRegistryKey(RegistryHiveFile registryHiveFile, String registryKey) {
        
        RegistryKey currentKey;
        try {
            RegistryKey rootKey = registryHiveFile.getRoot();
            String regKeyList[] = registryKey.split("/");
            currentKey = rootKey;
            for (String key : regKeyList) {
                currentKey = currentKey.getSubkey(key);
            }
        } catch (RegistryParseException ex) {
            return null;
        }
        return currentKey;   

    }
    
}
