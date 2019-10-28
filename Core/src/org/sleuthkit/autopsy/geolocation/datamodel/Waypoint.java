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
import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * The basic details of a waypoint.
 *
 */
public interface Waypoint {
    /**
     * Interface to describe a waypoint.  A waypoint is made up of 
     * a longitude, latitude, label, timestamp, type, image and altitude.
     * 
     * A good way point should have at minimum a longitude and latutude.
     *
     * @return Timestamp in java/unix epoch seconds or null if none was set.
     */
    Long getTimestamp();

    /**
     * Get the label for this point object.
     *
     * @return String label for the point or null if none was set
     */
    String getLabel();

    /**
     * Get the latitude for this point.
     *
     * @return Returns the latitude for the point or null if none was set
     */
    Double getLatitude();

    /**
     * Get the longitude for this point.
     *
     * @return Returns the longitude for the point or null if none was set
     */
    Double getLongitude();

    /**
     * Get the altitude for this point.
     *
     * @return Returns the altitude for the point or null if none was set
     */
    Double getAltitude();

    /**
     * Gets an unmodifiable List of other properties that may be interesting to this way point.
     * The List will not include properties for which getter functions
     * exist.
     *
     * @return A List of waypoint properties
     */
    List<Property> getOtherProperties();

    /**
     * Get the image for this waypoint.
     *
     * @return AbstractFile image or null if one was not set
     */
    AbstractFile getImage();

    /**
     * Returns a list of Waypoints for the artifacts with geolocation
     * information.
     *
     * List will include artifacts of type: TSK_GPS_TRACKPOINT TSK_GPS_SEARCH
     * TSK_GPS_LAST_KNOWN_LOCATION TSK_GPS_BOOKMARK TSK_METADATA_EXIF
     *
     * @param skCase Currently open SleuthkitCase
     *
     * @return List of Waypoint
     *
     * @throws GeoLocationDataException
     */
    static List<Waypoint> getAllWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<Waypoint> points = new ArrayList<>();

        points.addAll(getTrackpointWaypoints(skCase));
        points.addAll(getEXIFWaypoints(skCase));
        points.addAll(getSearchWaypoints(skCase));
        points.addAll(getLastKnownWaypoints(skCase));
        points.addAll(getBookmarkWaypoints(skCase));

        return points;
    }

    /**
     * Gets a list of Waypoints for TSK_GPS_TRACKPOINT artifacts.
     *
     * @param skCase Currently open SleuthkitCase
     *
     * @return List of Waypoint
     *
     * @throws GeoLocationDataException
     */
    static List<Waypoint> getTrackpointWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = ArtifactUtils.getArtifactsForType(skCase, BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT);
        
        List<Waypoint> points = new ArrayList<>();
        for (BlackboardArtifact artifact : artifacts) {
            ArtifactWaypoint point = new TrackpointWaypoint(artifact);
            // Only add to the list if the point has a valid latitude 
            // and longitude. 
            if (point.getLatitude() != null && point.getLongitude() != null) {
                points.add(point);
            }
        }
        return points;
    }

    /**
     * Gets a list of Waypoints for TSK_METADATA_EXIF artifacts.
     *
     * @param skCase Currently open SleuthkitCase
     *
     * @return List of Waypoint
     *
     * @throws GeoLocationDataException
     */
    static List<Waypoint> getEXIFWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = ArtifactUtils.getArtifactsForType(skCase, BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF);
        
        List<Waypoint> points = new ArrayList<>();
        if (artifacts != null) {
            for (BlackboardArtifact artifact : artifacts) {
                ArtifactWaypoint point = new EXIFWaypoint(artifact);
                // Only add to the list if the point has a valid latitude 
                // and longitude. 
                if (point.getLatitude() != null && point.getLongitude() != null) {
                    points.add(point);
                }
            }
        }
        return points;
    }

    /**
     * Gets a list of Waypoints for TSK_GPS_SEARCH artifacts.
     *
     * @param skCase Currently open SleuthkitCase
     *
     * @return List of Waypoint
     *
     * @throws GeoLocationDataException
     */
    static List<Waypoint> getSearchWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = ArtifactUtils.getArtifactsForType(skCase, BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH);
        
        List<Waypoint> points = new ArrayList<>();
        if (artifacts != null) {
            for (BlackboardArtifact artifact : artifacts) {
                ArtifactWaypoint point = new GPSSearchWaypoint(artifact);
                // Only add to the list if the point has a valid latitude 
                // and longitude. 
                if (point.getLatitude() != null && point.getLongitude() != null) {
                    points.add(point);
                }
            }
        }
        return points;
    }

    /**
     * Gets a list of Waypoints for TSK_GPS_LAST_KNOWN_LOCATION artifacts.
     *
     * @param skCase Currently open SleuthkitCase
     *
     * @return List of Waypoint
     *
     * @throws GeoLocationDataException
     */
    static List<Waypoint> getLastKnownWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = ArtifactUtils.getArtifactsForType(skCase, BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION);
        
        List<Waypoint> points = new ArrayList<>();
        if (artifacts != null) {
            
            for (BlackboardArtifact artifact : artifacts) {
                ArtifactWaypoint point = new LastKnownWaypoint(artifact);
                // Only add to the list if the point has a valid latitude 
                // and longitude. 
                if (point.getLatitude() != null && point.getLongitude() != null) {
                    points.add(point);
                }
            }
        }
        return points;
    }

    /**
     * Gets a list of Waypoints for TSK_GPS_BOOKMARK artifacts.
     *
     * @param skCase Currently open SleuthkitCase
     *
     * @return List of Waypoint
     *
     * @throws GeoLocationDataException
     */
    static List<Waypoint> getBookmarkWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = ArtifactUtils.getArtifactsForType(skCase, BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK);
        
        List<Waypoint> points = new ArrayList<>();
        if (artifacts != null) {
            for (BlackboardArtifact artifact : artifacts) {
                ArtifactWaypoint point = new ArtifactWaypoint(artifact);
                // Only add to the list if the point has a valid latitude 
                // and longitude. 
                if (point.getLatitude() != null && point.getLongitude() != null) {
                    points.add(point);
                }
            }
        }
        return points;
    }

    /**
     * Simple property class for waypoint properties that a purely
     * informational.
     */
    class Property {

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
