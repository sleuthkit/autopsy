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

import traceback
import general

"""
Finds the SQLite DB for Android installed applications, parses the DB,
and adds artifacts to the case.
"""
class InstalledApplicationsAnalyzer(general.AndroidComponentAnalyzer):
    
    moduleName = "Android Installed Applications Analyzer"
    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        libraryDbs = AppSQLiteDB.findAppDatabases(dataSource, "library.db", "com.android.vending")
        for libraryDb in libraryDbs:
            try:
                libraryDbHelper = AppDBParserHelper(self.moduleName, libraryDb.getDBFile())
                queryString = "SELECT doc_id, purchase_time FROM ownership"
                ownershipResultSet = libraryDb.runQuery(queryString)
                if ownershipResultSet is not None:
                    while ownershipResultSet.next():
                        purchase_time = ownershipResultSet.getLong("purchase_time") / 1000
                        libraryDbHelper.addInstalledProgram(ownershipResultSet.getString("doc_id"),
                                                    purchase_time)
            
            except SQLException as ex:
                self._logger.log(Level.SEVERE, "Error processing query result for installed applications. ", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
                
            finally:
                libraryDb.close()
    
