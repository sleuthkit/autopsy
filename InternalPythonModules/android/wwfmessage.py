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
from java.sql import Connection
from java.sql import DriverManager
from java.sql import ResultSet
from java.sql import SQLException
from java.sql import Statement
from java.util.logging import Level
from java.util import ArrayList
from java.util import UUID
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule import NoCurrentCaseException
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
from org.sleuthkit.datamodel import Account
from org.sleuthkit.datamodel import Relationship
from org.sleuthkit.datamodel.Blackboard import BlackboardException
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.ingest import ModuleDataEvent
from org.sleuthkit.autopsy.coreutils import AppSQLiteDB
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection

import traceback
import general

wwfAccountType = None


"""
Analyzes messages from Words With Friends
"""
class WWFMessageAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "com.zynga.words"
        self._PARSER_NAME = "Words With Friend Parser"
        self._MESSAGE_TYPE = "WWF Message"
        
    def analyze(self, dataSource, fileManager, context):
        try:

            # Create new account type, if doesnt exist
            global wwfAccountType
            wwfAccountType = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().addAccountType("WWF", "Words with Friends")

            wwfDbFiles = AppSQLiteDB.findAppDatabases(dataSource, "WordsFramework", True, self._PACKAGE_NAME)
            for wwfDbFile in wwfDbFiles:
                try:
                    self.__findWWFMessagesInDB(wwfDbFile, dataSource, context)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing WWF messages", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            # Error finding WWF messages.
            self._logger.log(Level.SEVERE, "Error finding WWF message files.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
            pass

    def __findWWFMessagesInDB(self, wwfDb, dataSource, context):
        if not wwfDb:
            return

        current_case = Case.getCurrentCaseThrows()

        # Create a helper to parse the DB
        wwfDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                                    self._PARSER_NAME,
                                                    wwfDb.getDBFile(),
                                                    wwfAccountType, context.getJobId())
            
        uuid = UUID.randomUUID().toString()

        resultSet = None
        try:
            resultSet = wwfDb.runQuery("SELECT message, strftime('%s' ,created_at) as datetime, user_id, game_id FROM chat_messages ORDER BY game_id DESC, created_at DESC;")

            while resultSet.next():
                message = resultSet.getString("message") # WWF Message
                created_at = resultSet.getLong("datetime")
                user_id = resultSet.getString("user_id") # the ID of the user who sent/received the message.
                game_id = resultSet.getString("game_id") # ID of the game which the the message was sent.
                thread_id = "{0}-{1}".format(uuid, user_id)

                messageArtifact = wwfDbHelper.addMessage( self._MESSAGE_TYPE,
                                                            CommunicationDirection.UNKNOWN,
                                                            user_id,    # fromId
                                                            None,       # toId
                                                            created_at,
                                                            MessageReadStatus.UNKNOWN,
                                                            "",     # subject
                                                            message,
                                                            thread_id)

        except SQLException as ex:
            self._logger.log(Level.WARNING, "Error processing query result for WWF messages", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, "Failed to add WWF message artifacts.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except NoCurrentCaseException as ex:
            self._logger.log(Level.WARNING, "No case currently open.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        finally:
            wwfDb.close()
