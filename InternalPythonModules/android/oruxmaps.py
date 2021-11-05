"""
Autopsy Forensic Browser

Copyright 2016-2021 Basis Technology Corp.
Contact: carrier <at> sleuthkit <dot> org

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

from java.io import File
from java.lang import Class
from java.lang import ClassNotFoundException
from java.lang import Double
from java.lang import Long
from java.sql import Connection
from java.sql import DriverManager
from java.sql import ResultSet
from java.sql import SQLException
from java.sql import Statement
from java.util.logging import Level
from java.util import ArrayList
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule import NoCurrentCaseException
from org.sleuthkit.autopsy.casemodule.services import FileManager
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import MessageNotifyUtil
from org.sleuthkit.autopsy.coreutils import AppSQLiteDB
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import Blackboard
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel.Blackboard import BlackboardException
from org.sleuthkit.datamodel.blackboardutils import GeoArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.attributes import GeoTrackPoints
from org.sleuthkit.datamodel.blackboardutils.attributes.GeoTrackPoints import TrackPoint

import traceback
import general

"""
Analyzes database created by ORUX Maps.
"""
class OruxMapsAnalyzer(general.AndroidComponentAnalyzer):

    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "oruxmaps"
        self._MODULE_NAME = "OruxMaps Analyzer"
        self._PROGRAM_NAME = "OruxMaps"
        self._VERSION = "7.5.7"
        self.current_case = None

    def analyze(self, dataSource, fileManager, context):
        oruxMapsTrackpointsDbs = AppSQLiteDB.findAppDatabases(dataSource, "oruxmapstracks.db", True, self._PACKAGE_NAME)
        for oruxMapsTrackpointsDb in oruxMapsTrackpointsDbs:
            try:
                current_case = Case.getCurrentCaseThrows()
                
                skCase = Case.getCurrentCase().getSleuthkitCase()
                geoArtifactHelper = GeoArtifactsHelper(skCase, self._MODULE_NAME, self._PROGRAM_NAME, oruxMapsTrackpointsDb.getDBFile(), context.getJobId()) 

                poiQueryString = "SELECT poilat, poilon, poialt, poitime, poiname FROM pois"
                poisResultSet = oruxMapsTrackpointsDb.runQuery(poiQueryString)
                abstractFile = oruxMapsTrackpointsDb.getDBFile()
                if poisResultSet is not None:
                    while poisResultSet.next():
                        latitude = poisResultSet.getDouble("poilat")
                        longitude = poisResultSet.getDouble("poilon")
                        time = poisResultSet.getLong("poitime") / 1000    # milliseconds since unix epoch
                        name = poisResultSet.getString("poiname")
                        altitude = poisResultSet.getDouble("poialt")

                        attributes = ArrayList()
                        attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, self._MODULE_NAME, time))
                        attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, self._MODULE_NAME, latitude))
                        attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, self._MODULE_NAME, longitude))
                        attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE, self._MODULE_NAME, altitude))
                        attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, self._MODULE_NAME, name))
                        attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, self._MODULE_NAME, self._PROGRAM_NAME))
						
                        artifact = abstractFile.newDataArtifact(BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK), attributes)
                        
                        try:
                            blackboard = Case.getCurrentCase().getSleuthkitCase().getBlackboard()
                            blackboard.postArtifact(artifact, self._MODULE_NAME, context.getJobId())
                        except Blackboard.BlackboardException as ex:
                            self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + str(artifact.getArtifactID()), ex)
                            self._logger.log(Level.SEVERE, traceback.format_exc())
                            MessageNotifyUtil.Notify.error("Failed to index trackpoint artifact for keyword search.", artifact.getDisplayName())
                        

                # tracks -> segments -> trackpoints
                #
                # The reason that the track and the segment are put into arrays is that once the segment query is run an error occurs that it cannot find the 
                # trackname column in the track query.  This is avoided if all the tracks/segments are found and put into an array(s) that can then be processed all at once.
                trackQueryString = "SELECT _id, trackname, trackciudad FROM tracks"
                trackResultSet = oruxMapsTrackpointsDb.runQuery(trackQueryString)
                if trackResultSet is not None:
                    trackResults = ArrayList()
                    while trackResultSet.next():
                        tempTrack = ArrayList()
                        trackName = trackResultSet.getString("trackname") + " - " + trackResultSet.getString("trackciudad")
                        trackId = str(trackResultSet.getInt("_id"))
                        tempTrack.append(trackId)
                        tempTrack.append(trackName)
                        trackResults.append(tempTrack)
                    for trackResult in trackResults:
                        trackId = trackResult[0]
                        trackName = trackResult[1]
                        segmentQueryString = "SELECT _id, segname FROM segments WHERE segtrack = " + trackId
                        segmentResultSet = oruxMapsTrackpointsDb.runQuery(segmentQueryString)
                        if segmentResultSet is not None:
                            segmentResults = ArrayList()
                            while segmentResultSet.next():
                                segmentName = trackName + " - " + segmentResultSet.getString("segname")
                                segmentId = str(segmentResultSet.getInt("_id"))
                                tempSegment = ArrayList()
                                tempSegment.append(segmentId)
                                tempSegment.append(segmentName)
                                segmentResults.append(tempSegment)
                            for segmentResult in segmentResults:
                                segmentId = segmentResult[0]
                                segmentName = segmentResult[1]                                
                                trackpointsQueryString = "SELECT trkptlat, trkptlon, trkptalt, trkpttime FROM trackpoints WHERE trkptseg = " + segmentId
                                trackpointsResultSet = oruxMapsTrackpointsDb.runQuery(trackpointsQueryString)
                                if trackpointsResultSet is not None:
                                    geoPointList = GeoTrackPoints()                            
                                    while trackpointsResultSet.next():
                                        latitude = trackpointsResultSet.getDouble("trkptlat")
                                        longitude = trackpointsResultSet.getDouble("trkptlon")
                                        altitude = trackpointsResultSet.getDouble("trkptalt")
                                        time = trackpointsResultSet.getLong("trkpttime") / 1000    # milliseconds since unix epoch
                                        
                                        geoPointList.addPoint(TrackPoint(latitude, longitude, altitude, segmentName, 0, 0, 0, time))

                                    try:
                                        geoartifact = geoArtifactHelper.addTrack(segmentName, geoPointList, None)
                                    except Blackboard.BlackboardException as ex:
                                        self._logger.log(Level.SEVERE, "Error using geo artifact helper with blackboard", ex)
                                        self._logger.log(Level.SEVERE, traceback.format_exc())
                                        MessageNotifyUtil.Notify.error("Failed to add track artifact.", "geoArtifactHelper")
                                    except TskCoreException as e:
                                        self._logger.log(Level.SEVERE, "Error using geo artifact helper with TskCoreException", ex)
                                        self._logger.log(Level.SEVERE, traceback.format_exc())
                                        MessageNotifyUtil.Notify.error("Failed to add track artifact with TskCoreException.", "geoArtifactHelper")
                        
            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for Orux Map trackpoints.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except TskCoreException as ex:
                self._logger.log(Level.SEVERE, "Failed to add Orux Map trackpoint artifacts.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
            except BlackboardException as ex:
                self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except NoCurrentCaseException as ex:
                self._logger.log(Level.WARNING, "No case currently open.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            finally:
                oruxMapsTrackpointsDb.close()
