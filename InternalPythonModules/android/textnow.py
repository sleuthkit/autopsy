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
from org.apache.commons.codec.binary import Base64
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import AppSQLiteDB
from org.sleuthkit.autopsy.coreutils import AppDBParserHelper
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel import Account
from TskMessagesParser import TskMessagesParser
from TskContactsParser import TskContactsParser
from TskCallLogsParser import TskCallLogsParser

import traceback
import general

class TextNowAnalyzer(general.AndroidComponentAnalyzer):
    """
        Parses the TextNow App databases for TSK contacts, message 
        and calllog artifacts.
    """
   
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._TEXTNOW_PACKAGE_NAME = "com.enflick.android.TextNow"
        self._PARSER_NAME = "TextNow Parser"

    def analyze(self, dataSource, fileManager, context):
        """
            Extract, Transform and Load all messages, contacts and 
            calllogs from the TextNow databases.
        """

        try:
            textnow_dbs = AppSQLiteDB.findAppDatabases(dataSource, 
                    "viber_data", True, self._TEXTNOW_PACKAGE_NAME)

            #Extract TSK_CONTACT and TSK_CALLLOG information
            for textnow_db in textnow_dbs:
                helper = AppDBParserHelper(self._PARSER_NAME, 
                        textnow_db.getDBFile(), Account.Type.TEXTNOW) 

                contacts_parser = TextNowContactsParser(textnow_db)
                while contacts_parser.next():
                    helper.addContact( 
                        contacts_parser.get_account_name(), 
                        contacts_parser.get_contact_name(), 
                        contacts_parser.get_phone(),
                        contacts_parser.get_home_phone(),
                        contacts_parser.get_mobile_phone(),
                        contacts_parser.get_email()
                    )
                contacts_parser.close()

                calllog_parser = TextNowCallLogsParser(textnow_db)
                while calllog_parser.next():
                    helper.addCalllog(
                        calllog_parser.get_call_direction(),
                        calllog_parser.get_phone_number_from(),
                        calllog_parser.get_phone_number_to(),
                        calllog_parser.get_call_start_date_time(),
                        calllog_parser.get_call_end_date_time(),
                        calllog_parser.get_call_type()
                    )
                calllog_parser.close()

                messages_parser = TextNowMessagesParser(textnow_db)
                while messages_parser.next():
                    helper.addMessage(
                        messages_parser.get_message_type(),
                        messages_parser.get_message_direction(),
                        messages_parser.get_phone_number_from(),
                        messages_parser.get_phone_number_to(),
                        messages_parser.get_message_date_time(),
                        messages_parser.get_message_read_status(),
                        messages_parser.get_message_subject(),
                        messages_parser.get_message_text(),
                        messages_parser.get_thread_id()
                    )
                messages_parser.close()
                
                textnow_db.close()
        except (SQLException, TskCoreException) as ex:
            #Error parsing TextNow db
            self._logger.log(Level.WARNING, "Error parsing TextNow Databases", ex)
            self._logger.log(Level.WARNING, traceback.format_exec())

class TextNowCallLogsParser(TskCallLogsParser):
    """
        Extracts TSK_CALLLOG information from the TextNow database.
        TSK_CALLLOG fields that are not in the TextNow database are given
        a default value inherited from the super class.
    """

    def __init__(self, calllog_db):
        super(TextNowCallLogsParser, self).__init__(calllog_db.runQuery(
                 """
                 """
             )    
        )

    def get_phone_number_from(self):

    def get_phone_number_to(self):

    def get_call_direction(self):

    def get_call_start_date_time(self):

    def get_call_end_date_time(self):

    def get_call_type(self):

class TextNowContactsParser(TskContactsParser):
    """
        Extracts TSK_CONTACT information from the TextNow database.
        TSK_CONTACT fields that are not in the TextNow database are given 
        a default value inherited from the super class. 
    """

    def __init__(self, contact_db):
        super(TextNowContactsParser, self).__init__(contact_db.runQuery(
                 """
                 """                                                         
             )
        )
    
    def get_account_name(self):
        
    def get_contact_name(self):

    def get_phone(self):

class TextNowMessagesParser(TskMessagesParser):
    """
        Extract TSK_MESSAGE information from the TextNow database.
        TSK_CONTACT fields that are not in the TextNow database are given
        a default value inherited from the super class. 
    """

    def __init__(self, message_db):
        super(TextNowMessagesParser, self).__init__(message_db.runQuery(
                 """
                 """
             )
        )
        self._TEXTNOW_MESSAGE_TYPE = "TextNow Message"

    def get_message_type(self):
        return self._TEXTNOW_MESSAGE_TYPE 

    def get_phone_number_from(self):

    def get_message_direction(self):  
    
    def get_phone_number_to(self):

    def get_message_date_time(self):

    def get_message_read_status(self):

    def get_message_text(self):

    def get_thread_id(self):
