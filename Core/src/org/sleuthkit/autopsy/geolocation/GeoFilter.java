/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.geolocation;

import java.util.Collections;
import java.util.List;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.DataSource;

/**
 * Class to store the values of the Geolocation user set filter parameters
 */
public final class GeoFilter {

    private final boolean showAll;
    private final boolean showWithoutTimeStamp;
    private final int mostRecentNumDays;
    private final List<DataSource> dataSources;
    private final List<BlackboardArtifact.ARTIFACT_TYPE> artifactTypes;

    /**
     * Construct a Geolocation filter. showAll and mostRecentNumDays are
     * exclusive filters, ie they cannot be used together.
     *
     * withoutTimeStamp is only applicable if mostRecentNumDays is true.
     *
     * When using the filters "most recent days" means to include waypoints for
     * the numbers of days after the most recent waypoint, not the current date.
     *
     * @param showAll True if all waypoints should be shown
     * @param withoutTimeStamp True to show waypoints without timeStamps, this
     * filter is only applicable if mostRecentNumDays is true
     * @param mostRecentNumDays Show Waypoint for the most recent given number
     * of days. This parameter is ignored if showAll is true.
     * @param dataSources A list of dataSources to filter waypoint for.
     * @param artifactTypes A list of artifactTypes to filter waypoint for.
     */
    public GeoFilter(boolean showAll, boolean withoutTimeStamp, int mostRecentNumDays, List<DataSource> dataSources, List<BlackboardArtifact.ARTIFACT_TYPE> artifactTypes) {
        this.showAll = showAll;
        this.showWithoutTimeStamp = withoutTimeStamp;
        this.mostRecentNumDays = mostRecentNumDays;
        this.dataSources = dataSources;
        this.artifactTypes = artifactTypes;
    }

    /**
     * Returns whether or not to show all waypoints.
     *
     * @return True if all waypoints should be shown.
     */
    boolean showAllWaypoints() {
        return showAll;
    }

    /**
     * Returns whether or not to include waypoints with time stamps.
     *
     * This filter is only applicable if "showAll" is true.
     *
     * @return True if waypoints with time stamps should be shown.
     */
    boolean showWaypointsWithoutTimeStamp() {
        return showWithoutTimeStamp;
    }

    /**
     * Returns the number of most recent days to show waypoints for. This value
     * should be ignored if showAll is true.
     *
     * @return The number of most recent days to show waypoints for
     */
    int getMostRecentNumDays() {
        return mostRecentNumDays;
    }

    /**
     * Returns a list of data sources to filter the waypoints by, or null if all
     * datasources should be include.
     *
     * @return A list of dataSources or null if all dataSources should be
     * included.
     */
    List<DataSource> getDataSources() {
        return Collections.unmodifiableList(dataSources);
    }

    /**
     * Returns a list of artifact types to filter the waypoints by, or null if
     * all types should be include.
     *
     * @return A list of artifactTypes or null if all artifactTypes should be
     * included.
     */
    List<BlackboardArtifact.ARTIFACT_TYPE> getArtifactTypes() {
        return Collections.unmodifiableList(artifactTypes);
    }

}
