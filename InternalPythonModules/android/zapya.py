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
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection

import traceback
import general

class ZapyaAnalyzer(general.AndroidComponentAnalyzer):

    """
        Zapya is a file transfer utility app.
        
        This module finds the SQLite DB for Zapya, parses the DB for contacts & messages,
        and adds artifacts to the case.

        Zapya version 5.8.3 has the following database structure:
            - transfer20.db 
                -- A transfer table, with records of files exchanged with other users
                    --- path - path of the file sent/received
                
    """

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "com.dewmobile.kuaiya.play"
        self._MODULE_NAME = "Zapya Analyzer"
        self._MESSAGE_TYPE = "Zapya Message"
        self._VERSION = "5.8.3"

    def analyze(self, dataSource, fileManager, context):
        transferDbs = AppSQLiteDB.findAppDatabases(dataSource, "transfer20.db", True, self._PACKAGE_NAME)
        for transferDb in transferDbs:
            try:
                current_case = Case.getCurrentCaseThrows()
                # 
                transferDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                    self._MODULE_NAME, transferDb.getDBFile(),
                                    Account.Type.ZAPYA, context.getJobId())

                queryString = "SELECT device, name, direction, createtime, path, title FROM transfer"
                transfersResultSet = transferDb.runQuery(queryString)
                if transfersResultSet is not None:
                    while transfersResultSet.next():
                        direction = CommunicationDirection.UNKNOWN
                        fromId = None
                        toId = None
                        fileAttachments = ArrayList()
                    
                        if (transfersResultSet.getInt("direction") == 1):
                            direction = CommunicationDirection.OUTGOING
                            toId = transfersResultSet.getString("device")
                        else:
                            direction = CommunicationDirection.INCOMING
                            fromId = transfersResultSet.getString("device")
                        
                        timeStamp = transfersResultSet.getLong("createtime") / 1000
                        messageArtifact = transferDbHelper.addMessage( 
                                                            self._MESSAGE_TYPE,
                                                            direction,
                                                            fromId,
                                                            toId,
                                                            timeStamp,
                                                            MessageReadStatus.UNKNOWN,
                                                            None,   # subject
                                                            None,   # message Text
                                                            None )    # thread id
                                                                                                
                        # add the file as attachment 
                        fileAttachments.add(FileAttachment(current_case.getSleuthkitCase(), transferDb.getDBFile().getDataSource(), transfersResultSet.getString("path")))
                        messageAttachments = MessageAttachments(fileAttachments, [])
                        transferDbHelper.addAttachments(messageArtifact, messageAttachments)

            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for transfer.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except TskCoreException as ex:
                self._logger.log(Level.SEVERE, "Failed to create Zapya message artifacts.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
            except BlackboardException as ex:
                self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except NoCurrentCaseException as ex:
                self._logger.log(Level.WARNING, "No case currently open.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            finally:
                transferDb.close()
                

    
