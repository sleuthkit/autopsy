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
from org.sleuthkit.autopsy.coreutils import AppDBParserHelper
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
        self._DEFAULT_DIRECTION = AppDBParserHelper.CommunicationDirection.UNKNOWN
        self._DEFAULT_ADDRESS = Account.Address("","")
        self._DEFAULT_CALL_TYPE = AppDBParserHelper.CallMediaType.UNKNOWN

        self.INCOMING_CALL = AppDBParserHelper.CommunicationDirection.INCOMING
        self.OUTGOING_CALL = AppDBParserHelper.CommunicationDirection.OUTGOING
        self.AUDIO_CALL = AppDBParserHelper.CallMediaType.AUDIO
        self.VIDEO_CALL = AppDBParserHelper.CallMediaType.VIDEO

    def get_call_direction(self):
        return self._DEFAULT_DIRECTION 

    def get_phone_number_from(self):
        return self._DEFAULT_ADDRESS 

    def get_phone_number_to(self):
        return self._DEFAULT_ADDRESS 

    def get_call_start_date_time(self):
        return self._DEFAULT_LONG 

    def get_call_end_date_time(self):
        return self._DEFAULT_LONG 
    
    def get_call_type(self):
        return self._DEFAULT_CALL_TYPE
