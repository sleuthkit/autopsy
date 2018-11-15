/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.textextractors;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;

/**
 *
 * @author dsmyda
 */
public class ExtractionContext {
    ClassToInstanceMap<Object> extractionConfigs;
    
    public ExtractionContext() {
        extractionConfigs = MutableClassToInstanceMap.create();
    }
        
    public <T> void set(Class<T> configClass, T configInstance) {
        extractionConfigs.put(configClass, configInstance);
    }	
    public <T> T get(Class<T> configClass) {
        return  (T) extractionConfigs.get(configClass);
    }
}
