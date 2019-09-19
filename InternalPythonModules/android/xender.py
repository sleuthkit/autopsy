"""
Autopsy Forensic Browser

Copyright 2019 Basis Technology Corp.
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
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection
import traceback
import general

"""
Finds the SQLite DB for Xender, parses the DB for contacts & messages,
and adds artifacts to the case.
"""
class XenderAnalyzer(general.AndroidComponentAnalyzer):
   
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "cn.xender"
        self._MODULE_NAME = "Xender Analyzer"
        self._MESSAGE_TYPE = "Xender Message"
        self._VERSION = "4.6.5"
        

    def analyze(self, dataSource, fileManager, context):
        selfAccountAddress = None
        transactionDbs = AppSQLiteDB.findAppDatabases(dataSource, "trans-history-db", True, self._PACKAGE_NAME)
        for transactionDb in transactionDbs:
            try:
                current_case = Case.getCurrentCaseThrows()
                # get the profile with connection_times 0, that's the self account.
                profilesResultSet = transactionDb.runQuery("SELECT device_id, nick_name FROM profile WHERE connect_times = 0")
                if profilesResultSet:
                    while profilesResultSet.next():
                        if not selfAccountAddress:
                            selfAccountAddress = Account.Address(profilesResultSet.getString("device_id"), profilesResultSet.getString("nick_name"))
                # create artifacts helper
                if selfAccountAddress is not None:
                    transactionDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                            self._MODULE_NAME, transactionDb.getDBFile(),
                                            Account.Type.XENDER, Account.Type.XENDER, selfAccountAddress )
                else:
                    transactionDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                            self._MODULE_NAME, transactionDb.getDBFile(),
                                            Account.Type.XENDER)

                queryString = "SELECT f_path, f_display_name, f_size_str, f_create_time, c_direction, c_session_id, s_name, s_device_id, r_name, r_device_id FROM new_history "
                messagesResultSet = transactionDb.runQuery(queryString)
                if messagesResultSet is not None:
                    while messagesResultSet.next():
                        direction = CommunicationDirection.UNKNOWN
                        fromAddress = None
                        toAdddress = None
                    
                        if (messagesResultSet.getInt("c_direction") == 1):
                            direction = CommunicationDirection.OUTGOING
                            toAddress = Account.Address(messagesResultSet.getString("r_device_id"), messagesResultSet.getString("r_name"))
                        else:
                            direction = CommunicationDirection.INCOMING
                            fromAddress = Account.Address(messagesResultSet.getString("s_device_id"), messagesResultSet.getString("s_name"))                            

                        msgBody = ""    # there is no body.
                        attachments = [messagesResultSet.getString("f_path")]
                        msgBody = general.appendAttachmentList(msgBody, attachments)
                        
                        timeStamp = messagesResultSet.getLong("f_create_time") / 1000
                        messageArtifact = transactionDbHelper.addMessage( 
                                                            self._MESSAGE_TYPE,
                                                            direction,
                                                            fromAddress,
                                                            toAddress,
                                                            timeStamp,
                                                            MessageReadStatus.UNKNOWN,
                                                            None,   # subject
                                                            msgBody,
                                                            messagesResultSet.getString("c_session_id") )
                                                                                                
                        # TBD: add the file as attachment ??

            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for profiles", ex)
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
                

    
