/*
 * Autopsy
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;
import java.util.HashMap;
import java.util.Map;


/**
 * Generic Ingest Job settings class.
 * Primary use of this class is for Python modules because classes created in Python
 * cannot be serialized / deserialized in Java. 
 */
public class GenericIngestModuleJobSettings implements IngestModuleIngestJobSettings {
    private static final long serialVersionUID = 1L;
    
    @Override
    public long getVersionNumber(){
        return serialVersionUID;
    }
    
    private final Map<String, String> settings;
    
    public GenericIngestModuleJobSettings(){
        this.settings = new HashMap<>();
    }
    
    /**
     * Return the string value for passed key parameter.
     *
     * @param key The key to lookup
     * @return The value or null if the key was not found. 
     */
    public String getSetting(String key){
        return settings.get(key);
    }
    
    /**
     * Adds the passed key value pair
     *
     * @param key The key to be added to the settings
     * @param value The value to be added for the key
     */
    public void setSetting(String key, String value){
        settings.put(key, value);
    }
    
    /**
     * Removes the key from the settings. 
     * @param key The key to be removed
     */
    public void removeSetting(String key){
        settings.remove(key);
    }
}
