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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Waypoint wrapper class for TSK_METADATA_EXIF artifacts.
 */
final class EXIFWaypoint extends Waypoint {

    /**
     * Construct a way point with the given artifact.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @throws GeoLocationDataException
     */
    EXIFWaypoint(BlackboardArtifact artifact) throws GeoLocationDataException {
        this(artifact, getAttributesFromArtifactAsMap(artifact), getImageFromArtifact(artifact));
    }

    /**
     * Constructs new waypoint using the given artifact and attribute map.
     *
     * @param artifact     Waypoint BlackboardArtifact
     * @param attributeMap Map of artifact attributes
     * @param image        EXIF AbstractFile image
     *
     * @throws GeoLocationDataException
     */
    private EXIFWaypoint(BlackboardArtifact artifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap, AbstractFile image) throws GeoLocationDataException {
        super(artifact,
                image != null ? image.getName() : "",
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED).getValueLong() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE).getValueDouble() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE).getValueDouble() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE).getValueDouble() : null,
                image, attributeMap, null);
    }

    /**
     * Gets the image from the given artifact.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @return AbstractFile image for this waypoint or null if one is not
     *         available
     *
     * @throws GeoLocationDataException
     */
    private static AbstractFile getImageFromArtifact(BlackboardArtifact artifact) throws GeoLocationDataException {
        AbstractFile abstractFile = null;
        BlackboardArtifact.ARTIFACT_TYPE artifactType = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());
        if (artifactType == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF) {
            try {
                abstractFile = artifact.getSleuthkitCase().getAbstractFileById(artifact.getObjectID());
            } catch (TskCoreException ex) {
                throw new GeoLocationDataException(String.format("Unable to getAbstractFileByID for artifactID: %d", artifact.getArtifactID()), ex);
            }
        }

        return abstractFile;
    }
}
