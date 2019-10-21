/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.geolocation.datamodel;

import java.util.HashMap;
import java.util.Map;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 *
 */
public class GeolocationUtility {

    private static final String DEFAULT_COORD_FORMAT = "%.2f, %.2f";

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

    static public String getFormattedCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return "";
        }

        return String.format(DEFAULT_COORD_FORMAT, latitude, longitude);
    }

    /**
     * Helper function for getting a String attribute from an artifact. This
     * will work for all attributes
     *
     * @param artifact      The BlackboardArtifact to get the attributeType
     * @param attributeType BlackboardAttribute attributeType
     *
     * @return String value for the given attribute or null if attribute was not
     *         set for the given artifact
     *
     * @throws TskCoreException
     */
    static protected String getString(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE attributeType) throws TskCoreException {
        if (artifact == null) {
            return null;
        }

        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(attributeType));
        return (attribute != null ? attribute.getDisplayString() : null);
    }

    /**
     * Helper function for getting a Double attribute from an artifact.
     *
     * @param artifact      The BlackboardArtifact to get the attributeType
     * @param attributeType BlackboardAttribute attributeType
     *
     * @return Double value for the given attribute.
     *
     * @throws TskCoreException
     */
    static protected Double getDouble(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE attributeType) throws TskCoreException {
        if (artifact == null) {
            return null;
        }

        if (attributeType.getValueType() != BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DOUBLE) {
            return null;
        }

        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(attributeType));
        return (attribute != null ? attribute.getValueDouble() : null);
    }

    /**
     * Helper function for getting a Long attribute from an artifact.
     *
     * @param artifact      The BlackboardArtifact to get the attributeType
     * @param attributeType BlackboardAttribute attributeType
     *
     * @return Long value for the given attribute.
     *
     * @throws TskCoreException
     */
    static protected Long getLong(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE attributeType) throws TskCoreException {
        if (artifact == null) {
            return null;
        }

        if (attributeType.getValueType() != BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG
                || attributeType.getValueType() != BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
            return null;
        }

        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(attributeType));
        return (attribute != null ? attribute.getValueLong() : null);
    }

    /**
     * Helper function for getting a Integer attribute from an artifact.
     *
     * @param artifact      The BlackboardArtifact to get the attributeType
     * @param attributeType BlackboardAttribute attributeType
     *
     * @return Integer value for the given attribute.
     *
     * @throws TskCoreException
     */
    static protected Integer getInteger(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE attributeType) throws TskCoreException {
        if (artifact == null) {
            return null;
        }

        if (attributeType.getValueType() != BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.INTEGER) {
            return null;
        }

        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(attributeType));
        return (attribute != null ? attribute.getValueInt() : null);
    }

    /**
     * Helper function for consistently formatting the timestamp.
     *
     * @param type BlackboardAttribute type
     *
     * @return The timestamp value formatted as string, or empty string if no
     *         timestamp is available.
     *
     * @throws TskCoreException
     */
    static protected String getFormattedTimestamp(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE attributeType) throws TskCoreException {
        if (artifact == null) {
            return null;
        }

        if (attributeType.getValueType() != BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
            return null;
        }

        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(attributeType));
        return (attribute != null ? attribute.getDisplayString() : null);
    }

    /**
     * Returns a Map of formatted artifact attributes for geolocation artifacts.
     * The map key will be the display string off the attribute, the value will
     * be either a formatted value or a an empty string.
     *
     * @param artifact
     *
     * @return
     */
    static protected Map<String, String> getOtherGeolocationAttributes(BlackboardArtifact artifact) throws TskCoreException {
        Map<String, String> map = new HashMap<>();

        for (BlackboardAttribute.ATTRIBUTE_TYPE type : OTHER_GEO_ATTRIBUTES) {
            String key = type.getDisplayName();
            String value = getString(artifact, type);

            if (value == null) {
                value = "";
            }

            map.put(key, value);
        }

        return map;
    }

}
