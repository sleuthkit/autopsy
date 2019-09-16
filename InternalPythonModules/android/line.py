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
from org.sleuthkit.autopsy.coreutils import AppDBParserHelper
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel import Account
from TskContactsParser import TskContactsParser
from TskMessagesParser import TskMessagesParser
from TskCallLogsParser import TskCallLogsParser
from general import appendAttachmentList

import traceback
import general

class LineAnalyzer(general.AndroidComponentAnalyzer):
    """
        Parses the Line App databases for TSK contacts & message artifacts.
    """

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._LINE_PACKAGE_NAME = "jp.naver.line.android"
        self._PARSER_NAME = "Line Parser"
        self._VERSION = "9.15.1"

    def analyze(self, dataSource, fileManager, context):
        try:
            contact_and_message_dbs = AppSQLiteDB.findAppDatabases(dataSource, 
                    "naver_line", True, self._LINE_PACKAGE_NAME)
            calllog_dbs = AppSQLiteDB.findAppDatabases(dataSource,
                    "call_history", True, self._LINE_PACKAGE_NAME)

            for contact_and_message_db in contact_and_message_dbs:
                helper = AppDBParserHelper(self._PARSER_NAME, 
                            contact_and_message_db.getDBFile(), Account.Type.LINE) 

                contacts_parser = LineContactsParser(contact_and_message_db)
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

                messages_parser = LineMessagesParser(contact_and_message_db)
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
                contact_and_message_db.close()

            for calllog_db in calllog_dbs:
                helper = AppDBParserHelper(self._PARSER_NAME,
                         calllog_db.getDBFile(), Account.Type.LINE)
                calllog_db.attachDatabase(dataSource, 
                        "naver_line", calllog_db.getDBFile().getParentPath(), "naver")

                calllog_parser = LineCallLogsParser(calllog_db)
                while calllog_parser.next():
                    helper.addCalllog(
                        calllog_parser.get_call_direction(),
                        calllog_parser.get_phone_number_from(),
                        calllog_parser.get_phone_number_to(),
                        calllog_parser.get_call_start_date_time(),
                        calllog_parser.get_call_end_date_time(),
                        calllog_parser.get_call_type()
                    )
                calllog_db.detachDatabase("naver")
                calllog_parser.close()

                calllog_db.close()
        except (SQLException, TskCoreException) as ex:
            # Error parsing Line databases.
            self._logger.log(Level.WARNING, "Error parsing the Line App Databases", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())   

class LineCallLogsParser(TskCallLogsParser):
    """
        Parses out TSK_CALLLOG information from the Line database.
        TSK_CALLLOG fields that are not in the line database are given
        a default value inherited from the super class.
    """

    def __init__(self, calllog_db):
        super(LineCallLogsParser, self).__init__(calllog_db.runQuery(
                 """
                     SELECT substr(CallH.call_type, -1) AS direction, 
                            CallH.start_time            AS start_time, 
                            CallH.end_time              AS end_time, 
                            ConT.server_name            AS name, 
                            CallH.voip_type             AS call_type, 
                            ConT.m_id 
                            FROM   call_history AS CallH 
                                   JOIN naver.contacts AS ConT 
                                     ON CallH.caller_mid = ConT.m_id
                 """
             )
        )
        self._OUTGOING_CALL_TYPE = "O"
        self._INCOMING_CALL_TYPE = "I"
        self._had_error = False
        self._VIDEO_CALL_TYPE = "V"
        self._AUDIO_CALL_TYPE = "A"

    def get_call_direction(self):
        direction = self.result_set.getString("direction")
        if direction == self._OUTGOING_CALL_TYPE:
            return self.OUTGOING_CALL
        return self.INCOMING_CALL

    def get_call_start_date_time(self):
        try:
            return long(self.result_set.getString("start_time")) / 1000
        except ValueError as ve:
            self._had_error = True
            return super(LineCallLogsParser, self).get_call_start_date_time()

    def get_call_end_date_time(self):
        try:
            return long(self.result_set.getString("end_time")) / 1000
        except ValueError as ve:
            self._had_error = True
            return super(LineCallLogsParser, self).get_call_end_date_time()
    
    def get_phone_number_to(self):
        if self.get_call_direction() == self.OUTGOING_CALL:
            return Account.Address(self.result_set.getString("m_id"), 
                        self.result_set.getString("name"))
        return super(LineCallLogsParser, self).get_phone_number_to()

    def get_phone_number_from(self):
        if self.get_call_direction() == self.INCOMING_CALL:
            return Account.Address(self.result_set.getString("m_id"),
                        self.result_set.getString("name"))
        return super(LineCallLogsParser, self).get_phone_number_from()

    def get_call_type(self):
        if self.result_set.getString("call_type") == self._VIDEO_CALL_TYPE:
            return self.VIDEO_CALL
        if self.result_set.getString("call_type") == self._AUDIO_CALL_TYPE:
            return self.AUDIO_CALL
        return super(LineCallLogsParser, self).get_call_type()

    def has_incomplete_results(self):
        return self._had_error

class LineContactsParser(TskContactsParser):
    """
        Parses out TSK_CONTACT information from the Line database.
        TSK_CONTACT fields that are not in the line database are given
        a default value inherited from the super class. 
    """

    def __init__(self, contact_db):
        super(LineContactsParser, self).__init__(contact_db.runQuery(
                 """
                     SELECT m_id,
                            server_name
                     FROM   contacts
                 """
              )
        )
    def get_account_name(self):
        return self.result_set.getString("m_id")

    def get_contact_name(self):
        return self.result_set.getString("server_name")

class LineMessagesParser(TskMessagesParser):
    """
        Parse out TSK_MESSAGE information from the Line database.
        TSK_MESSAGE fields that are not in the line database are given
        a default value inherited from the super class.
    """

    def __init__(self, message_db):
        super(LineMessagesParser, self).__init__(message_db.runQuery(
            """
                SELECT all_contacts.name, 
                       all_contacts.id, 
                       all_contacts.members, 
                       CH.from_mid, 
                       CH.content, 
                       CH.created_time, 
                       CH.attachement_type, 
                       CH.attachement_local_uri,
                       CH.status 
                FROM   (SELECT G.name, 
                               group_members.id, 
                               group_members.members 
                        FROM   (SELECT id, 
                                       group_concat(m_id) AS members 
                                FROM   membership 
                                GROUP  BY id) AS group_members 
                               JOIN groups AS G 
                                 ON G.id = group_members.id 
                        UNION 
                        SELECT server_name, 
                               m_id, 
                               NULL 
                        FROM   contacts) AS all_contacts 
                       JOIN chat_history AS CH 
                         ON CH.chat_id = all_contacts.id
                WHERE attachement_type != 6
            """
                        )
        )
        self._LINE_MESSAGE_TYPE = "Line Message"
        #From the limited test data, it appeared that incoming
        #was only associated with a 1 status. Status # 3 and 7
        #was only associated with outgoing.
        self._INCOMING_MESSAGE_TYPE = 1
        self._had_error = False

    def get_message_type(self):
        return self._LINE_MESSAGE_TYPE

    def get_message_date_time(self):
        created_time = self.result_set.getString("created_time")
        try:
            #Get time in seconds (created_time is stored in ms from epoch)
            return long(created_time) / 1000
        except ValueError as ve:
            self._had_error = True
            return super(LineMessagesParser, self).get_message_date_time()

    def get_message_text(self):
        content = self.result_set.getString("content") 
        attachment_uri = self.result_set.getString("attachement_local_uri")
        if attachment_uri is not None and content is not None:
            return appendAttachmentList(content, [attachment_uri])
        elif attachment_uri is not None and content is None:
            return appendAttachmentList("", [attachment_uri])
        return content

    def get_message_direction(self):  
        if self.result_set.getInt("status") == self._INCOMING_MESSAGE_TYPE:
            return self.INCOMING
        return self.OUTGOING

    def get_phone_number_from(self):
        if self.get_message_direction() == self.INCOMING:
            group = self.result_set.getString("members")
            if group is None:
                return Account.Address(self.result_set.getString("from_mid"),
                            self.result_set.getString("name"))
            return Account.Address(self.result_set.getString("from_mid"),
                            self.result_set.getString("name"))
        return super(LineMessagesParser, self).get_phone_number_from()

    def get_phone_number_to(self):
        if self.get_message_direction() == self.OUTGOING:
            group = self.result_set.getString("members")
            if group is None:
                return Account.Address(self.result_set.getString("id"),
                            self.result_set.getString("name"))
            return Account.Address(group, self.result_set.getString("name"))
        return super(LineMessagesParser, self).get_phone_number_to()

    def get_thread_id(self):
        members = self.result_set.getString("members")
        if members is not None:
            return self.result_set.getString("id")
        return super(LineMessagesParser, self).get_thread_id()
