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
 * 
 */
public class SimplePoint extends DefaultPoint{
    
    SimplePoint(BlackboardArtifact artifact) {
        super(artifact);
    }
    
    @Override
    public void initPosition() throws TskCoreException{        
        setLongitude(getDouble(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE));
        setLatitude(getDouble(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE));
        setAltitude(getDouble(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE));
        setTimestamp(getLong(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME));
   
        setDetails(getDetailsFromArtifact());
        setLabel(getLabelBasedOnType());
    }
    
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
