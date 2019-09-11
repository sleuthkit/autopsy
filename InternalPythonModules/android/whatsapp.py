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

class WhatsAppAnalyzer(general.AndroidComponentAnalyzer):
    """
        Parses the WhatsApp databases for TSK contact and message artifacts.
    """
   
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._WHATSAPP_PACKAGE_NAME = "com.whatsapp"
        self._PARSER_NAME = "WhatsApp Parser"

    def analyze(self, dataSource, fileManager, context):
        """
            Extract, Transform and Load all TSK contact and message
            artifacts from the WhatsApp databases.
        """

        try:
            contact_dbs = AppSQLiteDB.findAppDatabases(dataSource,
                    "wa.db", True, self._WHATSAPP_PACKAGE_NAME)
            message_dbs = AppSQLiteDB.findAppDatabases(dataSource,
                    "msgstore.db", True, self._WHATSAPP_PACKAGE_NAME)

            #Extract TSK_CONTACT information
            for contact_db in contact_dbs:
                helper = AppDBParserHelper(self._PARSER_NAME,
                        contact_db.getDBFile(), Account.Type.WHATSAPP) 

                contacts_parser = WhatsAppContactsParser(contact_db)
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

                contact_db.close()

            for message_db in message_dbs:
                helper = AppDBParserHelper(self._PARSER_NAME,
                        message_db.getDBFile(), Account.Type.WHATSAPP)

                message_db.attachDatabase(dataSource, "wa.db",
                        message_db.getDBFile().getParentPath(), "wadb")

                messages_parser = WhatsAppMessagesParser(message_db)
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

                message_db.close()
        except (SQLException, TskCoreException) as ex:
            #Error parsing WhatsApp db
            self._logger.log(Level.WARNING, "Error parsing WhatsApp Databases", ex)
            self._logger.log(Level.WARNING, traceback.format_exec())

class WhatsAppContactsParser(TskContactsParser):
    """
        Extracts TSK_CONTACT information from the WhatsApp database.
        TSK_CONTACT fields that are not in the WhatsApp database are given
        a default value inherited from the super class. 
    """

    def __init__(self, contact_db):
        super(WhatsAppContactsParser, self).__init__(contact_db.runQuery(
                 """ 
                     SELECT """ + _get_contacts_formatting() + """
                     FROM   wa_contacts AS WC
                 """
              )
        )

    def get_account_name(self):
        return self.get_phone()

    def get_contact_name(self):
        return self.result_set.getString("name")

    def get_phone(self):
        return self.result_set.getString("number")

class WhatsAppMessagesParser(TskMessagesParser):
    """
        Extract TSK_MESSAGE information from the WhatsApp database.
        TSK_CONTACT fields that are not in the WhatsApp database are given
        a default value inherited from the super class. 
    """

    def __init__(self, message_db):
        super(WhatsAppMessagesParser, self).__init__(message_db.runQuery(
                 """
                     SELECT M.data               AS content, 
                        """+_get_contacts_formatting()+""",
                            M.key_from_me        AS direction, 
                            M.received_timestamp AS received_datetime, 
                            M.timestamp          AS send_datetime 
                     FROM   messages AS M 
                            JOIN wadb.wa_contacts AS WC 
                              ON M.key_remote_jid = WC.jid
                 """
              )
        )
        self._WHATSAPP_MESSAGE_TYPE = "WhatsApp Message"
        self._INCOMING_MESSAGE_TYPE = 0
        self._OUTGOING_MESSAGE_TYPE = 1

    def get_message_type(self):
        return self._WHATSAPP_MESSAGE_TYPE 

    def get_phone_number_to(self):
        if self.get_message_direction() == self.OUTGOING_MSG:
            return Account.Address(self.result_set.getString("number"), 
                        self.result_set.getString("number"))
        return super(WhatsAppMessagesParser, self).get_phone_number_to()

    def get_phone_number_from(self):
        if self.get_message_direction() == self.INCOMING_MSG:
            return Account.Address(self.result_set.getString("number"), 
                        self.result_set.getString("number"))
        return super(WhatsAppMessagesParser, self).get_phone_number_from() 

    def get_message_direction(self):  
        direction = self.result_set.getInt("direction")
        if direction == self._INCOMING_MESSAGE_TYPE:
            return self.INCOMING_MSG
        return self.OUTGOING_MSG
    
    def get_message_date_time(self):
        #transform from ms to seconds
        if self.get_message_direction() == self.OUTGOING_MSG:
            return self.result_set.getLong("send_datetime") / 1000
        return self.result_set.getLong("received_datetime") / 1000

    def get_message_text(self):
        return self.result_set.getString("content") 

def _get_contacts_formatting():
    """
        This function is here to explicitly stress the point that the 
        formatting routine used in the contacts and messages parsers
        should never differ. These fields are used to correlate in Autopsy.
        
        The SQL statement assumes wa_contacts table is named WC.
    """

    return """ 
               CASE 
                 WHEN WC.number IS NULL THEN WC.jid 
                 WHEN WC.number == "" THEN WC.jid 
                 ELSE WC.number 
               END number, 
               CASE 
                 WHEN WC.given_name IS NULL 
                      AND WC.family_name IS NULL THEN WC.jid 
                 WHEN WC.given_name IS NULL THEN WC.family_name 
                 WHEN WC.family_name IS NULL THEN WC.given_name 
                 ELSE WC.given_name 
                      || " " 
                      || WC.family_name 
               END name 
           """
