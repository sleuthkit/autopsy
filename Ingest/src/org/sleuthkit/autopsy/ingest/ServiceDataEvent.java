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

import java.util.Collection;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * Event data that are fired off by ingest modules when they have posted new data
 * of specific type to the blackboard. The name of property change fired is then IngestManager.IngestModuleEvent.DATA.toString()
 * 
 * In its most generic form, it only gives notice about a type of artifact and it 
 * can also give notice about specific IDs that can be retrieved.
 * 
 * The object wraps a collection of blackboard artifacts and their associated attributes that are to be reported as the new data to listeners.
 * Passing the data as part of the event reduces memory footprint and decreases number of garbage collections of the blackboard artifacts and attributes objects (the objects are expected to be reused by the data event listeners).
 * 
 * If a service does not pass the data as part of ServiceDataEvent (ServiceDataEvent.getArtifacts() returns null) - it is an indication that the service 
 * has new data but it does not implement new data tracking.  The listener can then perform a blackboard query to get the latest data of interest (e.g. by artifact type).
 * 
 * By design, only a single type of artifacts can be contained in a single data event. 
 */
public class ServiceDataEvent {

    private String serviceName;
    private ARTIFACT_TYPE artifactType;
    private Collection<BlackboardArtifact> artifactIDs;
    
    /**
     * @param serviceName Module name
     * @param artifactType Type of artifact that was posted to blackboard
     */
    public ServiceDataEvent(String serviceName, ARTIFACT_TYPE artifactType) {
        this.serviceName = serviceName;
        this.artifactType = artifactType;
    }
    
    /**
     * @param serviceName Module name
     * @param artifactType Type of artifact that was posted to blackboard
     * @param artifactIDs List of specific artifact ID values that were added to blackboard
     */    
    public ServiceDataEvent(String serviceName, ARTIFACT_TYPE artifactType, Collection<BlackboardArtifact> artifactIDs) {
        this(serviceName, artifactType);
        this.artifactIDs = artifactIDs;
    }

    /**
     * get new artifact IDs associated with the event
     * @return Collection of artifact ids or null if not provided
     */
    public Collection<BlackboardArtifact> getArtifacts() {
        return artifactIDs;
    }

    /**
     * get artifact type of the new artifacts associated with the event
     * @return 
     */
    public ARTIFACT_TYPE getArtifactType() {
        return artifactType;
    }

    /**
     * get service name that created the artifacts and fired the event
     * @return 
     */
    public String getServiceName() {
        return serviceName;
    }
    
    
    
}
