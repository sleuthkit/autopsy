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
from general import appendAttachmentList

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
        self._VERSION = "6.41.0.2"

    def analyze(self, dataSource, fileManager, context):
        """
            Extract, Transform and Load all messages, contacts and 
            calllogs from the TextNow databases.
        """

        try:
            textnow_dbs = AppSQLiteDB.findAppDatabases(dataSource, 
                    "textnow_data.db", True, self._TEXTNOW_PACKAGE_NAME)

            for textnow_db in textnow_dbs:
                helper = AppDBParserHelper(self._PARSER_NAME, 
                        textnow_db.getDBFile(), Account.Type.TEXTNOW) 

                #Extract TSK_CONTACT information
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

                #Extract TSK_CALLLOG information
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

                #Extract TSK_MESSAGES information
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
                     SELECT contact_value     AS num, 
                            message_direction AS direction, 
                            message_text      AS duration, 
                            date              AS datetime 
                     FROM   messages AS M 
                     WHERE  message_type IN ( 100, 102 )
                 """
             )    
        )
        self._INCOMING_CALL_TYPE = 1
        self._OUTGOING_CALL_TYPE = 2
        self._has_errors = False

    def get_phone_number_from(self):
        if self.get_call_direction() == self.OUTGOING_CALL:
            return super(TextNowCallLogsParser, self).get_phone_number_from()
        return Account.Address(self.result_set.getString("num"),
                        self.result_set.getString("num"))

    def get_phone_number_to(self):
        if self.get_call_direction() == self.INCOMING_CALL:
            return super(TextNowCallLogsParser, self).get_phone_number_to() 
        return Account.Address(self.result_set.getString("num"), 
                        self.result_set.getString("num"))

    def get_call_direction(self):
        if self.result_set.getInt("direction") == self._INCOMING_CALL_TYPE:
            return self.INCOMING_CALL 
        return self.OUTGOING_CALL

    def get_call_start_date_time(self):
        return self.result_set.getLong("datetime") / 1000

    def get_call_end_date_time(self):
        start = self.get_call_start_date_time()
        duration = self.result_set.getString("duration")
        try:
            return start + long(duration)
        except ValueError as ve:
            self._has_errors = True 
            return super(TextNowCallLogsParser, self).get_call_end_date_time() 

class TextNowContactsParser(TskContactsParser):
    """
        Extracts TSK_CONTACT information from the TextNow database.
        TSK_CONTACT fields that are not in the TextNow database are given 
        a default value inherited from the super class. 
    """

    def __init__(self, contact_db):
        super(TextNowContactsParser, self).__init__(contact_db.runQuery(
                 """
                     SELECT C.contact_value AS number, 
                            CASE 
                              WHEN contact_name IS NULL THEN contact_value 
                              WHEN contact_name == "" THEN contact_value 
                              ELSE contact_name 
                            END             name 
                     FROM   contacts AS C
                 """                                                         
             )
        )
    
    def get_account_name(self):
        return self.result_set.getString("number")
        
    def get_contact_name(self):
        return self.result_set.getString("name")

    def get_phone(self):
        return self.result_set.getString("number")

class TextNowMessagesParser(TskMessagesParser):
    """
        Extract TSK_MESSAGE information from the TextNow database.
        TSK_CONTACT fields that are not in the TextNow database are given
        a default value inherited from the super class. 
    """

    def __init__(self, message_db):
        """
            The TextNow database in v6.41.0.2 is structured as follows:
                - A messages table, which stores messages from/to a number
                - A contacts table, which stores phone numbers
                - A groups table, which stores each group the device owner is a part of
                - A group_members table, which stores who is in each group

            The messages table contains both call logs and messages, with a type
            column differentiating the two.
            
            The query below does the following:
                - The group_info inner query creates a comma seperated list of group recipients
                  for each group. This result is then joined on the groups table to get the thread id. 
                - The contacts table is unioned with this result so we have a complete map
                  of "from" phone_numbers -> recipients (group or single). This is the
                  'to_from_map' inner query.
                - Finally, the to_from_map results are joined with the messages table to get all
                  of the communication details.  
        """
        super(TextNowMessagesParser, self).__init__(message_db.runQuery(
                 """
                    SELECT CASE 
                             WHEN message_direction == 2 THEN "" 
                             WHEN to_addresses IS NULL THEN M.contact_value 
                             ELSE contact_name 
                           end from_address, 
                           CASE 
                             WHEN message_direction == 1 THEN "" 
                             WHEN to_addresses IS NULL THEN M.contact_value 
                             ELSE to_addresses 
                           end to_address, 
                           message_direction, 
                           message_text, 
                           M.READ, 
                           M.date, 
                           M.attach, 
                           thread_id 
                    FROM   (SELECT group_info.contact_value, 
                                   group_info.to_addresses, 
                                   G._id AS thread_id 
                            FROM   (SELECT GM.contact_value, 
                                           Group_concat(GM.member_contact_value) AS to_addresses 
                                    FROM   group_members AS GM 
                                    GROUP  BY GM.contact_value) AS group_info 
                                   JOIN groups AS G 
                                     ON G.contact_value = group_info.contact_value 
                            UNION 
                            SELECT c.contact_value, 
                                   NULL, 
                                   -1 
                            FROM   contacts AS c) AS to_from_map 
                           JOIN messages AS M 
                             ON M.contact_value = to_from_map.contact_value 
                    WHERE  message_type NOT IN ( 102, 100 ) 
                 """
             )
        )
        self._TEXTNOW_MESSAGE_TYPE = "TextNow Message"
        self._INCOMING_MESSAGE_TYPE = 1
        self._OUTGOING_MESSAGE_TYPE = 2
        self._UNKNOWN_THREAD_ID = -1

    def get_message_type(self):
        return self._TEXTNOW_MESSAGE_TYPE 

    def get_phone_number_from(self):
        if self.result_set.getString("from_address") == "":
            return super(TextNowMessagesParser, self).get_phone_number_from() 
        return Account.Address(self.result_set.getString("from_address"),
                    self.result_set.getString("from_address"))

    def get_message_direction(self):  
        direction = self.result_set.getInt("message_direction")
        if direction == self._INCOMING_MESSAGE_TYPE:
            return self.INCOMING
        return self.OUTGOING
    
    def get_phone_number_to(self):
        if self.result_set.getString("to_address") == "":
            return super(TextNowMessagesParser, self).get_phone_number_to() 
        return Account.Address(self.result_set.getString("to_address"),
                    self.result_set.getString("to_address"))

    def get_message_date_time(self):
        #convert ms to s
        return self.result_set.getLong("date") / 1000;

    def get_message_read_status(self):
        read = self.result_set.getBoolean("read")
        if self.get_message_direction() == self.INCOMING:
            if read == True:
                return self.READ
            return self.UNREAD

        #read status for outgoing messages cannot be determined, give default
        return super(TextNowMessagesParser, self).get_message_read_status()

    def get_message_text(self):
        text = self.result_set.getString("message_text")
        attachment = self.result_set.getString("attach")
        if attachment != "":
            text = appendAttachmentList(text, [attachment]) 
        return text

    def get_thread_id(self):
        thread_id = self.result_set.getInt("thread_id")
        if thread_id == self._UNKNOWN_THREAD_ID:
            return super(TextNowMessagesParser, self).get_thread_id()
        return str(thread_id)
