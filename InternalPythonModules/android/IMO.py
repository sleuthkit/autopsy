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
from org.sleuthkit.autopsy.coreutils import MobileAppDBParserHelper
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
Locates database for the IMO app and adds info to blackboard.
"""
class IMOAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        try:
			selfAccountInstance = None
			
			appDBHelper = MobileAppDBParserHelper.createInstance("IMO Parser")
            accountDbFile = appDBHelper.findAppDB(dataSource, "accountdb.db", true, "com.imo.android.imous")
			if accountDbFile is not None:
				resultSet = appDBHelper.runQuery(accountDbFile, "SELECT uid, name FROM account")
				# TBD: Create a new account type for IMO ???
				if resultSet is not None:
					# We can determine the IMO user ID of the device owner. Therefore we can create and use a app account and use that 
					# as a 'self' account instead of a Device account 
					selfAccount = appDBHelper.createAccountInstance(accountDbFile, Account.Type.MESSAGING_APP, resultSet.getString("name") )
			
			
			friendsDbFile = appDBHelper.findAppDB(dataSource, "imofriends.db", true, "com.imo.android.imous")
			if friendsDbFile is not None:
				contactsResultSet = appDBHelper.runQuery(friendsDbFile, "SELECT buid, name FROM friends")
				if contactsResultSet is not None:
					while contactsResultSet.next()
						appDBHelper.addContact(friendsDbFile, selfAccountInstance, Account.Type.MESSAGING_APP, 
												contactsResultSet.getString("name"), 
												contactsResultSet.getString("name"), 
												"", 
												"", 
												None)
					
				messagesResultSet = appDBHelper.runQuery(friendsDbFile, "SELECT imdata, last_message, timestamp, message_type, message_read, name FROM messages INNER JOIN friends ON friends.buid = messages.buid")
				if messagesResultSet is not None:
					while messagesResultSet.next()
						direction = ""
						fromAddress = None
						toAdddress = None
						if (messagesResultSet.getInt("message_type") == 1):
							direction = "Incoming"
							fromAddress = messagesResultSet.getString("name")
						else
							direction = "Outgoing"
							toAddress = messagesResultSet.getString("name")
						
						timeStamp = messagesResultSet.getInt("timestamp") / 1000000
						messageArtifact = appDBHelper.addMessage(friendsDbFile, selfAccountInstance, Account.Type.MESSAGING_APP, 
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
												"", 
												None)
												
						# TBD: parse the imdata JSON structure to figure out if there is an attachment.
						#      If one exists, add the attachment as a derived file and a child of the message artifact.

			appDBHelper.release()
			
        except TskCoreException as ex:
            # Error finding Tango messages.
            pass

    