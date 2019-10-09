/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.geolocation;

import java.util.logging.Level;
import org.jxmapviewer.viewer.GeoPosition;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * EXIF way point object
 */
final class EXIFWaypoint extends DefaultArtifactWaypoint{
    
    private static final Logger logger = Logger.getLogger(EXIFWaypoint.class.getName());
    
    private AbstractFile imageFile;
    
    /**
     * Construct a EXIF way point
     * @param artifact 
     */
    EXIFWaypoint(BlackboardArtifact artifact) {
        super(artifact);
        initWaypoint();
    }
    
    /**
     * Initialize the way point.
     */
    private void initWaypoint() {
        BlackboardArtifact artifact = getArtifact();

        Double longitude = GeolocationUtilities.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE);
        Double latitude = GeolocationUtilities.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE);

        if (longitude != null && latitude != null) {
            setPosition(new GeoPosition(latitude, longitude));
        } else {
            setPosition(null);
            // No need to bother with other attributes if there are no
            // location parameters
            return;
        }

        Long datetime = GeolocationUtilities.getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED);
        if (datetime != null) {
            setTimestamp(datetime * 1000);
        }
        
        try {
            imageFile = artifact.getSleuthkitCase().getAbstractFileById(artifact.getObjectID());
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Failed to getAbstractFileByID for %d ", artifact.getObjectID()), ex);
        }
    }
}
