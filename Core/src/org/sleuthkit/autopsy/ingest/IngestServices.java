/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2013 Basis Technology Corp.
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

import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;


/**
 * Singleton class that provides services for ingest modules.
 * These exist to make it easier to write modules.  Use the getDefault()
 * method to get the singleton instance. 
 */
public class IngestServices {
    
    private IngestManager manager;
    
    private Logger logger = Logger.getLogger(IngestServices.class.getName());
    
    private static IngestServices instance;
    
    private IngestServices() {
        this.manager = IngestManager.getDefault();
    }
    
    /**
     * Get handle to singletone module services
     * @return the services handle
     */
    public static synchronized IngestServices getDefault() {
        if (instance == null) {
            instance = new IngestServices();
        }
        return instance;
    }
    
    /**
     * Get access to the current Case handle.
     * Note: When storing the Case database handle as a member variable in a module, 
     * this method needs to be called within the module's init() method and the 
     * member variable needs to be updated at each init(),
     * to ensure the correct Case handle is being used if the Case is changed.
     * 
     * @return current Case
     */
    public Case getCurrentCase() {
        return Case.getCurrentCase();
    }
    
     /**
     * Get access to the current Case database handle.  Like storing
      * the Case handle, call this method and update member variables for each
      * call to the module's init() method to ensure it is correct.
     * 
     * @return current Case database 
     */
    public SleuthkitCase getCurrentSleuthkitCaseDb() {
        return Case.getCurrentCase().getSleuthkitCase();
    }
    
    /**
     * Get a logger to be used by the module to log messages to log files.
     * @param module module to get the logger for
     * @return logger object
     */
    public Logger getLogger(IngestModuleAbstract module) {
        return Logger.getLogger(module.getName());
    }
    
    /**
     * Post ingest message to the inbox. This should be done for 
     * analysis messages.
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
     * Fire module data event to notify registered module data event listeners that there
     * is new data of a given type from a module
     * @param moduleDataEvent module data event, encapsulating blackboard artifact data
     */
    public void fireModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        IngestManager.fireModuleDataEvent(moduleDataEvent);
    }
    
    
     /**
     * Fire module content event to notify registered module content event listeners
     * that there is new content (from ZIP file contents, carving, etc.)
     * @param moduleContentEvent module content event, encapsulating content changed
     */
    public void fireModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        IngestManager.fireModuleContentEvent(moduleContentEvent);
    }
    
    /**
     * Schedule a new file for ingest with the same settings as the file
     * being analyzed.  This is used, for example, when opening an archive file.
     * File needs to have already been added to the database. 
     * 
     * @param file file to be scheduled
     * @param pipelineContext the ingest context for the file ingest pipeline
     */
    public void scheduleFile(AbstractFile file, PipelineContext<IngestModuleAbstractFile> pipelineContext)  {
        logger.log(Level.INFO, "Scheduling file: " + file.getName());
        manager.scheduleFile(file, pipelineContext);
    }
    
    
     /**
     * Get free disk space of a drive where ingest data are written to
     * That drive is being monitored by IngestMonitor thread when ingest is running.
     * 
     * @return amount of disk space, -1 if unknown
     */
    public long getFreeDiskSpace() {
        return manager.getFreeDiskSpace();
    }
    
    
    
    /**
     * Facility for a file ingest module to check a return value from a previously run file ingest module
     * that executed for the same file.
     * The module return value can be used as a guideline to skip processing the file
     * 
     * @param moduleName registered module name of the module to check the return value of
     * @return the return value of the previously executed module for the currently processed file in the file ingest pipeline
     */
    public IngestModuleAbstractFile.ProcessResult getAbstractFileModuleResult(String moduleName) {
        return manager.getAbstractFileModuleResult(moduleName);
    }
    
    /**
     * Gets a specific name/value configuration setting for a module
     * @param moduleName moduleName identifier unique to that module
     * @param settingName setting name to retrieve
     * @return setting value for the module / setting name, or null if not found
     */
    public String getConfigSetting(String moduleName, String settingName) {
        return ModuleSettings.getConfigSetting(moduleName, settingName);
    }
    
    /**
     * Sets a specific name/value configuration setting for a module
     * @param moduleName moduleName identifier unique to that module
     * @param settingName setting name to set
     * @param settingVal setting value to set
     */
    public void setConfigSetting(String moduleName, String settingName, String settingVal) {
        ModuleSettings.setConfigSetting(moduleName, settingName, settingVal);
    }
    
    /**
     * Gets all name/value configuration settings for a module
     * @param moduleName moduleName identifier unique to that module
     * @return settings for the module / setting name
     */
    public Map<String,String> getConfigSettings(String moduleName) {
        return ModuleSettings.getConfigSettings(moduleName);
    }
    
   /**
    * Sets all  name/value configuration setting for a module.  Names not in the list will have settings preserved. 
     * @param moduleName moduleName identifier unique to that module
     * @param settings settings to set and replace old settings, keeping settings not specified in the map.
     * 
     */
    public void setConfigSettings(String moduleName, Map<String,String>settings) {
        ModuleSettings.setConfigSettings(moduleName, settings);
    }
}
