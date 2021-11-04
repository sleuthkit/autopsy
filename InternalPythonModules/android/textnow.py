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
from org.sleuthkit.autopsy.casemodule import NoCurrentCaseException
from org.sleuthkit.datamodel import Account
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.attributes import MessageAttachments
from org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments import FileAttachment
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection

from TskMessagesParser import TskMessagesParser
from TskContactsParser import TskContactsParser
from TskCallLogsParser import TskCallLogsParser

import traceback
import general

class TextNowAnalyzer(general.AndroidComponentAnalyzer):
    """
        Parses the TextNow App databases for TSK contacts, message 
        and calllog artifacts.

        The TextNow database in v6.41.0.2 is structured as follows:
            - A messages table, which stores messages from/to a number
            - A contacts table, which stores phone numbers
            - A groups table, which stores each group the device owner is a part of
            - A group_members table, which stores who is in each group

        The messages table contains both call logs and messages, with a type
        column differentiating the two.
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

        textnow_dbs = AppSQLiteDB.findAppDatabases(dataSource, 
                    "textnow_data.db", True, self._TEXTNOW_PACKAGE_NAME)
        
        try:
            for textnow_db in textnow_dbs:
                current_case = Case.getCurrentCaseThrows()
                helper = CommunicationArtifactsHelper(
                            current_case.getSleuthkitCase(), self._PARSER_NAME, 
                            textnow_db.getDBFile(), Account.Type.TEXTNOW, context.getJobId()
                         ) 
                self.parse_contacts(textnow_db, helper) 
                self.parse_calllogs(textnow_db, helper)
                self.parse_messages(textnow_db, helper, current_case)
        except NoCurrentCaseException as ex:
            self._logger.log(Level.WARNING, "No case currently open.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        
        for textnow_db in textnow_dbs:
            textnow_db.close()

    def parse_contacts(self, textnow_db, helper):
        #Query for contacts and iterate row by row adding
        #each contact artifact
        try:
            contacts_parser = TextNowContactsParser(textnow_db)
            while contacts_parser.next():
                name = contacts_parser.get_contact_name()
                phone = contacts_parser.get_phone()
                home_phone = contacts_parser.get_home_phone()
                mobile_phone = contacts_parser.get_mobile_phone()
                email = contacts_parser.get_email()

                # add contact if we have at least one valid phone/email
                if phone or home_phone or mobile_phone or email:
                    helper.addContact( 
                        name, 
                        phone,
                        home_phone,
                        mobile_phone,
                        email
                    )
            contacts_parser.close()
        except SQLException as ex:
            #Error parsing TextNow db
            self._logger.log(Level.WARNING, "Error parsing TextNow databases for contacts", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            #Error adding artifacts to the case database.. case database is not complete.
            self._logger.log(Level.SEVERE, 
                    "Error adding TextNow contacts artifacts to the case database", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            #Error posting notification to blackboard...
            self._logger.log(Level.WARNING, 
                    "Error posting TextNow contacts artifact to the blackboard", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())

    def parse_calllogs(self, textnow_db, helper):
        #Query for call logs and iterate row by row adding
        #each call log artifact
        try:
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
        except SQLException as ex:
            self._logger.log(Level.WARNING, "Error parsing TextNow databases for calllogs", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            #Error adding artifacts to the case database.. case database is not complete.
            self._logger.log(Level.SEVERE, 
                    "Error adding TextNow call log artifacts to the case database", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            #Error posting notification to blackboard...
            self._logger.log(Level.WARNING, 
                    "Error posting TextNow call log artifact to the blackboard", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())

    def parse_messages(self, textnow_db, helper, current_case):
        #Query for messages and iterate row by row adding
        #each message artifact
        try:
            messages_parser = TextNowMessagesParser(textnow_db)
            while messages_parser.next():
                message_artifact = helper.addMessage(
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
                if (len(messages_parser.get_file_attachment()) > 0):
                    file_attachments = ArrayList()
                    self._logger.log(Level.INFO, "SHow Attachment ==> " + str(len(messages_parser.get_file_attachment())) + " <> " + str(messages_parser.get_file_attachment()))
                    file_attachments.add(FileAttachment(current_case.getSleuthkitCase(), textnow_db.getDBFile().getDataSource(), messages_parser.get_file_attachment()))
                    message_attachments = MessageAttachments(file_attachments, [])
                    helper.addAttachments(message_artifact, message_attachments)

            messages_parser.close()
        except SQLException as ex:
            #Error parsing TextNow db
            self._logger.log(Level.WARNING, "Error parsing TextNow databases for messages.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            #Error adding artifacts to the case database.. case database is not complete.
            self._logger.log(Level.SEVERE, 
                    "Error adding TextNow messages artifacts to the case database", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            #Error posting notification to blackboard...
            self._logger.log(Level.WARNING, 
                    "Error posting TextNow messages artifact to the blackboard", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())

class TextNowCallLogsParser(TskCallLogsParser):
    """
        Extracts TSK_CALLLOG information from the TextNow database.
        TSK_CALLLOG fields that are not in the TextNow database are given
        a default value inherited from the super class.
    """

    def __init__(self, calllog_db):
        """
            message_type of 100 or 102 are for calls (audio, video) 
        """
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

    def get_phone_number_from(self):
        if self.get_call_direction() == self.OUTGOING_CALL:
            return super(TextNowCallLogsParser, self).get_phone_number_from()
        return self.result_set.getString("num")

    def get_phone_number_to(self):
        if self.get_call_direction() == self.INCOMING_CALL:
            return super(TextNowCallLogsParser, self).get_phone_number_to() 
        return self.result_set.getString("num") 

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
    
    def get_contact_name(self):
        return self.result_set.getString("name")
    
    def get_phone(self):
        number = self.result_set.getString("number")
        return (number if general.isValidPhoneNumber(number) else None)
        
    def get_email(self):
        # occasionally the 'number' column may have an email address instead
        value = self.result_set.getString("number")
        return (value if general.isValidEmailAddress(value) else None)

class TextNowMessagesParser(TskMessagesParser):
    """
        Extract TSK_MESSAGE information from the TextNow database.
        TSK_CONTACT fields that are not in the TextNow database are given
        a default value inherited from the super class. 
    """

    def __init__(self, message_db):
        """
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
                             WHEN messages.message_direction == 2 THEN NULL 
                             WHEN contact_book_w_groups.to_addresses IS NULL THEN 
                             messages.contact_value 
                           END from_address, 
                           CASE 
                             WHEN messages.message_direction == 1 THEN NULL 
                             WHEN contact_book_w_groups.to_addresses IS NULL THEN 
                             messages.contact_value 
                             ELSE contact_book_w_groups.to_addresses 
                           END to_address, 
                           messages.message_direction, 
                           messages.message_text, 
                           messages.READ, 
                           messages.DATE, 
                           messages.attach, 
                           thread_id 
                    FROM   (SELECT GM.contact_value, 
                                   Group_concat(GM.member_contact_value) AS to_addresses, 
                                   G.contact_value                       AS thread_id 
                            FROM   group_members AS GM 
                                   join GROUPS AS G 
                                     ON G.contact_value = GM.contact_value 
                            GROUP  BY GM.contact_value 
                            UNION 
                            SELECT contact_value, 
                                   NULL, 
                                   NULL 
                            FROM   contacts) AS contact_book_w_groups 
                           join messages 
                             ON messages.contact_value = contact_book_w_groups.contact_value 
                    WHERE  message_type NOT IN ( 102, 100 ) 
                 """
             )
        )
        self._TEXTNOW_MESSAGE_TYPE = "TextNow Message"
        self._INCOMING_MESSAGE_TYPE = 1
        self._OUTGOING_MESSAGE_TYPE = 2

    def get_message_type(self):
        return self._TEXTNOW_MESSAGE_TYPE 

    def get_phone_number_from(self):
        if self.result_set.getString("from_address") is None:
            return super(TextNowMessagesParser, self).get_phone_number_from() 
        return self.result_set.getString("from_address")

    def get_message_direction(self):  
        direction = self.result_set.getInt("message_direction")
        if direction == self._INCOMING_MESSAGE_TYPE:
            return self.INCOMING
        return self.OUTGOING
    
    def get_phone_number_to(self):
        if self.result_set.getString("to_address") is None:
            return super(TextNowMessagesParser, self).get_phone_number_to() 
        return self.result_set.getString("to_address").split(",")

    def get_message_date_time(self):
        #convert ms to s
        return self.result_set.getLong("date") / 1000;

    def get_message_read_status(self):
        read = self.result_set.getBoolean("read")
        if self.get_message_direction() == self.INCOMING:
            if read:
                return self.READ
            return self.UNREAD

        #read status for outgoing messages cannot be determined, give default
        return super(TextNowMessagesParser, self).get_message_read_status()

    def get_message_text(self):
        text = self.result_set.getString("message_text")
        return text

    def get_thread_id(self):
        thread_id = self.result_set.getString("thread_id")
        if thread_id is None:
            return super(TextNowMessagesParser, self).get_thread_id()
        return thread_id

    def get_file_attachment(self):
        attachment = self.result_set.getString("attach")
        if attachment is None:
            return None
        return self.result_set.getString("attach")
