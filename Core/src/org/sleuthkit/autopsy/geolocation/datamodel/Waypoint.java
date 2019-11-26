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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Representation of a Waypoint created from a BlackboardArtifact.
 *
 */
public class Waypoint {

    final private Long timestamp;
    final private Double longitude;
    final private Double latitude;
    final private Double altitude;
    final private String label;
    final private AbstractFile image;
    final private BlackboardArtifact artifact;
    final private Route route;

    // This list is not expected to change after construction. The 
    // constructor will take care of making an unmodifiable List
    final private List<Waypoint.Property> immutablePropertiesList;

    /**
     * This is a list of attributes that are already being handled by the
     * by getter functions.
     */
    static private BlackboardAttribute.ATTRIBUTE_TYPE[] ALREADY_HANDLED_ATTRIBUTES = {
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
     * Construct a waypoint with the given artifact.
     *
     * @param artifact BlackboardArtifact for this waypoint
     *
     * @throws GeoLocationDataException Exception will be thrown if artifact did
     *                                  not have a valid longitude and latitude.
     */
    Waypoint(BlackboardArtifact artifact) throws GeoLocationDataException {
        this(artifact,
                getAttributesFromArtifactAsMap(artifact));
    }

    /**
     * Constructor that initializes all of the member variables.
     *
     * @param artifact     BlackboardArtifact for this waypoint
     * @param label        String waypoint label
     * @param timestamp    Long timestamp, unix/java epoch seconds
     * @param latitude     Double waypoint latitude
     * @param longitude    Double waypoint longitude
     * @param altitude     Double waypoint altitude
     * @param image        AbstractFile image for waypoint, this maybe null
     * @param attributeMap A Map of attributes for the given artifact
     *
     * @throws GeoLocationDataException Exception will be thrown if artifact did
     *                                  not have a valid longitude and latitude.
     */
    Waypoint(BlackboardArtifact artifact, String label, Long timestamp, Double latitude, Double longitude, Double altitude, AbstractFile image, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap, Route route) throws GeoLocationDataException {
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
        this.route = null;

        immutablePropertiesList = Collections.unmodifiableList(createGeolocationProperties(attributeMap));
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
    private Waypoint(BlackboardArtifact artifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        this(artifact,
                getLabelFromArtifact(attributeMap),
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME).getValueLong() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE).getValueDouble() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE).getValueDouble() : null,
                attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE) != null ? attributeMap.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE).getValueDouble() : null,
                null, attributeMap, null);
    }

    /**
     * Get the BlackboardArtifact that this waypoint represents.
     *
     * @return BlackboardArtifact for this waypoint.
     */
    public BlackboardArtifact getArtifact() {
        return artifact;
    }

    /**
     * Interface to describe a waypoint. A waypoint is made up of a longitude,
     * latitude, label, timestamp, type, image and altitude.
     *
     * A good way point should have at minimum a longitude and latutude.
     *
     * @return Timestamp in java/unix epoch seconds or null if none was set.
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * Get the label for this point object.
     *
     * @return String label for the point or null if none was set
     */
    public String getLabel() {
        return label;
    }

    /**
     * Get the latitude for this point.
     *
     * @return Returns the latitude for the point
     */
    public Double getLatitude() {
        return latitude;
    }

    /**
     * Get the longitude for this point.
     *
     * @return Returns the longitude for the point
     */
    public Double getLongitude() {
        return longitude;
    }

    /**
     * Get the altitude for this point.
     *
     * @return Returns the altitude for the point or null if none was set
     */
    public Double getAltitude() {
        return altitude;
    }

    /**
     * Get the image for this waypoint.
     *
     * @return AbstractFile image or null if one was not set
     */
    public AbstractFile getImage() {
        return image;
    }

    /**
     * Gets an unmodifiable List of other properties that may be interesting to
     * this way point. The List will not include properties for which getter
     * functions exist.
     *
     * @return A List of waypoint properties
     */
    public List<Waypoint.Property> getOtherProperties() {
        return immutablePropertiesList;
    }
    
    /**
     * Returns the route that this waypoint is apart of .
     * 
     * @return The waypoint route or null if the waypoint is not apart of a route. 
     */
    public Route getRoute() {
        return route;
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
    static public List<Waypoint.Property> createGeolocationProperties(Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        List<Waypoint.Property> list = new ArrayList<>();

        Set<BlackboardAttribute.ATTRIBUTE_TYPE> keys = new HashSet<>(attributeMap.keySet());

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

    /**
     * Simple property class for waypoint properties that a purely
     * informational.
     */
    public static final class Property {

        private final String displayName;
        private final String value;

        /**
         * Construct a Property object.
         *
         * @param displayName String display name for property. Ideally not null
         *                    or empty string.
         * @param value       String value for property. Can be null.
         */
        private Property(String displayName, String value) {
            this.displayName = displayName;
            this.value = value;
        }

        /**
         * Get the display name for this property.
         *
         * @return String display name.
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Get the property value.
         *
         * @return String value.
         */
        public String getValue() {
            return value;
        }
    }
}
