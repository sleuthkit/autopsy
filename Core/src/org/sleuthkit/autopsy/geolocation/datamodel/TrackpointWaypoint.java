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
 * A wrapper class for TSK_GPS_TRACKPOINT artifacts.
 */
final class TrackpointWaypoint extends ArtifactWaypoint {

    @Messages({
        "TrackpointWaypoint_DisplayLabel=GPS Trackpoint"
    })

    /**
     * Construct a waypoint for trackpoints.
     * 
     * @throws GeoLocationDataException
     */
    TrackpointWaypoint(BlackboardArtifact artifact) throws GeoLocationDataException {
        super(artifact, getLabelFromArtifact(artifact), Waypoint.Type.TRACKPOINT);
    }

    /**
     * Returns a Label for a GPS_Trackpoint artifact. This function assumes the
     * calling function has already checked TSK_NAME.
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
            typeLabel = ArtifactUtils.getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME);
        }

        if (typeLabel == null || typeLabel.isEmpty()) {
            typeLabel = ArtifactUtils.getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FLAG);
        }

        if (typeLabel == null || typeLabel.isEmpty()) {
            typeLabel = Bundle.TrackpointWaypoint_DisplayLabel();
        }

        return typeLabel;
    }

}
