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

import java.beans.PropertyChangeListener;

/**
 * Base interface for ingest services
 */
public interface IngestServiceAbstract {
    
    public enum ServiceType {Image, FsContent};
    public static final String BCKGRND_JOBS_COMPLETED_EVT = "BCKGRND_JOBS_COMPLETED_EVT";

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
     * @return unique service name
     */
    public String getName();
    
    /**
     * get user-friendly description of the service
     * @return service description
     */
    public String getDescription();
    
    /**
     * 
     * @return specialization of the service
     */
    public ServiceType getType();
    
     /**
     * A service can manage and use additional threads to perform some work in the background.
     * This method provides insight to the manager if the service has truly completed its work or not.
     *
     * 
     * @return true if any background threads/workers managed by this service are still running
     * false if all work has been done, or if background threads are not managed by this service
     */
    public boolean hasBackgroundJobsRunning();
    
    /**
     * Register listener to notify when all background jobs managed by this service have completed and the service 
     * has truly finished.  The service should first check if it has threads running, and then register the listener, all in a single atomic, synchronized operation, and return the result of the registration.
     * Do not register the listener if the background threads are not running and will not run during this service invocation.
     * If the service does use background threads it is required to implement this method properly and ensure the event is fired when the service-managed threads complete (are finished or cancelled)
     * The event fired off should be IngestServiceAbstract.BCKGRND_JOBS_COMPLETED_EVT, with the instance of IngestServiceAbstract in the newValue parameter.
     * The listeners should be reset at service init() - listeners are expected to register again as needed during the new service run.
     * Typical use case is for ingest manager to try to register the listener for every service when the queue has been consumed,
     * for a precise indication when all work is truly done.
     * 
     * @param l listener
     * @return true if listener registered, false otherwise (i.e. no background jobs were running, or the service does not manage additional threads)
     */
    public boolean backgroundJobsCompleteListener(PropertyChangeListener l);
    
    
    /**
     * @return does this service have a simple configuration?
     */
    public boolean hasSimpleConfiguration();
    
    /**
     * @return does this service have advanced configuration?
     */
    public boolean hasAdvancedConfiguration();
    
    /**	
     * Opportunity for the module to save its configuration options from from the getSimpleConfiguration() JPanel into the module
     * This is invoked by the framework e.g. when simple configuration panel is going out of scope
     */
    public void saveSimpleConfiguration();

    /** Opportunity for the module to save its configuration options from from the getAdvancedConfiguration() JPanel into the module
     * This is invoked by the framework e.g. when advanced configuration dialog is going out of scope	
     */
    public void saveAdvancedConfiguration();

    /**
     * Provides basic module configuration to the user (available e.g. via the add image wizard)
     * Only basic configuration should be exposed in this panel due to its size limitation
     * More options, if any, should be available via userConfigureAdvanced()
     * The module is responsible for preserving / saving its configuration state
     * In addition, userConfigureSave() can be used
     * 
     * @return JPanel containing basic configuration widgets or null
     */
    public javax.swing.JPanel getSimpleConfiguration();
    
     /**
     * Provides advanced module configuration to the user (available e.g. via the add image wizard)
     * The module is responsible for preserving / saving its configuration state
     * In addition, userConfigureAdvancedSave() can be used
     * 
     * @return JPanel containing basic configuration widgets or null
     */
    public javax.swing.JPanel getAdvancedConfiguration();
}
