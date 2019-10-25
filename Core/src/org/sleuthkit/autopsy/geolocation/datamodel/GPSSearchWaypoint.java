/*
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

import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * A GPSSearchWaypoint is a subclass of ArtifactWaypoint.
 */
final class GPSSearchWaypoint extends ArtifactWaypoint {

    @Messages({
        "SearchWaypoint_DisplayLabel=GPS Search"
    })

    /**
     * Construct a GPS Search waypoint.
     * 
     * @throws GeoLocationDataException
     */
    GPSSearchWaypoint(BlackboardArtifact artifact) throws GeoLocationDataException {
        super(artifact, getLabelFromArtifact(artifact), Waypoint.Type.SEARCH);
    }

    /**
     * Returns a Label for a GPS_SEARCH artifact.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @return String label for the artifacts way point.
     *
     * @throws GeoLocationDataException
     */
    private static String getLabelFromArtifact(BlackboardArtifact artifact) throws GeoLocationDataException {
        String typeLabel = ArtifactUtils.getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);

        if (typeLabel == null || typeLabel.isEmpty()) {
            typeLabel = ArtifactUtils.getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION);
        }

        if (typeLabel == null || typeLabel.isEmpty()) {
            typeLabel = Bundle.SearchWaypoint_DisplayLabel();
        }

        return typeLabel;
    }

}
