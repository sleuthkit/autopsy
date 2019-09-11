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
        self.INCOMING_CALL = "Incoming"
        self.OUTGOING_CALL = "Outgoing"
        self._DEFAULT_STRING = ""

    def get_account_name(self):
        return self._DEFAULT_STRING 

    def get_call_direction(self):
        return self._DEFAULT_STRING 

    def get_phone_number_from(self):
        return self._DEFAULT_STRING 

    def get_phone_number_to(self):
        return self._DEFAULT_STRING 

    def get_call_start_date_time(self):
        return self._DEFAULT_LONG 

    def get_call_end_date_time(self):
        return self._DEFAULT_LONG 

    def get_contact_name(self):
        return self._DEFAULT_STRING
