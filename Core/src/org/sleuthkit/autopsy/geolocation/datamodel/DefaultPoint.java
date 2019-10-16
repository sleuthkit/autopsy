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

import java.text.SimpleDateFormat;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * 
 */
public abstract class DefaultPoint implements BlackboardArtifactPoint{
    final private BlackboardArtifact artifact;
    
    private String label = null;
    private Long timestamp = null;
    private String details = null;
    private Double longitude = null;
    private Double latitude = null;
    private Double altitude = null;
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    private static final String DEFAULT_COORD_FORMAT = "%.2f, %.2f";
        
    public DefaultPoint(BlackboardArtifact artifact) {
        this.artifact = artifact;
    }
    
    @Override
    public BlackboardArtifact getArtifact() {
        return artifact;
    }
    
    @Override
    public Long getTimestamp() {
        return timestamp;
    }
    
    protected void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String getLabel() {
        return label;
    }
    
    protected void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String getDetails() {
        return details;
    }
    
    protected void setDetails(String details) {
        this.details = details;
    }
    
    @Override
    public Double getLatitude() {
        return latitude;
    }
    
    protected void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
    @Override
    public Double getLongitude() {
        return longitude;
    }
    
    protected void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    
    @Override
    public Double getAltitude() {
        return altitude;
    }
    
    protected void setAltitude(Double altitude) {
        this.altitude = altitude;
    }
    
    @Override
    public String getFormattedCoordinates() {
        return getFormattedCoordinates(DEFAULT_COORD_FORMAT);
    }
    
    @Override
    public String getFormattedCoordinates(String format) {
        return String.format(format, getLatitude(), getLongitude());
    }
    
    /**
     * This method creates a text description for a map feature using all the
     * geospatial and time data we can for the Artifact. It queries the
     * following attributes:
     *
     * TSK_GEO_LATITUDE 54; TSK_GEO_LONGITUDE 55; TSK_GEO_LATITUDE_START 98;
     * TSK_GEO_LATITUDE_END 99; TSK_GEO_LONGITUDE_START 100;
     * TSK_GEO_LONGITUDE_END 101; TSK_GEO_VELOCITY 56; TSK_GEO_ALTITUDE 57;
     * TSK_GEO_BEARING 58; TSK_GEO_HPRECISION 59; TSK_GEO_VPRECISION 60;
     * TSK_GEO_MAPDATUM 61; TSK_DATETIME_START 83; TSK_DATETIME_END 84;
     * TSK_LOCATION 86; TSK_PATH_SOURCE 94;
     *
     * @param artifact    the artifact to query.
     * @param featureType the type of Artifact we're working on.
     *
     * @return a String with the information we have available
     */
    String getDetailsFromArtifact() throws TskCoreException{
        final String SEP = "<br>";
        StringBuilder result = new StringBuilder(); //NON-NLS
        
        result.append("<h3>");
        result.append(getArtifact().getArtifactTypeName());
        result.append("</h3>");

        String name = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
        if (name != null && !name.isEmpty()) {
            result.append("<b>Name:</b> ").append(name).append(SEP); //NON-NLS
        }

        String location = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION);
        if (location != null && !location.isEmpty()) {
            result.append("<b>Location:</b> ").append(location).append(SEP); //NON-NLS
        }

        if (timestamp != null) {
            result.append("<b>Timestamp:</b> ").append(getTimeStamp(timestamp)).append(SEP); //NON-NLS
            result.append("<b>Unix timestamp:</b> ").append(timestamp).append(SEP); //NON-NLS
        }

        Long startingTimestamp = getLong( BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START);
        if (startingTimestamp != null) {
            result.append("<b>Starting Timestamp:</b> ").append(getTimeStamp(startingTimestamp)).append(SEP); //NON-NLS
            result.append("<b>Starting Unix timestamp:</b> ").append(startingTimestamp).append(SEP); //NON-NLS
        }

        Long endingTimestamp = getLong(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END);
        if (endingTimestamp != null) {
            result.append("<b>Ending Timestamp:</b> ").append(getTimeStamp(endingTimestamp)).append(SEP); //NON-NLS
            result.append("<b>Ending Unix timestamp:</b> ").append(endingTimestamp).append(SEP); //NON-NLS
        }

        Long createdTimestamp = getLong(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED);
        if (createdTimestamp != null) {
            result.append("<b>Created Timestamp:</b> ").append(getTimeStamp(createdTimestamp)).append(SEP); //NON-NLS
            result.append("<b>Created Unix timestamp:</b> ").append(createdTimestamp).append(SEP); //NON-NLS
        }

        if (latitude != null) {
            result.append("<b>Latitude:</b> ").append(latitude).append(SEP); //NON-NLS
        }

        if (longitude != null) {
            result.append("<b>Longitude:</b> ").append(longitude).append(SEP); //NON-NLS
        }

        Double velocity = getDouble(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_VELOCITY);
        if (velocity != null) {
            result.append("<b>Velocity:</b> ").append(velocity).append(SEP); //NON-NLS
        }

        if (altitude != null) {
            result.append("<b>Altitude:</b> ").append(altitude).append(SEP); //NON-NLS
        }

        Double bearing = getDouble(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_BEARING);
        if (bearing != null) {
            result.append("<b>Bearing:</b> ").append(bearing).append(SEP); //NON-NLS
        }

        Integer hPrecision = getInteger(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_HPRECISION);
        if (hPrecision != null) {
            result.append("<b>Horizontal Precision Figure of Merit:</b> ").append(hPrecision).append(SEP); //NON-NLS
        }

        Integer vPrecision = getInteger(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_VPRECISION);
        if (vPrecision != null) {
            result.append("<b>Vertical Precision Figure of Merit:</b> ").append(vPrecision).append(SEP); //NON-NLS
        }

        String mapDatum = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_MAPDATUM);
        if (mapDatum != null && !mapDatum.isEmpty()) {
            result.append("<b>Map Datum:</b> ").append(mapDatum).append(SEP); //NON-NLS
        }

        String programName = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME);
        if (programName != null && !programName.isEmpty()) {
            result.append("<b>Reported by:</b> ").append(programName).append(SEP); //NON-NLS
        }

        String flag = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FLAG);
        if (flag != null && !flag.isEmpty()) {
            result.append("<b>Flag:</b> ").append(flag).append(SEP); //NON-NLS
        }

        String pathSource = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_SOURCE);
        if (pathSource != null && !pathSource.isEmpty()) {
            result.append("<b>Source:</b> ").append(pathSource).append(SEP); //NON-NLS
        }

        String deviceMake = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE);
        if (deviceMake != null && !deviceMake.isEmpty()) {
            result.append("<b>Device Make:</b> ").append(deviceMake).append(SEP); //NON-NLS
        }

        String deviceModel = getString(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL);
        if (deviceModel != null && !deviceModel.isEmpty()) {
            result.append("<b>Device Model:</b> ").append(deviceModel).append(SEP); //NON-NLS
        }

        return result.toString();
    }
    
    String getString(BlackboardAttribute.ATTRIBUTE_TYPE type) throws TskCoreException{
        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(type));
        return (attribute != null ? attribute.getValueString() : null);
    }
    
    Double getDouble(BlackboardAttribute.ATTRIBUTE_TYPE type) throws TskCoreException{
        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(type));
        return (attribute != null ? attribute.getValueDouble() : null);
    }
    
    Long getLong(BlackboardAttribute.ATTRIBUTE_TYPE type) throws TskCoreException{
        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(type));
        return (attribute != null ? attribute.getValueLong() : null);
    }
    
    Integer getInteger(BlackboardAttribute.ATTRIBUTE_TYPE type) throws TskCoreException{
        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(type));
        return (attribute != null ? attribute.getValueInt() : null);
    }
    
    String getTimeStamp(long timeStamp) {
        return DATE_FORMAT.format(new java.util.Date(getTimestamp() * 1000));
    }
}
