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
from java.sql import DatabaseMetaData
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

import traceback
import general

class ContactAnalyzer(general.AndroidComponentAnalyzer):

    """
        Finds and parsers Android contacts database, and populates the blackboard with Contacts.
    """
    
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._PACKAGE_NAME = "com.android.providers.contacts"
        self._PARSER_NAME = "Android Contacts Parser"
        self._VERSION = "53.1.0.1"   # icu_version in 'properties' table.
        
    def analyze(self, dataSource, fileManager, context):
        try:

            contactsDbs = AppSQLiteDB.findAppDatabases(dataSource, "contacts.db", True, self._PACKAGE_NAME)
            contactsDbs.addAll(AppSQLiteDB.findAppDatabases(dataSource, "contacts2.db", True, self._PACKAGE_NAME))
            if contactsDbs.isEmpty():
                return
            for contactDb in contactsDbs:
                try:
                    self.__findContactsInDB(contactDb, dataSource, context)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing Contacts", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            # Error finding Contacts.
            pass

    """
        Queries the given contact database and adds Contacts to the case.
    """
    def __findContactsInDB(self, contactDb, dataSource, context):
        if not contactDb:
            return

        try:
            current_case = Case.getCurrentCaseThrows()

            # Create a helper to parse the DB
            contactDbHelper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(),
                                                    self._PARSER_NAME,
                                                    contactDb.getDBFile(),
                                                    Account.Type.PHONE, context.getJobId())
            
            # get display_name, mimetype(email or phone number) and data1 (phonenumber or email address depending on mimetype)
            # sorted by name, so phonenumber/email would be consecutive for a person if they exist.
            # check if contacts.name_raw_contact_id exists. Modify the query accordingly.
            columnFound = contactDb.columnExists("contacts", "name_raw_contact_id")
            if columnFound:
                resultSet = contactDb.runQuery(
                    "SELECT mimetype, data1, name_raw_contact.display_name AS display_name \n"
                    + "FROM raw_contacts JOIN contacts ON (raw_contacts.contact_id=contacts._id) \n"
                    + "JOIN raw_contacts AS name_raw_contact ON(name_raw_contact_id=name_raw_contact._id) "
                    + "LEFT OUTER JOIN data ON (data.raw_contact_id=raw_contacts._id) \n"
                    + "LEFT OUTER JOIN mimetypes ON (data.mimetype_id=mimetypes._id) \n"
                    + "WHERE mimetype = 'vnd.android.cursor.item/phone_v2' OR mimetype = 'vnd.android.cursor.item/email_v2'\n"
                    + "ORDER BY name_raw_contact.display_name ASC;")
            else:
                resultSet = contactDb.runQuery(
                    "SELECT mimetype, data1, raw_contacts.display_name AS display_name \n"
                    + "FROM raw_contacts JOIN contacts ON (raw_contacts.contact_id=contacts._id) \n"
                    + "LEFT OUTER JOIN data ON (data.raw_contact_id=raw_contacts._id) \n"
                    + "LEFT OUTER JOIN mimetypes ON (data.mimetype_id=mimetypes._id) \n"
                    + "WHERE mimetype = 'vnd.android.cursor.item/phone_v2' OR mimetype = 'vnd.android.cursor.item/email_v2'\n"
                    + "ORDER BY raw_contacts.display_name ASC;")

            contactArtifact = None
            oldName = None
            phoneNumber = None
            emailAddr = None
            name = None              
            while resultSet.next():
                name = resultSet.getString("display_name")
                data1 = resultSet.getString("data1") # the phone number or email
                mimetype = resultSet.getString("mimetype") # either phone or email
                if oldName and (name != oldName):
                    if phoneNumber or emailAddr:
                        contactArtifact = contactDbHelper.addContact(oldName,
                                                            phoneNumber,    # phoneNumber,
                                                            None,           # homePhoneNumber,
                                                            None,           # mobilePhoneNumber,
                                                            emailAddr)      # emailAddr

                        oldName = name
                        phoneNumber = None
                        emailAddr = None
                        name = None

                if mimetype == "vnd.android.cursor.item/phone_v2":
                    phoneNumber = data1                                      
                else:
                    emailAddr = data1
                    
                if name:
                    oldName = name


            # create contact for last row
            if oldName and (phoneNumber or emailAddr):
                contactArtifact = contactDbHelper.addContact(oldName,
                            phoneNumber,    # phoneNumber,
                            None,           # homePhoneNumber,
                            None,           # mobilePhoneNumber,
                            emailAddr)      # emailAddr                                            
                                                                     
        except SQLException as ex:
                self._logger.log(Level.WARNING, "Error processing query result for Android messages.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
                self._logger.log(Level.SEVERE, "Failed to add Android message artifacts.", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
                self._logger.log(Level.WARNING, "Failed to post artifacts.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
        except NoCurrentCaseException as ex:
                self._logger.log(Level.WARNING, "No case currently open.", ex)
                self._logger.log(Level.WARNING, traceback.format_exc())
        finally:
            contactDb.close()
