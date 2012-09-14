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


package org.sleuthkit.autopsy.ingest;

import org.sleuthkit.autopsy.coreutils.Logger;


/**
 * Services available to ingest modules via singleton instance,
 * e.g. for logging, interacting with the ingest manager
 * sending data events notifications, ingest messages, getting configurations, etc.
 * 
 */
public class IngestServices {
    
    private IngestManager manager;
    
    private static IngestServices instance;
    
    private IngestServices() {
        this.manager = IngestManager.getDefault();
    }
    
    /**
     * Get handle to module services
     * @return the services handle
     */
    public static synchronized IngestServices getDefault() {
        if (instance == null) {
            instance = new IngestServices();
        }
        return instance;
    }
    
    
    /**
     * Get a logger to be used by the module to log messages to log files
     * @param module module to get the logger for
     * @return logger object
     */
    public Logger getLogger(IngestModuleAbstract module) {
        return Logger.getLogger(module.getName());
    }
    
    /**
     * Post ingest message
     * @param message ingest message to be posted by ingest module
     */
    public void postMessage(final IngestMessage message) {
        manager.postMessage(message);
    }
    
    /**
     * Fire module event to notify registered module event listeners
     * @param eventType the event type, defined in IngestManager.IngestManagerEvents
     * @param moduleName the module name
     */
    public void fireModuleEvent(String eventType, String moduleName) {
        IngestManager.fireModuleEvent(eventType, moduleName);
    }

    
    /**
     * Fire module data event to notify registered module data event listeners
     * @param moduleDataEvent module data event, encapsulating blackboard artifact data
     */
    public void fireModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        IngestManager.fireModuleDataEvent(moduleDataEvent);
    }
    
    
    /**
     * Facility for the module to query the currently set recommended data update frequency in minutes
     * Modules that post data in controlled time intervals, should obey this setting
     * 
     * @return max. number of minutes before module posts new data, if data is available
     *
     * @Deprecated individual modules are be responsible for maintaining such settings
     */
    public int getUpdateFrequency() {
        return manager.getUpdateFrequency().getTime();
    }
    
    
    /**
     * Facility for a file ingest module to check a return value from another file ingest module
     * that executed for the same file earlier in the file ingest pipeline
     * The module return value can be used as a guideline to skip processing the file
     * 
     * @param moduleName registered module name of the module to check the return value of
     * @return the return value of the previously executed module for the currently processed file in the file ingest pipeline
     */
    public IngestModuleAbstractFile.ProcessResult getAbstractFileModuleResult(String moduleName) {
        return manager.getAbstractFileModuleResult(moduleName);
    }
    
    
    
    
    
}
