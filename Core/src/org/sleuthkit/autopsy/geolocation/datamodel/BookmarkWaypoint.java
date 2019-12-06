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
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * Class to represent TSK_GPS_BOOKMARK waypoints
 *
 */
final class BookmarkWaypoint extends Waypoint {

    /**
     * Constructs a new BookmarkWaypoint from the given artifact.
     *
     * @param artifact BlackboardArtifact for this waypoint
     *
     * @throws GeoLocationDataException
     */
    BookmarkWaypoint(BlackboardArtifact artifact) throws GeoLocationDataException {
        this(artifact, getAttributesFromArtifactAsMap(artifact));
    }

    /**
     * Constructs a new BookmarkWaypoint.
     *
     * @param artifact     BlackboardArtifact for this waypoint
     * @param attributeMap A Map of the BlackboardAttributes for the given
     *                     artifact.
     *
     * @throws GeoLocationDataException
     */
    private BookmarkWaypoint(BlackboardArtifact artifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        super(artifact,
                getLabelFromArtifact(attributeMap),
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME).getValueLong() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE).getValueDouble() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE).getValueDouble() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE).getValueDouble() : null,
                null, attributeMap, null);
    }

    /**
     * Gets the label for this waypoint.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @return Returns a label for the waypoint, or empty string if no label was
     *         found.
     */
    private static String getLabelFromArtifact(Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) {
        BlackboardAttribute attribute = attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
        if (attribute != null) {
            return attribute.getDisplayString();
        }

        return "";
    }

}
