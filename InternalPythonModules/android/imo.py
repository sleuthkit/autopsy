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
from org.sleuthkit.autopsy.coreutils import AppDBParserHelper
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel import Account

import traceback
import general

"""
Finds the SQLite DB for IMO, parses the DB for contacts & messages,
and adds artifacts to the case.
"""
class IMOAnalyzer(general.AndroidComponentAnalyzer):
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        try:
            selfAccountId = None
            accountDbs = AppSQLiteDB.findAppDatabases(dataSource, "accountdb.db", "com.imo.android.imous")
            for accountDb in accountDbs:
                accountResultSet = accountDb.runQuery("SELECT uid, name FROM account")
                if accountResultSet:
                    # We can determine the IMO user ID of the device owner. 
                    # Therefore we can create and use a app account and use that 
                    # as a 'self' account instead of a Device account
                    if not selfAccountId:
                        selfAccountId = accountResultSet.getString("name")
                    accountResultSet.close()
                accountDb.close();
                        
            friendsDbs = AppSQLiteDB.findAppDatabases(dataSource, "imofriends.db", "com.imo.android.imous")
            for friendsDb in friendsDbs:
                friendsDBHelper = AppDBParserHelper("IMO Parser", friendsDb.getDBFile(),
                                                    Account.Type.IMO, Account.Type.IMO, selfAccountId )
                contactsResultSet = friendsDb.runQuery("SELECT buid, name FROM friends")
                if contactsResultSet is not None:
                    while contactsResultSet.next():
                        friendsDBHelper.addContact( contactsResultSet.getString("name"),  ## name for account
                                                    contactsResultSet.getString("name"),  ## contact name
                                                    "", 	## phone
                                                    "", 	## home phone
                                                    "", 	## mobile
                                                    "")	        ## email
                                        
                messagesResultSet = friendsDb.runQuery("SELECT imdata, last_message, timestamp, message_type, message_read, name FROM messages INNER JOIN friends ON friends.buid = messages.buid")
                if messagesResultSet is not None:
                    while messagesResultSet.next():
                        direction = ""
                        fromAddress = None
                        toAdddress = None
                        if (messagesResultSet.getInt("message_type") == 1):
                            direction = "Incoming"
                            fromAddress = messagesResultSet.getString("name")
                        else:
                            direction = "Outgoing"
                            toAddress = messagesResultSet.getString("name")
                                                
                        timeStamp = messagesResultSet.getLong("timestamp") / 1000000000
                        messageArtifact = friendsDBHelper.addMessage( 
                                                            contactsResultSet.getString("name"),
                                                            "IMO Message",
                                                            direction,
                                                            fromAddress,
                                                            toAddress,
                                                            timeStamp,
                                                            messagesResultSet.getInt("message_read"),
                                                            "", 
                                                            messagesResultSet.getString("last_message"),
                                                            "", 
                                                            "" )
                                                                                                
                        # TBD: parse the imdata JSON structure to figure out if there is an attachment.
                        #      If one exists, add the attachment as a derived file and a child of the message artifact.

                friendsDb.close()		
        except TskCoreException as ex:
            # Error parsing IMO databases.
            self._logger.log(Level.SEVERE, "Error Parsing IMO Databases", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
            pass

    
