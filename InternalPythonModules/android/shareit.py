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
Finds the SQLite DB for ShareIt, parses the DB for contacts & messages,
and adds artifacts to the case.
"""
class ShareItAnalyzer(general.AndroidComponentAnalyzer):

    moduleName = "ShareIT Analyzer"
    progName = "ShareIt"
    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        selfAccountId = None
        historyDbs = AppSQLiteDB.findAppDatabases(dataSource, "history.db", "com.lenovo.anyshare.gps")
        for historyDb in historyDbs:
            try:
                historyDbHelper = AppDBParserHelper(self.moduleName, historyDb.getDBFile(),
                                                    Account.Type.SHAREIT)

                queryString = "SELECT history_type, device_id, device_name, description, timestamp, import_path FROM history"
                historyResultSet = historyDb.runQuery(queryString)
                if historyResultSet is not None:
                    while historyResultSet.next():
                        direction = ""
                        fromAddress = None
                        toAdddress = None
                        otherAccountId = None
                        if (historyResultSet.getInt("history_type") == 1):
                            direction = "Outgoing"
                            toAddress = "{0} ({1})".format(historyResultSet.getString("device_name"), historyResultSet.getString("device_id") )
                            otherAccountId = toAddress
                        else:
                            direction = "Incoming"
                            fromAddress = "{0} ({1})".format(historyResultSet.getString("device_name"), historyResultSet.getString("device_id") )
                            otherAccountId = fromAddress
                                                
                        timeStamp = historyResultSet.getLong("timestamp") / 1000
                        messageArtifact = transferDbHelper.addMessage( 
                                                            otherAccountId,
                                                            "Zapya Message",
                                                            direction,
                                                            fromAddress,
                                                            toAddress,
                                                            timeStamp,
                                                            -1,     # read status
                                                            historyResultSet.getString("description"), 
                                                            historyResultSet.getString("import_path"),
                                                            "" )
                                                                                                
                        # TBD: add the file as attachment ??

            except SQLException as ex:
                self._logger.log(Level.SEVERE, "Error processing query result for ShareIt history.", ex)       
            finally:
                historyDb.close()
                

    
