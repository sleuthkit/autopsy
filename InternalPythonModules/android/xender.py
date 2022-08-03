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
from org.sleuthkit.datamodel.blackboardutils.attributes import MessageAttachments
from org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments import FileAttachment
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection
import traceback
import general


class XenderAnalyzer(general.AndroidComponentAnalyzer):

    """
        Xender is a file transfer utility app.
        
        This module finds the SQLite DB for Xender, parses the DB for contacts & messages,
        and adds artifacts to the case.

        Xender version 4.6.5 has the following database structure:
            - trans-history.db 
                -- A profile table with the device_id/name of users interacted with
                -- A new_history table, with records of files exchanged with other users
                    --- f_path - path of the file sent/received
                
    """
   
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "cn.xender"
        self._MODULE_NAME = "Xender Analyzer"
        self._MESSAGE_TYPE = "Xender Message"
        self._VERSION = "4.6.5"
        

    def analyze(self, dataSource, fileManager, context):
        selfAccountId = None
        transactionDbs = AppSQLiteDB.findAppDatabases(dataSource, "trans-history-db", True, self._PACKAGE_NAME)
        for transactionDb in transactionDbs:
            try:
                current_case = Case.getCurrentCaseThrows()
                # get the profile with connection_times 0, that's the self account.
                profilesResultSet = transactionDb.runQuery("SELECT device_id, nick_name FROM profile WHERE connect_times = 0")
                if profilesResultSet:
                    while profilesResultSet.next():
                        if not selfAccountId:
                            selfAccountId = profilesResultSet.getString("device_id")
                # create artifacts helper
                if selfAccountId is not None:
                    transactionDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                            self._MODULE_NAME, transactionDb.getDBFile(),
                                            Account.Type.XENDER, Account.Type.XENDER, selfAccountId, context.getJobId())
                else:
                    transactionDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                            self._MODULE_NAME, transactionDb.getDBFile(),
                                            Account.Type.XENDER, context.getJobId())

                queryString = """
                                SELECT f_path, f_display_name, f_size_str, c_start_time, c_direction, c_session_id,
                                    s_name, s_device_id, r_name, r_device_id
                                FROM new_history
                              """
                messagesResultSet = transactionDb.runQuery(queryString)
                if messagesResultSet is not None:
                    while messagesResultSet.next():
                        direction = CommunicationDirection.UNKNOWN
                        fromId = None
                        toId = None
                    
                        fileAttachments = ArrayList()
                    
                        if (messagesResultSet.getInt("c_direction") == 1):
                            direction = CommunicationDirection.OUTGOING
                            toId = messagesResultSet.getString("r_device_id")
                        else:
                            direction = CommunicationDirection.INCOMING
                            fromId = messagesResultSet.getString("s_device_id")                          

                        timeStamp = messagesResultSet.getLong("c_start_time") / 1000
                        messageArtifact = transactionDbHelper.addMessage( 
                                                            self._MESSAGE_TYPE,
                                                            direction,
                                                            fromId,
                                                            toId,
                                                            timeStamp,
                                                            MessageReadStatus.UNKNOWN,
                                                            None,   # subject
                                                            None,   # message text
                                                            messagesResultSet.getString("c_session_id") )
                                                                                                
                        # add the file as attachment 
                        fileAttachments.add(FileAttachment(current_case.getSleuthkitCase(), transactionDb.getDBFile().getDataSource(), messagesResultSet.getString("f_path")))
                        messageAttachments = MessageAttachments(fileAttachments, [])
                        transactionDbHelper.addAttachments(messageArtifact, messageAttachments)

            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for profiles.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except TskCoreException as ex:
                self._logger.log(Level.SEVERE, "Failed to create Xender message artifacts.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
            except BlackboardException as ex:
                self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except NoCurrentCaseException as ex:
                self._logger.log(Level.WARNING, "No case currently open.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            finally:
                transactionDb.close()
                

    
