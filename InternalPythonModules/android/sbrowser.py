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
from org.sleuthkit.datamodel.Blackboard import BlackboardException
from org.sleuthkit.datamodel.blackboardutils import WebBrowserArtifactsHelper

import traceback
import general

"""
Finds the SQLite DB for S-Browser, parses the DB for Bookmarks, Cookies, Web History
and adds artifacts to the case.
"""
class SBrowserAnalyzer(general.AndroidComponentAnalyzer):

    moduleName = "SBrowser Parser"
    progName = "SBrowser"
    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyzeBookmarks(self, dataSource, fileManager, context):
            sbrowserDbs = AppSQLiteDB.findAppDatabases(dataSource, "sbrowser.db", True, "com.sec.android.app.sbrowser")
            for sbrowserDb in sbrowserDbs:
                try:
                    sbrowserDbHelper = WebBrowserArtifactsHelper(Case.getCurrentCase().getSleuthkitCase(),
                                            self.moduleName, sbrowserDb.getDBFile())
                    bookmarkResultSet = sbrowserDb.runQuery("SELECT url, title, created FROM bookmarks WHERE url IS NOT NULL")
                    if bookmarkResultSet is not None:
                        while bookmarkResultSet.next():
                            createTime = bookmarkResultSet.getLong("created") / 1000
                            sbrowserDbHelper.addWebBookmark( bookmarkResultSet.getString("url"),
                                                        bookmarkResultSet.getString("title"),
                                                        createTime,     
                                                        self.progName)
                except SQLException as ex:
                    self._logger.log(Level.WARNING, "Error processing query results for SBrowser bookmarks.", ex)
                except (TskCoreException, BlackboardException) as ex:
                    self._logger.log(Level.WARNING, "Failed to add SBrowser bookmark artifacts.", ex)
                finally:  
                    sbrowserDb.close()                    
            	
        

    def analyzeCookies(self, dataSource, fileManager, context):
            cookiesDbs = AppSQLiteDB.findAppDatabases(dataSource, "Cookies", True, "com.sec.android.app.sbrowser")
            for cookiesDb in cookiesDbs:
                try:
                    cookiesDbHelper = WebBrowserArtifactsHelper(Case.getCurrentCase().getSleuthkitCase(),
                                            self.moduleName, cookiesDb.getDBFile())
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
                    self._logger.log(Level.WARNING, "Error processing query results for SBrowser cookies.", ex)
                except (TskCoreException, BlackboardException) as ex:
                    self._logger.log(Level.WARNING, "Failed to add SBrowser cookie artifacts.", ex)
                finally:      
                    cookiesDb.close()                    
            	
        

    def analyzeHistory(self, dataSource, fileManager, context):
            historyDbs = AppSQLiteDB.findAppDatabases(dataSource, "History", True, "com.sec.android.app.sbrowser")
            for historyDb in historyDbs:
                try:
                    historyDbHelper = WebBrowserArtifactsHelper(Case.getCurrentCase().getSleuthkitCase(),
                                            self.moduleName, historyDb.getDBFile())
                    historyResultSet = historyDb.runQuery("SELECT url, title, last_visit_time FROM urls")
                    if historyResultSet is not None:
                        while historyResultSet.next():
                            accessTime = historyResultSet.getLong("last_visit_time") / 1000000 - 11644473600 # Webkit time
                            historyDbHelper.addWebHistory( historyResultSet.getString("url"),
                                                        accessTime,
                                                        "",     # referrer
                                                        historyResultSet.getString("title"),
                                                        self.progName)
                except SQLException as ex:
                    self._logger.log(Level.WARNING, "Error processing query results for SBrowser history.", ex)
                except (TskCoreException, BlackboardException) as ex:
                    self._logger.log(Level.WARNING, "Failed to add SBrowser history artifacts.", ex)
                finally:        
                    historyDb.close()                    
                
        

    def analyzeDownloads(self, dataSource, fileManager, context):
            downloadsDbs = AppSQLiteDB.findAppDatabases(dataSource, "History", True, "com.sec.android.app.sbrowser")
            for downloadsDb in downloadsDbs:
                try:
                    downloadsDbHelper = WebBrowserArtifactsHelper(Case.getCurrentCase().getSleuthkitCase(),
                                            self.moduleName, downloadsDb.getDBFile())
                    queryString = "SELECT target_path, start_time, url FROM downloads"\
                                  " INNER JOIN downloads_url_chains ON downloads.id = downloads_url_chains.id"
                    downloadsResultSet = downloadsDb.runQuery(queryString)
                    if downloadsResultSet is not None:
                        while downloadsResultSet.next():
                            startTime = historyResultSet.getLong("start_time") / 1000000 - 11644473600 # Webkit time
                            downloadsDbHelper.addWebDownload( downloadsResultSet.getString("target_path"),
                                                        startTime,
                                                        downloadsResultSet.getString("url"),
                                                        self.progName)
                
                except SQLException as ex:
                    self._logger.log(Level.WARNING, "Error processing query results for SBrowser downloads.", ex)
                except (TskCoreException, BlackboardException) as ex:
                    self._logger.log(Level.WARNING, "Failed to add SBrowser download artifacts.", ex)
                finally:
                    downloadsDb.close()                    
                
    def analyzeAutofill(self, dataSource, fileManager, context):
            autofillDbs = AppSQLiteDB.findAppDatabases(dataSource, "Web Data", True, "com.sec.android.app.sbrowser")
            for autofillDb in autofillDbs:
                try:
                    autofillDbHelper = WebBrowserArtifactsHelper(Case.getCurrentCase().getSleuthkitCase(),
                                            self.moduleName, autofillDb.getDBFile())
                    autofillsResultSet = autofillDb.runQuery("SELECT name, value, count, date_created FROM autofill INNER JOIN autofill_dates ON autofill.pair_id = autofill_dates.pair_id")
                    if autofillsResultSet is not None:
                        while autofillsResultSet.next():
                            creationTime = autofillsResultSet.getLong("date_created") / 1000000 - 11644473600 # Webkit time
                            autofillDbHelper.addWebFormAutofill( autofillsResultSet.getString("name"),
                                                        autofillsResultSet.getString("value"),
                                                        creationTime,
                                                        0,
                                                        autofillsResultSet.getInt("count"))
                
                except SQLException as ex:
                    self._logger.log(Level.WARNING, "Error processing query results for SBrowser autofill.", ex)
                except (TskCoreException, BlackboardException) as ex:
                    self._logger.log(Level.WARNING, "Failed to add SBrowser autofill artifacts.", ex)
                finally:
                    autofillDb.close()

    def analyzeWebFormAddress(self, dataSource, fileManager, context):
            webFormAddressDbs = AppSQLiteDB.findAppDatabases(dataSource, "Web Data", True, "com.sec.android.app.sbrowser")
            for webFormAddressDb in webFormAddressDbs:
                try:                    
                    webFormAddressDbHelper = WebBrowserArtifactsHelper(Case.getCurrentCase().getSleuthkitCase(),
                                                self.moduleName, webFormAddressDb.getDBFile())
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
                                                 
                            creationTime = webFormAddressResultSet.getLong("date_modified") / 1000000 - 11644473600 # Webkit time
                            autofillDbHelper.addWebFormAddress( personName,
                                                        webFormAddressResultSet.getString("email"),
                                                        webFormAddressResultSet.getString("number"),
                                                        address,
                                                        creationTime,
                                                        0,
                                                        0)
                
                except SQLException as ex:
                    self._logger.log(Level.WARNING, "Error processing query results for SBrowser form addresses.", ex)
                except (TskCoreException, BlackboardException) as ex:
                    self._logger.log(Level.WARNING, "Failed to add SBrowser form address artifacts.", ex)
                finally:
                    webFormAddressDb.close()
                    
    def analyze(self, dataSource, fileManager, context):
        self.analyzeBookmarks(dataSource, fileManager, context)
        self.analyzeCookies(dataSource, fileManager, context)
        self.analyzeHistory(dataSource, fileManager, context)
        self.analyzeDownloads(dataSource, fileManager, context)
        self.analyzeAutofill(dataSource, fileManager, context)
        self.analyzeWebFormAddress(dataSource, fileManager, context)

