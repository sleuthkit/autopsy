"""
Autopsy Forensic Browser

Copyright 2016 Basis Technology Corp.
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
        body = body + "\n".join(attachmentsList)

    return body
