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

class SampleJythonDataSourceIngestModule(DataSourceIngestModule):

    def __init__(self):
        self.context = None

    def startUp(self, context):
        self.context = context

    def process(self, dataSource, progressBar):
		if self.context.isJobCancelled():
			return IngestModule.ProcessResult.OK

		# There are two tasks to do.
		progressBar.switchToDeterminate(2)

		autopsyCase = Case.getCurrentCase()
		sleuthkitCase = autopsyCase.getSleuthkitCase()
		services = Services(sleuthkitCase)
		fileManager = services.getFileManager()

		#Get count of files with .doc extension.
		fileCount = 0;
		docFiles = fileManager.findFiles(dataSource, "%.doc")
		for docFile in docFiles:
			fileCount += 1
		progressBar.progress(1)

		if self.context.isJobCancelled():
			return IngestModule.ProcessResult.OK

		# Get files by creation time.
		currentTime = System.currentTimeMillis() / 1000
		minTime = currentTime - (14 * 24 * 60 * 60) # Go back two weeks.
		otherFiles = sleuthkitCase.findFilesWhere("crtime > %d" % minTime)
		for otherFile in otherFiles:
			fileCount += 1
		progressBar.progress(1);

		if self.context.isJobCancelled():
			return IngestModule.ProcessResult.OK;

		#Post a message to the ingest messages in box.
		# message = IngestMessage.createMessage(IngestMessage.MessageType.DATA, "SampleJythonDataSourceIngestModule", "Found %d files" % fileCount)
		# IngestServices.getInstance().postMessage(message)

		return IngestModule.ProcessResult.OK;

        
class SampleJythonFileIngestModule(FileIngestModule):

    def startUp(self, context):
        pass

    def process(self, file):
		# If the file has a txt extension, post an artifact to the blackboard.
		if file.getName().endswith("txt"):
			art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT)
			att = BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), "SampleJythonFileIngestModule", "Text file")
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
			#msgText = "Size of %s is %d bytes" % ((file.getName(), totLen))
			#message = IngestMessage.createMessage(IngestMessage.MessageType.DATA, "SampleJythonFileIngestModule", msgText)
			#ingestServices = IngestServices.getInstance().postMessage(message)

		return IngestModule.ProcessResult.OK

    def shutDown(self):
        pass

class SampleJythonIngestModuleFactory(IngestModuleFactoryAdapter):

    def getModuleDisplayName(self):
        return "Sample Jython Ingest Module"

    def getModuleDescription(self):
        return "A sample Jython ingest module"

    def getModuleVersionNumber(self):
        return "1.0"

    def isDataSourceIngestModuleFactory(self):
        return True

    def createDataSourceIngestModule(self, ingestOptions):
        return SampleJythonDataSourceIngestModule()

    def isFileIngestModuleFactory(self):
        return True

    def createFileIngestModule(self, ingestOptions):
        return SampleJythonFileIngestModule()

