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
from org.sleuthkit.datamodel.blackboardutils import ArtifactsHelper

import traceback
import general


class InstalledApplicationsAnalyzer(general.AndroidComponentAnalyzer):

    """
        Android has a database to track the applications that are
        purchased and installed on the phone.
        
        This module finds the SQLite DB for insalled application, and creates artifacts.

        
        Android 5.1.1 has the following database structure istalled applications:
            - library.db 
                -- A ownership table that stores pplications purchased, with purchase date
                            
    """
    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "com.android.vending"
        self._MODULE_NAME = "Android Installed Applications Analyzer"
        self._VERSION = "5.1.1"     ## Android version
        self.current_case = None

    def analyze(self, dataSource, fileManager, context):
        libraryDbs = AppSQLiteDB.findAppDatabases(dataSource, "library.db", True, self._PACKAGE_NAME)
        for libraryDb in libraryDbs:
            try:
                current_case = Case.getCurrentCaseThrows()
                libraryDbHelper = ArtifactsHelper(current_case.getSleuthkitCase(),
                                    self._MODULE_NAME, libraryDb.getDBFile(), context.getJobId())
                queryString = "SELECT doc_id, purchase_time FROM ownership"
                ownershipResultSet = libraryDb.runQuery(queryString)
                if ownershipResultSet is not None:
                    while ownershipResultSet.next():
                        purchase_time = ownershipResultSet.getLong("purchase_time") / 1000
                        libraryDbHelper.addInstalledProgram(ownershipResultSet.getString("doc_id"),
                                                    purchase_time)
            
            except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for installed applications. ", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except TskCoreException as ex:
                self._logger.log(Level.SEVERE, "Failed to adding installed application artifacts.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
            except BlackboardException as ex:
                self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            except NoCurrentCaseException as ex:
                self._logger.log(Level.WARNING, "No case currently open.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
            finally:
                libraryDb.close()
    
