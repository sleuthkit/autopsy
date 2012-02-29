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

import javax.swing.JPanel;

/**
 * Base interface for ingest services
 */
public interface IngestServiceAbstract {
    
    public enum ServiceType {Image, FsContent};

    /**
     * notification from manager that brand new processing should be initiated.
     * Service loads its configuration and performs initialization
     * called once per new worker thread
     * 
     * @param IngestManagerProxy interface to manager for posting messages, getting configurations
     */
    public void init(IngestManagerProxy managerProxy);

    /**
     * notification from manager that there is no more content to process and all work is done.
     * Service performs any clean-up, notifies viewers and may also write results to the black-board
     */
    public void complete();

    /**
     * notification from manager to stop processing due to some interruption (user, error, exception)
     */
    public void stop();

    /**
     * get specific name of the service
     * should be unique across services, a user-friendly name of the service shown in GUI
     */
    public String getName();
    
    /**
     * 
     * @return specialization of the service
     */
    public ServiceType getType();
    
     /**
     * A service can manage and use additional threads to perform some work in the background.
     * This method provides insight to the manager if the service has truly completed its work or not.
     * @return true if any background threads/workers managed by this service are still running
     * false if all work has been done, or if background threads are not used by this service
     */
    public boolean hasBackgroundJobsRunning();
    
    
    /**
     * @return true if the service has basic configuration
     */
    public boolean hasSimpleConfiguration();
    
    /**
     * Provides basic module configuration to the user (available e.g. via the add image wizard)
     * Only basic configuration should be exposed in this panel due to its size limitation
     * More options, if any, should be available via userConfigureAdvanced()
     * The module is responsible for preserving / saving its configuration state
     * In addition, userConfigureSave() can be used
     * 
     * @return JPanel containing basic configuration widgets or null
     */
    public JPanel getSimpleConfiguration();
    
    /**
     * Opportunity for the module to save its configuration options from from the userConfigure() JPanel into the module
     * This is invoked by the framework e.g. when configuration dialog is going out of scope
     */
    public void saveSimpleConfiguration();
    
    
     /**
     * @return does the service have advanced configuration panel
     */
    public boolean hasAdvancedConfiguration();
    
    
     /**
     * Provides advanced module configuration to the user (available e.g. via the add image wizard)
     * The module is responsible for preserving / saving its configuration state
     * In addition, userConfigureAdvancedSave() can be used
     * 
     * @return JPanel containing basic configuration widgets or null
     */
    public JPanel getAdvancedConfiguration();
    
    /**
     * Opportunity for the module to save its configuration options from from the userConfigureAdvanced() JPanel into the module
     * This is invoked by the framework e.g. when advanced configuration dialog is going out of scope
     */
    public void saveAdvancedConfiguration();
    
    
    
    
   
}
