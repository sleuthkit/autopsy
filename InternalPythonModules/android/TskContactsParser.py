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

class TskContactsParser(ResultSetIterator):
    """
        Generic TSK_CONTACT artifact template. Each of these methods
        will contain the extraction and transformation logic for 
        converting raw database records to the expected TSK_CONTACT
        format.
    """

    def __init__(self, result_set):
        super(TskContactsParser, self).__init__(result_set)
        self._DEFAULT_VALUE = ""

    def get_contact_name(self):
        return self._DEFAULT_VALUE

    def get_phone(self):
        return self._DEFAULT_VALUE

    def get_home_phone(self):
        return self._DEFAULT_VALUE

    def get_mobile_phone(self):
        return self._DEFAULT_VALUE
    
    def get_email(self):
        return self._DEFAULT_VALUE
    
    def get_other_attributes(self):
        return None
