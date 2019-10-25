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

import java.util.Collections;
import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * Representation of a Waypoint created from a BlackboardArtifact.
 *
 */
class ArtifactWaypoint implements Waypoint {

    final private Long timestamp;
    final private Double longitude;
    final private Double latitude;
    final private Double altitude;
    final private String label;
    final private AbstractFile image;
    final private BlackboardArtifact artifact;
    final private Waypoint.Type type;

    // This list is not expected to change after construction so the 
    // constructor will take care of creating an unmodifiable List
    final private List<Waypoint.Property> immutablePropertiesList;

    /**
     * Construct a simple waypoint with the given artifact and assign the given
     * type.
     *
     * This constructor is for use with artifacts that use the basic attributes
     * of: TSK_NAME TSK_GEO_LONGITUDE TSK_GEO_LATITUDE TSK_GEO_ALITUDE
     * TSK_DATETIME
     *
     * @param artifact BlackboardArtifact for this waypoint
     * @param type     Waypoint type
     *
     * @throws GeoLocationDataException
     */
    protected ArtifactWaypoint(BlackboardArtifact artifact, Waypoint.Type type) throws GeoLocationDataException {
        this(artifact,
                getLabelFromArtifact(artifact),
                type);
    }

    /**
     * For use by subclasses that want to customize the label, but use the basic
     * attributes of: TSK_GEO_LONGITUDE TSK_GEO_LATITUDE TSK_GEO_ALITUDE
     * TSK_DATETIME
     *
     * @param artifact BlackboardArtifact for this waypoint
     * @param label    String label for this waypoint
     * @param type     Waypoint type
     *
     * @throws GeoLocationDataException
     */
    protected ArtifactWaypoint(BlackboardArtifact artifact, String label, Waypoint.Type type) throws GeoLocationDataException {
        this(artifact,
                label,
                getTimestampFromArtifact(artifact),
                null,
                type);
    }

    /**
     * Constructor for use by Waypoint subclasses that want to customize the
     * label, specify the timestamp or supply and image.
     *
     * Uses the following attributes to set longitude, latitude, altitude:
     * TSK_GEO_LONGITUDE TSK_GEO_LATITUDE TSK_GEO_ALITUDE
     *
     * @param artifact  BlackboardArtifact for this waypoint
     * @param label     String waypoint label
     * @param timestamp Long timestamp, epoch seconds
     * @param image     AbstractFile image for waypoint, this maybe null
     * @param type      Waypoint.Type value for waypoint
     *
     * @throws GeoLocationDataException
     */
    protected ArtifactWaypoint(BlackboardArtifact artifact, String label, Long timestamp, AbstractFile image, Waypoint.Type type) throws GeoLocationDataException {
        this(artifact,
                label,
                timestamp,
                ArtifactUtils.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE),
                ArtifactUtils.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE),
                ArtifactUtils.getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE),
                image,
                type);
    }

    /**
     * Private constructor that sets all of the member variables.
     *
     * @param artifact  BlackboardArtifact for this waypoint
     * @param label     String waypoint label
     * @param timestamp Long timestamp, epoch seconds
     * @param latitude  Double waypoint latitude
     * @param longitude Double waypoint longitude
     * @param altitude  Double waypoint altitude
     * @param image     AbstractFile image for waypoint, this maybe null
     * @param type      Waypoint.Type value for waypoint
     *
     * @throws GeoLocationDataException
     */
    private ArtifactWaypoint(BlackboardArtifact artifact, String label, Long timestamp, Double latitude, Double longitude, Double altitude, AbstractFile image, Waypoint.Type type) throws GeoLocationDataException {
        this.artifact = artifact;
        this.label = label;
        this.type = type;
        this.image = image;
        this.timestamp = timestamp;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;

        immutablePropertiesList = Collections.unmodifiableList(GeolocationUtils.getOtherGeolocationProperties(artifact));
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
    public List<Waypoint.Property> getOtherProperties() {
        return immutablePropertiesList;
    }

    /**
     * Get the timestamp attribute based on type for the given artifact.
     *
     * @param artifact BlackboardArtifact for waypoint
     *
     * @return Long timestamp or null if a value was not found.
     *
     * @throws GeoLocationDataException
     */
    private static Long getTimestampFromArtifact(BlackboardArtifact artifact) throws GeoLocationDataException {
        if (artifact == null) {
            return null;
        }

        return ArtifactUtils.getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
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
     * @throws GeoLocationDataException
     */
    private static String getLabelFromArtifact(BlackboardArtifact artifact) throws GeoLocationDataException {

        String typeLabel = ArtifactUtils.getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
        if (typeLabel == null) {
            typeLabel = "";
        }
        return typeLabel;
    }
}
