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

import java.util.Collection;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * representation of an event fired off by services when they have posted new data
 * of specific type
 * additionally, new artifact ids can be provided
 */
public class ServiceDataEvent {

    private String serviceName;
    private ARTIFACT_TYPE artifactType;
    private Collection<BlackboardArtifact> artifactIDs;
    
    public ServiceDataEvent(String serviceName, ARTIFACT_TYPE artifactType) {
        this.serviceName = serviceName;
        this.artifactType = artifactType;
    }
    
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
