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
from org.sleuthkit.datamodel.blackboardutils import WebBrowserArtifactsHelper

import traceback
import general


class SBrowserAnalyzer(general.AndroidComponentAnalyzer):

    """
        SBrowser is the default/native browser on Android phones.
        
        This module finds the SQLite DB for SBrowser, parses the DB for bookmarks,
            downloads, web history, cookies, autofill and creates artifacts.

        
        SBrowser has the following database structure:
            - sbrowser.db 
                -- A bookmarks table that store bookmarks 
            - cookies
                -- A cookies table to store cookies
            - history
                -- A urls table to store history of visted urls
                -- A downloads table to store downloads
            - Web Data
                -- A autofill table to store discrete autofill name/value pairs
                -- A autofill_profile_names to store name fields (first name, middle name, last name)
                -- A autofill_profiles to store the physical snailmail address (street address, city, state, country, zip)
                -- A autofill_profile_phones to store phone numbers
                -- A autofill_profile_emails to store email addresses
                    
                
    """
 
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "com.sec.android.app.sbrowser"
        self._MODULE_NAME = "SBrowser Analyzer"
        self._PROGRAM_NAME = "SBrowser"
        self._VERSION = "10.1.00.27"
        self.current_case = None

    def analyzeBookmarks(self, dataSource, fileManager, context):
            sbrowserDbs = AppSQLiteDB.findAppDatabases(dataSource, "sbrowser.db", True, self._PACKAGE_NAME)
            for sbrowserDb in sbrowserDbs:
                try:
                    sbrowserDbHelper = WebBrowserArtifactsHelper(self.current_case.getSleuthkitCase(),
                                            self._MODULE_NAME, sbrowserDb.getDBFile(), context.getJobId())
                    bookmarkResultSet = sbrowserDb.runQuery("SELECT url, title, created FROM bookmarks WHERE url IS NOT NULL")
                    if bookmarkResultSet is not None:
                        while bookmarkResultSet.next():
                            createTime = bookmarkResultSet.getLong("created") / 1000
                            sbrowserDbHelper.addWebBookmark( bookmarkResultSet.getString("url"),
                                                        bookmarkResultSet.getString("title"),
                                                        createTime,     
                                                        self._PROGRAM_NAME)
                except SQLException as ex:
                    self._logger.log(Level.WARNING, "Error processing query results for SBrowser bookmarks.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())
                except TskCoreException as ex:
                    self._logger.log(Level.SEVERE, "Failed to add SBrowser bookmark artifacts.", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                except BlackboardException as ex:
                    self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())
                finally:  
                    sbrowserDb.close()                    
            	
        

    def analyzeCookies(self, dataSource, fileManager, context):
            cookiesDbs = AppSQLiteDB.findAppDatabases(dataSource, "Cookies", True, self._PACKAGE_NAME)
            for cookiesDb in cookiesDbs:
                try:
                    cookiesDbHelper = WebBrowserArtifactsHelper(self.current_case.getSleuthkitCase(),
                                            self._MODULE_NAME, cookiesDb.getDBFile(), context.getJobId())
                    cookiesResultSet = cookiesDb.runQuery("SELECT host_key, name, value, creation_utc FROM cookies")
                    if cookiesResultSet is not None:
                        while cookiesResultSet.next():
                            createTime = cookiesResultSet.getLong("creation_utc") / 1000000 - 11644473600 # Webkit time
                            cookiesDbHelper.addWebCookie( cookiesResultSet.getString("host_key"),
                                                        createTime,  
                                                        cookiesResultSet.getString("name"),
                                                        cookiesResultSet.getString("value"),
                                                        self._PROGRAM_NAME)

                except SQLException as ex:
                    self._logger.log(Level.WARNING, "Error processing query results for SBrowser cookies.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())
                except TskCoreException as ex:
                    self._logger.log(Level.SEVERE, "Failed to add SBrowser cookie artifacts.", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                except BlackboardException as ex:
                    self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())
                finally:      
                    cookiesDb.close()                    
            	
        

    def analyzeHistory(self, dataSource, fileManager, context):
            historyDbs = AppSQLiteDB.findAppDatabases(dataSource, "History", True, self._PACKAGE_NAME)
            for historyDb in historyDbs:
                try:
                    historyDbHelper = WebBrowserArtifactsHelper(self.current_case.getSleuthkitCase(),
                                            self._MODULE_NAME, historyDb.getDBFile(), context.getJobId())
                    historyResultSet = historyDb.runQuery("SELECT url, title, last_visit_time FROM urls")
                    if historyResultSet is not None:
                        while historyResultSet.next():
                            accessTime = historyResultSet.getLong("last_visit_time") / 1000000 - 11644473600 # Webkit time
                            historyDbHelper.addWebHistory( historyResultSet.getString("url"),
                                                        accessTime,
                                                        "",     # referrer
                                                        historyResultSet.getString("title"),
                                                        self._PROGRAM_NAME)
                except SQLException as ex:
                    self._logger.log(Level.WARNING, "Error processing query results for SBrowser history.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())
                except TskCoreException as ex:
                    self._logger.log(Level.SEVERE, "Failed to add SBrowser history artifacts.", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                except BlackboardException as ex:
                    self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())
                finally:        
                    historyDb.close()                    
                
        

    def analyzeDownloads(self, dataSource, fileManager, context):
            downloadsDbs = AppSQLiteDB.findAppDatabases(dataSource, "History", True, self._PACKAGE_NAME)
            for downloadsDb in downloadsDbs:
                try:
                    downloadsDbHelper = WebBrowserArtifactsHelper(self.current_case.getSleuthkitCase(),
                                            self._MODULE_NAME, downloadsDb.getDBFile(), context.getJobId())
                    queryString = "SELECT target_path, start_time, url FROM downloads"\
                                  " INNER JOIN downloads_url_chains ON downloads.id = downloads_url_chains.id"
                    downloadsResultSet = downloadsDb.runQuery(queryString)
                    if downloadsResultSet is not None:
                        while downloadsResultSet.next():
                            startTime = historyResultSet.getLong("start_time") / 1000000 - 11644473600 # Webkit time
                            downloadsDbHelper.addWebDownload( downloadsResultSet.getString("url"),
                                                        startTime,
                                                        downloadsResultSet.getString("target_path"),
                                                        self._PROGRAM_NAME)
                
                except SQLException as ex:
                    self._logger.log(Level.WARNING, "Error processing query results for SBrowser downloads.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())
                except TskCoreException as ex:
                    self._logger.log(Level.SEVERE, "Failed to add SBrowser download artifacts.", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                except BlackboardException as ex:
                    self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())
                finally:
                    downloadsDb.close()                    
                
    def analyzeAutofill(self, dataSource, fileManager, context):
            autofillDbs = AppSQLiteDB.findAppDatabases(dataSource, "Web Data", True, self._PACKAGE_NAME)
            for autofillDb in autofillDbs:
                try:
                    autofillDbHelper = WebBrowserArtifactsHelper(self.current_case.getSleuthkitCase(),
                                            self._MODULE_NAME, autofillDb.getDBFile(), context.getJobId())
                    queryString = """
                                SELECT name, value, count, date_created
                                FROM autofill
                                INNER JOIN autofill_dates ON autofill.pair_id = autofill_dates.pair_id
                                """
                    autofillsResultSet = autofillDb.runQuery(queryString)
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
                    self._logger.log(Level.WARNING, traceback.format_exc())
                except TskCoreException as ex:
                    self._logger.log(Level.SEVERE, "Failed to add SBrowser autofill artifacts.", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                except BlackboardException as ex:
                    self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())
                finally:
                    autofillDb.close()

    def analyzeWebFormAddress(self, dataSource, fileManager, context):
            webFormAddressDbs = AppSQLiteDB.findAppDatabases(dataSource, "Web Data", True, self._PACKAGE_NAME)
            for webFormAddressDb in webFormAddressDbs:
                try:                    
                    webFormAddressDbHelper = WebBrowserArtifactsHelper(self.current_case.getSleuthkitCase(),
                                                self._MODULE_NAME, webFormAddressDb.getDBFile(), context.getJobId())
                    """
                        Autofill form data is split across multiple tables. The quqery below joins the various tables.
                    """
                    queryString = """
                                SELECT street_address, city, state, zipcode, country_code, date_modified, first_name, last_name, number, email 
                                FROM autofill_profiles
                                INNER JOIN autofill_profile_names ON autofill_profiles.guid = autofill_profile_names.guid 
                                INNER JOIN autofill_profile_phones ON autofill_profiles.guid = autofill_profile_phones.guid
                                INNER JOIN autofill_profile_emails ON autofill_profiles.guid = autofill_profile_emails.guid
                            """
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
                    self._logger.log(Level.WARNING, traceback.format_exc())
                except TskCoreException as ex:
                    self._logger.log(Level.SEVERE, "Failed to add SBrowser form address artifacts.", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                except BlackboardException as ex:
                    self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())
                finally:
                    webFormAddressDb.close()
                    
    def analyze(self, dataSource, fileManager, context):
        ## open current case
        try:
            self.current_case = Case.getCurrentCaseThrows()
        except NoCurrentCaseException as ex:
            self._logger.log(Level.WARNING, "No case currently open.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
            return

                
        self.analyzeBookmarks(dataSource, fileManager, context)
        self.analyzeCookies(dataSource, fileManager, context)
        self.analyzeHistory(dataSource, fileManager, context)
        self.analyzeDownloads(dataSource, fileManager, context)
        self.analyzeAutofill(dataSource, fileManager, context)
        self.analyzeWebFormAddress(dataSource, fileManager, context)

