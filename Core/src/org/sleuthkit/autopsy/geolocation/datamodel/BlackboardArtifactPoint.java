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

import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Interface to be implemented by all ArtifactPoint objects.
 *
 */
public interface BlackboardArtifactPoint {

    /**
     * Get the BlackboardArtifact for which this point represents.
     *
     * @return BlackboardArtifact for this point.
     */
    BlackboardArtifact getArtifact();

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
     * Get the details of this point as an HTML formated string.
     *
     * @return The details string or empty string if none was set.
     */
    String getDetails();

    /**
     * initPoint is the function where the work of setting the attributes the
     * point.
     *
     * @throws TskCoreException
     */
    void initPoint() throws TskCoreException;

    /**
     * Get the latitude and longitude as a formatted string.
     *
     * @return Formatted String.
     */
    String getFormattedCoordinates();

    /**
     * Get the latitude and longitude formatted as given.
     *
     * @param format The format String should assume that latitude will be
     *               passed first and that both are number values.
     *
     * @return Formatted string.
     */
    String getFormattedCoordinates(String format);
}
