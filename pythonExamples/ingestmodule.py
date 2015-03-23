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
from javax.swing import JCheckBox
from javax.swing import BoxLayout
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import Services
from org.sleuthkit.autopsy.ingest import DataSourceIngestModule
from org.sleuthkit.autopsy.ingest import FileIngestModule
from org.sleuthkit.autopsy.ingest import IngestMessage
from org.sleuthkit.autopsy.ingest import IngestModule
from org.sleuthkit.autopsy.ingest import IngestModuleFactoryAdapter
from org.sleuthkit.autopsy.ingest import IngestModuleIngestJobSettings
from org.sleuthkit.autopsy.ingest import IngestModuleIngestJobSettingsPanel
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.ingest import IngestModuleGlobalSettingsPanel
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import ReadContentInputStream
from org.sleuthkit.autopsy.coreutils import Logger
from java.lang import IllegalArgumentException

# Sample factory that defines basic functionality and features of the module
# It implements IngestModuleFactoryAdapter which is a no-op implementation of
# IngestModuleFactory.
class SampleJythonIngestModuleFactory(IngestModuleFactoryAdapter):
    def __init__(self):
        self.settings = None

    def getModuleDisplayName(self):
        return "Sample Jython(GUI) ingest module"

    def getModuleDescription(self):
        return "Sample Jython Ingest Module with GUI example code"

    def getModuleVersionNumber(self):
        return "1.0"

    def getDefaultIngestJobSettings(self):
        return SampleIngestModuleSettings()

    def hasIngestJobSettingsPanel(self):
        return True

    def getIngestJobSettingsPanel(self, settings):
        if not isinstance(settings, SampleIngestModuleSettings):
            raise IllegalArgumentException("Expected settings argument to be instanceof SampleIngestModuleSettings")
        self.settings = settings
        return SampleIngestModuleSettingsPanel(self.settings)

    # Return true if module wants to get passed in a data source
    def isDataSourceIngestModuleFactory(self):
        return True

    # can return null if isDataSourceIngestModuleFactory returns false
    def createDataSourceIngestModule(self, ingestOptions):
        return SampleJythonDataSourceIngestModule(self.settings)

    # Return true if module wants to get called for each file

    def isFileIngestModuleFactory(self):
        return True

    # can return null if isFileIngestModuleFactory returns false
    def createFileIngestModule(self, ingestOptions):
        return SampleJythonFileIngestModule(self.settings)

    def hasGlobalSettingsPanel(self):
        return True

    def getGlobalSettingsPanel(self):
        globalSettingsPanel = SampleIngestModuleGlobalSettingsPanel();
        return globalSettingsPanel


class SampleIngestModuleGlobalSettingsPanel(IngestModuleGlobalSettingsPanel):
    def __init__(self):
        self.setLayout(BoxLayout(self, BoxLayout.Y_AXIS))
        checkbox = JCheckBox("Flag inside the Global Settings Panel")
        self.add(checkbox)


class SampleJythonDataSourceIngestModule(DataSourceIngestModule):
    '''
        Data Source-level ingest module.  One gets created per data source. 
        Queries for various files. If you don't need a data source-level module,
        delete this class.
    '''

    def __init__(self, settings):
        self.local_settings = settings
        self.context = None

    def startUp(self, context):
        # Used to verify if the GUI checkbox event been recorded or not.
        logger = Logger.getLogger("SampleJythonFileIngestModule")
        if self.local_settings.getFlag():
            logger.info("flag is set")
        else:
            logger.info("flag is not set")

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
        minTime = currentTime - (14 * 24 * 60 * 60)  # Go back two weeks.
        otherFiles = sleuthkitCase.findAllFilesWhere("crtime > %d" % minTime)
        for otherFile in otherFiles:
            fileCount += 1
        progressBar.progress(1);

        if self.context.isJobCancelled():
            return IngestModule.ProcessResult.OK;

        # Post a message to the ingest messages in box.
        message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                                              "Sample Jython Data Source Ingest Module", "Found %d files" % fileCount)
        IngestServices.getInstance().postMessage(message)

        return IngestModule.ProcessResult.OK;


class SampleJythonFileIngestModule(FileIngestModule):
    '''
        File-level ingest module.  One gets created per thread. Looks at the 
        attributes of the passed in file. if you don't need a file-level module,
        delete this class.
    '''

    def __init__(self, settings):
        self.local_settings = settings

    def startUp(self, context):
        # Used to verify if the GUI checkbox event been recorded or not.
        logger = Logger.getLogger("SampleJythonFileIngestModule")
        if self.local_settings.getFlag():
            logger.info("flag is set")
        else:
            logger.info("flag is not set")
        pass

    def process(self, file):
        # If the file has a txt extension, post an artifact to the blackboard.
        if file.getName().find("test") != -1:
            art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT)
            att = BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(),
                                      "Sample Jython File Ingest Module", "Text Files")
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
            message = IngestMessage.createMessage(IngestMessage.MessageType.DATA, "Sample Jython File IngestModule",
                                                  msgText)
            ingestServices = IngestServices.getInstance().postMessage(message)

        return IngestModule.ProcessResult.OK

    def shutDown(self):
        pass


class SampleIngestModuleSettings(IngestModuleIngestJobSettings):
    serialVersionUID = 1L

    def __init__(self):
        self.flag = False

    def getVersionNumber(self):
        return serialVersionUID

    def getFlag(self):
        return self.flag

    def setFlag(self, flag):
        self.flag = flag


class SampleIngestModuleSettingsPanel(IngestModuleIngestJobSettingsPanel):
    # self.settings instance variable not used. Rather, self.local_settings is used.
    # https://wiki.python.org/jython/UserGuide#javabean-properties
    # Jython Introspector generates a property - 'settings' on the basis
    # of getSettings() defined in this class. Since only getter function
    # is present, it creates a read-only 'settings' property. This auto-
    # generated read-only property overshadows the instance-variable -
    # 'settings'

    def checkBoxEvent(self, event):
        if self.checkbox.isSelected():
            self.local_settings.setFlag(True)
        else:
            self.local_settings.setFlag(False)

    def initComponents(self):
        self.setLayout(BoxLayout(self, BoxLayout.Y_AXIS))
        self.checkbox = JCheckBox("Flag", actionPerformed=self.checkBoxEvent)
        self.add(self.checkbox)

    def customizeComponents(self):
        self.checkbox.setSelected(self.local_settings.getFlag())

    def __init__(self, settings):
        self.local_settings = settings
        self.initComponents()
        self.customizeComponents()

    def getSettings(self):
        return self.local_settings