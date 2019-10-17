/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.geolocation.datamodel;

import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * A basic Artifact point for artifacts that use the following attributes:
 * TSK_GEO_LONGITUDE
 * TSK_GEO_LATITUDE
 * TSK_GEO_ALTITUDE
 * TSK_DATETIME
 * 
 */
public class SimplePoint extends DefaultPoint{
    
    /**
     * Construct a simple point object.
     * 
     * @param artifact 
     */
    SimplePoint(BlackboardArtifact artifact) {
        super(artifact);
    }
    
    @Override
    public void initPoint() throws TskCoreException{        
        setLongitude(getDouble(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE));
        setLatitude(getDouble(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE));
        setAltitude(getDouble(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE));
        setTimestamp(getLong(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME));
   
        setDetails(getDetailsFromArtifact());
        setLabel(getLabelBasedOnType());
    }
    
    /**
     * Create the point label based on the artifact type.
     * 
     * This code needs to be revisited to make sure these back up labels make
     * sense.
     * 
     * @return A label for the point.
     * 
     * @throws TskCoreException 
     */
    String getLabelBasedOnType() throws TskCoreException{
        String label = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
        
        if(label == null || label.isEmpty()) {
            if (getArtifact().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH.getTypeID()) {
                label = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION);
                if(label == null || label.isEmpty()) {
                    label = "GPS Search";
                }
            } else if (getArtifact().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT.getTypeID()) {
                label = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME);
                if(label == null || label.isEmpty()) {
                    label = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FLAG);
                }
                
                if(label == null || label.isEmpty()) {
                    label = "GPS Trackpoint";
                }
            } else if (getArtifact().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION.getTypeID()) {
                label = "Last Known Location";
            }
        }
        
        return label;
    }
}
