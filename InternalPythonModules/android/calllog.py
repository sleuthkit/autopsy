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
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CallMediaType

import traceback
import general

class CallLogAnalyzer(general.AndroidComponentAnalyzer):
    """
        Locates a variety of different call log databases, parses them, and populates the blackboard.
    """

    # the names of db files that potentially hold call logs
    _dbFileNames = ["logs.db", "contacts.db", "contacts2.db"]
    
    # the names of tables that potentially hold call logs in the dbs
    _tableNames = ["calls", "logs"]
    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "com.sec.android.provider.logsprovider"
        self._PARSER_NAME = "Android CallLog Parser"


    def analyze(self, dataSource, fileManager, context):
        for _dbFileName in CallLogAnalyzer._dbFileNames:
            selfAccountId = None
            callLogDbs = AppSQLiteDB.findAppDatabases(dataSource, _dbFileName, True, self._PACKAGE_NAME)
            for callLogDb in callLogDbs:
                try:
                    current_case = Case.getCurrentCaseThrows()
                    if selfAccountId is not None:
                        callLogDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                                        self._PARSER_NAME,
                                                        callLogDb.getDBFile(),
                                                        Account.Type.PHONE, Account.Type.PHONE, selfAccountId, context.getJobId())
                    else:
                        callLogDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                                        self._PARSER_NAME,
                                                        callLogDb.getDBFile(),
                                                        Account.Type.PHONE, context.getJobId())
                        
                    for tableName in CallLogAnalyzer._tableNames:
                        try:
                            tableFound = callLogDb.tableExists(tableName)
                            if tableFound:
                                resultSet = callLogDb.runQuery("SELECT number, date, duration, type, name FROM " + tableName + " ORDER BY date DESC;")
                                self._logger.log(Level.INFO, "Reading call log from table {0} in db {1}", [tableName, callLogDb.getDBFile().getName()])
                                if resultSet is not None:
                                    while resultSet.next():
                                        direction = ""
                                        callerId = None
                                        calleeId = None
                                        
                                        timeStamp = resultSet.getLong("date") / 1000
                                        number = resultSet.getString("number")
                                            
                                        duration = resultSet.getLong("duration") # duration of call is in seconds
                                        name = resultSet.getString("name") # name of person dialed or called. None if unregistered

                                        calltype = resultSet.getInt("type")
                                        if calltype == 1 or calltype == 3:
                                            direction = CommunicationDirection.INCOMING
                                            callerId = number
                                        elif calltype == 2 or calltype == 5:
                                            direction = CommunicationDirection.OUTGOING
                                            calleeId = number
                                        else:
                                            direction = CommunicationDirection.UNKNOWN
                                            

                                        ## add a call log
                                        if callerId is not None or calleeId is not None:
                                            callLogArtifact = callLogDbHelper.addCalllog( direction,
                                                                                callerId,
                                                                                calleeId,
                                                                                timeStamp,                      ## start time
                                                                                timeStamp + duration * 1000,    ## end time
                                                                                CallMediaType.AUDIO)

                        except SQLException as ex:
                            self._logger.log(Level.WARNING, "Error processing query result for Android messages.", ex)
                            self._logger.log(Level.WARNING, traceback.format_exc())
                        except TskCoreException as ex:
                            self._logger.log(Level.SEVERE, "Failed to add Android call log artifacts.", ex)
                            self._logger.log(Level.SEVERE, traceback.format_exc())
                        except BlackboardException as ex:
                            self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                            self._logger.log(Level.WARNING, traceback.format_exc())

                except TskCoreException as ex:
                        self._logger.log(Level.SEVERE, "Failed to create CommunicationArtifactsHelper.", ex)
                        self._logger.log(Level.SEVERE, traceback.format_exc())
                except NoCurrentCaseException as ex:
                    self._logger.log(Level.WARNING, "No case currently open.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())
                finally:
                    callLogDb.close()           
