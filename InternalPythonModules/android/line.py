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
from org.sleuthkit.autopsy.coreutils import AppSQLiteDB as SQLiteUtil
from org.sleuthkit.autopsy.coreutils import AppDBParserHelper as BlackboardUtil
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

    def analyze(self, dataSource, fileManager, context):
        try:
            contact_and_message_dbs = SQLiteUtil.findAppDatabases(dataSource, "naver_line", self._LINE_PACKAGE_NAME)
            calllog_dbs = SQLiteUtil.findAppDatabases(dataSource, "call_history", self._LINE_PACKAGE_NAME)

            for contact_and_message_db in contact_and_message_dbs:
                blackboard_util = BlackboardUtil(self._PARSER_NAME, contact_and_message_db.getDBFile(), Account.Type.LINE) 

                contacts_parser = LineContactsParser(contact_and_message_db)
                while contacts_parser.next():
                    blackboard_util.addContact( 
                        contacts_parser.get_account_name(), 
                        contacts_parser.get_contact_name(), 
                        contacts_parser.get_phone(),
                        contacts_parser.get_home_phone(),
                        contacts_parser.get_mobile_phone(),
                        contacts_parser.get_email()
                    )
                contacts_parser.close()
                """
                messages_parser = LineMessagesParser(line_db)
                while messages_parser.next():
                    blackboard_util.addMessage(
                        messages_parser.get_account_id(),
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
                contact_and_message_db.close()

            for calllog_db in calllog_dbs:
                blackboard_util = BlackboardUtil(self._PARSER_NAME, calllog_db.getDBFile(), Account.Type.LINE)

                calllog_parser = LineCallLogsParser(calllog_db)
                while calllog_parser.next():
                    blackboard_util.addCalllog(
                        calllog_parser.get_account_name(),
                        calllog_parser.get_call_direction(),
                        calllog_parser.get_phone_number_from(),
                        calllog_parser.get_phone_number_to(),
                        calllog_parser.get_call_start_date_time(),
                        calllog_parser.get_call_end_date_time(),
                        calllog_parser.get_contact_name()
                    )

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
                     SELECT C.caller_mid            AS mid, 
                            substr(C.call_type, -1) AS direction, 
                            C.start_time            AS start_time, 
                            C.end_time              AS end_time 
                     FROM   call_history AS C 
                 """
             )
        )
        self._OUTGOING_CALL = "O"
        self._INCOMING_CALL = "I"
        self._had_error = False

        def get_call_direction(self):
            direction = self.result_set.getString("direction")
            if direction == self._OUTGOING_CALL:
                return self.OUTGOING_MSG_STRING
            return self.INCOMING_MSG_STRING

        def get_call_start_date_time(self):
            start_time = self.result_set.getString("start_time")
            try:
                return long(start_time) / 1000
            except ValueError as ve:
                self._had_error = True

        def get_call_end_date_time(self):
            end_time = self.result_set.getString("end_time")
            try:
                return long(end_time) / 1000
            except ValueError as ve:
                self._had_error = True

class LineContactsParser(TskContactsParser):
    """
        Parses out TSK_CONTACT information from the Line database.
        TSK_CONTACT fields that are not in the line database are given
        a default value inherited from the super class. 
    """

    def __init__(self, contact_db):
        super(LineContactsParser, self).__init__(contact_db.runQuery(
                 """
                     SELECT name,
                            server_name
                     FROM   contacts
                 """
              )
        )
    def get_account_name(self):
        return self.result_set.getString("server_name")

    def get_contact_name(self):
        return self.result_set.getString("name")

class LineMessagesParser(TskMessagesParser):
    """
        Parse out TSK_MESSAGE information from the Line database.
        TSK_MESSAGE fields that are not in the line database are given
        a default value inherited from the super class.
    """

    def __init__(self, message_db):
        super().__init__(message_db.runQuery(
            """SELECT created_time, content, contacts.server_name AS server_name, read_count
                                              FROM chat_history 
                                                   JOIN contacts 
                                                        ON chat_history.from_mid = contacts.m_id"""
        ))
        self._LINE_MESSAGE_TYPE = "Line Message"
        self._had_error = False

    def get_account_id(self):
        return self.result_set.getString("server_name")

    def get_message_type(self):
        return self.LINE_MESSAGE_TYPE

    def get_phone_number_from(self):
        return self.result_set("server_name")

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
        if not LineContentUtil.is_text_message(content):
            return ""
        return content
