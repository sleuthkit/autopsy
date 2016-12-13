import jarray
import inspect
import traceback

from java.util.logging import Level
from org.sleuthkit.autopsy.coreutils import Version
from org.sleuthkit.autopsy.ingest import IngestModuleFactory
from org.sleuthkit.autopsy.ingest import DataSourceIngestModule
from org.sleuthkit.autopsy.ingest import IngestModuleFactoryAdapter
from org.sleuthkit.autopsy.ingest import IngestModuleIngestJobSettings
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import FileManager
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.ingest import DataSourceIngestModuleProgress
from org.sleuthkit.autopsy.ingest import IngestModule
from org.sleuthkit.datamodel import Content
from org.sleuthkit.autopsy.ingest import DataSourceIngestModule
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.autopsy.ingest import IngestMessage

import general
import browserlocation
import cachelocation
import calllog
import contact
import googlemaplocation
import tangomessage
import textmessage
import wwfmessage

class AndroidModuleFactory(IngestModuleFactoryAdapter):

    moduleName = general.MODULE_NAME

    def getModuleDisplayName(self):
        return self.moduleName

    def getModuleDescription(self):
        return "Extracts Android system and third-party app data."

    def getModuleVersionNumber(self):
        return Version.getVersion()

    def isDataSourceIngestModuleFactory(self):
        return True

    def createDataSourceIngestModule(self, ingestOptions):
        return AndroidIngestModule()


class AndroidIngestModule(DataSourceIngestModule):

    _logger = Logger.getLogger(AndroidModuleFactory.moduleName)

    def log(self, level, msg):
        self._logger.logp(level, self.__class__.__name__, inspect.stack()[1][3], msg)

    def __init__(self):
        self.context = None

    def startUp(self, context):
        self.context = context

        # Throw an IngestModule.IngestModuleException exception if there was a problem setting up

    # Where the analysis is done.
    def process(self, dataSource, progressBar):

        errors = []
        fileManager = Case.getCurrentCase().getServices().getFileManager()
        analyzers = [contact.ContactAnalyzer(), calllog.CallLogAnalyzer(), textmessage.TextMessageAnalyzer(), tangomessage.TangoMessageAnalyzer(), wwfmessage.WWFMessageAnalyzer(), googlemaplocation.GoogleMapLocationAnalyzer(), browserlocation.BrowserLocationAnalyzer(), cachelocation.CacheLocationAnalyzer()]
        self.log(Level.INFO, "running " + str(len(analyzers)) + " analyzers")
        progressBar.switchToDeterminate(len(analyzers))

        n = 0
        for analyzer in analyzers:
            if self.context.dataSourceIngestIsCancelled():
                return IngestModule.ProcessResult.OK
            try:
                analyzer.analyze(dataSource, fileManager, self.context)
                n += 1
                progressBar.progress(n)
            except Exception as ex:
                errors.append("Error getting Contacts")
                self.log(Level.SEVERE, traceback.format_exc())
        errorMessage = [] # NOTE: this isn't used?
        errorMessageSubject = "" # NOTE: this isn't used?
        msgLevel = IngestMessage.MessageType.INFO

        if errors:
            msgLevel = IngestMessage.MessageType.ERROR
            errorMessage.append("Errors were encountered")

            errorMessage.append("<ul>") # NOTE: this was missing in the original java code
            for msg in errors:
                errorMessage.extend(["<li>", msg, "</li>\n"])
            errorMessage.append("</ul>\n")

            if len(errors) == 1:
                errorMsgSubject = "One error was found"
            else:
                errorMsgSubject = "errors found: " + str(len(errors))
        else:
            errorMessage.append("No errors")
            errorMsgSubject = "No errors"

        return IngestModule.ProcessResult.OK
