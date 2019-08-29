"""
Autopsy Forensic Browser

Copyright 2016-2018 Basis Technology Corp.
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
from org.sleuthkit.autopsy.casemodule.services import FileManager
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import MessageNotifyUtil
from org.sleuthkit.autopsy.coreutils import AppSQLiteDB
from org.sleuthkit.autopsy.coreutils import AppDBParserHelper
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import Blackboard
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException

import traceback
import general

"""
Analyzes database created by ORUX Maps.
"""
class OruxMapsAnalyzer(general.AndroidComponentAnalyzer):

    moduleName = "Orux Maps Analyzer"
    programName = "Orux Maps"
    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        oruxMapsTrackpointsDbs = AppSQLiteDB.findAppDatabases(dataSource, "oruxmapstracks.db", "oruxmaps")
        for oruxMapsTrackpointsDb in oruxMapsTrackpointsDbs:
            try:
                oruxDbHelper = AppDBParserHelper(self.moduleName, oruxMapsTrackpointsDb.getDBFile())
                
                poiQueryString = "SELECT poilat, poilon, poitime, poiname FROM pois"
                poisResultSet = oruxMapsTrackpointsDb.runQuery(poiQueryString)
                if poisResultSet is not None:
                    while poisResultSet.next():
                        oruxDbHelper.addGPSLocation(
                                            poisResultSet.getDouble("poilat"),
                                            poisResultSet.getDouble("poilon"),
                                            poisResultSet.getLong("poitime") / 1000,    # milliseconds since unix epoch
                                            poisResultSet.getString("poiname"),
                                            self.programName)
                        
                trackpointsQueryString = "SELECT trkptlat, trkptlon, trkpttime FROM trackpoints"
                trackpointsResultSet = oruxMapsTrackpointsDb.runQuery(trackpointsQueryString)
                if trackpointsResultSet is not None:
                    while trackpointsResultSet.next():
                        oruxDbHelper.addGPSLocation(
                                            trackpointsResultSet.getDouble("trkptlat"),
                                            trackpointsResultSet.getDouble("trkptlon"),
                                            trackpointsResultSet.getLong("trkpttime") / 1000,    # milliseconds since unix epoch
                                            "",
                                            self.programName)
            except SQLException as ex:
                self._logger.log(Level.SEVERE, "Error processing query result for Orux Map trackpoints", ex)       
            finally:
                oruxMapsTrackpointsDb.close()
