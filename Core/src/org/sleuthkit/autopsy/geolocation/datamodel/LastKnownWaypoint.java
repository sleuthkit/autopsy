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
 * A Last Know Location Waypoint object.
 */
@Messages({
    "LastKnownWaypoint_Label=Last Known Location",})
final class LastKnownWaypoint extends ArtifactWaypoint {

    protected LastKnownWaypoint(BlackboardArtifact artifact) throws GeoLocationDataException {
        super(artifact, getLabelFromArtifact(artifact));
    }

    /**
     * Gets the label for a TSK_LAST_KNOWN_LOCATION.
     *
     * @param artifact BlackboardArtifact to get label from
     *
     * @return String value from attribute TSK_NAME or LastKnownWaypoint_Label
     *
     * @throws GeoLocationDataException
     */
    private static String getLabelFromArtifact(BlackboardArtifact artifact) throws GeoLocationDataException {
        String label = ArtifactUtils.getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);

        if (label == null || label.isEmpty()) {
            label = Bundle.LastKnownWaypoint_Label();
        }

        return label;
    }
}
