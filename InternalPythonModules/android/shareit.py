"""
Autopsy Forensic Browser

Copyright 2019-2021 Basis Technology Corp.
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
from java.sql import ResultSet
from java.sql import SQLException
from java.sql import Statement
from java.util.logging import Level
from java.util import ArrayList
from org.apache.commons.codec.binary import Base64
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule import NoCurrentCaseException
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import MessageNotifyUtil
from org.sleuthkit.autopsy.coreutils import AppSQLiteDB
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel.Blackboard import BlackboardException
from org.sleuthkit.datamodel import Account
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.attributes import MessageAttachments
from org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments import FileAttachment
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection

import traceback
import general

"""
Finds the SQLite DB for ShareIt, parses the DB for contacts & messages,
and adds artifacts to the case.
"""
class ShareItAnalyzer(general.AndroidComponentAnalyzer):

    """
        ShareIt is a file transfer utility app.
        
        This module finds the SQLite DB for Xender, parses the DB for contacts & messages,
        and adds artifacts to the case.

        ShareIt version 5.0.28 has the following database structure:
            - history.db 
                -- A history table, with records of file transfers 
                -- An item table with details of the files transfered
                    
                
    """

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "com.lenovo.anyshare.gps"
        self._MODULE_NAME = "ShareIt Analyzer"
        self._MESSAGE_TYPE = "ShareIt Message"
        self._VERSION = "5.0.28_ww"

    def analyze(self, dataSource, fileManager, context):
        historyDbs = AppSQLiteDB.findAppDatabases(dataSource, "history.db", True, self._PACKAGE_NAME)
        for historyDb in historyDbs:
            try:
                current_case = Case.getCurrentCaseThrows()
                historyDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                    self._MODULE_NAME, historyDb.getDBFile(),
                                    Account.Type.SHAREIT, context.getJobId())

                queryString = """
                                SELECT history_type, device_id, device_name, description, timestamp, file_path
                                FROM history
                                JOIN item where history.content_id = item.item_id
                              """
                historyResultSet = historyDb.runQuery(queryString)
                if historyResultSet is not None:
                    while historyResultSet.next():
                        direction = ""
                        fromId = None
                        toId = None
                        fileAttachments = ArrayList()
                        
                        if (historyResultSet.getInt("history_type") == 1):
                            direction = CommunicationDirection.INCOMING
                            fromId = historyResultSet.getString("device_id")
                        else:
                            direction = CommunicationDirection.OUTGOING
                            toId = historyResultSet.getString("device_id")
                            
                        timeStamp = historyResultSet.getLong("timestamp") / 1000
                        messageArtifact = historyDbHelper.addMessage(
                                                            self._MESSAGE_TYPE,
                                                            direction,
                                                            fromId,
                                                            toId,
                                                            timeStamp,
                                                            MessageReadStatus.UNKNOWN,
                                                            None,   # subject
                                                            None,   # message text
                                                            None )  # thread id
                                                                                                
                        # add the file as attachment
                        fileAttachments.add(FileAttachment(current_case.getSleuthkitCase(), historyDb.getDBFile().getDataSource(), historyResultSet.getString("file_path")))
                        messageAttachments = MessageAttachments(fileAttachments, [])
                        historyDbHelper.addAttachments(messageArtifact, messageAttachments)
                        

            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for ShareIt history.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
            except TskCoreException as ex:
                self._logger.log(Level.SEVERE, "Failed to create ShareIt message artifacts.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
            except BlackboardException as ex:
                self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except NoCurrentCaseException as ex:
                self._logger.log(Level.WARNING, "No case currently open.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            finally:
                historyDb.close()
                

    
