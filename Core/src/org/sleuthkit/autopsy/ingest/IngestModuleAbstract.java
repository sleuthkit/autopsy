/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2012 Basis Technology Corp.
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


/**
 * Base interface for ingest modules
 */
 public abstract class IngestModuleAbstract {
    
    private String args;
    
    /**
     * Possible module types for the implementing classes
     */
    public enum ModuleType {
        /**
         * DataSource type module
         */
        DataSource,  
        
        /**
         * AbstractFile type module
         */
        AbstractFile
    };
    
    public class IngestModuleException extends Exception {
        public IngestModuleException(String msg) {
            super(msg);
        }
    }
    

    /**
     * Invoked every time an ingest session is started by the framework.  
     * A module should support multiple invocations of init() throughout the application life-cycle.  
     * In this method, the module should reinitialize its internal objects and resources and get them ready 
     * for a brand new ingest processing.  
     * 
     * Here are some things you may do in this method if you'll need them later. 
     * - Get a handle to the ingest services using org.sleuthkit.autopsy.ingest.IngestServices.getDefault().
     * - Get the current case using org.sleuthkit.autopsy.ingest.IngestServices.getCurrentSleuthkitCaseDb().
     * 
     * NEVER initialize IngestServices handle in the member declaration, because it might result
     * in multiple instances of the singleton -- different class loaders are used in different modules.
     * @param initContext context used to initialize some modules
     * 
     * @throws IngestModuleException if a critical error occurs in initializing the module.
     */
    abstract public void init(IngestModuleInit initContext) throws IngestModuleException;

    /**
     * Invoked when an ingest session completes.  
     * The module should perform any resource (files, handles, caches) 
     * cleanup in this method and submit final results and post a final ingest inbox message. 
     */
    abstract public void complete();

    /**
     * Invoked on a module when an ingest session is interrupted by the user or system.
     * The method implementation should be similar to complete() in that the 
     * module should perform any cleanup work.  
     * If there is pending data to be processed or pending results to be reported by the module 
     * then the results should be rejected and ignored and the method should return as early as possible.
     * It should ensure it is in a defined state so that ingest can be rerun later.
     */
    abstract public void stop();

    /**
     * Returns unique name of the module.  Should not have collisions.
     * @return unique module name
     */
    abstract public String getName();
    
    /**
     * Gets the module version
     * @return module version string
     */
    abstract public String getVersion();
    
    /**
     * Gets user-friendly description of the module
     * @return module description
     */
    abstract public String getDescription();
    
    /**
     * Returns type of the module (data source-level or file-level)
     * @return module type
     */
    abstract public ModuleType getType();
    
   
     /**
     * A module can manage and use additional threads to perform some work in the background.
     * This method provides insight to the manager if the module has truly completed its work or not.
     *
     * 
     * @return true if any background threads/workers managed by this module are still running or are pending to be run,
     * false if all work has been done, or if background threads are not used/managed by this module
     */
    abstract public boolean hasBackgroundJobsRunning();
    
    
    /**
     * Used to determine if a module has implemented a simple (run-time)
     * configuration panel that is displayed by the ingest manager.
     * 
     * @return true if this module has a simple (run-time) configuration
     */
    public boolean hasSimpleConfiguration() {
        return false;
    }
    
    /**
     * Used to determine if a module has implemented an advanced (general)
     * configuration that can be used for more in-depth module configuration.
     * 
     * @return true if this module has an advanced configuration
     */
    public boolean hasAdvancedConfiguration() {
        return false;
    }
    
    /**	
     * Called by the ingest manager if the simple (run-time) configuration
     * panel should save its current state so that the settings can be used
     * during the ingest.
     */
    public void saveSimpleConfiguration() {}

    /**	
     * If module implements advanced configuration panel
     * it should read its current state and make it persistent / save it in this method
     * so that the new configuration will be in effect during the ingest.
     */
    public void saveAdvancedConfiguration() {}

     
    /**
     * Returns a panel that displays the simple (run-time) configuration for the
     * given configuration context (such as pipeline instance). This is
     * presented to the user before ingest starts and only basic settings should
     * be given here. Use the advanced (general) configuration panel for more
     * in-depth interfaces. The module (or its configuration controller object)
     * is responsible for preserving / saving its configuration state In
     * addition, saveSimpleConfiguration() can be used as the trigger.
     *     
     * @param context the configuration context to use in the panel
     * @return JPanel containing basic configuration widgets or null if simple
     * configuration is not available
     */
    public javax.swing.JPanel getSimpleConfiguration(String context) {
        return null;
    }

    /**
     * Returns a panel that displays the advanced (run-time) configuration for
     * the given configuration context (such as pipeline instance). Implements
     * advanced module configuration exposed to the user before ingest starts.
     *     
     * The module (or its configuration controller object) is responsible for
     * preserving / saving its configuration state In addition,
     * saveAdvancedConfiguration() can be used as the trigger.
     *     
     * @param context the configuration context to use in the panel
     * @return JPanel containing advanced configuration widgets or null if
     * advanced configuration is not available
     */
    public javax.swing.JPanel getAdvancedConfiguration(String context) {
        return null;
    }
 }    