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
Analyzes messages from Words With Friends
"""
class WWFMessageAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        try:
            absFiles = fileManager.findFiles(dataSource, "WordsFramework")
            for abstractFile in absFiles:
                try:
                    jFile = File(Case.getCurrentCase().getTempDirectory(), str(abstractFile.getId()) + abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.__findWWFMessagesInDB(jFile.toString(), abstractFile)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing WWF messages", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, "Error finding WWF messages", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())

    def __findWWFMessagesInDB(self, databasePath, abstractFile):
        if not databasePath:
            return

        try:
            Class.forName("org.sqlite.JDBC"); # load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)
            statement = connection.createStatement()
        except (ClassNotFoundException, SQLException) as ex:
            self._logger.log(Level.SEVERE, "Error opening database", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
            return

        try:
            resultSet = statement.executeQuery(
                    "SELECT message, strftime('%s' ,created_at) as datetime, user_id, game_id FROM chat_messages ORDER BY game_id DESC, created_at DESC;")

            while resultSet.next():
                message = resultSet.getString("message") # WWF Message
                created_at = resultSet.getLong("datetime")
                user_id = resultSet.getString("user_id") # the ID of the user who sent the message.
                game_id = resultSet.getString("game_id") # ID of the game which the the message was sent.

                attributes = ArrayList()
                artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE) # create a call log and then add attributes from result set.
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, general.MODULE_NAME, created_at))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, general.MODULE_NAME, user_id))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MSG_ID, general.MODULE_NAME, game_id))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, general.MODULE_NAME, message))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, general.MODULE_NAME, "Words With Friends Message"))

                artifact.addAttrisbutes(attributes)
                try:
                    # index the artifact for keyword search
                    blackboard = Case.getCurrentCase().getServices().getBlackboard()
                    blackboard.indexArtifact(artifact)
                except Blackboard.BlackboardException as ex:
                    self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                    MessageNotifyUtil.Notify.error("Failed to index WWF message artifact for keyword search.", artifact.getDisplayName())

        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error parsing WWF messages to the blackboard", ex)
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
