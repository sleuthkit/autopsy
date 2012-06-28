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
 * Ingest manager facade used by ingest services
 * 
 * Facility for services to interact with the ingest manager
 * for sending data events, ingest messages, getting configuration, such as
 * update frequency configuration
 * 
 */
public class IngestManagerProxy {
    
    private IngestManager manager;
    
    IngestManagerProxy(IngestManager manager) {
        this.manager = manager;
    }
    
    /**
     * Post ingest message
     * @param message ingest message to be posted by ingest service
     */
    public void postMessage(final IngestMessage message) {
        manager.postMessage(message);
    }
    
    /**
     * Fire service event to notify registered service event listeners
     * @param eventType the event type, defined in IngestManager.IngestManagerEvents
     * @param serviceName the service name
     */
    public static void fireServiceEvent(String eventType, String serviceName) {
        IngestManager.fireServiceEvent(eventType, serviceName);
    }

    
    /**
     * Fire service data event to notify registered service data event listeners
     * @param serviceDataEvent service data event, encapsulating blackboard artifact data
     */
    public static void fireServiceDataEvent(ServiceDataEvent serviceDataEvent) {
        IngestManager.fireServiceDataEvent(serviceDataEvent);;
    }
    
    
    /**
     * Facility for the service to query the currently set recommended data update frequency in minutes
     * Services that post data in controlled time intervals, should obey this setting
     * 
     * @return max. number of minutes before service posts new data, if data is available
     */
    public int getUpdateFrequency() {
        return manager.getUpdateFrequency().getTime();
    }
    
    
    /**
     * Facility for a file ingest service to check a return value from another file ingest service
     * that executed for the same file earlier in the file ingest pipeline
     * The service return value can be used as a guideline to skip processing the file
     * 
     * @param serviceName registered service name of the service to check the return value of
     * @return the return value of the previously executed service for the currently processed file in the file ingest pipeline
     */
    public IngestServiceAbstractFile.ProcessResult getAbstractFileServiceResult(String serviceName) {
        return manager.getAbstractFileServiceResult(serviceName);
    }
    
    
    
    
    
}
