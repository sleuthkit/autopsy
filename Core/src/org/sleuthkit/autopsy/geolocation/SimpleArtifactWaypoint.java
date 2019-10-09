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

import org.jxmapviewer.viewer.GeoPosition;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * This is a wrapper for artifacts with the basic set of geolocation attributes.
 */
class SimpleArtifactWaypoint extends DefaultArtifactWaypoint {

    SimpleArtifactWaypoint(BlackboardArtifact artifact) {
        super(artifact);
        initWaypoint();
    }

    /**
     * Initialize the waypoint basic information.
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

        Long datetime = GeolocationUtilities.getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
        if (datetime != null) {
            setTimestamp(datetime * 1000);
        }
    }
}
