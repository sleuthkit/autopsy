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
from org.sleuthkit.datamodel import Account
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection

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
        historyDbs = AppSQLiteDB.findAppDatabases(dataSource, "history.db", True, "com.lenovo.anyshare.gps")
        for historyDb in historyDbs:
            try:
                historyDbHelper = CommunicationArtifactsHelper(Case.getCurrentCase().getSleuthkitCase(),
                                    self.moduleName, historyDb.getDBFile(),
                                    Account.Type.SHAREIT)

                queryString = "SELECT history_type, device_id, device_name, description, timestamp, import_path FROM history"
                historyResultSet = historyDb.runQuery(queryString)
                if historyResultSet is not None:
                    while historyResultSet.next():
                        direction = ""
                        fromAddress = None
                        toAdddress = None
                        
                        if (historyResultSet.getInt("history_type") == 1):
                            direction = CommunicationDirection.OUTGOING
                            toAddress = Account.Address(historyResultSet.getString("device_id"), historyResultSet.getString("device_name") )
                        else:
                            direction = CommunicationDirection.INCOMING
                            fromAddress = Account.Address(historyResultSet.getString("device_id"), historyResultSet.getString("device_name") )
                            
                        msgBody = ""    # there is no body.
                        attachments = [historyResultSet.getString("import_path")]
                        msgBody = general.appendAttachmentList(msgBody, attachments)
                        
                        timeStamp = historyResultSet.getLong("timestamp") / 1000
                        messageArtifact = transferDbHelper.addMessage(
                                                            "ShareIt Message",
                                                            direction,
                                                            fromAddress,
                                                            toAddress,
                                                            timeStamp,
                                                            MessageReadStatus.UNKNOWN,
                                                            None,   # subject
                                                            msgBody,
                                                            "" )
                                                                                                
                        # TBD: add the file as attachment ??

            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for ShareIt history.", ex)
            except TskCoreException as ex:
                self._logger.log(Level.WARNING, "Failed to create CommunicationArtifactsHelper for adding artifacts.", ex)
            finally:
                historyDb.close()
                

    
