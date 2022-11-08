/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
import org.sleuthkit.datamodel.Content;
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
    final private GeoPath parentGeoPath;
    final private Content content;

    final private List<Waypoint.Property> propertiesList;

    /**
     * This is a list of attributes that are already being handled by the by
     * getter functions.
     */
    static final BlackboardAttribute.ATTRIBUTE_TYPE[] ALREADY_HANDLED_ATTRIBUTES = {
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
    Waypoint(BlackboardArtifact artifact, String label, Long timestamp, Double latitude, Double longitude, Double altitude, AbstractFile image, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap, GeoPath parentGeoPath) throws GeoLocationDataException {
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
        this.parentGeoPath = parentGeoPath;

        propertiesList = createGeolocationProperties(attributeMap);
        try {
            content = artifact.getSleuthkitCase().getContentById(artifact.getObjectID());
        } catch (TskCoreException ex) {
            throw new GeoLocationDataException(String.format("Failed to get contend for artifact id (%d)", artifact.getId()), ex);
        }
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
        return Collections.unmodifiableList(propertiesList);
    }

    /**
     * Returns the GeoPath that this waypoint is apart of .
     *
     * @return The waypoint route or null if the waypoint is not apart of a
     *         route.
     */
    public GeoPath getParentGeoPath() {
        return parentGeoPath;
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
                try{
                    BlackboardAttribute.ATTRIBUTE_TYPE type = BlackboardAttribute.ATTRIBUTE_TYPE.fromID(attribute.getAttributeType().getTypeID());
                    attributeMap.put(type, attribute);
                } catch(IllegalArgumentException ex) {
                    // This was thrown due to a custom attribute that geolocation
                    // does not currently support.
                }
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
     * @param attributeMap Attributes for the given artifact
     *
     * @return A List of Waypoint.Property objects
     *
     * @throws GeoLocationDataException
     */
    static List<Waypoint.Property> createGeolocationProperties(Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attributeMap) throws GeoLocationDataException {
        List<Waypoint.Property> list = new ArrayList<>();
        
        if(attributeMap != null) {

            Set<BlackboardAttribute.ATTRIBUTE_TYPE> keys = new HashSet<>(attributeMap.keySet());

            for (BlackboardAttribute.ATTRIBUTE_TYPE type : ALREADY_HANDLED_ATTRIBUTES) {
                keys.remove(type);
            }

            for (BlackboardAttribute.ATTRIBUTE_TYPE type : keys) {
                // Don't add JSON properties to this list.
                if (type.getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.JSON) {
                    continue;
                }
                String key = type.getDisplayName();
                String value = attributeMap.get(type).getDisplayString();

                list.add(new Waypoint.Property(key, value));
            }
        }
        return list;
    }
    
    public Content getContent() {
        return content;
    }
   
    /**
     * Simple property class for waypoint properties that a purely
     * informational.
     */
    public final static class Property {

        private final String displayName;
        private final String value;

        /**
         * Construct a Property object.
         *
         * @param displayName String display name for property. Ideally not null
         *                    or empty string.
         * @param value       String value for property. Can be null.
         */
        Property(String displayName, String value) {
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
