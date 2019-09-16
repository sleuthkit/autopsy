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

class SkypeAnalyzer(general.AndroidComponentAnalyzer):
    """
        Parses the Viber App databases for TSK contacts, message 
        and calllog artifacts.
    """
   
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._SKYPE_PACKAGE_NAME = "com.skype.raider"
        self._PARSER_NAME = "Skype Parser"
        self._VERSION = "8.15.0.428"

    def analyze(self, dataSource, fileManager, context):
        """
            Extract, Transform and Load all messages, contacts and 
            calllogs from the Skype databases.
        """

        try:
            skype_dbs = AppSQLiteDB.findAppDatabases(dataSource, 
                    "live", False, "") #self._SKYPE_PACKAGE_NAME)

            #Extract TSK_CONTACT and TSK_CALLLOG information
            for skype_db in skype_dbs:
                helper = AppDBParserHelper(self._PARSER_NAME, 
                        skype_db.getDBFile(), Account.Type.SKYPE) 

                contacts_parser = SkypeContactsParser(skype_db)
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
                """
                calllog_parser = SkypeCallLogsParser(skype_db)
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

                messages_parser = SkypeMessagesParser(skype_db)
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
                """
                skype_db.close()
        except (SQLException, TskCoreException) as ex:
            #Error parsing Viber db
            self._logger.log(Level.WARNING, "Error parsing Skype Databases", ex)
            self._logger.log(Level.WARNING, traceback.format_exec())

class SkypeCallLogsParser(TskCallLogsParser):
    """
        Extracts TSK_CALLLOG information from the Skype database.
        TSK_CALLLOG fields that are not in the Skype database are given
        a default value inherited from the super class.
    """

    def __init__(self, calllog_db):
        super(SkypeCallLogsParser, self).__init__(calllog_db.runQuery(
                 """
                    SELECT full_contacts_list.id, 
                           full_contacts_list.members, 
                           full_contacts_list.names, 
                           time, 
                           duration, 
                           is_sender_me, 
                           person_id
                    FROM   (SELECT conversation_id AS id , 
                                   Group_concat(person_id) AS members, 
                                   Group_concat(CASE 
                                                  WHEN Ifnull(first_name, "") == "" 
                                                       AND Ifnull(last_name,"") == "" THEN entry_id 
                                                  WHEN Ifnull(first_name, "") == "" THEN last_name 
                                                  WHEN Ifnull(last_name, "") == "" THEN first_name 
                                                  ELSE first_name || " " || last_name 
                                                END) AS names 
                            FROM   particiapnt AS PART 
                                   JOIN person AS P 
                                     ON PART.person_id = P.entry_id 
                            GROUP  BY conversation_id 
                            UNION 
                            SELECT entry_id AS id, 
                                   NULL, 
                                   CASE 
                                     WHEN Ifnull(first_name, "") == "" 
                                          AND Ifnull(last_name, "") == "" THEN entry_id 
                                     WHEN Ifnull(first_name, "") == "" THEN last_name 
                                     WHEN Ifnull(last_name, "") == "" THEN first_name 
                                     ELSE first_name 
                                          || " " 
                                          || last_name 
                                   end      AS name 
                            FROM   person) AS full_contacts_list 
                           JOIN chatitem AS C 
                             ON C.conversation_link = full_contacts_list.id
                    WHERE message_type == 3
                 """
             )    
        )
        self._INCOMING_CALL_TYPE = 0
        self._OUTGOING_CALL_TYPE = 1
        

    def get_phone_number_from(self):
        if self.get_call_direction() == self._INCOMING_CALL_TYPE:
            return Account.Address(self.result_set.getString("id"),
                        self.result_set.getString("names"))
        return super(SkypeCallLogsParser, self).get_phone_number_from()

    def get_phone_number_to(self):
        if self.get_call_direction() == self._OUTGOING_CALL_TYPE:
            return Account.Address(self.result_set.getString("id"), 
                        self.result_set.getString("names"))
        return super(SkypeCallLogsParser, self).get_phone_number_to()
        

    def get_call_direction(self):
        direction = self.result_set.getInt("is_sender_me")
        if direction == self._INCOMING_TYPE:
            return self.INCOMING_CALL
        return self.OUTGOING_CALL

    def get_call_start_date_time(self):
        return self.result_set.getLong("time") / 1000

    def get_call_end_date_time(self):
        start = self.get_call_start_date_time()
        duration = self.result_set.getInt("duration") / 1000
        return start + duration

class SkypeContactsParser(TskContactsParser):
    """
        Extracts TSK_CONTACT information from the Skype database.
        TSK_CONTACT fields that are not in the Skype database are given 
        a default value inherited from the super class. 
    """

    def __init__(self, contact_db):
        super(SkypeContactsParser, self).__init__(contact_db.runQuery(
                 """
                    SELECT entry_id, 
                           CASE 
                             WHEN Ifnull(first_name, "") == "" 
                                  AND Ifnull(last_name, "") == "" THEN entry_id 
                             WHEN Ifnull(first_name, "") == "" THEN last_name 
                             WHEN Ifnull(last_name, "") == "" THEN first_name 
                             ELSE first_name 
                                  || " " 
                                  || last_name 
                           end AS name 
                    FROM   person 
                    union
                    SELECT entry_id, CASE 
                             WHEN Ifnull(first_name, "") == "" 
                                  AND Ifnull(last_name, "") == "" THEN entry_id 
                             WHEN Ifnull(first_name, "") == "" THEN last_name 
                             WHEN Ifnull(last_name, "") == "" THEN first_name 
                             ELSE first_name 
                                  || " " 
                                  || last_name 
                           end AS name
                    FROM user
                 """                                                         
              )
        )
    
    def get_account_name(self):
        return self.result_set.getString("entry_id")
        
    def get_contact_name(self):
        return self.result_set.getString("name")

class SkypeMessagesParser(TskMessagesParser):
    """
        Extract TSK_MESSAGE information from the Skype database.
        TSK_CONTACT fields that are not in the Skype database are given
        a default value inherited from the super class. 
    """

    def __init__(self, message_db):
        super(SkypeMessagesParser, self).__init__(message_db.runQuery(
                 """
                 """
             )
        )

    #def get_message_type(self):

    #def get_phone_number_from(self):

    #def get_message_direction(self):  
    
    #def get_phone_number_to(self):

    #def get_message_date_time(self):

    #def get_message_read_status(self):

    #def get_message_text(self):

    #def get_thread_id(self):
