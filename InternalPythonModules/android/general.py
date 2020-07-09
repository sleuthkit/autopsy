"""
Autopsy Forensic Browser

Copyright 2016-2020 Basis Technology Corp.
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

from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel import CommunicationsUtils

MODULE_NAME = "Android Analyzer"

"""
A parent class of the analyzers
"""
class AndroidComponentAnalyzer:
    # The Analyzer should implement this method
    def analyze(self, dataSource, fileManager, context):
        raise NotImplementedError

"""
A utility method to append list of attachments to msg body
"""
def appendAttachmentList(msgBody, attachmentsList):
    body = msgBody
    if attachmentsList:
        body = body + "\n\n------------Attachments------------\n"
        body = body + "\n".join(list(filter(None, attachmentsList)))

    return body

"""
Checks if the given string might be a phone number.
"""
def isValidPhoneNumber(data):
    return CommunicationsUtils.isValidPhoneNumber(data)
   
        

"""
Checks if the given string is a valid email address.
"""
def isValidEmailAddress(data):
    return CommunicationsUtils.isValidEmailAddress(data)

    
