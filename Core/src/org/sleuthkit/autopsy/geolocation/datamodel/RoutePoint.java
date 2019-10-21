/*
 *
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
import org.sleuthkit.datamodel.AbstractFile;

/**
 * A point in a Route. For future use this point will have a pointer to its
 * parent route.
 */
public class RoutePoint implements Waypoint {

    private final Route parent;
    private final Double longitude;
    private final Double latitude;
    private final String label;

    /**
     * Construct a route for a route.
     *
     * @param parent    The parent route object.
     * @param latitude  Latitude for point
     * @param longitude Longitude for point
     * @param label     Way point label.
     */
    protected RoutePoint(Route parent, double latitude, double longitude, String label) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.label = label;
        this.parent = parent;
    }

    @Override
    public Long getTimestamp() {
        return parent.getTimestamp();
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
        return parent.getAltitude();
    }

    @Override
    public Map<String, String> getOtherProperties() {
        return parent.getOtherProperties();
    }

    @Override
    public AbstractFile getImage() {
        return null;
    }

    @Override
    public Waypoint.Type getType() {
        return Waypoint.Type.ROUTE_POINT;
    }
}
