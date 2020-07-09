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
from ResultSetIterator import ResultSetIterator
from org.sleuthkit.datamodel import Account
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection
 
class TskMessagesParser(ResultSetIterator):
    """
        Generic TSK_MESSAGE artifact template. Each of these methods
        will contain the extraction and transformation logic for 
        converting raw database records to the expected TSK_MESSAGE
        format.

        An easy example of such a transformation would be converting
        message date time from milliseconds to seconds. 
    """

    def __init__(self, result_set):
        super(TskMessagesParser, self).__init__(result_set)
        self._DEFAULT_TEXT = ""
        self._DEFAULT_LONG = -1L
        self._DEFAULT_MSG_READ_STATUS = MessageReadStatus.UNKNOWN
        self._DEFAULT_COMMUNICATION_DIRECTION = CommunicationDirection.UNKNOWN

        self.INCOMING = CommunicationDirection.INCOMING
        self.OUTGOING = CommunicationDirection.OUTGOING
        self.READ = MessageReadStatus.READ
        self.UNREAD = MessageReadStatus.UNREAD

    def get_message_type(self):
        return self._DEFAULT_TEXT

    def get_message_direction(self):  
        return self._DEFAULT_COMMUNICATION_DIRECTION

    def get_phone_number_from(self):
        return self._DEFAULT_TEXT

    def get_phone_number_to(self):
        return self._DEFAULT_TEXT

    def get_message_date_time(self):
        return self._DEFAULT_LONG

    def get_message_read_status(self):
        return self._DEFAULT_MSG_READ_STATUS

    def get_message_subject(self):
        return self._DEFAULT_TEXT

    def get_message_text(self):
        return self._DEFAULT_TEXT

    def get_thread_id(self):
        return self._DEFAULT_TEXT
