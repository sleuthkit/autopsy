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
                    jFile = File(Case.getCurrentCase().getTempDirectory(), str(abstractFile.getId()) + abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.__findGeoLocationsInDB(jFile.toString(), abstractFile, context)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing browser location files", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            # Error finding browser location files.
            pass

    def __findGeoLocationsInDB(self, databasePath, abstractFile, context):
        if not databasePath:
            return

        try:
            Class.forName("org.sqlite.JDBC") #load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)
            statement = connection.createStatement()
        except (ClassNotFoundException) as ex:
            self._logger.log(Level.SEVERE, "Error loading JDBC driver", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
            return
        except (SQLException) as ex:
            # Error connecting to SQL databse.
            return

        resultSet = None
        try:
            resultSet = statement.executeQuery("SELECT timestamp, latitude, longitude, accuracy FROM CachedPosition;")
            while resultSet.next():
                timestamp = Long.valueOf(resultSet.getString("timestamp")) / 1000
                latitude = Double.valueOf(resultSet.getString("latitude"))
                longitude = Double.valueOf(resultSet.getString("longitude"))

                attributes = ArrayList()
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, general.MODULE_NAME, latitude))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, general.MODULE_NAME, longitude))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, general.MODULE_NAME, timestamp))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, general.MODULE_NAME, "Browser Location History"))
                artifact = abstractFile.newDataArtifact(BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK), attributes)
                # artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(),moduleName, accuracy))
                # NOTE: originally commented out

                try:
                    blackboard = Case.getCurrentCase().getSleuthkitCase().getBlackboard()
                    blackboard.postArtifact(artifact, general.MODULE_NAME, context.getJobId())
                except Blackboard.BlackboardException as ex:
                    self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + str(artifact.getArtifactTypeName()), ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                    MessageNotifyUtil.Notify.error("Failed to index GPS trackpoint artifact for keyword search.", artifact.getDisplayName())

        except SQLException as ex:
            # Unable to execute browser location SQL query against database.
            pass
        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error processing browser location history.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        finally:
            try:
                if resultSet is not None:
                    resultSet.close()
                statement.close()
                connection.close()
            except Exception as ex:
                # Error closing database.
                pass
