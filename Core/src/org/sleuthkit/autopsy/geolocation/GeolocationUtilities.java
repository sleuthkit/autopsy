/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.geolocation;

import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Helper function for managing the getting artifact information.  These functions
 * were grabbed from KMLReport.
 * 
 */
class GeolocationUtilities {
    private static final Logger logger = Logger.getLogger(GeolocationUtilities.class.getName());
    /**
     * Get a Double from an artifact if it exists, return null otherwise.
     *
     * @param artifact The artifact to query
     * @param type     The attribute type we're looking for
     *
     * @return The Double if it exists, or null if not
     */
    static Double getDouble(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) {
        Double returnValue = null;
        try {
            BlackboardAttribute bba = artifact.getAttribute(new BlackboardAttribute.Type(type));
            if (bba != null) {
                Double value = bba.getValueDouble();
                returnValue = value;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting Double value: " + type.toString(), ex); //NON-NLS
        }
        return returnValue;
    }

    /**
     * Get a Long from an artifact if it exists, return null otherwise.
     *
     * @param artifact The artifact to query
     * @param type     The attribute type we're looking for
     *
     * @return The Long if it exists, or null if not
     */
    static Long getLong(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) {
        Long returnValue = null;
        try {
            BlackboardAttribute bba = artifact.getAttribute(new BlackboardAttribute.Type(type));
            if (bba != null) {
                Long value = bba.getValueLong();
                returnValue = value;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting Long value: " + type.toString(), ex); //NON-NLS
        }
        return returnValue;
    }

    /**
     * Get an Integer from an artifact if it exists, return null otherwise.
     *
     * @param artifact The artifact to query
     * @param type     The attribute type we're looking for
     *
     * @return The Integer if it exists, or null if not
     */
    static Integer getInteger(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) {
        Integer returnValue = null;
        try {
            BlackboardAttribute bba = artifact.getAttribute(new BlackboardAttribute.Type(type));
            if (bba != null) {
                Integer value = bba.getValueInt();
                returnValue = value;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting Integer value: " + type.toString(), ex); //NON-NLS
        }
        return returnValue;
    }

    /**
     * Get a String from an artifact if it exists, return null otherwise.
     *
     * @param artifact The artifact to query
     * @param type     The attribute type we're looking for
     *
     * @return The String if it exists, or null if not
     */
    static String getString(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) {
        String returnValue = null;
        try {
            BlackboardAttribute bba = artifact.getAttribute(new BlackboardAttribute.Type(type));
            if (bba != null) {
                String value = bba.getValueString();
                if (value != null && !value.isEmpty()) {
                    returnValue = value;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting String value: " + type.toString(), ex); //NON-NLS
        }
        return returnValue;
    }
}
