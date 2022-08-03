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
from org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments import URLAttachment
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection

import json
import traceback
import general


class IMOAnalyzer(general.AndroidComponentAnalyzer):
    """
        Finds the SQLite DB for IMO, parses the DB for contacts & messages,
        and adds artifacts to the case.

        IMO version 9.8.0 has the following database structure:
            - accountdb.db 
                -- A 'account' table with the id/name of the IMO account of the owner - used as the self account
            - imofriends.db - a database with contacts and messages
                -- A friends table, with id and name of the friends
                    --- buid - application specific unique id
                    --- name of contact
                -- A messages table which stores the message details
                    --- sender/receiver buid, timestamp, message_type (1: incoming, 0: outgoing), message_read...
                    --- 'imdata' column stores a json structure with all the message details, including attachments
                    ----  attachment file path may be specified in local_path or original_path.  Original path, if available is a better candidate.
                    ----  For sent files, files seem to get uploaded to IMO Servers.  There is no URL available in the imdata though.

    """
    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "com.imo.android.imous"
        self._PARSER_NAME = "IMO Parser"
        self._MESSAGE_TYPE = "IMO Message"
        self._VERSION = "9.8.0"

    def analyze(self, dataSource, fileManager, context):
        selfAccountId = None
        accountDbs = AppSQLiteDB.findAppDatabases(dataSource, "accountdb.db", True, self._PACKAGE_NAME)
        for accountDb in accountDbs:
            try:
                accountResultSet = accountDb.runQuery("SELECT uid, name FROM account")
                if accountResultSet:
                    # We can determine the IMO user ID of the device owner. 
                    # Therefore we can create and use a app account and use that 
                    # as a 'self' account instead of a Device account
                    if not selfAccountId:
                        selfAccountId = accountResultSet.getString("uid")
            
            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for account", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            finally:
                accountDb.close()
                        
        friendsDbs = AppSQLiteDB.findAppDatabases(dataSource, "imofriends.db", True, self._PACKAGE_NAME)
        for friendsDb in friendsDbs:
            try:
                current_case = Case.getCurrentCaseThrows()
                if selfAccountId is not None:
                    friendsDBHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                                    self._PARSER_NAME,
                                                    friendsDb.getDBFile(),
                                                    Account.Type.IMO, Account.Type.IMO, selfAccountId, context.getJobId())
                else:
                   friendsDBHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                                    self._PARSER_NAME,
                                                    friendsDb.getDBFile(),
                                                    Account.Type.IMO, context.getJobId()) 
                contactsResultSet = friendsDb.runQuery("SELECT buid, name FROM friends")
                if contactsResultSet is not None:
                    while contactsResultSet.next():
                        contactId = contactsResultSet.getString("buid")
                        
                        ## add a  TSK_ID attribute with contact's IMO Id
                        additionalAttributes = ArrayList()
                        additionalAttributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ID, self._PARSER_NAME, contactId))
                         
                        friendsDBHelper.addContact( contactsResultSet.getString("name"),       ##  contact name
                                                    "", 	## phone
                                                    "", 	## home phone
                                                    "", 	## mobile
                                                    "",	        ## email
                                                    additionalAttributes)
                        
                queryString = """
                                SELECT messages.buid AS buid, imdata, last_message, timestamp, message_type, message_read, name
                                FROM messages
                                INNER JOIN friends ON friends.buid = messages.buid
                              """
                messagesResultSet = friendsDb.runQuery(queryString)
                if messagesResultSet is not None:
                    while messagesResultSet.next():
                        direction = ""
                        fromId = None
                        toId = None
                        name = messagesResultSet.getString("name")
                        uniqueId = messagesResultSet.getString("buid")

                        if (messagesResultSet.getInt("message_type") == 1):
                            direction = CommunicationDirection.INCOMING
                            fromId = uniqueId
                        else:
                            direction = CommunicationDirection.OUTGOING
                            toId = uniqueId
                        
                        
                        message_read = messagesResultSet.getInt("message_read")
                        if (message_read == 1):
                            msgReadStatus = MessageReadStatus.READ
                        elif (message_read == 0):
                            msgReadStatus = MessageReadStatus.UNREAD
                        else:
                            msgReadStatus = MessageReadStatus.UNKNOWN
                                                
                        timeStamp = messagesResultSet.getLong("timestamp") / 1000000000
                        msgBody = messagesResultSet.getString("last_message")

                        messageArtifact = friendsDBHelper.addMessage( 
                                                            self._MESSAGE_TYPE,
                                                            direction,
                                                            fromId,
                                                            toId,
                                                            timeStamp,
                                                            msgReadStatus,
                                                            "",     # subject
                                                            msgBody,
                                                            "")   # thread id

                                                                           
                        # Parse the imdata JSON structure to check if there is an attachment.
                        # If one exists, create an attachment and add to the message.
                        fileAttachments = ArrayList()
                        urlAttachments = ArrayList()
                          
                        imdataJsonStr = messagesResultSet.getString("imdata")
                        if imdataJsonStr is not None:
                            imdata_dict = json.loads(imdataJsonStr)
                            
                            # set to none if the key doesn't exist in the dict
                            attachmentOriginalPath = imdata_dict.get('original_path', None)
                            attachmentLocalPath = imdata_dict.get('local_path', None)
                            if attachmentOriginalPath:
                                attachmentPath = attachmentOriginalPath
                            else:
                                attachmentPath = attachmentLocalPath
                                
                            if attachmentPath:
                                # Create a file attachment with given path
                                fileAttachment = FileAttachment(current_case.getSleuthkitCase(), friendsDb.getDBFile().getDataSource(), attachmentPath)
                                fileAttachments.add(fileAttachment)
                                
                                msgAttachments = MessageAttachments(fileAttachments, [])
                                attachmentArtifact = friendsDBHelper.addAttachments(messageArtifact, msgAttachments)
                    
            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for IMO friends", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except TskCoreException as ex:
                self._logger.log(Level.SEVERE, "Failed to add IMO message artifacts.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
            except BlackboardException as ex:
                self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except NoCurrentCaseException as ex:
                self._logger.log(Level.WARNING, "No case currently open.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            finally:
                friendsDb.close()
                

    
