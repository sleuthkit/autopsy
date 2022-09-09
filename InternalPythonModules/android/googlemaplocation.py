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
from org.sleuthkit.autopsy.casemodule.services import FileManager
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import MessageNotifyUtil
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
from org.sleuthkit.datamodel.blackboardutils.attributes import GeoWaypoints
from org.sleuthkit.datamodel.blackboardutils.attributes.GeoWaypoints import Waypoint

import traceback
import general

"""
Finds and parses the Google Maps database.
"""
class GoogleMapLocationAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self.current_case = None
        self.PROGRAM_NAME = "Google Maps History"
        self.CAT_DESTINATION = "Destination"

    def analyze(self, dataSource, fileManager, context):
        try:
            self.current_case = Case.getCurrentCaseThrows()
        except NoCurrentCaseException as ex:
            self._logger.log(Level.WARNING, "No case currently open.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
            return

        try:
            absFiles = fileManager.findFiles(dataSource, "da_destination_history")
            if absFiles.isEmpty():
                return
            for abstractFile in absFiles:
                try:
                    jFile = File(self.current_case.getTempDirectory(), str(abstractFile.getId()) + abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.__findGeoLocationsInDB(jFile.toString(), abstractFile, context)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing Google map locations", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            # Error finding Google map locations.
            pass

    def __findGeoLocationsInDB(self, databasePath, abstractFile, context):
        if not databasePath:
            return

        try:
            artifactHelper = GeoArtifactsHelper(self.current_case.getSleuthkitCase(),
                                    general.MODULE_NAME, self.PROGRAM_NAME, abstractFile, context.getJobId())
            Class.forName("org.sqlite.JDBC") # load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)
            statement = connection.createStatement()
        except (ClassNotFoundException) as ex:
            self._logger.log(Level.SEVERE, "Error loading JDBC driver", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
            return
        except (SQLException) as ex:
            # Error opening database.
            return

        resultSet = None
        try:
            resultSet = statement.executeQuery(
                "SELECT time, dest_lat, dest_lng, dest_title, dest_address, source_lat, source_lng FROM destination_history;")

            while resultSet.next():
                time = Long.valueOf(resultSet.getString("time")) / 1000
                dest_title = resultSet.getString("dest_title")
                dest_address = resultSet.getString("dest_address")

                dest_lat = GoogleMapLocationAnalyzer.convertGeo(resultSet.getString("dest_lat"))
                dest_lng = GoogleMapLocationAnalyzer.convertGeo(resultSet.getString("dest_lng"))
                source_lat = GoogleMapLocationAnalyzer.convertGeo(resultSet.getString("source_lat"))
                source_lng = GoogleMapLocationAnalyzer.convertGeo(resultSet.getString("source_lng"))

                waypointlist = GeoWaypoints()
                waypointlist.addPoint(Waypoint(source_lat, source_lng, None, None))
                waypointlist.addPoint(Waypoint(dest_lat, dest_lng, None, dest_address))
				
                artifactHelper.addRoute(dest_title, time, waypointlist, None)

        except SQLException as ex:
            # Unable to execute Google map locations SQL query against database.
            pass
        except TskCoreException as ex:
                self._logger.log(Level.SEVERE, "Failed to add route artifacts.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
                self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error processing google maps history.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        finally:
            try:
                if resultSet is not None:
                    resultSet.close()
                statement.close()
                connection.close()
            except Exception as ex:
                # Error closing the database.
                pass

    # add periods 6 decimal places before the end.
    @staticmethod
    def convertGeo(s):
        length = len(s)
        if length > 6:
            return Double.valueOf(s[0 : length-6] + "." + s[length-6 : length])
        else:
            return Double.valueOf(s)
