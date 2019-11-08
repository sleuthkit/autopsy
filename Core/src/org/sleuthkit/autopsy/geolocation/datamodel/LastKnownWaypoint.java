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

import java.util.Map;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * A Last Known Location Waypoint object.
 */
final class LastKnownWaypoint extends Waypoint {

    /**
     * Constructs a new waypoint.
     *
     * @param artifact BlackboardArtifact from which to construct the waypoint
     *
     * @throws GeoLocationDataException
     */
    LastKnownWaypoint(BlackboardArtifact artifact) throws GeoLocationDataException {
        this(artifact, getAttributesFromArtifactAsMap(artifact));
    }

    /**
     * Constructs a new waypoint with the given artifact and attribute map.
     *
     * @param artifact     BlackboardArtifact from which to construct the
     *                     waypoint
     * @param attributeMap Map of artifact attributes
     *
     * @throws GeoLocationDataException
     */
    private LastKnownWaypoint(BlackboardArtifact artifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        super(artifact,
                getLabelFromArtifact(attributeMap),
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME).getValueLong() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE).getValueDouble() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE).getValueDouble() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE).getValueDouble() : null,
                null, attributeMap, null);
    }

    /**
     * Gets the label for a TSK_LAST_KNOWN_LOCATION.
     *
     * @param attributeMap Map of artifact attributes for this waypoint
     *
     * @return String value from attribute TSK_NAME or LastKnownWaypoint_Label
     *
     * @throws GeoLocationDataException
     */
    @Messages({
    "LastKnownWaypoint_Label=Last Known Location",})
    private static String getLabelFromArtifact(Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        BlackboardAttribute attribute = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
        String label = attribute.getDisplayString();

        if (label == null || label.isEmpty()) {
            label = Bundle.LastKnownWaypoint_Label();
        }

        return label;
    }
}
