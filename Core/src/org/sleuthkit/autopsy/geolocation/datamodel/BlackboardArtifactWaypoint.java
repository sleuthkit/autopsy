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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Representation of a Waypoint created from a BlackboardArtifact.
 *
 */
public class BlackboardArtifactWaypoint implements Waypoint {

    final private Long timestamp;
    final private Double longitude;
    final private Double latitude;
    final private Double altitude;
    final private String label;
    final private AbstractFile image;
    final private BlackboardArtifact artifact;
    final private Waypoint.Type type;
    final private Map<String, String> otherAttributesMap;
    
    @Messages({
        "BlackboardArtifactWaypoint_Last_Known_Label=Last Known Location",
        "BlackboardArtifactWaypoint_GPS_Trackpoint=GPS Trackpoint",
        "BlackboardArtifactWaypoint_GPS_Search=GPS Search"
    })

    /**
     * Constructs a Waypoint from a BlackboardArtifact
     *
     * @param artifact BlackboardArtifact with which to create the waypoint
     *
     * @throws TskCoreException
     */
    protected BlackboardArtifactWaypoint(BlackboardArtifact artifact) throws TskCoreException {
        this.artifact = artifact;
        timestamp = getTimestampFromArtifact(artifact);
        longitude = GeolocationUtility.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE);
        latitude = GeolocationUtility.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE);
        altitude = GeolocationUtility.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE);
        image = getImageFromArtifact(artifact);
        label = getLabelFromArtifact(artifact);
        type = getTypeFromArtifact(artifact);
        otherAttributesMap = GeolocationUtility.getOtherGeolocationAttributes(artifact);
    }

    /**
     * Get the BlackboardArtifact that this waypoint represents.
     *
     * @return BlackboardArtifact for this waypoint.
     */
    BlackboardArtifact getArtifact() {
        return artifact;
    }

    @Override
    public Long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public Double getLatitude() {
        return latitude;
    }

    @Override
    public Double getLongitude() {
        return longitude;
    }

    @Override
    public Double getAltitude() {
        return altitude;
    }

    @Override
    public AbstractFile getImage() {
        return image;
    }

    @Override
    public Waypoint.Type getType() {
        return type;
    }

    @Override
    public Map<String, String> getOtherProperties() {
        return otherAttributesMap;
    }

    /**
     * Get the timestamp attribute based on type for the given artifact.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @return Long timestamp or null if a value was not found.
     *
     * @throws TskCoreException
     */
    private Long getTimestampFromArtifact(BlackboardArtifact artifact) throws TskCoreException {
        if (artifact == null) {
            return null;
        }

        BlackboardArtifact.ARTIFACT_TYPE artifactType = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());

        if (artifactType == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF) {
           return GeolocationUtility.getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED);
        }

        return GeolocationUtility.getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
    }

    /**
     * Gets the image from the given artifact, this is really only applicable to
     * the artifact type TSK_METADATA_EXIF
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @return AbstractFile image for this waypoint or null if one is not
     *         available
     *
     * @throws TskCoreException
     */
    private AbstractFile getImageFromArtifact(BlackboardArtifact artifact) throws TskCoreException {
        if (artifact == null) {
            return null;
        }

        BlackboardArtifact.ARTIFACT_TYPE artifactType = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());

        switch (artifactType) {
            case TSK_METADATA_EXIF:
                return artifact.getSleuthkitCase().getAbstractFileById(artifact.getObjectID());
            default:
                return null;
        }
    }

    /**
     * Gets the label for this waypoint based on the artifact type.
     *
     * This is the original waypoint naming code from the KML report, we may
     * what to thinki about better ways to name some of the point.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @return Returns a label for the waypoint based on artifact type, or empty
     *         string if no label was found.
     *
     * @throws TskCoreException
     */
    private String getLabelFromArtifact(BlackboardArtifact artifact) throws TskCoreException {
        String typeLabel = getLabelFromImage();
        if (typeLabel != null && !typeLabel.isEmpty()) {
            return typeLabel;
        }

        BlackboardArtifact.ARTIFACT_TYPE artifactType = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());
        typeLabel = GeolocationUtility.getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
        if (typeLabel == null || typeLabel.isEmpty()) {
            switch (artifactType) {
                case TSK_GPS_SEARCH:
                    typeLabel = getLabelForSearch(artifact);
                    break;
                case TSK_GPS_TRACKPOINT:
                    typeLabel = getLabelforTrackpoint(artifact);
                    break;
                case TSK_GPS_LAST_KNOWN_LOCATION:
                    typeLabel = Bundle.BlackboardArtifactWaypoint_Last_Known_Label();
                    break;
                default:
                    typeLabel = "";
                    break;
            }
        }
        return typeLabel;
    }

    /**
     * Returns a Label for a GPS_Trackpoint artifact. This function assumes the
     * calling function has already checked TSK_NAME.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @return String label for the artifacts way point.
     *
     * @throws TskCoreException
     */
    private String getLabelforTrackpoint(BlackboardArtifact artifact) throws TskCoreException {
        String typeLabel = GeolocationUtility.getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME);
        if (typeLabel == null || typeLabel.isEmpty()) {
            typeLabel = GeolocationUtility.getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FLAG);
        }

        if (typeLabel == null || typeLabel.isEmpty()) {
            typeLabel = Bundle.BlackboardArtifactWaypoint_GPS_Trackpoint();
        }

        return typeLabel;
    }

    /**
     * Returns a Label for a GPS_SEARCH artifact. This function assumes the
     * calling function has already checked TSK_NAME.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @return String label for the artifacts way point.
     *
     * @throws TskCoreException
     */
    private String getLabelForSearch(BlackboardArtifact artifact) throws TskCoreException {
        String typeLabel = GeolocationUtility.getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION);
        if (typeLabel == null || typeLabel.isEmpty()) {
            typeLabel = Bundle.BlackboardArtifactWaypoint_GPS_Search();
        }

        return typeLabel;
    }
    
    /**
     * Returns a Label from an image.
     * 
     * @return String label for waypoint with image or null.
     */
    private String getLabelFromImage() {
        if (getImage() != null) {
            return getImage().getName();
        } else {
            return null;
        }
    }

    /**
     * Gets the type of waypoint based on the artifact.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @return A waypoint type.
     */
    private Waypoint.Type getTypeFromArtifact(BlackboardArtifact artifact) {
        BlackboardArtifact.ARTIFACT_TYPE artifactType = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());

        switch (artifactType) {
            case TSK_GPS_LAST_KNOWN_LOCATION:
                return Waypoint.Type.LAST_KNOWN_LOCATION;
            case TSK_GPS_TRACKPOINT:
                return Waypoint.Type.TRACKPOINT;
            case TSK_GPS_SEARCH:
                return Waypoint.Type.SEARCH;
            case TSK_GPS_BOOKMARK:
                return Waypoint.Type.BOOKMARK;
            case TSK_METADATA_EXIF:
                return Waypoint.Type.METADATA_EXIF;
            default:
                return Waypoint.Type.UNKNOWN;
        }
    }
}
