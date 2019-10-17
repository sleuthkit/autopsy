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

import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

public class RoutePoint extends DefaultPoint {

    private final Route parent;

    /**
     * Construct a route for a route.
     *
     * @param parent    The parent route object.
     * @param latitude  Latitude for point
     * @param longitude Longitude for point
     * @param label     Way point label.
     */
    protected RoutePoint(BlackboardArtifact artifact, Route parent, double latitude, double longitude, String label) {
        super(artifact, latitude, longitude, label);   
        this.parent = parent;
    }

    @Override
    public void initPoint()  throws TskCoreException{
        setDetails(getDetailsFromArtifact());
    }

    @Override
    public Long getTimestamp() {
        return parent.getTimestamp();
    }
    
    @Override
    public Double getAltitude() {
        return parent.getAltitude();
    }
}
