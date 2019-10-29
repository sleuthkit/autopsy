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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for simplifying the use of Waypoint Artifacts.
 */
final class ArtifactUtils {

    /**
     * Private constructor for this Utility class.
     */
    private ArtifactUtils() {

    }

    /**
     * Gets the list of attributes from the artifact and puts them into a map
     * with the ATRIBUTE_TYPE as the key.
     *
     * @param artifact BlackboardArtifact current artifact
     *
     * @return A Map of BlackboardAttributes for the given artifact with
     *         ATTRIBUTE_TYPE as the key.
     *
     * @throws GeoLocationDataException
     */
    static Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> getAttributesFromArtifactAsMap(BlackboardArtifact artifact) throws GeoLocationDataException {
        Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap = new HashMap<>();
        try {
            List<BlackboardAttribute> attributeList = artifact.getAttributes();
            for (BlackboardAttribute attribute : attributeList) {
                BlackboardAttribute.ATTRIBUTE_TYPE type = BlackboardAttribute.ATTRIBUTE_TYPE.fromID(attribute.getAttributeType().getTypeID());
                attributeMap.put(type, attribute);
            }
        } catch (TskCoreException ex) {
            throw new GeoLocationDataException("Unable to get attributes from artifact", ex);
        }

        return attributeMap;
    }
}
