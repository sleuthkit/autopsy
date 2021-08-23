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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary.CityData;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.DataSource;

/**
 * Wrapper class for converting
 * org.sleuthkit.autopsy.contentutils.GeolocationSummary functionality into a
 * DefaultArtifactUpdateGovernor used by GeolocationPanel tab.
 */
public class GeolocationSummaryGetter implements DefaultArtifactUpdateGovernor {

    private final GeolocationSummary geoSummary;

    /**
     * Default constructor.
     */
    public GeolocationSummaryGetter() {
        geoSummary = new GeolocationSummary();
    }

    /**
     * @return Returns all the geolocation artifact types.
     */
    public List<ARTIFACT_TYPE> getGeoTypes() {
        return GeolocationSummary.getGeoTypes();
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return GeolocationSummary.getArtifactTypeIdsForRefresh();
    }

    /**
     * Get this list of hits per city where the list is sorted descending by
     * number of found hits (i.e. most hits is first index).
     *
     * @param dataSource The data source.
     * @param daysCount  Number of days to go back.
     * @param maxCount   Maximum number of results.
     *
     * @return The sorted list.
     *
     * @throws SleuthkitCaseProviderException
     * @throws GeoLocationDataException
     * @throws InterruptedException
     */
    public CityData getCityCounts(DataSource dataSource, int daysCount, int maxCount)
            throws SleuthkitCaseProviderException, GeoLocationDataException, InterruptedException, IOException {
        return geoSummary.getCityCounts(dataSource, daysCount, maxCount);
    }
}
