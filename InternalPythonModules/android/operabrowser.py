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
Finds the SQLite DB for Opera browser, parses the DB for Bookmarks, Cookies, Web History
and adds artifacts to the case.
"""
class OperaAnalyzer(general.AndroidComponentAnalyzer):

    moduleName = "Opera Parser"
    progName = "Opera"
    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyzeCookies(self, dataSource, fileManager, context):
            cookiesDbs = AppSQLiteDB.findAppDatabases(dataSource, "Cookies", True, "com.opera.browser")
            for cookiesDb in cookiesDbs:
                try:
                    cookiesDbHelper = AppDBParserHelper(self.moduleName, cookiesDb.getDBFile())
                    cookiesResultSet = cookiesDb.runQuery("SELECT host_key, name, value, creation_utc FROM cookies")
                    if cookiesResultSet is not None:
                        while cookiesResultSet.next():
                            createTime = cookiesResultSet.getLong("creation_utc") / 1000000 - 11644473600 # Webkit time
                            cookiesDbHelper.addWebCookie( cookiesResultSet.getString("host_key"),
                                                        createTime,  
                                                        cookiesResultSet.getString("name"),
                                                        cookiesResultSet.getString("value"),
                                                        self.progName)

                except SQLException as ex:
                    self._logger.log(Level.SEVERE, "Error processing query results for Opera cookies.", ex)
                except TskCoreException as ex:
                    self._logger.log(Level.SEVERE, "Failed to create AppDBParserHelper for adding Opera cookies.", ex)
                finally:      
                    cookiesDb.close()                    
            	
        

    def analyzeHistory(self, dataSource, fileManager, context):
            historyDbs = AppSQLiteDB.findAppDatabases(dataSource, "History", True, "com.opera.browser")
            for historyDb in historyDbs:
                try:
                    historyDbHelper = AppDBParserHelper(self.moduleName, historyDb.getDBFile())
                    historyResultSet = historyDb.runQuery("SELECT url, title, last_visit_time FROM urls")
                    if historyResultSet is not None:
                        while historyResultSet.next():
                            accessTime = historyResultSet.getLong("last_visit_time") / 1000000 - 11644473600
                            historyDbHelper.addWebHistory( historyResultSet.getString("url"),
                                                        accessTime,
                                                        "",     # referrer
                                                        historyResultSet.getString("title"),
                                                        self.progName)
                except SQLException as ex:
                    self._logger.log(Level.SEVERE, "Error processing query results for Opera history.", ex)
                except TskCoreException as ex:
                    self._logger.log(Level.SEVERE, "Failed to create AppDBParserHelper for adding Opera history.", ex)
                finally:        
                    historyDb.close()                    
                
        

    def analyzeDownloads(self, dataSource, fileManager, context):
            downloadsDbs = AppSQLiteDB.findAppDatabases(dataSource, "History", True, "com.opera.browser")
            for downloadsDb in downloadsDbs:
                try:
                    downloadsDbHelper = AppDBParserHelper(self.moduleName, downloadsDb.getDBFile())
                    queryString = "SELECT target_path, start_time, url FROM downloads"\
                                  " INNER JOIN downloads_url_chains ON downloads.id = downloads_url_chains.id"
                    downloadsResultSet = downloadsDb.runQuery(queryString)
                    if downloadsResultSet is not None:
                        while downloadsResultSet.next():
                            startTime = historyResultSet.getLong("start_time") / 1000000 - 11644473600 #Webkit time format
                            downloadsDbHelper.addWebDownload( downloadsResultSet.getString("target_path"),
                                                        startTime,
                                                        downloadsResultSet.getString("url"),
                                                        self.progName)
                
                except SQLException as ex:
                    self._logger.log(Level.SEVERE, "Error processing query results for Opera downloads.", ex)
                except TskCoreException as ex:
                    self._logger.log(Level.SEVERE, "Failed to create AppDBParserHelper for adding Opera downloads.", ex)
                finally:
                    downloadsDb.close()                    
                
    def analyzeAutofill(self, dataSource, fileManager, context):
            autofillDbs = AppSQLiteDB.findAppDatabases(dataSource, "Web Data", True, "com.opera.browser")
            for autofillDb in autofillDbs:
                try:
                    autofillDbHelper = AppDBParserHelper(self.moduleName, autofillDb.getDBFile())
                    autofillsResultSet = autofillDb.runQuery("SELECT name, value, count, date_created FROM autofill")
                    if autofillsResultSet is not None:
                        while autofillsResultSet.next():
                            creationTime = autofillsResultSet.getLong("date_created") / 1000000 - 11644473600 #Webkit time format
                            autofillDbHelper.addWebFormAutofill( autofillsResultSet.getString("name"),
                                                        autofillsResultSet.getString("value"),
                                                        creationTime,
                                                        0,
                                                        autofillsResultSet.getInt("count"))
                
                except SQLException as ex:
                    self._logger.log(Level.SEVERE, "Error processing query results for Opera autofill.", ex)
                except TskCoreException as ex:
                    self._logger.log(Level.SEVERE, "Failed to create AppDBParserHelper for adding Opera autofill.", ex)
                finally:
                    autofillDb.close()

    def analyzeWebFormAddress(self, dataSource, fileManager, context):
            webFormAddressDbs = AppSQLiteDB.findAppDatabases(dataSource, "Web Data", True, "com.opera.browser")
            for webFormAddressDb in webFormAddressDbs:
                try:
                    webFormAddressDbHelper = AppDBParserHelper(self.moduleName, webFormAddressDb.getDBFile())
                    queryString = "SELECT street_address, city, state, zipcode, country_code, date_modified, first_name, last_name, number, email FROM autofill_profiles "\
                                " INNER JOIN autofill_profile_names"\
                                " ON autofill_profiles.guid = autofill_profile_names.guid"\
                                " INNER JOIN autofill_profile_phones"\
                                " ON autofill_profiles.guid = autofill_profile_phones.guid"\
                                " INNER JOIN autofill_profile_emails"\
                                " ON autofill_profiles.guid = autofill_profile_emails.guid"
                    webFormAddressResultSet = webFormAddressDb.runQuery(queryString)
                    if webFormAddressResultSet is not None:
                        while webFormAddressResultSet.next():
                            personName = webFormAddressResultSet.getString("first_name") + " " + webFormAddressResultSet.getString("last_name")
                            address = '\n'.join([ webFormAddressResultSet.getString("street_address"),
                                                  webFormAddressResultSet.getString("city"),
                                                  webFormAddressResultSet.getString("state") + " " + webFormAddressResultSet.getString("zipcode"),
                                                  webFormAddressResultSet.getString("country_code") ])
                                                 
                            creationTime = webFormAddressResultSet.getLong("date_modified") / 1000000 - 11644473600
                            autofillDbHelper.addWebFormAddress( personName,
                                                        webFormAddressResultSet.getString("email"),
                                                        webFormAddressResultSet.getString("number"),
                                                        address,
                                                        creationTime,
                                                        0,
                                                        0)
                
                except SQLException as ex:
                    self._logger.log(Level.SEVERE, "Error processing query results for Opera web form addresses.", ex)
                except TskCoreException as ex:
                    self._logger.log(Level.SEVERE, "Failed to create AppDBParserHelper for adding Opera form addresses.", ex)
                finally:
                    webFormAddressDb.close()
                    
    def analyze(self, dataSource, fileManager, context):
        self.analyzeCookies(dataSource, fileManager, context)
        self.analyzeHistory(dataSource, fileManager, context)
        self.analyzeDownloads(dataSource, fileManager, context)
        self.analyzeAutofill(dataSource, fileManager, context)
        self.analyzeWebFormAddress(dataSource, fileManager, context)

