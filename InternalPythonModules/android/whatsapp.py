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
from org.sleuthkit.autopsy.casemodule import NoCurrentCaseException
from org.sleuthkit.datamodel import Account
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection
from TskMessagesParser import TskMessagesParser
from TskContactsParser import TskContactsParser
from TskCallLogsParser import TskCallLogsParser

import traceback
import general

class WhatsAppAnalyzer(general.AndroidComponentAnalyzer):
    """
        Parses the WhatsApp databases for TSK contact, message 
        and calllog artifacts.
    
        About WhatsApp parser for v2.19.244:
            - Database Design Details:
                There are 2 databases and 6 tables this parser uses.

                1) Prerequisties:
                    Each user is assigned a whatsapp id, refered to as jid in the
                    database. A jid is of the form:

                             ####...####@whatsapp.net

                    where # is a placeholder for an arbitrary length of digits 1-9.

                2) Databases:
                    - databases/msgstore.db: contains msg and call log info
                    - databases/wa.db: contains contact info

                3) Tables:
                    - wa/wa_contacts:                   Each record maps a jid to a users personal 
                                                        details, such as name and phone number.

                    - msgstore/call_log:                Each call made on the device is a single row 
                                                        in the call_log table. Each record holds 
                                                        information such as duration, direction, and 
                                                        type (Video or Audio). 

                    - msgstore/call_log_participant_v2: Each row of this table maps a jid to
                                                        a call_log record. Multiple rows that 
                                                        share a call_log id indicate a group call.

                    - msgstore/messages:                Each message is represented as a single row. 
                                                        A row maps a jid or a gjid (group jid) to some
                                                        message details. Both the jid and gjid are 
                                                        stored in 1 column, called key_remote_jid. 
                                                        gjid's are of the form:
                            
                                                              #####...###-#####...####@g.us

                                                        where # is a place holder for a digit 1-9. The
                                                        '-' is a fixed character surrounded by digits 
                                                        of arbiturary length n and m. 

                                                        If the message is not from a group, the jid the
                                                        message is to/from is stored in key_remote_jid 
                                                        column. If it is a group, the key_remote_jid 
                                                        column contains the gjid and the 'from' jid is 
                                                        stored in a secondary column called 
                                                        remote_resource.

                    - msgstore/group_participants:      Each row of this table maps a jid to a gjid.

                    - msgstore/jid:                     This table stores raw jid string. Some tables 
                                                        only store the jid_row. A join must be 
                                                        performed to get the jid value out. 
            - Implementation details:
                1) Group calls and single calls are extracted in two different queries. 
                2) Group messages and single messages are extracted in 1 query.
                    - The general approach was to build one complete contacts table containing
                      both jid and gjid. A join can be performed once on all of the messages. 
                      All jids that are part of a gjid were concatenated into a comma seperated 
                      list of jids.
    """
   
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._WHATSAPP_PACKAGE_NAME = "com.whatsapp"
        self._PARSER_NAME = "WhatsApp Parser"
        self._VERSION = "2.19.244"

    def analyze(self, dataSource, fileManager, context):
        """
            Extract, Transform and Load all TSK contact, message
            and calllog artifacts from the WhatsApp databases.
        """

        try:
            contact_dbs = AppSQLiteDB.findAppDatabases(dataSource,
                    "wa.db", True, self._WHATSAPP_PACKAGE_NAME)
            calllog_and_message_dbs = AppSQLiteDB.findAppDatabases(dataSource,
                    "msgstore.db", True, self._WHATSAPP_PACKAGE_NAME)

            #Extract TSK_CONTACT information
            for contact_db in contact_dbs:
                current_case = Case.getCurrentCaseThrows()
                helper = CommunicationArtifactsHelper(
                        current_case.getSleuthkitCase(), self._PARSER_NAME,
                        contact_db.getDBFile(), Account.Type.WHATSAPP) 
                self.parse_contacts(contact_db, helper)

            for calllog_and_message_db in calllog_and_message_dbs:
                current_case = Case.getCurrentCaseThrows()
                helper = CommunicationArtifactsHelper(
                        current_case.getSleuthkitCase(), self._PARSER_NAME,
                        calllog_and_message_db.getDBFile(), Account.Type.WHATSAPP)
                self.parse_calllogs(calllog_and_message_db, helper)
                self.parse_messages(dataSource, calllog_and_message_db, helper)

        except NoCurrentCaseException as ex:
            #If there is no current case, bail out immediately.
            self._logger.log(Level.WARNING, "No case currently open.", ex)
            self._logger.log(Level.WARNING, traceback.format_exec())
        
        #Clean up open file handles.
        for contact_db in contact_dbs:
            contact_db.close()

        for calllog_and_message_db in calllog_and_message_dbs:
            calllog_and_message_db.close()

    def parse_contacts(self, contacts_db, helper):
        try:
            contacts_parser = WhatsAppContactsParser(contacts_db, self._PARSER_NAME)
            while contacts_parser.next():
                helper.addContact( 
                    contacts_parser.get_contact_name(), 
                    contacts_parser.get_phone(),
                    contacts_parser.get_home_phone(),
                    contacts_parser.get_mobile_phone(),
                    contacts_parser.get_email(),
                    contacts_parser.get_other_attributes()
                )
            contacts_parser.close()
        except SQLException as ex:
            self._logger.log(Level.WARNING, "Error querying the whatsapp database for contacts.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, 
                    "Error adding whatsapp contact artifacts to the case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            self._logger.log(Level.WARNING, 
                    "Error posting contact artifact to the blackboard.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())

    def parse_calllogs(self, calllogs_db, helper):
        try:
            single_calllogs_parser = WhatsAppSingleCallLogsParser(calllogs_db)
            while single_calllogs_parser.next():
                helper.addCalllog(
                    single_calllogs_parser.get_call_direction(),
                    single_calllogs_parser.get_phone_number_from(),
                    single_calllogs_parser.get_phone_number_to(),
                    single_calllogs_parser.get_call_start_date_time(),
                    single_calllogs_parser.get_call_end_date_time(),
                    single_calllogs_parser.get_call_type()
                )
            single_calllogs_parser.close()

            group_calllogs_parser = WhatsAppGroupCallLogsParser(calllogs_db)
            while group_calllogs_parser.next():
                helper.addCalllog(
                    group_calllogs_parser.get_call_direction(),
                    group_calllogs_parser.get_phone_number_from(),
                    group_calllogs_parser.get_phone_number_to(),
                    group_calllogs_parser.get_call_start_date_time(),
                    group_calllogs_parser.get_call_end_date_time(),
                    group_calllogs_parser.get_call_type()
                )
            group_calllogs_parser.close()
        except SQLException as ex:
            self._logger.log(Level.WARNING, "Error querying the whatsapp database for calllogs.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, 
                    "Error adding whatsapp calllog artifacts to the case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            self._logger.log(Level.WARNING, 
                    "Error posting calllog artifact to the blackboard.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())

    def parse_messages(self, dataSource, messages_db, helper):
        try:
            messages_db.attachDatabase(dataSource, "wa.db",
                        messages_db.getDBFile().getParentPath(), "wadb")

            messages_parser = WhatsAppMessagesParser(messages_db)
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
        except SQLException as ex:
            self._logger.log(Level.WARNING, "Error querying the whatsapp database for contacts.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, 
                    "Error adding whatsapp contact artifacts to the case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            self._logger.log(Level.WARNING, 
                    "Error posting contact artifact to the blackboard.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())

class WhatsAppGroupCallLogsParser(TskCallLogsParser):
    """
        Extracts TSK_CALLLOG information from group call logs
        in the WhatsApp database. 
    """

    def __init__(self, calllog_db):
        super(WhatsAppGroupCallLogsParser, self).__init__(calllog_db.runQuery(
                 """
                    SELECT CL.video_call,
                            CL.timestamp,
                            CL.duration,
                            CL.from_me,
		            J1.raw_string AS from_id,
                            group_concat(J.raw_string) AS group_members
                     FROM   call_log_participant_v2 AS CLP
                            JOIN call_log AS CL
                              ON CL._id = CLP.call_log_row_id
                            JOIN jid AS J
                              ON J._id = CLP.jid_row_id
                            JOIN jid as J1
                              ON J1._id = CL.jid_row_id
                            GROUP  BY CL._id
                 """
             )
        )
        self._INCOMING_CALL_TYPE = 0
        self._OUTGOING_CALL_TYPE = 1
        self._VIDEO_CALL_TYPE = 1

    def get_call_direction(self):
        if self.result_set.getInt("from_me") == self._INCOMING_CALL_TYPE:
            return self.INCOMING_CALL
        return self.OUTGOING_CALL

    def get_phone_number_from(self):
        if self.get_call_direction() == self.INCOMING_CALL:
            sender = self.result_set.getString("from_id")
            return sender
        return super(WhatsAppGroupCallLogsParser, self).get_phone_number_from()

    def get_phone_number_to(self):
        if self.get_call_direction() == self.OUTGOING_CALL:
            #group_members column stores comma seperated list of groups or single contact
            group = self.result_set.getString("group_members")
            return group.split(",")
        return super(WhatsAppGroupCallLogsParser, self).get_phone_number_to()

    def get_call_start_date_time(self):
        return self.result_set.getLong("timestamp") / 1000

    def get_call_end_date_time(self):
        start = self.get_call_start_date_time()
        duration = self.result_set.getInt("duration")
        return start + duration
    
    def get_call_type(self):
        if self.result_set.getInt("video_call") == self._VIDEO_CALL_TYPE:
            return self.VIDEO_CALL
        return self.AUDIO_CALL 

class WhatsAppSingleCallLogsParser(TskCallLogsParser):
    """
        Extracts TSK_CALLLOG information from 1 to 1 call logs
        in the WhatsApp database. 
    """
    
    def __init__(self, calllog_db):
        super(WhatsAppSingleCallLogsParser, self).__init__(calllog_db.runQuery(
                 """
                     SELECT CL.timestamp, 
                            CL.video_call, 
                            CL.duration, 
                            J.raw_string AS num, 
                            CL.from_me
                     FROM   call_log AS CL 
                            JOIN jid AS J 
                              ON J._id = CL.jid_row_id 
                     WHERE  CL._id NOT IN (SELECT DISTINCT call_log_row_id 
                                           FROM   call_log_participant_v2) 
                 """
             )
        )
        self._INCOMING_CALL_TYPE = 0
        self._OUTGOING_CALL_TYPE = 1
        self._VIDEO_CALL_TYPE = 1
    
    def get_call_direction(self):
        if self.result_set.getInt("from_me") == self._INCOMING_CALL_TYPE:
            return self.INCOMING_CALL
        return self.OUTGOING_CALL

    def get_phone_number_from(self):
        if self.get_call_direction() == self.INCOMING_CALL:
            sender = self.result_set.getString("num")
            return sender
        return super(WhatsAppSingleCallLogsParser, self).get_phone_number_from()

    def get_phone_number_to(self):
        if self.get_call_direction() == self.OUTGOING_CALL:
            to = self.result_set.getString("num") 
            return to
        return super(WhatsAppSingleCallLogsParser, self).get_phone_number_to()

    def get_call_start_date_time(self):
        return self.result_set.getLong("timestamp") / 1000

    def get_call_end_date_time(self):
        start = self.get_call_start_date_time()
        duration = self.result_set.getInt("duration")
        return start + duration
    
    def get_call_type(self):
        if self.result_set.getInt("video_call") == self._VIDEO_CALL_TYPE:
            return self.VIDEO_CALL
        return self.AUDIO_CALL 


class WhatsAppContactsParser(TskContactsParser):
    """
        Extracts TSK_CONTACT information from the WhatsApp database.
        TSK_CONTACT fields that are not in the WhatsApp database are given
        a default value inherited from the super class. 
    """

    def __init__(self, contact_db, analyzer):
        super(WhatsAppContactsParser, self).__init__(contact_db.runQuery(
                 """ 
                     SELECT jid, 
                            CASE 
                              WHEN WC.number IS NULL THEN WC.jid 
                              WHEN WC.number == "" THEN WC.jid 
                              ELSE WC.number 
                            END number, 
                            CASE 
                              WHEN WC.given_name IS NULL 
                                   AND WC.family_name IS NULL 
                                   AND WC.display_name IS NULL THEN WC.jid 
                              WHEN WC.given_name IS NULL 
                                   AND WC.family_name IS NULL THEN WC.display_name 
                              WHEN WC.given_name IS NULL THEN WC.family_name 
                              WHEN WC.family_name IS NULL THEN WC.given_name 
                              ELSE WC.given_name 
                                   || " " 
                                   || WC.family_name 
                            END name 
                     FROM   wa_contacts AS WC
                 """
                  )
        )

        self._PARENT_ANALYZER = analyzer
    
    def get_contact_name(self):
        return self.result_set.getString("name")

    def get_phone(self):
        return self.result_set.getString("number")

    def get_other_attributes(self):
        return [BlackboardAttribute(
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ID, 
                    self._PARENT_ANALYZER, 
                    self.result_set.getString("jid"))]

class WhatsAppMessagesParser(TskMessagesParser):
    """
        Extract TSK_MESSAGE information from the WhatsApp database.
        TSK_CONTACT fields that are not in the WhatsApp database are given
        a default value inherited from the super class. 
    """

    def __init__(self, message_db):
        super(WhatsAppMessagesParser, self).__init__(message_db.runQuery(
                 """
                    SELECT messages.key_remote_jid  AS id, 
                           contact_book_w_groups.recipients, 
                           key_from_me              AS direction, 
                           messages.data            AS content, 
                           messages.timestamp       AS send_timestamp, 
                           messages.received_timestamp, 
                           messages.remote_resource AS group_sender, 
                           messages.media_url       AS attachment 
                    FROM   (SELECT jid, 
                                   recipients 
                            FROM   wadb.wa_contacts AS contacts 
                                   left join (SELECT gjid, 
                                                     Group_concat(CASE 
                                                                    WHEN jid == "" THEN NULL 
                                                                    ELSE jid 
                                                                  END) AS recipients 
                                              FROM   group_participants 
                                              GROUP  BY gjid) AS groups 
                                          ON contacts.jid = groups.gjid 
                            GROUP  BY jid) AS contact_book_w_groups 
                           join messages 
                             ON messages.key_remote_jid = contact_book_w_groups.jid
                 """
              )
        )
        self._WHATSAPP_MESSAGE_TYPE = "WhatsApp Message"
        self._INCOMING_MESSAGE_TYPE = 0
        self._OUTGOING_MESSAGE_TYPE = 1
        self._message_db = message_db

    def get_message_type(self):
        return self._WHATSAPP_MESSAGE_TYPE 

    def get_phone_number_to(self):
        if self.get_message_direction() == self.OUTGOING:
            group = self.result_set.getString("recipients")
            if group is not None:
                group = group.split(",")
                return group 

            return self.result_set.getString("id") 
        return super(WhatsAppMessagesParser, self).get_phone_number_to()

    def get_phone_number_from(self):
        if self.get_message_direction() == self.INCOMING:
            group_sender = self.result_set.getString("group_sender")
            group = self.result_set.getString("recipients")
            if group_sender is not None and group is not None:
                return group_sender
            else:
                return self.result_set.getString("id") 
        return super(WhatsAppMessagesParser, self).get_phone_number_from() 

    def get_message_direction(self):  
        direction = self.result_set.getInt("direction")
        if direction == self._INCOMING_MESSAGE_TYPE:
            return self.INCOMING
        return self.OUTGOING
    
    def get_message_date_time(self):
        #transform from ms to seconds
        if self.get_message_direction() == self.OUTGOING:
            return self.result_set.getLong("send_timestamp") / 1000
        return self.result_set.getLong("received_timestamp") / 1000

    def get_message_text(self):
        message = self.result_set.getString("content") 
        if message is None:
            message = super(WhatsAppMessagesParser, self).get_message_text()
        attachment = self.result_set.getString("attachment")
        if attachment is not None:
            return general.appendAttachmentList(message, [attachment])
        return message
    
    def get_thread_id(self):
        group = self.result_set.getString("recipients")
        if group is not None:
            return self.result_set.getString("id")
        return super(WhatsAppMessagesParser, self).get_thread_id()
