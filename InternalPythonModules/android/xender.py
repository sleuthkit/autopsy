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
Finds the SQLite DB for Xender, parses the DB for contacts & messages,
and adds artifacts to the case.
"""
class XenderAnalyzer(general.AndroidComponentAnalyzer):

    moduleName = "Xender Analyzer"
    progName = "Xender"
    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        selfAccountId = None
        transactionDbs = AppSQLiteDB.findAppDatabases(dataSource, "trans-history-db", "cn.xender")
        for transactionDb in transactionDbs:
            try:
                # get the profile with connection_times 0, that's the self account.
                profilesResultSet = transactionDb.runQuery("SELECT device_id, nick_name FROM profile WHERE connect_times = 0")
                if profilesResultSet:
                    while profilesResultSet.next():
                        if not selfAccountId:
                            selfAccountId = "{0} ({1})".format(profilesResultSet.getString("nick_name"), profilesResultSet.getString("device_id") )

                transactionDbHelper = AppDBParserHelper(self.moduleName, transactionDb.getDBFile(),
                                                    Account.Type.XENDER, Account.Type.XENDER, selfAccountId )

                queryString = "SELECT f_path, f_display_name, f_size_str, f_create_time, c_direction, c_session_id, s_name, s_device_id, r_name, r_device_id FROM new_history "
                messagesResultSet = transactionDb.runQuery(queryString)
                if messagesResultSet is not None:
                    while messagesResultSet.next():
                        direction = ""
                        fromAddress = None
                        toAdddress = None
                        otherAccountId = None
                        if (messagesResultSet.getInt("c_direction") == 1):
                            direction = "Outgoing"
                            toAddress = "{0} ({1})".format(messagesResultSet.getString("r_name"), messagesResultSet.getString("r_device_id") )
                            otherAccountId = toAddress
                        else:
                            direction = "Incoming"
                            fromAddress = "{0} ({1})".format(messagesResultSet.getString("s_name"), messagesResultSet.getString("s_device_id") )
                            otherAccountId = fromAddress
                                                
                        timeStamp = messagesResultSet.getLong("f_create_time") / 1000
                        messageArtifact = transactionDbHelper.addMessage( 
                                                            otherAccountId,
                                                            "Xender Message",
                                                            direction,
                                                            fromAddress,
                                                            toAddress,
                                                            timeStamp,
                                                            -1,     # read status
                                                            messagesResultSet.getString("f_display_name"), 
                                                            messagesResultSet.getString("f_path"),
                                                            messagesResultSet.getString("c_session_id") )
                                                                                                
                        # TBD: add the file as attachment ??

            except SQLException as ex:
                self._logger.log(Level.SEVERE, "Error processing query result for profiles", ex)       
            finally:
                transactionDb.close()
                

    
