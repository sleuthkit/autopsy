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
from java.lang import Long
from java.lang import String
from java.sql import Connection
from java.sql import DriverManager
from java.sql import ResultSet
from java.sql import SQLException
from java.sql import Statement
from java.util.logging import Level
from java.util import ArrayList
from org.apache.commons.codec.binary import Base64
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
from org.sleuthkit.datamodel.Blackboard import BlackboardException
from org.sleuthkit.autopsy.coreutils import AppSQLiteDB
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection

import traceback
import general

"""
Locates database for the Tango app and adds info to blackboard.
"""
class TangoMessageAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "com.sgiggle.production"
        self._PARSER_NAME = "Tango Parser"
        self._MESSAGE_TYPE = "Tango Message"
        self._VERSION = "7"  # DB_VERSION in 'profiles' table
        

    def analyze(self, dataSource, fileManager, context):
        try:
           
            tangoDbFiles = AppSQLiteDB.findAppDatabases(dataSource, "tc.db", True, self._PACKAGE_NAME)
            for tangoDbFile in tangoDbFiles:
                try:
                    self.__findTangoMessagesInDB(tangoDbFile, dataSource, context)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing Tango messages", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            # Error finding Tango messages.
            pass

    def __findTangoMessagesInDB(self, tangoDb, dataSource, context):
        if not tangoDb:
            return

        try:
            current_case = Case.getCurrentCaseThrows()

            # Create a helper to parse the DB
            tangoDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                                    self._PARSER_NAME,
                                                    tangoDb.getDBFile(),
                                                    Account.Type.TANGO, context.getJobId())  

            resultSet = tangoDb.runQuery(
                "SELECT conv_id, create_time, direction, payload FROM messages ORDER BY create_time DESC;")

            while resultSet.next():
                fromId = None
                toId = None
                conv_id = resultSet.getString("conv_id") # seems to wrap around the message found in payload after decoding from base-64
                create_time = Long.valueOf(resultSet.getString("create_time")) / 1000
                
                if resultSet.getString("direction") == "1": # 1 incoming, 2 outgoing
                    direction = CommunicationDirection.INCOMING
                else:
                    direction = CommunicationDirection.OUTGOING
                    
                payload = resultSet.getString("payload")
                msgBody = TangoMessageAnalyzer.decodeMessage(conv_id, payload)
                
                messageArtifact = tangoDbHelper.addMessage( 
                                                            self._MESSAGE_TYPE,
                                                            direction,
                                                            fromId,
                                                            toId,
                                                            create_time,
                                                            MessageReadStatus.UNKNOWN,
                                                            "",     # subject
                                                            msgBody,
                                                            "")

        except SQLException as ex:
            self._logger.log(Level.WARNING, "Error processing query result for Tango messages", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, "Failed to add Tango message artifacts.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except NoCurrentCaseException as ex:
            self._logger.log(Level.WARNING, "No case currently open.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        finally:
            tangoDb.close()

    # take the message string which is wrapped by a certain string, and return the text enclosed.
    @staticmethod
    def decodeMessage(wrapper, message):
        result = ""
        decoded = Base64.decodeBase64(message)
        try:
            Z = String(decoded, "UTF-8")
            result = Z.split(wrapper)[1]
        except Exception as ex:
            # Error decoding a Tango message.
            pass
        return result
