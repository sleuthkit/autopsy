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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.DataSource;

/**
 * Class for building lists of waypoints.
 *
 */
public final class WaypointBuilder {

    private static final Logger logger = Logger.getLogger(WaypointBuilder.class.getName());

    // SELECT statement for getting a list of waypoints.  
    final static String GEO_ARTIFACT_QUERY
            = "SELECT artifact_id, artifact_type_id "
            + "FROM blackboard_attributes "
            + "WHERE attribute_type_id IN (%d, %d) ";  //NON-NLS
    
    // SELECT statement to get only artifact_ids
    final static String GEO_ARTIFACT_QUERY_ID_ONLY
            = "SELECT artifact_id "
            + "FROM blackboard_attributes "
            + "WHERE attribute_type_id IN (%d, %d) ";  //NON-NLS

    // This Query will return a list of waypoint artifacts
    final static String GEO_ARTIFACT_WITH_DATA_SOURCES_QUERY
            = "SELECT blackboard_attributes.artifact_id "
            + "FROM blackboard_attributes, blackboard_artifacts "
            + "WHERE blackboard_attributes.attribute_type_id IN(%d, %d) "
            + "AND data_source_obj_id IN (%s)"; //NON-NLS

    // Select will return the "most recent" timestamp from all waypoings
    final static String MOST_RECENT_TIME
            = "SELECT MAX(value_int64) - (%d * 86400)" //86400 is the number of seconds in a day.
            + "FROM blackboard_attributes "
            + "WHERE attribute_type_id IN(%d, %d) "
            + "AND artifact_id "
            + "IN ( "
            + "%s" //GEO_ARTIFACT with or without data source
            + " )";

    // Returns a list of artifacts with no time stamp
    final static String SELECT_WO_TIMESTAMP = 
            "SELECT DISTINCT artifact_id, artifact_type_id "
            + "FROM blackboard_attributes "
            + "WHERE artifact_id NOT IN (%s) "
            + "AND artifact_id IN (%s)"; //NON-NLS

    /**
     * A callback interface to process the results of waypoint filtering.
     */
    public interface WaypointFilterQueryCallBack {

        /**
         * This function will be called after the waypoints have been filtered.
         *
         * @param wwaypoints This of waypoints.
         */
        void process(List<Waypoint> wwaypoints);
    }

    /**
     * private constructor
     */
    private WaypointBuilder() {

    }

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
    public static List<Waypoint> getAllWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
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
    public static List<Waypoint> getTrackpointWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = null;
        try {
            artifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT);
        } catch (TskCoreException ex) {
            throw new GeoLocationDataException("Unable to get artifacts for type: TSK_GPS_TRACKPOINT", ex);//NON-NLS
        }

        List<Waypoint> points = new ArrayList<>();
        for (BlackboardArtifact artifact : artifacts) {
            try {
                Waypoint point = new TrackpointWaypoint(artifact);
                points.add(point);
            } catch (GeoLocationDataException ex) {
                logger.log(Level.WARNING, String.format("No longitude or latitude available for TSK_GPS_TRACKPOINT artifactID: %d", artifact.getArtifactID()));//NON-NLS
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
    static public List<Waypoint> getEXIFWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = null;
        try {
            artifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF);
        } catch (TskCoreException ex) {
            throw new GeoLocationDataException("Unable to get artifacts for type: TSK_GPS_LAST_KNOWN_LOCATION", ex);//NON-NLS
        }

        List<Waypoint> points = new ArrayList<>();
        if (artifacts != null) {
            for (BlackboardArtifact artifact : artifacts) {
                try {
                    Waypoint point = new EXIFWaypoint(artifact);
                    points.add(point);
                } catch (GeoLocationDataException ex) {
                    // I am a little relucant to log this error because I suspect
                    // this will happen more often than not. It is valid for
                    // METADAT_EXIF to not have longitude and latitude
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
    public static List<Waypoint> getSearchWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = null;
        try {
            artifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH);
        } catch (TskCoreException ex) {
            throw new GeoLocationDataException("Unable to get artifacts for type: TSK_GPS_SEARCH", ex);//NON-NLS
        }

        List<Waypoint> points = new ArrayList<>();
        if (artifacts != null) {
            for (BlackboardArtifact artifact : artifacts) {
                try {
                    Waypoint point = new SearchWaypoint(artifact);
                    points.add(point);
                } catch (GeoLocationDataException ex) {
                    logger.log(Level.WARNING, String.format("No longitude or latitude available for TSK_GPS_SEARCH artifactID: %d", artifact.getArtifactID()));//NON-NLS
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
    public static List<Waypoint> getLastKnownWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = null;
        try {
            artifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION);
        } catch (TskCoreException ex) {
            throw new GeoLocationDataException("Unable to get artifacts for type: TSK_GPS_LAST_KNOWN_LOCATION", ex);//NON-NLS
        }

        List<Waypoint> points = new ArrayList<>();
        if (artifacts != null) {
            for (BlackboardArtifact artifact : artifacts) {
                try {
                    Waypoint point = new LastKnownWaypoint(artifact);
                    points.add(point);
                } catch (GeoLocationDataException ex) {
                    logger.log(Level.WARNING, String.format("No longitude or latitude available for TSK_GPS_LAST_KNOWN_LOCATION artifactID: %d", artifact.getArtifactID()));//NON-NLS
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
    public static List<Waypoint> getBookmarkWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = null;
        try {
            artifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK);
        } catch (TskCoreException ex) {
            throw new GeoLocationDataException("Unable to get artifacts for type: TSK_GPS_BOOKMARK", ex);//NON-NLS
        }

        List<Waypoint> points = new ArrayList<>();
        if (artifacts != null) {
            for (BlackboardArtifact artifact : artifacts) {
                try {
                    Waypoint point = new Waypoint(artifact);
                    points.add(point);
                } catch (GeoLocationDataException ex) {
                    logger.log(Level.WARNING, String.format("No longitude or latitude available for TSK_GPS_BOOKMARK artifactID: %d", artifact.getArtifactID()), ex);//NON-NLS
                }
            }
        }
        return points;
    }

    /**
     * Get a filtered list of waypoints.
     *
     * If showAll is true, the values of cntDaysFromRecent and notTimeStamp will
     * be ignored.
     *
     * To include data from all dataSources pass a null or empty dataSource
     * list.
     *
     *
     * @param skCase            Currently open sleuthkit case.
     * @param dataSources       This of data sources to filter the waypoints by.
     *                          Pass a null or empty list to show way points for
     *                          all dataSources.
     *
     * @param showAll           True to get all waypoints.
     *
     * @param cntDaysFromRecent Number of days from the most recent time stamp
     *                          to get waypoints for. This parameter will be
     *                          ignored if showAll is true;
     *
     * @param noTimeStamp       True to include waypoints without timestamp.
     *                          This parameter will be ignored if showAll is
     *                          true.
     *
     * @param queryCallBack     Function to call after the DB query has
     *                          completed.
     *
     * @throws GeoLocationDataException
     */
    static public void getAllWaypoints(SleuthkitCase skCase, List<DataSource> dataSources, boolean showAll, int cntDaysFromRecent, boolean noTimeStamp, WaypointFilterQueryCallBack queryCallBack) throws GeoLocationDataException {
        String query = buildQuery(dataSources, showAll, cntDaysFromRecent, noTimeStamp);

        logger.log(Level.INFO, query);

        try {
            // The CaseDBAccessManager.select function will add a SELECT 
            // to the beginning of the query
            if (query.startsWith("SELECT")) { //NON-NLS
                query = query.replaceFirst("SELECT", ""); //NON-NLS
            }

            skCase.getCaseDbAccessManager().select(query, new CaseDbAccessManager.CaseDbAccessQueryCallback() {
                @Override
                public void process(ResultSet rs) {
                    List<Waypoint> waypoints = new ArrayList<>();
                    try {
                        while (rs.next()) {
                            int artifact_type_id = rs.getInt("artifact_type_id"); //NON-NLS
                            long artifact_id = rs.getLong("artifact_id"); //NON-NLS

                            BlackboardArtifact.ARTIFACT_TYPE type = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact_type_id);

                            waypoints.addAll(getWaypointForArtifact(skCase.getBlackboardArtifact(artifact_id), type));

                        }
                        queryCallBack.process(waypoints);
                    } catch (GeoLocationDataException | SQLException | TskCoreException ex) {
                        logger.log(Level.WARNING, "Failed to filter waypoint.", ex); //NON-NLS
                    }

                }
            });
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to filter waypoint.", ex);  //NON-NLS
        }
    }

    /**
     * Create the query for getting a list of waypoints that do not have time
     * stamps.
     *
     * @param dataSources List of data Sources to filter by
     *
     * @return SQL SELECT statement
     */
    static private String buildQueryForWaypointsWOTimeStamps(List<DataSource> dataSources) {
        return String.format(SELECT_WO_TIMESTAMP,
                String.format(GEO_ARTIFACT_QUERY_ID_ONLY,
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID()),
                getWaypointListQuery(dataSources));
    }

    /**
     * Build the query to filter the list of waypoints.
     *
     * If showAll is true, the values of cntDaysFromRecent and noTimeStamp are
     * ignored.
     *
     * @param dataSources       This of data sources to filter the waypoints by.
     *                          Pass a null or empty list to show way points for
     *                          all dataSources.
     *
     * @param showAll           True to get all waypoints.
     *
     * @param cntDaysFromRecent Number of days from the most recent time stamp
     *                          to get waypoints for. This parameter will be
     *                          ignored if showAll is true;
     *
     * @param noTimeStamp       True to include waypoints without timestamp.
     *                          This parameter will be ignored if showAll is
     *                          true.
     *
     * @return
     */
    static private String buildQuery(List<DataSource> dataSources, boolean showAll, int cntDaysFromRecent, boolean noTimeStamp) {
        String mostRecentQuery = "";

        if (!showAll && cntDaysFromRecent > 0) {
            mostRecentQuery = String.format("AND value_int64 > (%s)",  //NON-NLS
                    String.format(MOST_RECENT_TIME,
                            cntDaysFromRecent,
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(),
                            getWaypointListQuery(dataSources)
                    ));
        }

        // This givens us all artifact_ID that have time stamp
        String query = String.format(GEO_ARTIFACT_QUERY,
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID());

        // That are in the list of artifacts for the given data Sources
        query += String.format("AND artifact_id IN(%s)", getWaypointListQuery(dataSources)); //NON-NLS
        query += mostRecentQuery;

        if (showAll || noTimeStamp) {
            query = String.format("%s UNION %s", buildQueryForWaypointsWOTimeStamps(dataSources), query); //NON-NLS
        }

        return query;
    }

    /**
     * Returns the query to get a list of waypoints filted by the given data
     * sources.
     *
     * An artifact is assumed to be a "waypoint" if it has the attributes
     * TSK_GEO_LATITUDE or TSK_GEO_LATITUDE_START
     *
     * @param dataSources A list of data sources to filter by. If the list is
     *                    null or empty the data source list will be ignored.
     *
     * @return
     */
    static private String getWaypointListQuery(List<DataSource> dataSources) {

        if (dataSources == null || dataSources.isEmpty()) {
            return String.format(GEO_ARTIFACT_QUERY,
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START.getTypeID());
        }

        String dataSourceList = "";
        for (DataSource source : dataSources) {
            dataSourceList += Long.toString(source.getId()) + ",";
        }

        if (!dataSourceList.isEmpty()) {
            // Remove the last ,
            dataSourceList = dataSourceList.substring(0, dataSourceList.length() - 1);
        }

        return String.format(GEO_ARTIFACT_WITH_DATA_SOURCES_QUERY,
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(),
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START.getTypeID(),
                dataSourceList);
    }

    /**
     * Create a Waypoint object for the given Blackboard artifact.
     *
     * @param artifact The artifact to create the waypoint from
     * @param type     The type of artifact
     *
     * @return A new waypoint object
     *
     * @throws GeoLocationDataException
     */
    static private List<Waypoint> getWaypointForArtifact(BlackboardArtifact artifact, BlackboardArtifact.ARTIFACT_TYPE type) throws GeoLocationDataException {
        List<Waypoint> waypoints = new ArrayList<>();
        switch (type) {
            case TSK_METADATA_EXIF:
                waypoints.add(new EXIFWaypoint(artifact));
                break;
            case TSK_GPS_BOOKMARK:
                waypoints.add(new Waypoint(artifact));
                break;
            case TSK_GPS_TRACKPOINT:
                waypoints.add(new TrackpointWaypoint(artifact));
                break;
            case TSK_GPS_SEARCH:
                waypoints.add(new SearchWaypoint(artifact));
                break;
            case TSK_GPS_ROUTE:
                Route route = new Route(artifact);
                waypoints.addAll(route.getRoute());
                break;
            default:
                waypoints.add(new Waypoint(artifact));
                break;
        }

        return waypoints;
    }
}
