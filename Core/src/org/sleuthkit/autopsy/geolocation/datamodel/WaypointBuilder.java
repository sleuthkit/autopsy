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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
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

    private final static String TIME_TYPE_IDS = String.format("%d, %d",
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID());

    private final static String GEO_ATTRIBUTE_TYPE_IDS = String.format("%d, %d, %d",
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_WAYPOINTS.getTypeID());

    // SELECT statement for getting a list of waypoints where %s is a comma separated list
    // of attribute type ids.
    private final static String GEO_ARTIFACT_QUERY
            = "SELECT artifact_id, artifact_type_id "
            + "FROM blackboard_attributes "
            + "WHERE attribute_type_id IN (%s) ";  //NON-NLS

    // SELECT statement to get only artifact_ids
    private final static String GEO_ARTIFACT_QUERY_ID_ONLY
            = "SELECT artifact_id "
            + "FROM blackboard_attributes "
            + "WHERE attribute_type_id IN (%s) ";  //NON-NLS

    // This Query will return a list of waypoint artifacts
    private final static String GEO_ARTIFACT_WITH_DATA_SOURCES_QUERY
            = "SELECT blackboard_attributes.artifact_id "
            + "FROM blackboard_attributes, blackboard_artifacts "
            + "WHERE blackboard_attributes.artifact_id = blackboard_artifacts.artifact_id "
            + "AND blackboard_attributes.attribute_type_id IN(%s) "
            + "AND data_source_obj_id IN (%s)"; //NON-NLS

    // Select will return the "most recent" timestamp from all waypoings
    private final static String MOST_RECENT_TIME
            = "SELECT MAX(value_int64) - (%d * 86400)" //86400 is the number of seconds in a day.
            + "FROM blackboard_attributes "
            + "WHERE attribute_type_id IN(%s) "
            + "AND artifact_id "
            + "IN ( "
            + "%s" //GEO_ARTIFACT with or without data source
            + " )";

    // Returns a list of artifacts with no time stamp
    private final static String SELECT_WO_TIMESTAMP
            = "SELECT DISTINCT artifact_id, artifact_type_id "
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
         * @param waypoints The list of waypoints and whether they were all
         *                  successfully parsed.
         */
        void process(GeoLocationParseResult<Waypoint> waypoints);
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
     * Returns a list of routes from the given list of waypoints.
     *
     * @param waypoints A list of waypoints
     *
     * @return A list of routes or an empty list if none were found.
     */
    public static List<Route> getRoutes(List<Waypoint> waypoints) {
        List<Route> routeList = new ArrayList<>();
        for (Waypoint point : waypoints) {
            GeoPath path = point.getParentGeoPath();
            if (path instanceof Route) {
                Route route = (Route) path;
                if (!routeList.contains(route)) {
                    routeList.add(route);
                }
            }
        }

        return routeList;
    }

    /**
     * Returns a list of tracks from the given list of waypoints.
     *
     * @param waypoints A list of waypoints
     *
     * @return A list of track or an empty list if none were found.
     */
    public static List<Track> getTracks(List<Waypoint> waypoints) {
        List<Track> trackList = new ArrayList<>();
        for (Waypoint point : waypoints) {
            GeoPath path = point.getParentGeoPath();
            if (path instanceof Track) {
                Track route = (Track) path;
                if (!trackList.contains(route)) {
                    trackList.add(route);
                }
            }
        }

        return trackList;
    }
    
    /**
     * Returns a list of areas from the given list of waypoints.
     *
     * @param waypoints A list of waypoints
     *
     * @return A list of areas or an empty list if none were found.
     */
    public static List<Area> getAreas(List<Waypoint> waypoints) {
        List<Area> areaList = new ArrayList<>();
        for (Waypoint point : waypoints) {
            GeoPath path = point.getParentGeoPath();
            if (path instanceof Area) {
                Area area = (Area) path;
                if (!areaList.contains(area)) {
                    areaList.add(area);
                }
            }
        }

        return areaList;
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
    @SuppressWarnings("deprecation")
    public static List<Waypoint> getTrackpointWaypoints(SleuthkitCase skCase) throws GeoLocationDataException {
        List<BlackboardArtifact> artifacts = null;
        try {
            artifacts = skCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_GPS_TRACKPOINT);
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
     * Returns a list of waypoints that come from TSK_GEO_TRACKPOINT artifacts.
     *
     * @param waypoints A list of waypoints
     *
     * @return A list of trackpoint waypoints or empty list if none were found.
     */
    public static List<Waypoint> getTrackpointWaypoints(List<Waypoint> waypoints) {
        List<Waypoint> specificPoints = new ArrayList<>();

        for (Waypoint point : waypoints) {
            if (point instanceof TrackpointWaypoint) {
                specificPoints.add(point);
            }
        }

        return specificPoints;
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
            artifacts = skCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_METADATA_EXIF);
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
     * Returns a list of waypoints that come from TSK_METADATA_EXIF artifacts.
     *
     * @param waypoints A list of waypoints
     *
     * @return A list of trackpoint waypoints or empty list if none were found.
     */
    public static List<Waypoint> getEXIFWaypoints(List<Waypoint> waypoints) {
        List<Waypoint> specificPoints = new ArrayList<>();

        for (Waypoint point : waypoints) {
            if (point instanceof EXIFWaypoint) {
                specificPoints.add(point);
            }
        }

        return specificPoints;
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
            artifacts = skCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_GPS_SEARCH);
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
     * Returns a list of waypoints that come from TSK_GPS_SEARCH artifacts.
     *
     * @param waypoints A list of waypoints
     *
     * @return A list of trackpoint waypoints or empty list if none were found.
     */
    public static List<Waypoint> getSearchWaypoints(List<Waypoint> waypoints) {
        List<Waypoint> specificPoints = new ArrayList<>();

        for (Waypoint point : waypoints) {
            if (point instanceof SearchWaypoint) {
                specificPoints.add(point);
            }
        }

        return specificPoints;
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
            artifacts = skCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION);
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
     * Returns a list of waypoints that come from TSK_GPS_LAST_KNOWN_LOCATION
     * artifacts.
     *
     * @param waypoints A list of waypoints
     *
     * @return A list of trackpoint waypoints or empty list if none were found.
     */
    public static List<Waypoint> getLastKnownWaypoints(List<Waypoint> waypoints) {
        List<Waypoint> specificPoints = new ArrayList<>();

        for (Waypoint point : waypoints) {
            if (point instanceof LastKnownWaypoint) {
                specificPoints.add(point);
            }
        }

        return specificPoints;
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
            artifacts = skCase.getBlackboardArtifacts(ARTIFACT_TYPE.TSK_GPS_BOOKMARK);
        } catch (TskCoreException ex) {
            throw new GeoLocationDataException("Unable to get artifacts for type: TSK_GPS_BOOKMARK", ex);//NON-NLS
        }

        List<Waypoint> points = new ArrayList<>();
        if (artifacts != null) {
            for (BlackboardArtifact artifact : artifacts) {
                try {
                    Waypoint point = new BookmarkWaypoint(artifact);
                    points.add(point);
                } catch (GeoLocationDataException ex) {
                    logger.log(Level.WARNING, String.format("No longitude or latitude available for TSK_GPS_BOOKMARK artifactID: %d", artifact.getArtifactID()), ex);//NON-NLS
                }
            }
        }
        return points;
    }

    /**
     * Returns a list of waypoints that come from TSK_GPS_LAST_KNOWN_LOCATION
     * artifacts.
     *
     * @param waypoints A list of waypoints
     *
     * @return A list of trackpoint waypoints or empty list if none were found.
     */
    public static List<Waypoint> getBookmarkWaypoints(List<Waypoint> waypoints) {
        List<Waypoint> specificPoints = new ArrayList<>();

        for (Waypoint point : waypoints) {
            if (point instanceof BookmarkWaypoint) {
                specificPoints.add(point);
            }
        }

        return specificPoints;
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
     * @param artifactTypes     List of types from which we want to get
     *                          waypoints.
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
    static public void getAllWaypoints(SleuthkitCase skCase, List<DataSource> dataSources, List<ARTIFACT_TYPE> artifactTypes, boolean showAll, int cntDaysFromRecent, boolean noTimeStamp, WaypointFilterQueryCallBack queryCallBack) throws GeoLocationDataException {
        String query = buildQuery(dataSources, showAll, cntDaysFromRecent, noTimeStamp);

        try {
            // The CaseDBAccessManager.select function will add a SELECT 
            // to the beginning of the query
            if (query.startsWith("SELECT")) { //NON-NLS
                query = query.replaceFirst("SELECT", ""); //NON-NLS
            }

            skCase.getCaseDbAccessManager().select(query, new CaseDbAccessManager.CaseDbAccessQueryCallback() {
                @Override
                public void process(ResultSet rs) {
                    GeoLocationParseResult<Waypoint> waypointResults = new GeoLocationParseResult<>();
                    try {
                        while (rs.next()) {
                            int artifact_type_id = rs.getInt("artifact_type_id"); //NON-NLS
                            long artifact_id = rs.getLong("artifact_id"); //NON-NLS

                            ARTIFACT_TYPE type = ARTIFACT_TYPE.fromID(artifact_type_id);
                            if (artifactTypes.contains(type)) {
                                waypointResults.add(getWaypointForArtifact(skCase.getBlackboardArtifact(artifact_id), type));
                            }

                        }

                        queryCallBack.process(waypointResults);
                    } catch (SQLException | TskCoreException ex) {
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

//      SELECT_WO_TIMESTAMP
//          SELECT DISTINCT artifact_id, artifact_type_id
//          FROM blackboard_attributes
//          WHERE artifact_id NOT IN (%s)
//          AND artifact_id IN (%s)
//        GEO_ARTIFACT_QUERY_ID_ONLY
//            SELECT artifact_id
//            FROM blackboard_attributes
//            WHERE attribute_type_id IN (%d, %d)
        return String.format(SELECT_WO_TIMESTAMP,
                String.format(GEO_ARTIFACT_QUERY_ID_ONLY, TIME_TYPE_IDS),
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
//          MOST_RECENT_TIME
//              SELECT MAX(value_int64) - (%d * 86400)
//              FROM blackboard_attributes
//              WHERE attribute_type_id IN(%s)
//              AND artifact_id
//              IN ( %s )
//       
            mostRecentQuery = String.format("AND value_int64 > (%s)", //NON-NLS           
                    String.format(MOST_RECENT_TIME,
                            cntDaysFromRecent, TIME_TYPE_IDS,
                            getWaypointListQuery(dataSources)
                    ));
        }

//      GEO_ARTIFACT_QUERY
//          SELECT artifact_id, artifact_type_id
//          FROM blackboard_attributes
//          WHERE attribute_type_id IN (%s)
        String query = String.format(GEO_ARTIFACT_QUERY, TIME_TYPE_IDS);

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
//      GEO_ARTIFACT_QUERY
//          SELECT artifact_id, artifact_type_id
//          FROM blackboard_attributes
//          WHERE attribute_type_id IN (%s)
            return String.format(GEO_ARTIFACT_QUERY, GEO_ATTRIBUTE_TYPE_IDS);
        }

        String dataSourceList = "";
        for (DataSource source : dataSources) {
            dataSourceList += Long.toString(source.getId()) + ",";
        }

        if (!dataSourceList.isEmpty()) {
            // Remove the last ,
            dataSourceList = dataSourceList.substring(0, dataSourceList.length() - 1);
        }

        return String.format(GEO_ARTIFACT_WITH_DATA_SOURCES_QUERY, GEO_ATTRIBUTE_TYPE_IDS,
                dataSourceList);
    }

    /**
     * A parser that could throw a GeoLocationDataException when there is a
     * parse issue.
     *
     * @param <T> The return type.
     */
    private interface ParserWithError<T> {

        T parse(BlackboardArtifact artifact) throws GeoLocationDataException;
    }

    /**
     * Parses one waypoint.
     *
     * @param parser   The parser to use.
     * @param artifact The artifact to be parsed.
     *
     * @return Returns a parse result that is either successful with a parsed
     *         waypoint or unsuccessful with an exception.
     */
    private static GeoLocationParseResult<Waypoint> parseWaypoint(ParserWithError<Waypoint> parser, BlackboardArtifact artifact) {
        try {
            return new GeoLocationParseResult<>(Arrays.asList(parser.parse(artifact)), true);
        } catch (GeoLocationDataException ex) {
            return new GeoLocationParseResult<>(null, false);
        }
    }

    /**
     * Parses a list of waypoints.
     *
     * @param parser   The parser to use.
     * @param artifact The artifact to be parsed.
     *
     * @return Returns a parse result that is either successful with a parsed
     *         waypoint or unsuccessful with an exception.
     */
    private static GeoLocationParseResult<Waypoint> parseWaypoints(ParserWithError<List<Waypoint>> parser, BlackboardArtifact artifact) {
        try {
            return new GeoLocationParseResult<>(parser.parse(artifact), true);
        } catch (GeoLocationDataException ignored) {
            return new GeoLocationParseResult<>(null, false);
        }
    }

    /**
     * Create a Waypoint object for the given Blackboard artifact.
     *
     * @param artifact The artifact to create the waypoint from
     * @param type     The type of artifact
     *
     * @return A new waypoint object
     */
    private static GeoLocationParseResult<Waypoint> getWaypointForArtifact(BlackboardArtifact artifact, ARTIFACT_TYPE type) {
        GeoLocationParseResult<Waypoint> waypoints = new GeoLocationParseResult<>();
        switch (type) {
            case TSK_METADATA_EXIF:
                waypoints.add(parseWaypoint(EXIFWaypoint::new, artifact));
                break;
            case TSK_GPS_BOOKMARK:
                waypoints.add(parseWaypoint(BookmarkWaypoint::new, artifact));
                break;
            case TSK_GPS_TRACKPOINT:
                waypoints.add(parseWaypoint(TrackpointWaypoint::new, artifact));
                break;
            case TSK_GPS_SEARCH:
                waypoints.add(parseWaypoint(SearchWaypoint::new, artifact));
                break;
            case TSK_GPS_ROUTE:
                waypoints.add(parseWaypoints((a) -> new Route(a).getRoute(), artifact));
                break;
            case TSK_GPS_LAST_KNOWN_LOCATION:
                waypoints.add(parseWaypoint(LastKnownWaypoint::new, artifact));
                break;
            case TSK_GPS_TRACK:
                waypoints.add(parseWaypoints((a) -> new Track(a).getPath(), artifact));
                break;
            default:
                waypoints.add(parseWaypoint(CustomArtifactWaypoint::new, artifact));
        }

        return waypoints;
    }
}
