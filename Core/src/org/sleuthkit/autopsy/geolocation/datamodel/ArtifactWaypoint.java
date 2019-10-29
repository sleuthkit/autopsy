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
import java.util.Map;
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

    // This list is not expected to change after construction so the 
    // constructor will take care of creating an unmodifiable List
    final private List<Waypoint.Property> immutablePropertiesList;

    /**
     * Construct a waypoint with the given artifact.
     *
     * @param artifact BlackboardArtifact for this waypoint
     *
     * @throws GeoLocationDataException Exception will be thrown if artifact did
     *                                  not have a valid longitude and latitude.
     */
    protected ArtifactWaypoint(BlackboardArtifact artifact) throws GeoLocationDataException {
        this(artifact,
                ArtifactUtils.getAttributesFromArtifactAsMap(artifact));
    }

    /**
     * Constructor that sets all of the member variables.
     *
     * @param artifact     BlackboardArtifact for this waypoint
     * @param label        String waypoint label
     * @param timestamp    Long timestamp, epoch seconds
     * @param latitude     Double waypoint latitude
     * @param longitude    Double waypoint longitude
     * @param altitude     Double waypoint altitude
     * @param image        AbstractFile image for waypoint, this maybe null
     * @param type         Waypoint.Type value for waypoint
     * @param attributeMap A Map of attributes for the given artifact
     *
     * @throws GeoLocationDataException Exception will be thrown if artifact did
     *                                  not have a valid longitude and latitude.
     */
    protected ArtifactWaypoint(BlackboardArtifact artifact, String label, Long timestamp, Double latitude, Double longitude, Double altitude, AbstractFile image, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        if (longitude == null || latitude == null) {
            throw new GeoLocationDataException("Invalid waypoint, null value passed for longitude or latitude");
        }

        this.artifact = artifact;
        this.label = label;
        this.image = image;
        this.timestamp = timestamp;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;

        immutablePropertiesList = Collections.unmodifiableList(GeolocationUtils.createGeolocationProperties(attributeMap));
    }

    /**
     * Constructs a new ArtifactWaypoint.
     *
     * @param artifact     BlackboardArtifact for this waypoint
     * @param attributeMap A Map of the BlackboardAttributes for the given
     *                     artifact.
     *
     * @throws GeoLocationDataException
     */
    private ArtifactWaypoint(BlackboardArtifact artifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        this(artifact,
                getLabelFromArtifact(attributeMap),
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME).getValueLong() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE).getValueDouble() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE).getValueDouble() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE).getValueDouble() : null,
                null, attributeMap);
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
    public List<Waypoint.Property> getOtherProperties() {
        return immutablePropertiesList;
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
