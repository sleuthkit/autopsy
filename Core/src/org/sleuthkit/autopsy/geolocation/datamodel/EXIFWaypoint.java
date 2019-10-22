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

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Waypoint wrapper class for TSK_METADATA_EXIF artifacts.
 */
final class EXIFWaypoint extends ArtifactWaypoint {

    /**
     * Construct a way point with the given artifact.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @throws TskCoreException
     */
    protected EXIFWaypoint(BlackboardArtifact artifact) throws TskCoreException {
        this(artifact, getImageFromArtifact(artifact));
    }

    /**
     * Private constructor to help with the construction of EXIFWaypoints.
     *
     * @param artifact Waypoint BlackboardArtifact
     * @param image    EXIF AbstractFile image
     *
     * @throws TskCoreException
     */
    private EXIFWaypoint(BlackboardArtifact artifact, AbstractFile image) throws TskCoreException {
        super(artifact,
                image != null ? image.getName() : "",
                AttributeUtils.getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED),
                image,
                Waypoint.Type.METADATA_EXIF);
    }

    /**
     * Gets the image from the given artifact.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @return AbstractFile image for this waypoint or null if one is not
     *         available
     *
     * @throws TskCoreException
     */
    private static AbstractFile getImageFromArtifact(BlackboardArtifact artifact) throws TskCoreException {
        BlackboardArtifact.ARTIFACT_TYPE artifactType = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());

        if (artifactType == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF) {
            return artifact.getSleuthkitCase().getAbstractFileById(artifact.getObjectID());
        }

        return null;
    }

}
