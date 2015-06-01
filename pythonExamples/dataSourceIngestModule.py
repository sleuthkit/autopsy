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

# Simple data source-level ingest module for Autopsy.
# Search for TODO for the things that you need to change
# See http://sleuthkit.org/autopsy/docs/api-docs/3.1/index.html for documentation

import jarray
import inspect
from java.lang import System
from java.util.logging import Level
from org.sleuthkit.datamodel import SleuthkitCase
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import ReadContentInputStream
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.autopsy.ingest import IngestModule
from org.sleuthkit.autopsy.ingest.IngestModule import IngestModuleException
from org.sleuthkit.autopsy.ingest import DataSourceIngestModule
from org.sleuthkit.autopsy.ingest import FileIngestModule
from org.sleuthkit.autopsy.ingest import IngestModuleFactoryAdapter
from org.sleuthkit.autopsy.ingest import IngestMessage
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import Services
from org.sleuthkit.autopsy.casemodule.services import FileManager


# Factory that defines the name and details of the module and allows Autopsy
# to create instances of the modules that will do the analysis.
# TODO: Rename this to something more specific. Search and replace for it because it is used a few times
class SampleJythonDataSourceIngestModuleFactory(IngestModuleFactoryAdapter):

    # TODO: give it a unique name.  Will be shown in module list, logs, etc.
    moduleName = "Sample Data Source Module" 
	
    def getModuleDisplayName(self):
        return self.moduleName
    
    # TODO: Give it a description
    def getModuleDescription(self):
        return "Sample module that does X, Y, and Z."

    def getModuleVersionNumber(self):
        return "1.0"

    def isDataSourceIngestModuleFactory(self):
        return True

    def createDataSourceIngestModule(self, ingestOptions):
        # TODO: Change the class name to the name you'll make below
        return SampleJythonDataSourceIngestModule()


# Data Source-level ingest module.  One gets created per data source.
# TODO: Rename this to something more specific. Could just remove "Factory" from above name.
class SampleJythonDataSourceIngestModule(DataSourceIngestModule):

    logger = Logger.getLogger(SampleJythonDataSourceIngestModuleFactory.moduleName)

    def python_logger(self, level, msg):
        self.logger.logp(level, self.__class__.__name__, inspect.stack()[1][3], msg)

    def __init__(self):
        self.context = None

    # Where any setup and configuration is done
    # TODO: Add any setup code that you need here.
    def startUp(self, context):
        self.context = context
        # Throw an IngestModule.IngestModuleException exception if there was a problem setting up
		# raise IngestModuleException(IngestModule(), "Oh No!")
        
    # Where the analysis is done.
    # TODO: Add your analysis code in here.
    def process(self, dataSource, progressBar):
        if self.context.isJobCancelled():
            return IngestModule.ProcessResult.OK

        # we don't know how much work there is yet
        progressBar.switchToIndeterminate()

        autopsyCase = Case.getCurrentCase()
        sleuthkitCase = autopsyCase.getSleuthkitCase()
        services = Services(sleuthkitCase)
        fileManager = services.getFileManager()

        # For our example, we will use FileManager to get all 
        # files with the word "test"
        # in the name and then count and read them
        files = fileManager.findFiles(dataSource, "%test%")

        numFiles = len(files)
        self.python_logger(Level.INFO, "found " + str(numFiles) + " files")
        progressBar.switchToDeterminate(numFiles)
        fileCount = 0;
        for file in files:

            # Check if the user pressed cancel while we were busy
            if self.context.isJobCancelled():
                return IngestModule.ProcessResult.OK

            self.python_logger(Level.INFO, "Processing file: " + file.getName())
            fileCount += 1

            # Make an artifact on the blackboard.  TSK_INTERESTING_FILE_HIT is a generic type of
            # artfiact.  Refer to the developer docs for other examples.
            art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT)
            att = BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), SampleJythonDataSourceIngestModuleFactory.moduleName, "Test file")
            art.addAttribute(att)

            
            # To further the example, this code will read the contents of the file and count the number of bytes
            inputStream = ReadContentInputStream(file)
            buffer = jarray.zeros(1024, "b")
            totLen = 0
            readLen = inputStream.read(buffer)
            while (readLen != -1):
                totLen = totLen + readLen
                readLen = inputStream.read(buffer)


            # Update the progress bar
            progressBar.progress(fileCount)


        #Post a message to the ingest messages in box.
        message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
            "Sample Jython Data Source Ingest Module", "Found %d files" % fileCount)
        IngestServices.getInstance().postMessage(message)

        return IngestModule.ProcessResult.OK;
