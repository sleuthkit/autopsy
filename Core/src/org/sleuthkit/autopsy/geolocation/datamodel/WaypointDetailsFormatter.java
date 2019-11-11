/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Common class for the KMLReport and the UI to generate HTML formatted
 * waypoint details.
 */
public class WaypointDetailsFormatter {
    private final static String HTML_PROP_FORMAT = "<b>%s: </b>%s<br>";
    
    static private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
     
    /**
     * Returns an HTML formatted string of all the
     *
     * @param route
     *
     * @return A HTML formatted list of the Route attributes
     */

    static public String getFormattedDetails(Route route, String header) {
        List<Waypoint> points = route.getRoute();
        StringBuilder result = new StringBuilder(); //NON-NLS

        result.append("<html>");
        
        if(header != null && !header.isEmpty()) {
            result.append(String.format("<h3>%s</h3>", header));
        }
        
        result.append(formatAttribute("Name", route.getLabel()));

        Long timestamp = route.getTimestamp();
        if (timestamp != null) {
            result.append(formatAttribute("Timestamp", getTimeStamp(timestamp)));
        }

        if (points.size() > 1) {
            Waypoint start = points.get(0);
            Waypoint end = points.get(1);

            result.append(formatAttribute("Start Latitude", start.getLatitude().toString()))
                    .append(formatAttribute("Start Longitude", start.getLongitude().toString()));
            
            Double altitude = start.getAltitude();
            if(altitude != null) {
                result.append(formatAttribute("Start Altitude", altitude.toString()));
            }
            
            result.append(formatAttribute("End Latitude", end.getLatitude().toString()))
                    .append(formatAttribute("End Longitude", end.getLongitude().toString()));
            
            altitude = end.getAltitude();
            if(altitude != null) {
                result.append(formatAttribute("End Altitude", altitude.toString()));
            }
            
            result.append("</html>");
        }

        List<Waypoint.Property> list = route.getOtherProperties();
        for(Waypoint.Property prop: list) {
            String value = prop.getValue();
            if(value != null && !value.isEmpty()) {
                result.append(formatAttribute(prop.getDisplayName(), value));
            }
        }

        return result.toString();
    }
    
     /**
     * Get the nicely formatted details for the given waypoint.
     * 
     * @param point Waypoint object
     * @param header String details header
     * 
     * @return HTML formatted String of details for given waypoint 
     */
    static public String getFormattedDetails(Waypoint point, String header) {
        StringBuilder result = new StringBuilder(); //NON-NLS
        
        result.append("<html>");
        
        if(header != null && !header.isEmpty()) {
            result.append(String.format("<h3>%s</h3>", header));
        }
        result.append(formatAttribute("Name", point.getLabel()));
        
       
        Long timestamp = point.getTimestamp();
        if (timestamp != null) {
            result.append(formatAttribute("Timestamp", getTimeStamp(timestamp)));
        }

        result.append(formatAttribute("Latitude", point.getLatitude().toString()))
                .append(formatAttribute("Longitude", point.getLongitude().toString()));
       
        if (point.getAltitude() != null) {
            result.append(formatAttribute("Altitude", point.getAltitude().toString()));
        }

        List<Waypoint.Property> list = point.getOtherProperties();
        for(Waypoint.Property prop: list) {
            String value = prop.getValue();
            if(value != null && !value.isEmpty()) {
                result.append(formatAttribute(prop.getDisplayName(), value));
            }
        }
        
        result.append("</html>");

        return result.toString();
    }

    static private String formatAttribute(String title, String value) {
        return String.format(HTML_PROP_FORMAT, title, value);
    }
    
    /**
     * Format a point time stamp (in seconds) to the report format.
     *
     * @param timeStamp The timestamp in epoch seconds.
     *
     * @return The formatted timestamp
     */
    static private String getTimeStamp(long timeStamp) {
        return dateFormat.format(new java.util.Date(timeStamp * 1000));
    }
}
