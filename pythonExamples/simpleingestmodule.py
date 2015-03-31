# Sample module in the public domain. Feel free to use this as a template
# for your modules (and you can remove this header and take complete credit
# and liability)
#
# Contact: Brian Carrier [carrier <at> sleuthkit [dot] org]
#
# This is free and unencumbered software released into the public domain.
#
# Anyone is free to copy, modify, publish, use, compile, sell, or
# distribute this software, either in source code form or as a compiled
# binary, for any purpose, commercial or non-commercial, and by any
# means.
#
# In jurisdictions that recognize copyright laws, the author or authors
# of this software dedicate any and all copyright interest in the
# software to the public domain. We make this dedication for the benefit
# of the public at large and to the detriment of our heirs and
# successors. We intend this dedication to be an overt act of
# relinquishment in perpetuity of all present and future rights to this
# software under copyright law.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
# IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
# OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
# ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
# OTHER DEALINGS IN THE SOFTWARE.

import jarray
from java.lang import System
from org.sleuthkit.datamodel import SleuthkitCase
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import ReadContentInputStream
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.autopsy.ingest import IngestModule
from org.sleuthkit.autopsy.ingest import DataSourceIngestModule
from org.sleuthkit.autopsy.ingest import FileIngestModule
from org.sleuthkit.autopsy.ingest import IngestModuleFactoryAdapter
from org.sleuthkit.autopsy.ingest import IngestMessage
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import Services
from org.sleuthkit.autopsy.casemodule.services import FileManager

# Sample factory that defines basic functionality and features of the module
class SampleJythonIngestModuleFactory(IngestModuleFactoryAdapter):

    def getModuleDisplayName(self):
        return "Sample Jython ingest module"

    def getModuleDescription(self):
        return "Sample Jython Ingest Module without GUI example code"

    def getModuleVersionNumber(self):
        return "1.0"

    # Return true if module wants to get passed in a data source
    def isDataSourceIngestModuleFactory(self):
        return True

    # can return null if isDataSourceIngestModuleFactory returns false
    def createDataSourceIngestModule(self, ingestOptions):
        return SampleJythonDataSourceIngestModule()

    # Return true if module wants to get called for each file
    def isFileIngestModuleFactory(self):
        return True

    # can return null if isFileIngestModuleFactory returns false
    def createFileIngestModule(self, ingestOptions):
        return SampleJythonFileIngestModule()


# Data Source-level ingest module.  One gets created per data source.
# Queries for various files.
# If you don't need a data source-level module, delete this class.
class SampleJythonDataSourceIngestModule(DataSourceIngestModule):

    def __init__(self):
        self.context = None

    def startUp(self, context):
        self.context = context

    def process(self, dataSource, progressBar):
        if self.context.isJobCancelled():
            return IngestModule.ProcessResult.OK

        # Configure progress bar for 2 tasks
        progressBar.switchToDeterminate(2)

        autopsyCase = Case.getCurrentCase()
        sleuthkitCase = autopsyCase.getSleuthkitCase()
        services = Services(sleuthkitCase)
        fileManager = services.getFileManager()

        # Get count of files with "test" in name.
        fileCount = 0;
        files = fileManager.findFiles(dataSource, "%test%")
        for file in files:
            fileCount += 1
        progressBar.progress(1)

        if self.context.isJobCancelled():
            return IngestModule.ProcessResult.OK

        # Get files by creation time.
        currentTime = System.currentTimeMillis() / 1000
        minTime = currentTime - (14 * 24 * 60 * 60) # Go back two weeks.
        otherFiles = sleuthkitCase.findAllFilesWhere("crtime > %d" % minTime)
        for otherFile in otherFiles:
            fileCount += 1
        progressBar.progress(1);

        if self.context.isJobCancelled():
            return IngestModule.ProcessResult.OK;

        #Post a message to the ingest messages in box.
        message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
            "Sample Jython Data Source Ingest Module", "Found %d files" % fileCount)
        IngestServices.getInstance().postMessage(message)

        return IngestModule.ProcessResult.OK;


# File-level ingest module.  One gets created per thread.
# Looks at the attributes of the passed in file.
# if you don't need a file-level module, delete this class.
class SampleJythonFileIngestModule(FileIngestModule):

    def startUp(self, context):
        pass

    def process(self, file):
        # If the file has a txt extension, post an artifact to the blackboard.
        if file.getName().find("test") != -1:
            art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT)
            att = BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), "Sample Jython File Ingest Module", "Text Files")
            art.addAttribute(att)

            # Read the contents of the file.
            inputStream = ReadContentInputStream(file)
            buffer = jarray.zeros(1024, "b")
            totLen = 0
            len = inputStream.read(buffer)
            while (len != -1):
                    totLen = totLen + len
                    len = inputStream.read(buffer)

            # Send the size of the file to the ingest messages in box.
            msgText = "Size of %s is %d bytes" % ((file.getName(), totLen))
            message = IngestMessage.createMessage(IngestMessage.MessageType.DATA, "Sample Jython File IngestModule", msgText)
            ingestServices = IngestServices.getInstance().postMessage(message)

        return IngestModule.ProcessResult.OK

    def shutDown(self):
        pass