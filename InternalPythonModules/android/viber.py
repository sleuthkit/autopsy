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

class ViberAnalyzer(general.AndroidComponentAnalyzer):
    """
        Parses the Viber App databases for TSK contacts, message 
        and calllog artifacts.
    """
   
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._VIBER_PACKAGE_NAME = "com.viber.voip"
        self._PARSER_NAME = "Viber Parser"

    def analyze(self, dataSource, fileManager, context):
        """
            Extract, Transform and Load all messages, contacts and 
            calllogs from the Viber databases.
        """

        try:
            contact_and_calllog_dbs = AppSQLiteDB.findAppDatabases(dataSource, 
                    "viber_data", True, self._VIBER_PACKAGE_NAME)
            message_dbs = AppSQLiteDB.findAppDatabases(dataSource, 
                    "viber_messages", True, self._VIBER_PACKAGE_NAME)

            #Extract TSK_CONTACT and TSK_CALLLOG information
            for contact_and_calllog_db in contact_and_calllog_dbs:
                parser_helper = AppDBParserHelper(self._PARSER_NAME, 
                        contact_and_calllog_db.getDBFile(), Account.Type.VIBER) 

                contacts_parser = ViberContactsParser(contact_and_calllog_db)
                while contacts_parser.next():
                    parser_helper.addContact( 
                        contacts_parser.get_account_name(), 
                        contacts_parser.get_contact_name(), 
                        contacts_parser.get_phone(),
                        contacts_parser.get_home_phone(),
                        contacts_parser.get_mobile_phone(),
                        contacts_parser.get_email()
                    )
                contacts_parser.close()

                calllog_parser = ViberCallLogsParser(contact_and_calllog_db)
                while calllog_parser.next():
                    parser_helper.addCalllog(
                        calllog_parser.get_account_name(),
                        calllog_parser.get_call_direction(),
                        calllog_parser.get_phone_number_from(),
                        calllog_parser.get_phone_number_to(),
                        calllog_parser.get_call_start_date_time(),
                        calllog_parser.get_call_end_date_time(),
                        calllog_parser.get_contact_name()
                    )
                calllog_parser.close()

                contact_and_calllog_db.close()

            #Extract TSK_MESSAGE information
            for message_db in message_dbs:
                parser_helper = AppDBParserHelper(self._PARSER_NAME, 
                        message_db.getDBFile(), Account.Type.VIBER)

                messages_parser = ViberMessagesParser(message_db)
                while messages_parser.next():
                    parser_helper.addMessage(
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
            #Error parsing Viber db
            self._logger.log(Level.WARNING, "Error parsing Viber Databases", ex)
            self._logger.log(Level.WARNING, traceback.format_exec())

class ViberCallLogsParser(TskCallLogsParser):
    """
        Extracts TSK_CALLLOG information from the Viber database.
        TSK_CALLLOG fields that are not in the Viber database are given
        a default value inherited from the super class.
    """

    def __init__(self, calllog_db):
        super(ViberCallLogsParser, self).__init__(calllog_db.runQuery(
                 """
                      SELECT C.canonized_number AS number, 
                             C.type             AS direction, 
                             C.duration         AS seconds, 
                             C.date             AS start_time 
                      FROM   calls AS C 
                 """
             )    
        )

        self._OUTGOING_CALL_TYPE = 2
        self._INCOMING_CALL_TYPE = 1
        self._MISSED_CALL_TYPE = 3

    def get_account_name(self):
        return self.result_set.getString("number")

    def get_phone_number_from(self):
        if self.get_call_direction() == self.INCOMING_CALL:
            return self.result_set.getString("number")
        #Give default value if the call is outgoing, 
        #the device's # is not stored in the database.
        return super(ViberCallLogsParser, self).get_phone_number_from()

    def get_phone_number_to(self):
        if self.get_call_direction() == self.OUTGOING_CALL:
            return self.result_set.getString("number")
        #Give default value if the call is incoming, 
        #the device's # is not stored in the database.
        return super(ViberCallLogsParser, self).get_phone_number_to()

    def get_call_direction(self):
        direction = self.result_set.getInt("direction")
        if direction == self._INCOMING_CALL_TYPE or direction == self._MISSED_CALL_TYPE:
            return self.INCOMING_CALL
        return self.OUTGOING_CALL

    def get_call_start_date_time(self):
        return self.result_set.getLong("start_time") / 1000

    def get_call_end_date_time(self):
        start_time = self.get_call_start_date_time()
        duration = self.result_set.getLong("seconds")
        return start_time + duration

class ViberContactsParser(TskContactsParser):
    """
        Extracts TSK_CONTACT information from the Viber database.
        TSK_CONTACT fields that are not in the Viber database are given 
        a default value inherited from the super class. 
    """

    def __init__(self, contact_db):
        super(ViberContactsParser, self).__init__(contact_db.runQuery(
                 """
                      SELECT C.display_name AS name, 
                             D.data2        AS number 
                      FROM   phonebookcontact AS C 
                             JOIN phonebookdata AS D 
                               ON C._id = D.contact_id
                 """                                                         
             )
        )
    
    def get_account_name(self):
        return self.result_set.getString("number")
        
    def get_contact_name(self):
        return self.result_set.getString("name")

    def get_phone(self):
        return self.result_set.getString("number")

class ViberMessagesParser(TskMessagesParser):
    """
        Extract TSK_MESSAGE information from the Viber database.
        TSK_CONTACT fields that are not in the Viber database are given
        a default value inherited from the super class. 
    """

    def __init__(self, message_db):
        super(ViberMessagesParser, self).__init__(message_db.runQuery(
                 """
                     SELECT convo_participants.from_number AS from_number, 
                            convo_participants.recipients  AS recipients, 
                            M.conversation_id              AS thread_id, 
                            M.body                         AS msg_content, 
                            M.send_type                    AS direction, 
                            M.msg_date                     AS msg_date, 
                            M.unread                       AS read_status 
                     FROM   (SELECT *, 
                                    group_concat(TO_RESULT.number) AS recipients 
                             FROM   (SELECT P._id     AS FROM_ID, 
                                            P.conversation_id, 
                                            PI.number AS FROM_NUMBER 
                                     FROM   participants AS P 
                                            JOIN participants_info AS PI 
                                              ON P.participant_info_id = PI._id) AS FROM_RESULT 
                                    JOIN (SELECT P._id AS TO_ID, 
                                                 P.conversation_id, 
                                                 PI.number 
                                          FROM   participants AS P 
                                                 JOIN participants_info AS PI 
                                                   ON P.participant_info_id = PI._id) AS TO_RESULT 
                                      ON FROM_RESULT.from_id != TO_RESULT.to_id 
                                         AND FROM_RESULT.conversation_id = TO_RESULT.conversation_id 
                             GROUP  BY FROM_RESULT.from_id) AS convo_participants 
                            JOIN messages AS M 
                              ON M.participant_id = convo_participants.from_id 
                                 AND M.conversation_id = convo_participants.conversation_id
                 """
             )
        )
        self._VIBER_MESSAGE_TYPE = "Viber Message"
        self._INCOMING_MESSAGE_TYPE = 0
        self._OUTGOING_MESSAGE_TYPE = 1

    def get_message_type(self):
        return self._VIBER_MESSAGE_TYPE 

    def get_phone_number_from(self):
        return Account.Address(self.result_set.getString("from_number"), 
                self.result_set.getString("from_number"))

    def get_message_direction(self):  
        direction = self.result_set.getInt("direction")
        if direction == self._INCOMING_MESSAGE_TYPE:
            return self.INCOMING_MSG 
        return self.OUTGOING_MSG 
    
    def get_phone_number_to(self):
        recipients = []
        for token in self.result_set.getString("recipients").split(","):
            recipients.append(Account.Address(token, token))
        return recipients

    def get_message_date_time(self):
        #transform from ms to seconds
        return self.result_set.getLong("msg_date") / 1000 

    def get_message_read_status(self):
        if self.get_message_direction() == self.INCOMING_MSG: 
            if self.result_set.getInt("read_status") == 0:
                return AppDBParserHelper.MessageReadStatusEnum.READ
            else:
                return AppDBParserHelper.MessageReadStatusEnum.UNREAD
        return super(ViberMessagesParser, self).get_message_read_status()

    def get_message_text(self):
        return self.result_set.getString("msg_content") 

    def get_thread_id(self):
        return str(self.result_set.getInt("thread_id")) 
