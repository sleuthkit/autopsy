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

/**
 * The basic details of a waypoint.
 *
 */
public interface Waypoint {

    /**
     * Get the timestamp for this BlackboardArtifact.
     *
     * @return Timestamp in epoch seconds or null if none was set.
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
     * Get the Altitude for this point.
     *
     * @return Returns the Altitude for the point or null if none was set
     */
    Double getAltitude();

    /**
     * Gets a Map of other properties that may be interesting to this way point.
     * This map will not include properties for which there are getter functions
     * for.
     *
     * The key is a "Display String", the value will be either an empty string
     * or the formatted value.
     *
     * @return A Map of waypoint properties
     */
    Map<String, String> getOtherProperties();

    /**
     * Get the image for this waypoint.
     *
     * @return AbstractFile image
     */
    AbstractFile getImage();

    /**
     * Get the type of waypoint
     *
     * @return WaypointType value
     */
    Type getType();

    // Display names are from the original KML Report
    @Messages({
        "Waypoint_Bookmark_Display_String=GPS Bookmark",
        "Waypoint_Last_Known_Display_String=GPS Last Known Location",
        "Waypoint_EXIF_Display_String=EXIF Metadata With Location",
        "Waypoint_Route_Point_Display_String=GPS Individual Route Point",
        "Waypoint_Search_Display_String=GPS Search",
        "Waypoint_Trackpoint_Display_String=GPS Trackpoint"
    })

    /**
     * An enum to keep track of the type of a way point.
     */
    enum Type {
        BOOKMARK(Bundle.Waypoint_Bookmark_Display_String()),
        LAST_KNOWN_LOCATION(Bundle.Waypoint_Last_Known_Display_String()),
        METADATA_EXIF(Bundle.Waypoint_EXIF_Display_String()),
        ROUTE_POINT(Bundle.Waypoint_Route_Point_Display_String()),
        SEARCH(Bundle.Waypoint_Search_Display_String()),
        TRACKPOINT(Bundle.Waypoint_Trackpoint_Display_String()),
        UNKNOWN("Unknown");

        private final String displayName;

        /**
         * Constructs a Waypoint.Type enum value
         *
         * @param displayName String value title for enum
         */
        Type(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the display name for the type
         *
         * @return String display name
         */
        public String getDisplayName() {
            return displayName;
        }
    }
}
