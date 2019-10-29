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
import java.util.Map;
import java.util.Set;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * GeolocationUtilis class for common shared between Routes and Waypoints.
 *
 */
final class GeolocationUtils {

    /**
     * This is a list of attributes that are already being handled by the
     * waypoint classes and will have get functions.
     */
    private static final BlackboardAttribute.ATTRIBUTE_TYPE[] ALREADY_HANDLED_ATTRIBUTES = {
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END,};

    /**
     * This is a Utility class that should not be constructed.
     */
    private GeolocationUtils() {

    }

    /**
     * Get a list of Waypoint.Property objects for the given artifact. This list
     * will not include attributes that the Waypoint interfact has get functions
     * for.
     *
     * @param artifact Blackboard artifact to get attributes\properties from
     *
     * @return A List of Waypoint.Property objects
     *
     * @throws GeoLocationDataException
     */
    static List<Waypoint.Property> createGeolocationProperties(Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        List<Waypoint.Property> list = new ArrayList<>();

        Set<BlackboardAttribute.ATTRIBUTE_TYPE> keys = attributeMap.keySet();

        for (BlackboardAttribute.ATTRIBUTE_TYPE type : ALREADY_HANDLED_ATTRIBUTES) {
            keys.remove(type);
        }

        for (BlackboardAttribute.ATTRIBUTE_TYPE type : keys) {
            String key = type.getDisplayName();
            String value = attributeMap.get(type).getDisplayString();

            list.add(new Waypoint.Property(key, value));
        }
        return list;
    }
}
