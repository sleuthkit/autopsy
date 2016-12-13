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
Analyzes database created by browser that stores GEO location info.
"""
class BrowserLocationAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        try:
            abstractFiles = fileManager.findFiles(dataSource, "CachedGeoposition%.db")
            for abstractFile in abstractFiles:
                if abstractFile.getSize() == 0:
                    continue
                try:
                    jFile = File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.findGeoLocationsInDB(jFile.toString(), abstractFile)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing Browser Location files", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, "Error finding Browser Location files", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())

    def findGeoLocationsInDB(self, databasePath, abstractFile):
        if not databasePath:
            return

        try:
            Class.forName("org.sqlite.JDBC") #load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)
            statement = connection.createStatement()
        except (ClassNotFoundException, SQLException) as ex:
            self._logger.log(Level.SEVERE, "Error connecting to sql database", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
            return

        try:
            resultSet = statement.executeQuery("SELECT timestamp, latitude, longitude, accuracy FROM CachedPosition;")
            while resultSet.next():
                timestamp = Long.valueOf(resultSet.getString("timestamp")) / 1000
                latitude = Double.valueOf(resultSet.getString("latitude"))
                longitude = Double.valueOf(resultSet.getString("longitude"))

                artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT)
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, general.MODULE_NAME, latitude))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, general.MODULE_NAME, longitude))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, general.MODULE_NAME, timestamp))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, general.MODULE_NAME, "Browser Location History"))
                # artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(),moduleName, accuracy))
                # NOTE: originally commented out

                try:
                    # index the artifact for keyword search
                    blackboard = Case.getCurrentCase().getServices().getBlackboard()
                    blackboard.indexArtifact(artifact)
                except Blackboard.BlackboardException as ex:
                    self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactTypeName(), ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                    MessageNotifyUtil.Notify.error("Failed to index GPS trackpoint artifact for keyword search.", artifact.getDisplayName())

        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error putting artifacts to Blackboard", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        finally:
            try:
                if resultSet is not None:
                    resultSet.close()
                statement.close()
                connection.close()
            except Exception as ex:
                self._logger.log(Level.SEVERE, "Error closing database", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
