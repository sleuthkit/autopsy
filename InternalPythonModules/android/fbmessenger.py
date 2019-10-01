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
from java.lang import IllegalArgumentException
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
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection

import json
import traceback
import general


class FBMessengerAnalyzer(general.AndroidComponentAnalyzer):

    """
        Facebook Messenger is a messaging application for Facebook users.
        It can be used to have one-to-one as well as group message conversations -
        text, send photos videos and other media file. It can also be used to make
        phone calls - audio as well as video.
        
        This module finds the SQLite DB for FB messenger, parses the DB for contacts,
            messages, and call logs and creates artifacts.

        FB messenger requires Facebook accounts.  Although Facebook and Facebook Messenger are
        two different applications with separate packages, their database structure is very similar
        and FB messenger seems to share the FB database if FB is inatalled.

        FB assigns each user a unique FB id, fbid - a long numeric id.
        Each user also has a display name.

        FB uses a notion of user key, which is of the form FACEBOOK:<fbid> 
        
        FB messenger version 239.0.0.41 has the following database structure:
            - contacts_db2 
                -- A contacts table that stores the contacts/friends. 
            
            - threads_db2
                -- A messages table to store the messages
                    --- A sender column - this is a JSON structure which has a the FB user key of sender.
                    --- A attachments column - a JSON structure that has details of the attachments,
                    --- A msg_type column: message type - indicates whether its a text/mms message or a audio/video call
                    --- A thread_key column - identifies the message thread
                    --- A timestamp_ms column - date/time message was sent
                    --- A text column - message text, if applicable
                
                -- A thread_participants table to identify participants in a particular thread
                    --- A thread_key column - identifies a message thread
                    --- A user_key column to identify a particpant in the thread
                
                
                -- A thread_users to identify the user details, primarliy name, of a user that has been a particiapnt in any thread
                    --- A user_key column - identifies a unique user
                    --- A name column - user display name
                    
                
    """
     
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        
        self._FB_MESSENGER_PACKAGE_NAME = "com.facebook.orca"
        self._FACEBOOK_PACKAGE_NAME = "com.facebook.katana"
        self._MODULE_NAME = "FB Messenger Analyzer"
        self._MESSAGE_TYPE = "Facebook Messenger"
        self._VERSION = "239.0.0.41"  ## FB version number. Did not find independent version number in FB Messenger

        self.selfAccountId = None
        self.current_case = None

    ## Analyze contacts
    def analyzeContacts(self, dataSource, fileManager, context):
        
        ## FB messenger and FB have same database structure for contacts.
        ## In our dataset, the FB Messenger database was empty.
        ## But the FB database had the data.
        
        contactsDbs = AppSQLiteDB.findAppDatabases(dataSource, "contacts_db2", True, self._FACEBOOK_PACKAGE_NAME)
        for contactsDb in contactsDbs:
            try:
                ## The device owner's FB account details can be found in the contacts table in a row with added_time_ms of 0.
                selfAccountResultSet = contactsDb.runQuery("SELECT fbid, display_name FROM contacts WHERE added_time_ms = 0")
                if selfAccountResultSet:
                    if not self.selfAccountId:
                        self.selfAccountId = selfAccountResultSet.getString("fbid")

                if self.selfAccountId is not None:
                    contactsDBHelper = CommunicationArtifactsHelper(self.current_case.getSleuthkitCase(),
                                        self._MODULE_NAME, contactsDb.getDBFile(),
                                        Account.Type.FACEBOOK, Account.Type.FACEBOOK, self.selfAccountId )
                else:
                    contactsDBHelper = CommunicationArtifactsHelper(self.current_case.getSleuthkitCase(),
                                        self._MODULE_NAME, contactsDb.getDBFile(),
                                        Account.Type.FACEBOOK)

                ## get the other contacts/friends
                contactsResultSet = contactsDb.runQuery("SELECT fbid, display_name, added_time_ms FROM contacts WHERE added_time_ms <> 0")
                if contactsResultSet is not None:
                    while contactsResultSet.next():
                        fbid = contactsResultSet.getString("fbid")
                        contactName = contactsResultSet.getString("display_name")
                        dateCreated = contactsResultSet.getLong("added_time_ms") / 1000

                        ## create additional attributes for contact.
                        additionalAttributes = ArrayList();
                        additionalAttributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ID, self._MODULE_NAME, fbid))
                        additionalAttributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED, self._MODULE_NAME, dateCreated))

                        contactsDBHelper.addContact( contactName, ##  contact name 
                                                    "", 	## phone
                                                    "", 	## home phone
                                                    "", 	## mobile
                                                    "",	        ## email
                                                    additionalAttributes)
                        
            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for account", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except TskCoreException as ex:
                self._logger.log(Level.SEVERE, "Failed to add FB Messenger contact artifacts.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
            except IllegalArgumentException as ex:
                self._logger.log(Level.WARNING, "Invalid arguments for FB Messenger contact artifact.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except BlackboardException as ex:
                self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            finally:
                contactsDb.close()



    ## Adds a recipient to given list
    def addRecipientToList(self, user_key, fromId, recipientList):
        if user_key is not None: 
            recipientId = user_key.replace('FACEBOOK:', '')                    
            # ensure sender, if known, isn't added to recipientList.
            if (fromId and (fromId != recipientId)) or (not fromId) :
                # add recipient to list
                recipientList.append(recipientId)
            
    ## Analyze messages 
    def analyzeMessages(self, dataSource, fileManager, context):
        threadsDbs = AppSQLiteDB.findAppDatabases(dataSource, "threads_db2", True, self._FB_MESSENGER_PACKAGE_NAME)
        for threadsDb in threadsDbs:
            try:
                if self.selfAccountId is not None:
                    threadsDBHelper = CommunicationArtifactsHelper(self.current_case.getSleuthkitCase(),
                                        self._MODULE_NAME, threadsDb.getDBFile(),
                                        Account.Type.FACEBOOK, Account.Type.FACEBOOK, self.selfAccountId )
                else:
                    threadsDBHelper = CommunicationArtifactsHelper(self.current_case.getSleuthkitCase(),
                                        self._MODULE_NAME, threadsDb.getDBFile(),
                                        Account.Type.FACEBOOK)
                
                ## Messages are found in the messages table.
                ## The participant ids can be found in the thread_participants table.
                ## Participant names are found in thread_users table.
                ## Joining these tables produces multiple rows per message, one row for each recipient.
                ## The result set is processed to collect the multiple recipients for a given message.
                    
                sqlString = """
                            SELECT msg_id, text, sender, timestamp_ms, messages.thread_key as thread_key, 
                                 snippet, thread_participants.user_key as user_key, thread_users.name as name
                            FROM messages
                            JOIN thread_participants ON messages.thread_key = thread_participants.thread_key
                            JOIN thread_users ON thread_participants.user_key = thread_users.user_key
                            ORDER BY msg_id
                            """
                
                messagesResultSet = threadsDb.runQuery(sqlString)
                if messagesResultSet is not None:
                    oldMsgId = None

                    direction = CommunicationDirection.UNKNOWN
                    fromId = None
                    recipientIdsList = None
                    timeStamp = -1
                    msgText = ""
                    threadId = ""
                    
                    while messagesResultSet.next():
                        msgId = messagesResultSet.getString("msg_id")

                        # new msg begins when msgId changes
                        if msgId != oldMsgId:
                            # Create message artifact with collected attributes
                            if oldMsgId is not None:
                                messageArtifact = threadsDBHelper.addMessage( 
                                                            self._MESSAGE_TYPE,
                                                            direction,
                                                            fromId,
                                                            recipientIdsList,
                                                            timeStamp,
                                                            MessageReadStatus.UNKNOWN,
                                                            "",     # subject
                                                            msgText,
                                                            threadId)

                            oldMsgId = msgId

                            # New message - collect all attributes
                            recipientIdsList = []

                            ## get sender id by parsing JSON in sender column
                            senderJsonStr = messagesResultSet.getString("sender")
                            if senderJsonStr is not None: 
                                sender_dict = json.loads(senderJsonStr)
                                senderId = sender_dict['user_key']
                                senderId = senderId.replace('FACEBOOK:', '')
                                senderName = sender_dict['name']
                                fromId = senderId
                                if senderId == self.selfAccountId:
                                    direction = CommunicationDirection.OUTGOING
                                else:
                                    direction = CommunicationDirection.INCOMING
                                

                            # Get recipient and add to list
                            self.addRecipientToList(messagesResultSet.getString("user_key"),
                                                    fromId, recipientIdsList)

                            timeStamp = messagesResultSet.getLong("timestamp_ms") / 1000

                            # Get msg text
                            # Sometimes there may not be an explict msg text,
                            # but an app generated snippet instead
                            msgText = messagesResultSet.getString("text")
                            if not msgText:
                                msgText = messagesResultSet.getString("snippet")

                            # TBD: get attachment

                            threadId = messagesResultSet.getString("thread_key")

                        else:   # same msgId as last, just collect recipient from current row
                             self.addRecipientToList(messagesResultSet.getString("user_key"),
                                                    fromId, recipientIdsList)

    
                    # at the end of the loop, add last message 
                    messageArtifact = threadsDBHelper.addMessage( 
                                            self._MESSAGE_TYPE,
                                            direction,
                                            fromId,
                                            recipientIdsList,
                                            timeStamp,
                                            MessageReadStatus.UNKNOWN,
                                            "",     # subject
                                            msgText,
                                            threadId)
                        
            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for FB Messenger messages.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except TskCoreException as ex:
                self._logger.log(Level.SEVERE, "Failed to add FB Messenger message artifacts.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
            except IllegalArgumentException as ex:
                self._logger.log(Level.WARNING, "Invalid arguments for FB Messenger message artifact.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except BlackboardException as ex:
                self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            finally:
                threadsDb.close()

    def analyze(self, dataSource, fileManager, context):
        try:
            self.current_case = Case.getCurrentCaseThrows()
        except NoCurrentCaseException as ex:
            self._logger.log(Level.WARNING, "No case currently open.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
            return

        self.analyzeContacts(dataSource, fileManager, context)
        self.analyzeMessages(dataSource, fileManager, context)
        
        
