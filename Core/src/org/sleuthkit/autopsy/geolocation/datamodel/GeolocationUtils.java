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

import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * GeolocationUtilis class for common to be share across in the package
 *
 */
final class GeolocationUtils {

    /**
     * This is a list of attributes that are related to a geolocation waypoint
     * but are for information\artifact properties purpose. They are not needed
     * for the placement of a point on a map;
     */
    private static final BlackboardAttribute.ATTRIBUTE_TYPE[] OTHER_GEO_ATTRIBUTES = {
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_VELOCITY,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_BEARING,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_HPRECISION,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_VPRECISION,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_MAPDATUM,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_SOURCE,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FLAG,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL
    };

    /**
     * This is a Utility class that should not be constructed.
     */
    private GeolocationUtils() {

    }

    /**
     * Get a list of Waypoint.Property objects for the given artifact.
     *
     * @param artifact Blackboard artifact to get attributes\properties from
     *
     * @return A List of Waypoint.Property objects
     *
     * @throws TskCoreException
     */
    static List<Waypoint.Property> getOtherGeolocationProperties(BlackboardArtifact artifact) throws TskCoreException {
        List<Waypoint.Property> list = new ArrayList<>();

        for (BlackboardAttribute.ATTRIBUTE_TYPE type : OTHER_GEO_ATTRIBUTES) {
            String key = type.getDisplayName();
            String value = AttributeUtils.getString(artifact, type);

            if (value == null) {
                value = "";
            }

            list.add(new Waypoint.Property(key, value));
        }

        return list;
    }
}
