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
from java.lang import Integer
from java.lang import Long
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
from org.sleuthkit.autopsy.coreutils import AppSQLiteDB
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.ingest import ModuleDataEvent
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import Blackboard
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel.Blackboard import BlackboardException
from org.sleuthkit.datamodel import Account
from org.sleuthkit.datamodel.blackboardutils.attributes import MessageAttachments
from org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments import FileAttachment
from org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments import URLAttachment
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection

import traceback
import general

class TextMessageAnalyzer(general.AndroidComponentAnalyzer):
    """
        Finds and parsers Android SMS/MMS database, and populates the blackboard with messages.
    """

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "com.android.providers.telephony"
        self._PARSER_NAME = "Android Message Parser"
        self._MESSAGE_TYPE = "Android Message"


    def analyze(self, dataSource, fileManager, context):
        selfAccountId = None
        messageDbs = AppSQLiteDB.findAppDatabases(dataSource, "mmssms.db", True, self._PACKAGE_NAME)
        for messageDb in messageDbs:
            try:
                current_case = Case.getCurrentCaseThrows()
                if selfAccountId is not None:
                    messageDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                                    self._PARSER_NAME,
                                                    messageDb.getDBFile(),
                                                    Account.Type.PHONE, Account.Type.IMO, selfAccountId, context.getJobId())
                else:
                    messageDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                                    self._PARSER_NAME,
                                                    messageDb.getDBFile(),
                                                    Account.Type.PHONE, context.getJobId()) 

                uuid = UUID.randomUUID().toString()
                messagesResultSet = messageDb.runQuery("SELECT address, date, read, type, subject, body, thread_id FROM sms;")
                if messagesResultSet is not None:
                    while messagesResultSet.next():
                        direction = ""
                        address = None
                        fromId = None
                        toId = None
                        
                        address = messagesResultSet.getString("address") # may be phone number, or other addresses
                        timeStamp = Long.valueOf(messagesResultSet.getString("date")) / 1000
                        read = messagesResultSet.getInt("read") # may be unread = 0, read = 1
                        subject = messagesResultSet.getString("subject") # message subject
                        msgBody = messagesResultSet.getString("body") # message body
                        thread_id = "{0}-{1}".format(uuid, messagesResultSet.getInt("thread_id"))
                        if messagesResultSet.getString("type") == "1":
                            direction = CommunicationDirection.INCOMING
                            fromId = address
                        else:
                            direction = CommunicationDirection.OUTGOING
                            toId = address

                        message_read = messagesResultSet.getInt("read") # may be unread = 0, read = 1
                        if (message_read == 1):
                            msgReadStatus = MessageReadStatus.READ
                        elif (message_read == 0):
                            msgReadStatus = MessageReadStatus.UNREAD
                        else:
                            msgReadStatus = MessageReadStatus.UNKNOWN

                        ## add a message
                        if address is not None:
                            messageArtifact = messageDbHelper.addMessage( 
                                                            self._MESSAGE_TYPE,
                                                            direction,
                                                            fromId,
                                                            toId,
                                                            timeStamp,
                                                            msgReadStatus,
                                                            subject,     # subject
                                                            msgBody,
                                                            thread_id)

                        
            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for Android messages.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except TskCoreException as ex:
                self._logger.log(Level.SEVERE, "Failed to add Android message artifacts.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
            except BlackboardException as ex:
                self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except NoCurrentCaseException as ex:
                self._logger.log(Level.WARNING, "No case currently open.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            finally:
                messageDb.close()           
