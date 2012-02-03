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
     * provides means for user to input service specific configuration options
     * the new configuration is effective on next ingest
     */
    public void userConfigure();
}
