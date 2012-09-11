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
public interface IngestModuleAbstract {
    
    /**
     * Possible module types for the implementing classes
     */
    public enum ModuleType {
        /**
         * Image type module
         */
        Image,  
        
        /**
         * AbstractFile type module
         */
        AbstractFile
    };

    /**
     * Notification from manager that brand new ingest should be initiated..
     * Module loads its configuration and performs initialization.
     * Invoked once per new worker thread, per ingest.
     * In this method initialize always IngestServices handle 
     * using IngestServices.getDefault() lazy-loading approach.
     * NEVER initialize IngestServices handle in the member declaration, because it might result
     * in multiple instances of the singleton -- different class loaders are used in different modules.
     * @param initContext context used to initialize some modules
     */
    public void init(IngestModuleInit initContext);

    /**
     * Notification from manager that there is no more content to process and all work is done.
     * Module performs any clean-up of internal resources, and finalizes processing to produce complete result
     * Module also posts ingest message indicating it is done, and posts ingest stats and errors in the details of the message.
     */
    public void complete();

    /**
     * Notification from manager to stop processing due to some interruption (user, error, exception)
     * Module performs any clean-up of internal resources
     * It may also discard any pending results, but it should ensure it is in a defined state so that ingest can be rerun later.
     */
    public void stop();

    /**
     * Gets specific name of the module
     * The name should be unique across modules
     * @return unique module name
     */
    public String getName();
    
    /**
     * Gets user-friendly description of the module
     * @return module description
     */
    public String getDescription();
    
    /**
     * Returns type of the module
     * @return module type
     */
    public ModuleType getType();
    
     /**
     * A module can manage and use additional threads to perform some work in the background.
     * This method provides insight to the manager if the module has truly completed its work or not.
     *
     * 
     * @return true if any background threads/workers managed by this module are still running or are pending to be run,
     * false if all work has been done, or if background threads are not used/managed by this module
     */
    public boolean hasBackgroundJobsRunning();
    
    
    /**
     * Used to determine if a module has implemented a simple (run-time)
     * configuration panel that is displayed by the ingest manager.
     * 
     * @return true if this module has a simple (run-time) configuration
     */
    public boolean hasSimpleConfiguration();
    
    /**
     * Used to determine if a module has implemented an advanced (general)
     * configuration that can be used for more in-depth module configuration.
     * 
     * @return true if this module has an advanced configuration
     */
    public boolean hasAdvancedConfiguration();
    
    /**	
     * Called by the ingest manager if the simple (run-time) configuration
     * panel should save its current state so that the settings can be used
     * during the ingest.
     */
    public void saveSimpleConfiguration();

    /**	
     * If module implements advanced configuration panel
     * it should read its current state and make it persistent / save it in this method
     * so that the new configuration will be in effect during the ingest.
     */
    public void saveAdvancedConfiguration();

    /**
     * Returns a panel that displays the simple (run-time) configuration. 
     * This is presented to the user before ingest starts and only basic
     * settings should be given here.  use the advanced (general) configuration
     * panel for more in-depth interfaces. 
     * The module is responsible for preserving / saving its configuration state
     * In addition, saveSimpleConfiguration() can be used
     * 
     * @return JPanel containing basic configuration widgets or null if simple configuration is not available
     */
    public javax.swing.JPanel getSimpleConfiguration();
    
     /**
     * Implements advanced module configuration exposed to the user before ingest starts
     * The module is responsible for preserving / saving its configuration state
     * In addition, saveAdvancedConfiguration() can be used
     * 
     * @return JPanel containing advanced configuration widgets or null if advanced configuration is not available
     */
    public javax.swing.JPanel getAdvancedConfiguration();
}
