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

class ViberAnalyzer(general.AndroidComponentAnalyzer):
    """
        Parses the Viber App databases for TSK contacts, message 
        and calllog artifacts.

        The Viber v11.5.0 database structure is as follows:
            - People can take part in N conversation(s). A conversation can have M 
              members and messages are exchanged in a conversation. 
            - Viber has a conversation table, a participant table (the people/members in the above
              analogy) and a messages table.
            - Each row of the participants table maps a person to a conversation_id
            - Each row in the messages table has a from participant id and a conversation id.
    """
   
    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._VIBER_PACKAGE_NAME = "com.viber.voip"
        self._PARSER_NAME = "Viber Parser"
        self._VERSION = "11.5.0"

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
                current_case = Case.getCurrentCaseThrows()
                helper = CommunicationArtifactsHelper(
                        current_case.getSleuthkitCase(), self._PARSER_NAME, 
                        contact_and_calllog_db.getDBFile(), Account.Type.VIBER, context.getJobId()) 
                self.parse_contacts(contact_and_calllog_db, helper, context)
                self.parse_calllogs(contact_and_calllog_db, helper)

            #Extract TSK_MESSAGE information
            for message_db in message_dbs:
                current_case = Case.getCurrentCaseThrows()
                helper = CommunicationArtifactsHelper(
                        current_case.getSleuthkitCase(), self._PARSER_NAME, 
                        message_db.getDBFile(), Account.Type.VIBER, context.getJobId())
                self.parse_messages(message_db, helper, current_case)

        except NoCurrentCaseException as ex:
            self._logger.log(Level.WARNING, "No case currently open.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        
        for message_db in message_dbs:
            message_db.close()
        
        for contact_and_calllog_db in contact_and_calllog_dbs:
            contact_and_calllog_db.close()

    def parse_contacts(self, contacts_db, helper, context):
        try:
            contacts_parser = ViberContactsParser(contacts_db)
            while contacts_parser.next():
                if (not(not contacts_parser.get_phone() or contacts_parser.get_phone().isspace())):
                    helper.addContact( 
                        contacts_parser.get_contact_name(), 
                        contacts_parser.get_phone(),
                        contacts_parser.get_home_phone(),
                        contacts_parser.get_mobile_phone(),
                        contacts_parser.get_email()
                    )
                # Check if contact_name is blank and if it is not create a TSK_CONTACT otherwise ignore as not Contact Info
                elif (not(not contacts_parser.get_contact_name() or contacts_parser.get_contact_name().isspace())):
                    current_case = Case.getCurrentCase().getSleuthkitCase()
                    attributes = ArrayList()
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), self._PARSER_NAME, contacts_parser.get_contact_name()))
                    artifact = contacts_db.getDBFile().newDataArtifact(BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT), attributes)
                    current_case.getBlackboard().postArtifact(artifact, self._PARSER_NAME, context.getJobId())

            contacts_parser.close()
        except SQLException as ex:
            self._logger.log(Level.WARNING, "Error querying the viber database for contacts.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, 
                    "Error adding viber contacts artifact to case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            self._logger.log(Level.WARNING, 
                    "Error posting viber contacts artifact to the blackboard.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())

    def parse_calllogs(self, calllogs_db, helper):
        try:
            calllog_parser = ViberCallLogsParser(calllogs_db)
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
            self._logger.log(Level.WARNING, "Error querying the viber database for calllogs.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, 
                    "Error adding viber calllogs artifact to case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            self._logger.log(Level.WARNING, 
                    "Error posting viber calllogs artifact to the blackboard.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())


    def parse_messages(self, messages_db, helper, current_case):
        try:
            messages_parser = ViberMessagesParser(messages_db)
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
                    file_attachments.add(FileAttachment(current_case.getSleuthkitCase(), messages_db.getDBFile().getDataSource(), messages_parser.get_file_attachment()))
                    message_attachments = MessageAttachments(file_attachments, [])
                    helper.addAttachments(message_artifact, message_attachments)
            messages_parser.close()
        except SQLException as ex:
            self._logger.log(Level.WARNING, "Error querying the viber database for messages.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, 
                    "Error adding viber messages artifact to case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        except BlackboardException as ex:
            self._logger.log(Level.WARNING, 
                    "Error posting viber messages artifact to the blackboard.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())
        
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
                             C.date             AS start_time, 
                             C.viber_call_type  AS call_type
                      FROM   calls AS C 
                 """
             )    
        )

        self._OUTGOING_CALL_TYPE = 2
        self._INCOMING_CALL_TYPE = 1
        self._MISSED_CALL_TYPE = 3
        self._AUDIO_CALL_TYPE = 1
        self._VIDEO_CALL_TYPE = 4

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

    def get_call_type(self):
        call_type = self.result_set.getInt("call_type")
        if call_type == self._AUDIO_CALL_TYPE:
            return self.AUDIO_CALL 
        if call_type == self._VIDEO_CALL_TYPE:
            return self.VIDEO_CALL
        return super(ViberCallLogsParser, self).get_call_type()

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
                             coalesce(D.data2, D.data1, D.data3) AS number 
                      FROM   phonebookcontact AS C 
                             JOIN phonebookdata AS D 
                               ON C._id = D.contact_id
                 """                                                         
             )
        )
    
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
        """
            The query below does the following:
                - The first two inner joins on participants and participants_info build
                  the 1 to many (M) mappings between the sender and the recipients for each 
                  conversation_id. If a and b do private messaging, then 2 rows in the result 
                  will be a -> b and b -> a.
                  If a, b, c, d are in a group, then 4 rows containing a -> b,c,d. b -> a,c,d. etc.
                  Participants_info is needed to get phone numbers. 
                - The result of the above step is a look up table for each message. Joining this result
                  onto the messages table lets us know which participant a message originated from and 
                  everyone else that received it.
        """
        super(ViberMessagesParser, self).__init__(message_db.runQuery(
                 """
                     SELECT convo_participants.from_number AS from_number, 
                            convo_participants.recipients  AS recipients, 
                            M.conversation_id              AS thread_id, 
                            M.body                         AS msg_content, 
                            M.send_type                    AS direction, 
                            M.msg_date                     AS msg_date, 
                            M.unread                       AS read_status,
                            M.extra_uri                    AS file_attachment                            
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
        return self.result_set.getString("from_number") 

    def get_message_direction(self):  
        direction = self.result_set.getInt("direction")
        if direction == self._INCOMING_MESSAGE_TYPE:
            return self.INCOMING
        return self.OUTGOING
    
    def get_phone_number_to(self):
        return self.result_set.getString("recipients").split(",")

    def get_message_date_time(self):
        #transform from ms to seconds
        return self.result_set.getLong("msg_date") / 1000 

    def get_message_read_status(self):
        if self.get_message_direction() == self.INCOMING: 
            if self.result_set.getInt("read_status") == 0:
                return self.READ
            else:
                return self.UNREAD
        return super(ViberMessagesParser, self).get_message_read_status()

    def get_message_text(self):
        return self.result_set.getString("msg_content") 

    def get_thread_id(self):
        return str(self.result_set.getInt("thread_id")) 
        
    def get_file_attachment(self):
        if (self.result_set.getString("file_attachment") is None):
            return None
        elif ("content:" in self.result_set.getString("file_attachment")):
            return self.result_set.getString("msg_content").replace("file://", "")
        else:
            return self.result_set.getString("file_attachment").replace("file://", "")
