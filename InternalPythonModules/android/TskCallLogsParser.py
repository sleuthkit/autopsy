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
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CallMediaType
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection
from org.sleuthkit.datamodel import Account

class TskCallLogsParser(ResultSetIterator):
    """
        Generic TSK_CALLLOG artifact template. Each of these methods
        will contain the extraction and transformation logic for 
        converting raw database records to the expected TSK_CALLLOG
        format.

        A simple example of data transformation would be computing 
        the end time of a call when the database only supplies the start
        time and duration.
    """

    def __init__(self, result_set):
        super(TskCallLogsParser, self).__init__(result_set)
        self._DEFAULT_STRING = ""
        self._DEFAULT_DIRECTION = CommunicationDirection.UNKNOWN
        self._DEFAULT_CALL_TYPE = CallMediaType.UNKNOWN
        self._DEFAULT_LONG = -1L

        self.INCOMING_CALL = CommunicationDirection.INCOMING
        self.OUTGOING_CALL = CommunicationDirection.OUTGOING
        self.AUDIO_CALL = CallMediaType.AUDIO
        self.VIDEO_CALL = CallMediaType.VIDEO

    def get_call_direction(self):
        return self._DEFAULT_DIRECTION 

    def get_phone_number_from(self):
        return self._DEFAULT_STRING

    def get_phone_number_to(self):
        return self._DEFAULT_STRING

    def get_call_start_date_time(self):
        return self._DEFAULT_LONG 

    def get_call_end_date_time(self):
        return self._DEFAULT_LONG 
    
    def get_call_type(self):
        return self._DEFAULT_CALL_TYPE
