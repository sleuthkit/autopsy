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
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import Blackboard
from org.sleuthkit.autopsy.casemodule.services import FileManager
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import MessageNotifyUtil
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.datamodel import AbstractFile
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
        self._logger = Logger.getLogger("GoogleMapLocationAnalyzer")

    def analyze(self, dataSource, fileManager, context):
        try:
            absFiles = fileManager.findFiles(dataSource, "da_destination_history")
            if absFiles.isEmpty():
                return
            for abstractFile in absFiles:
                try:
                    jFile = File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.findGeoLocationsInDB(jFile.toString(), abstractFile)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing Google map locations", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, "Error finding Google map locations", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())

    def findGeoLocationsInDB(self, databasePath, abstractFile):
        if not databasePath:
            return

        try:
            Class.forName("org.sqlite.JDBC") # load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)
            statement = connection.createStatement()
        except (ClassNotFoundException, SQLException) as ex:
            self._logger.log(Level.SEVERE, "Error opening database", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
            return

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

                artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE)
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY, general.MODULE_NAME, "Destination"))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, general.MODULE_NAME, time))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END, general.MODULE_NAME, dest_lat))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END, general.MODULE_NAME, dest_lng))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START, general.MODULE_NAME, source_lat))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START, general.MODULE_NAME, source_lng))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, general.MODULE_NAME, dest_title))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION, general.MODULE_NAME, dest_address))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, general.MODULE_NAME, "Google Maps History"))

                try:
                    # index the artifact for keyword search
                    blackboard = Case.getCurrentCase().getServices().getBlackboard()
                    blackboard.indexArtifact(artifact)
                except Blackboard.BlackboardException as ex:
                    self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                    MessageNotifyUtil.Notify.error("Failed to index GPS route artifact for keyword search.", artifact.getDisplayName())

        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error parsing Google map locations to the Blackboard", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        finally:
            try:
                if resultSet is not None:
                    resultSet.close()
                statement.close()
                connection.close()
            except Exception as ex:
                self._logger.log(Level.SEVERE, "Error closing the database", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())

    # add periods 6 decimal places before the end.
    @staticmethod
    def convertGeo(s):
        length = len(s)
        if length > 6:
            return Double.valueOf(s[0 : length-6] + "." + s[length-6 : length])
        else:
            return Double.valueOf(s)
