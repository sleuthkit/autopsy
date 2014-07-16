import jarray
from org.sleuthkit.autopsy.ingest import FileIngestModule
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import ReadContentInputStream
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.autopsy.ingest import IngestModuleFactoryAdapter
 
class SampleJythonFileIngestModule(FileIngestModule):

    def startUp(self, context):
        pass

    def process(self, file):
        # Read the contents of the file.
        inputStream = ReadContentInputStream(file)
        buffer = jarray.zeros(1024, "b")
        totLen = 0
        len = inputStream.read(buffer)
        while (len != -1):
            totLen = totLen + len
            len = inputStream.read(buffer)

        # If the file has a txtr extension, post an artifact to the blackboard.
        if file.getName().endswith("txt"):
            # Make an artifact
            art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT)
            att = BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), "pythonModule", "Text FILE")
            art.addAttribute(att)

class SampleJythonIngestModuleFactory(IngestModuleFactoryAdapter):

    def getModuleDisplayName(self):
        return "Sample Jython Ingest Module"

    def getModuleDescription(self):
        return "A sample Jython ingest module"

    def getModuleVersionNumber(self):
        return "1.0"

    def isFileIngestModuleFactory(self):
        return True

    def createFileIngestModule(self, ingestOptions):
        return SampleJythonFileIngestModule()

