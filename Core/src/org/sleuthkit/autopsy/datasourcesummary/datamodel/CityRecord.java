/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.util.Objects;
import org.sleuthkit.autopsy.geolocation.KdTree;

/**
 * A record for a particular city including country and location.
 */
public class CityRecord extends KdTree.XYZPoint {

    private final String cityName;
    private final String country;
    private final String state;

    /**
     * Main constructor.
     *
     * @param cityName The name of the city.
     * @param state The state of the city.
     * @param country The country of that city.
     * @param latitude Latitude for the city.
     * @param longitude Longitude for the city.
     */
    CityRecord(String cityName, String state, String country, double latitude, double longitude) {
        super(latitude, longitude);
        this.cityName = cityName;
        this.state = state;
        this.country = country;
    }

    /**
     * @return The name of the city.
     */
    public String getCityName() {
        return cityName;
    }

    /**
     * @return The state of the city.
     */
    public String getState() {
        return state;
    }
    
    /**
     * @return The country of that city.
     */
    public String getCountry() {
        return country;
    }

    /**
     * @return Latitude for the city.
     */
    public double getLatitude() {
        return getY();
    }

    /**
     * @return Longitude for the city.
     */
    public double getLongitude() {
        return getX();
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 37 * hash + Objects.hashCode(this.cityName);
        hash = 37 * hash + Objects.hashCode(this.country);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CityRecord other = (CityRecord) obj;
        if (!Objects.equals(this.cityName, other.cityName)) {
            return false;
        }
        if (!Objects.equals(this.country, other.country)) {
            return false;
        }
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return "CityRecord{" + "cityName=" + cityName + ", country=" + country + ", lat=" + getX() + ", lng=" + getY() + '}';
    }

}
