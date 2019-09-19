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

import json
import traceback
import general

"""
Finds the SQLite DB for Facebook messenger, parses the DB for contacts & messages,
and adds artifacts to the case.
"""
class FBMessengerAnalyzer(general.AndroidComponentAnalyzer):
    
    selfAccountAddress = None
    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    ## Analyze contacts
    def analyzeContacts(self, dataSource, fileManager, context):
        ## FB messenger and FB have same database structure for contacts.
        ## In our example the FB Messenger database was empty.
        ## But the FB database had the data.
        
        contactsDbs = AppSQLiteDB.findAppDatabases(dataSource, "contacts_db2", True, "com.facebook.katana")
        for contactsDb in contactsDbs:
            try:
                selfAccountResultSet = contactsDb.runQuery("SELECT fbid, display_name FROM contacts WHERE added_time_ms = 0")
                if selfAccountResultSet:
                
                    if not self.selfAccountAddress:
                        self.selfAccountAddress = Account.Address(selfAccountResultSet.getString("fbid"), selfAccountResultSet.getString("display_name"))

                contactsDBHelper = CommunicationArtifactsHelper(Case.getCurrentCase().getSleuthkitCase(),
                                        "Facebook Parser", contactsDb.getDBFile(),
                                        Account.Type.FACEBOOK, Account.Type.FACEBOOK, self.selfAccountAddress )
                contactsResultSet = contactsDb.runQuery("SELECT fbid, display_name FROM contacts WHERE added_time_ms <> " + self.selfAccountAddress.getUniqueID() )
                if contactsResultSet is not None:
                    while contactsResultSet.next():
                        contactsDBHelper.addContact( contactsResultSet.getString("fbid"),  ##  unique id for account
                                                    contactsResultSet.getString("display_name"),  ## contact name
                                                    "", 	## phone
                                                    "", 	## home phone
                                                    "", 	## mobile
                                                    "")	        ## email
                        
            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for account", ex)
            except (TskCoreException, BlackboardException) as ex:
                self._logger.log(Level.WARNING, "Failed to add Facebook Messenger contact artifacts.", ex)
            finally:
                contactsDb.close()



    ## Adds a recipient to given list
    def addRecipientToList(self, user_key, name, fromAddress, recipientList):
        if user_key is not None: 
            recipientId = user_key.replace('FACEBOOK:', '')                    
            toAddress = Account.Address(recipientId, name)
            # ensure sender, if known, isn't added to recipientList.
            if (fromAddress and fromAddress.getUniqueID() != toAddress.getUniqueID()) or (not fromAddress) :
                # add recipient to list
                recipientList.append(toAddress)
            
    ## Analyze messages 
    def analyzeMessages(self, dataSource, fileManager, context):
        threadsDbs = AppSQLiteDB.findAppDatabases(dataSource, "threads_db2", True, "com.facebook.orca")
        for threadsDb in threadsDbs:
            try:
                threadsDBHelper = CommunicationArtifactsHelper(Case.getCurrentCase().getSleuthkitCase(),
                                        "FB Messenger Parser", threadsDb.getDBFile(),
                                        Account.Type.FACEBOOK, Account.Type.FACEBOOK, self.selfAccountAddress )
                
                ## Messages are found in the messages table.  The participant ids can be found in the thread_participants table.
                ## Participant names are found in thread_users table.
                sqlString = "SELECT msg_id, text, sender, timestamp_ms, messages.thread_key as thread_key,"\
                            " snippet, thread_participants.user_key as user_key, thread_users.name as name FROM messages"\
                            " JOIN thread_participants ON messages.thread_key = thread_participants.thread_key"\
                            " JOIN thread_users ON thread_participants.user_key = thread_users.user_key"\
                            " ORDER BY msg_id"
                
                messagesResultSet = threadsDb.runQuery(sqlString)
                if messagesResultSet is not None:
                    oldMsgId = None

                    direction = CommunicationDirection.UNKNOWN
                    fromAddress = None
                    recipientAddressList = None
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
                                                            "FB Messenger Message",
                                                            direction,
                                                            fromAddress,
                                                            recipientAddressList,
                                                            timeStamp,
                                                            MessageReadStatus.UNKNOWN,
                                                            "", 
                                                            msgText,
                                                            threadId)

                            oldMsgId = msgId

                            # New message - collect all attributes
                            recipientAddressList = []

                            ## get sender address by parsing JSON in sender column
                            senderJsonStr = messagesResultSet.getString("sender")
                            if senderJsonStr is not None: 
                                sender_dict = json.loads(senderJsonStr)
                                senderId = sender_dict['user_key']
                                senderId = senderId.replace('FACEBOOK:', '')
                                senderName = sender_dict['name']
                                fromAddress = Account.Address(senderId, senderName)
                                if senderId == self.selfAccountAddress.getUniqueID():
                                    direction = CommunicationDirection.OUTGOING
                                else:
                                    direction = CommunicationDirection.INCOMING
                                

                            # Get recipient and add to list
                            self.addRecipientToList(messagesResultSet.getString("user_key"), messagesResultSet.getString("name"),
                                                    fromAddress, recipientAddressList)

                            timeStamp = messagesResultSet.getLong("timestamp_ms") / 1000

                            # Get msg text
                            # Sometimes there may not be an explict msg text,
                            # but a app genrated snippet instead
                            msgText = messagesResultSet.getString("text")
                            if not msgText:
                                msgText = messagesResultSet.getString("snippet")

                            # TBD: get attachment

                            threadId = messagesResultSet.getString("thread_key")

                           
                        else:   # same msgId as last, just collect recipient from current row
                            
                             self.addRecipientToList(messagesResultSet.getString("user_key"), messagesResultSet.getString("name"),
                                                    fromAddress, recipientAddressList)

    
                    # at the end of th loop, add last message 
                    messageArtifact = threadsDBHelper.addMessage( 
                                            "FB Messenger Message",
                                            direction,
                                            fromAddress,
                                            recipientAddressList,
                                            timeStamp,
                                            MessageReadStatusEnum.UNKNOWN,
                                            "", 
                                            msgText,
                                            threadId)
                        
            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for FB Messenger messages.", ex)
            except (TskCoreException, BlackboardException) as ex:
                self._logger.log(Level.WARNING, "Failed to add FB Messenger message artifacts.", ex)
            finally:
                threadsDb.close()

    def analyze(self, dataSource, fileManager, context):
        self.analyzeContacts(dataSource, fileManager, context)
        self.analyzeMessages(dataSource, fileManager, context)
        
        
