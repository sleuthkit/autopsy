"""
Autopsy Forensic Browser

Copyright 2016 Basis Technology Corp.
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
from java.lang import Long
from java.lang import String
from java.sql import Connection
from java.sql import DriverManager
from java.sql import ResultSet
from java.sql import SQLException
from java.sql import Statement
from java.util.logging import Level
from org.apache.commons.codec.binary import Base64
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
Locates database for the Tango app and adds info to blackboard.
"""
class TangoMessageAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        try:
            absFiles = fileManager.findFiles(dataSource, "tc.db")
            for abstractFile in absFiles:
                try:
                    jFile = File(Case.getCurrentCase().getTempDirectory(), str(abstractFile.getId()) + abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.__findTangoMessagesInDB(jFile.toString(), abstractFile)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing Tango messages", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, "Error finding Tango messages", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())

    def __findTangoMessagesInDB(self, databasePath, abstractFile):
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
                "SELECT conv_id, create_time, direction, payload FROM messages ORDER BY create_time DESC;")

            while resultSet.next():
                conv_id = resultSet.getString("conv_id") # seems to wrap around the message found in payload after decoding from base-64
                create_time = Long.valueOf(resultSet.getString("create_time")) / 1000
                if resultSet.getString("direction") == "1": # 1 incoming, 2 outgoing
                    direction = "Incoming"
                else:
                    direction = "Outgoing"
                payload = resultSet.getString("payload")

                artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE) #create a call log and then add attributes from result set.
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, general.MODULE_NAME, create_time))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, general.MODULE_NAME, direction))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, general.MODULE_NAME, TangoMessageAnalyzer.decodeMessage(conv_id, payload)))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, general.MODULE_NAME, "Tango Message"))

                try:
                    # index the artifact for keyword search
                    blackboard = Case.getCurrentCase().getServices().getBlackboard()
                    blackboard.indexArtifact(artifact)
                except Blackboard.BlackboardException as ex:
                    self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                    MessageNotifyUtil.Notify.error("Failed to index Tango message artifact for keyword search.", artifact.getDisplayName())

        except Exception as ex:
           self._logger.log(Level.SEVERE, "Error parsing Tango messages to the blackboard", ex)
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

    # take the message string which is wrapped by a certain string, and return the text enclosed.
    @staticmethod
    def decodeMessage(wrapper, message):
        result = ""
        decoded = Base64.decodeBase64(message)
        try:
            Z = String(decoded, "UTF-8")
            result = Z.split(wrapper)[1]
        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error decoding a Tango message", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        return result
