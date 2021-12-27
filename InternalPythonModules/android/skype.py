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
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection
from org.sleuthkit.datamodel.blackboardutils.attributes import MessageAttachments
from org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments import FileAttachment
from TskMessagesParser import TskMessagesParser
from TskContactsParser import TskContactsParser
from TskCallLogsParser import TskCallLogsParser

import traceback
import general

class SkypeAnalyzer(general.AndroidComponentAnalyzer):
    """
        Parses the Skype App databases for TSK contacts, message 
        and calllog artifacts.

        About version 8.15.0.428 (9/17/2019) Skype database:
            - There are 4 tables this parser uses:
                1) person - this table appears to hold all contacts known to the user.
                2) user - this table holds information about the user. 
                3) particiapnt - Yes, that is not a typo. This table maps group chat
                                 ids to skype ids (1 to many).
                4) chatItem - This table contains all messages. It maps the group id or
                              skype id (for 1 to 1 communication) to the message content
                              and metadata. Either the group id or skype id is stored in
                              a column named 'conversation_link'.

        More info and implementation details:
            - The person table does not include groups. To get 
              all 1 to 1 communications, we could simply join the person and chatItem tables. 
              This would mean we'd need to do a second pass to get all the group information
              as they would be excluded in the join. Since the chatItem table stores both the
              group id or skype_id in one column, an implementation decision was made to union 
              the person and particiapnt table together so that all rows are matched in one join 
              with chatItem. This result is consistently labeled contact_book_w_groups in the 
              following queries.
    """
   
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._SKYPE_PACKAGE_NAME = "com.skype.raider"
        self._PARSER_NAME = "Skype Parser"
        self._VERSION = "8.15.0.428"
    
    def get_user_account(self, skype_db):
        account_query_result = skype_db.runQuery(
            """
               SELECT entry_id,
                      CASE
                        WHEN Ifnull(first_name, "") == "" AND Ifnull(last_name, "") == "" THEN entry_id
                        WHEN first_name is NULL THEN replace(last_name, ",", "")
                        WHEN last_name is NULL THEN replace(first_name, ",", "")
                        ELSE replace(first_name, ",", "") || " " || replace(last_name, ",", "")
                      END AS name
               FROM user
            """
        )

        if account_query_result is not None and account_query_result.next():
            return account_query_result.getString("entry_id")
        return None

    def analyze(self, dataSource, fileManager, context):
        #Skype databases are of the form: live:XYZ.db, where
        #XYZ is the skype id of the user. The following search
        #does a generic substring match for 'live' in the skype
        #package.
        skype_dbs = AppSQLiteDB.findAppDatabases(dataSource, 
                        "live:", False, self._SKYPE_PACKAGE_NAME)
        try:
            for skype_db in skype_dbs:
                #Attempt to get the user account id from the database
                user_account_instance = None
                try:
                    user_account_instance = self.get_user_account(skype_db) 
                except SQLException as ex:
                    self._logger.log(Level.WARNING, 
                            "Error querying for the user account in the Skype db.", ex)
                    self._logger.log(Level.WARNING, traceback.format_exc())

                current_case = Case.getCurrentCaseThrows()

                if user_account_instance is None:
                    helper = CommunicationArtifactsHelper(
                                current_case.getSleuthkitCase(), self._PARSER_NAME, 
                                skype_db.getDBFile(), Account.Type.SKYPE, context.getJobId()
                             ) 
                else:
                    helper = CommunicationArtifactsHelper(
                                current_case.getSleuthkitCase(), self._PARSER_NAME,
                                skype_db.getDBFile(), Account.Type.SKYPE,
                                Account.Type.SKYPE, user_account_instance, context.getJobId()
                             )
                self.parse_contacts(skype_db, helper)
                self.parse_calllogs(skype_db, helper)
                self.parse_messages(skype_db, helper, current_case)
        except NoCurrentCaseException as ex:
            self._logger.log(Level.WARNING, "No case currently open.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())

        for skype_db in skype_dbs:
            skype_db.close()

    def parse_contacts(self, skype_db, helper):
        #Query for contacts and iterate row by row adding
        #each contact artifact
        try:
            contacts_parser = SkypeContactsParser(skype_db, self._PARSER_NAME)
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
            #Error parsing Skype db
            self._logger.log(Level.WARNING, 
                    "Error parsing contact database for call logs artifacts.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            #Severe error trying to add to case database.. case is not complete.
            #These exceptions are thrown by the CommunicationArtifactsHelper.
            self._logger.log(Level.SEVERE, 
                    "Failed to add contact artifacts to the case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            #Failed to post notification to blackboard
            self._logger.log(Level.WARNING, 
                    "Failed to post contact artifact to the blackboard", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())

    def parse_calllogs(self, skype_db, helper):
        #Query for call logs and iterate row by row adding
        #each call log artifact
        try:
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
        except SQLException as ex:
            #Error parsing Skype db
            self._logger.log(Level.WARNING, 
                    "Error parsing Skype database for call logs artifacts.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            #Severe error trying to add to case database.. case is not complete.
            #These exceptions are thrown by the CommunicationArtifactsHelper.
            self._logger.log(Level.SEVERE, 
                    "Failed to add call log artifacts to the case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            #Failed to post notification to blackboard
            self._logger.log(Level.WARNING, 
                    "Failed to post call log artifact to the blackboard", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())

    def parse_messages(self, skype_db, helper, current_case):
        #Query for messages and iterate row by row adding
        #each message artifact
        try:
            messages_parser = SkypeMessagesParser(skype_db)
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
                
                if (messages_parser.get_file_attachment() is not None):
                    file_attachments = ArrayList()
                    file_attachments.add(FileAttachment(current_case.getSleuthkitCase(), skype_db.getDBFile().getDataSource(), messages_parser.get_file_attachment()))
                    message_attachments = MessageAttachments(file_attachments, [])
                    helper.addAttachments(message_artifact, message_attachments)

            messages_parser.close()
        except SQLException as ex:
            #Error parsing Skype db
            self._logger.log(Level.WARNING, 
                    "Error parsing Skype database for message artifacts.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            #Severe error trying to add to case database.. case is not complete.
            #These exceptions are thrown by the CommunicationArtifactsHelper.
            self._logger.log(Level.SEVERE, 
                    "Failed to add message artifacts to the case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            #Failed to post notification to blackboard
            self._logger.log(Level.WARNING, 
                    "Failed to post message artifact to the blackboard", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())

class SkypeCallLogsParser(TskCallLogsParser):
    """
        Extracts TSK_CALLLOG information from the Skype database.
        TSK_CALLLOG fields that are not in the Skype database are given
        a default value inherited from the super class.
    """

    def __init__(self, calllog_db):
        """
            Implementation details:
                - message_type w/ value 3 appeared to be the call type, regardless
                  of if it was audio or video.
            
        """
        super(SkypeCallLogsParser, self).__init__(calllog_db.runQuery(
                 """
                    SELECT contact_book_w_groups.conversation_id, 
                           contact_book_w_groups.participant_ids, 
                           messages.time, 
                           messages.duration, 
                           messages.is_sender_me, 
                           messages.person_id AS sender_id 
                    FROM   (SELECT conversation_id, 
                                   Group_concat(person_id) AS participant_ids 
                            FROM   particiapnt 
                            GROUP  BY conversation_id 
                            UNION 
                            SELECT entry_id AS conversation_id, 
                                   NULL 
                            FROM   person) AS contact_book_w_groups 
                           join chatitem AS messages 
                             ON messages.conversation_link = contact_book_w_groups.conversation_id 
                    WHERE  message_type == 3
                 """
             )    
        )
        self._INCOMING_CALL_TYPE = 0
        self._OUTGOING_CALL_TYPE = 1
        

    def get_phone_number_from(self):
        if self.get_call_direction() == self.INCOMING_CALL:
            return self.result_set.getString("sender_id")

    def get_phone_number_to(self):
        if self.get_call_direction() == self.OUTGOING_CALL:
            group_ids = self.result_set.getString("participant_ids")

            if group_ids is not None:
                group_ids = group_ids.split(",")
                return group_ids 

            return self.result_set.getString("conversation_id")      

        return super(SkypeCallLogsParser, self).get_phone_number_to()

    def get_call_direction(self):
        direction = self.result_set.getInt("is_sender_me")
        if direction == self._INCOMING_CALL_TYPE:
            return self.INCOMING_CALL
        if direction == self._OUTGOING_CALL_TYPE:
            return self.OUTGOING_CALL
        return super(SkypeCallLogsParser, self).get_call_direction()

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

    def __init__(self, contact_db, analyzer):
        super(SkypeContactsParser, self).__init__(contact_db.runQuery(
                 """
                    SELECT entry_id, 
                           CASE
                             WHEN Ifnull(first_name, "") == "" AND Ifnull(last_name, "") == "" THEN entry_id
                             WHEN first_name is NULL THEN replace(last_name, ",", "")
                             WHEN last_name is NULL THEN replace(first_name, ",", "")
                             ELSE replace(first_name, ",", "") || " " || replace(last_name, ",", "")
                           END AS name
                    FROM   person 
                 """                                                         
              )
        )
        self._PARENT_ANALYZER = analyzer
    
    def get_contact_name(self):
        return self.result_set.getString("name")
    
    def get_other_attributes(self):
        return [BlackboardAttribute(
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ID, 
                    self._PARENT_ANALYZER, 
                    self.result_set.getString("entry_id"))]


class SkypeMessagesParser(TskMessagesParser):
    """
        Extract TSK_MESSAGE information from the Skype database.
        TSK_CONTACT fields that are not in the Skype database are given
        a default value inherited from the super class. 
    """

    def __init__(self, message_db):
        """
            This query is very similar to the call logs query, the only difference is
            it grabs more columns in the SELECT and excludes message_types which have
            the call type value (3).
        """
        super(SkypeMessagesParser, self).__init__(message_db.runQuery(
                 """
		    SELECT contact_book_w_groups.conversation_id,
                           contact_book_w_groups.participant_ids,
                           messages.time,
                           messages.content,
                           messages.device_gallery_path,
                           messages.is_sender_me,
                           messages.person_id as sender_id
                    FROM   (SELECT conversation_id,
                                   Group_concat(person_id) AS participant_ids
                            FROM   particiapnt
                            GROUP  BY conversation_id
                            UNION
                            SELECT entry_id as conversation_id,
                                   NULL
                            FROM   person) AS contact_book_w_groups
                           JOIN chatitem AS messages
                             ON messages.conversation_link = contact_book_w_groups.conversation_id
                    WHERE message_type != 3
                 """
             )
        )
        self._SKYPE_MESSAGE_TYPE = "Skype Message"
        self._OUTGOING_MESSAGE_TYPE = 1
        self._INCOMING_MESSAGE_TYPE = 0

    def get_message_type(self):
        return self._SKYPE_MESSAGE_TYPE

    def get_phone_number_from(self):
        if self.get_message_direction() == self.INCOMING:
            return self.result_set.getString("sender_id") 
        return super(SkypeMessagesParser, self).get_phone_number_from()

    def get_message_direction(self):  
        direction = self.result_set.getInt("is_sender_me")
        if direction == self._OUTGOING_MESSAGE_TYPE:
            return self.OUTGOING
        if direction == self._INCOMING_MESSAGE_TYPE:
            return self.INCOMING
        return super(SkypeMessagesParser, self).get_message_direction()
    
    def get_phone_number_to(self):
        if self.get_message_direction() == self.OUTGOING:
            group_ids = self.result_set.getString("participant_ids")

            if group_ids is not None:
                group_ids = group_ids.split(",") 
                return group_ids  
            
            return self.result_set.getString("conversation_id")

        return super(SkypeMessagesParser, self).get_phone_number_to()

    def get_message_date_time(self):
        date = self.result_set.getLong("time")
        return date / 1000

    def get_message_text(self):
        content = self.result_set.getString("content")

        if content is not None:
            return content

        return super(SkypeMessagesParser, self).get_message_text()

    def get_thread_id(self):
        group_ids = self.result_set.getString("participant_ids")
        if group_ids is not None:
            return self.result_set.getString("conversation_id")
        return super(SkypeMessagesParser, self).get_thread_id()


    def get_file_attachment(self):
        if (self.result_set.getString("device_gallery_path") is None):
            return None
        else:
            return self.result_set.getString("device_gallery_path")
   
