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
Finds and parses the Google Maps database.
"""
class GoogleMapLocationAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        try:
            absFiles = fileManager.findFiles(dataSource, "da_destination_history")
            if absFiles.isEmpty():
                return
            for abstractFile in absFiles:
                try:
                    jFile = File(Case.getCurrentCase().getTempDirectory(), str(abstractFile.getId()) + abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.__findGeoLocationsInDB(jFile.toString(), abstractFile)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing Google map locations", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            # Error finding Google map locations.
            pass

    def __findGeoLocationsInDB(self, databasePath, abstractFile):
        if not databasePath:
            return

        try:
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

                attributes = ArrayList()
                artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE)
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY, general.MODULE_NAME, "Destination"))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, general.MODULE_NAME, time))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END, general.MODULE_NAME, dest_lat))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END, general.MODULE_NAME, dest_lng))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START, general.MODULE_NAME, source_lat))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START, general.MODULE_NAME, source_lng))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, general.MODULE_NAME, dest_title))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION, general.MODULE_NAME, dest_address))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, general.MODULE_NAME, "Google Maps History"))

                artifact.addAttributes(attributes)
                try:
                    # index the artifact for keyword search
                    blackboard = Case.getCurrentCase().getSleuthkitCase().getBlackboard()
                    blackboard.postArtifact(artifact, MODULE_NAME)
                except Blackboard.BlackboardException as ex:
                    self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + str(artifact.getArtifactID()), ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                    MessageNotifyUtil.Notify.error("Failed to index GPS route artifact for keyword search.", artifact.getDisplayName())

        except SQLException as ex:
            # Unable to execute Google map locations SQL query against database.
            pass
        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error parsing Google map locations to the blackboard", ex)
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
