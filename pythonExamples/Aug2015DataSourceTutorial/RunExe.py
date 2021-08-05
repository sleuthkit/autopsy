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
# Used as part of Python tutorials from Basis Technology - August 2015
# 
# Runs img_stat tool from The Sleuth Kit on each data source, saves the
# output, and adds a report to the Case for the output

import jarray
import inspect
import os
import java.util.ArrayList as ArrayList
from java.lang import Class
from java.lang import System
from java.lang import ProcessBuilder
from java.io import File
from java.util.logging import Level
from org.sleuthkit.datamodel import SleuthkitCase
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import ReadContentInputStream
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Image
from org.sleuthkit.autopsy.ingest import IngestModule
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.autopsy.ingest.IngestModule import IngestModuleException
from org.sleuthkit.autopsy.ingest import DataSourceIngestModule
from org.sleuthkit.autopsy.ingest import DataSourceIngestModuleProcessTerminator
from org.sleuthkit.autopsy.ingest import IngestModuleFactoryAdapter
from org.sleuthkit.autopsy.ingest import IngestMessage
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.ingest import ModuleDataEvent
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import PlatformUtil
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import Services
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.coreutils import ExecUtil


# Factory that defines the name and details of the module and allows Autopsy
# to create instances of the modules that will do the analysis.
class RunExeIngestModuleFactory(IngestModuleFactoryAdapter):

    moduleName = "Run EXE Module"

    def getModuleDisplayName(self):
        return self.moduleName

    def getModuleDescription(self):
        return "Sample module that runs img_stat on each disk image."

    def getModuleVersionNumber(self):
        return "1.0"

    def isDataSourceIngestModuleFactory(self):
        return True

    def createDataSourceIngestModule(self, ingestOptions):
        return RunExeIngestModule()


# Data Source-level ingest module.  One gets created per data source.
class RunExeIngestModule(DataSourceIngestModule):

    _logger = Logger.getLogger(RunExeIngestModuleFactory.moduleName)

    def log(self, level, msg):
        self._logger.logp(level, self.__class__.__name__, inspect.stack()[1][3], msg)

    def __init__(self):
        self.context = None

    # Where any setup and configuration is done
    # 'context' is an instance of org.sleuthkit.autopsy.ingest.IngestJobContext.
    # See: http://sleuthkit.org/autopsy/docs/api-docs/latest/classorg_1_1sleuthkit_1_1autopsy_1_1ingest_1_1_ingest_job_context.html
    def startUp(self, context):
        self.context = context
        
        # Get path to EXE based on where this script is run from.
        # Assumes EXE is in same folder as script
        # Verify it is there before any ingest starts
        exe_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "img_stat.exe")
        self.pathToEXE = File(exe_path)
        if not self.pathToEXE.exists():
            raise IngestModuleException("EXE was not found in module folder")
            
    # Where the analysis is done.
    # The 'dataSource' object being passed in is of type org.sleuthkit.datamodel.Content.
    # See: http://www.sleuthkit.org/sleuthkit/docs/jni-docs/latest/interfaceorg_1_1sleuthkit_1_1datamodel_1_1_content.html
    # 'progressBar' is of type org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress
    # See: http://sleuthkit.org/autopsy/docs/api-docs/latest/classorg_1_1sleuthkit_1_1autopsy_1_1ingest_1_1_data_source_ingest_module_progress.html
    def process(self, dataSource, progressBar):
        
        # we don't know how much work there will be
        progressBar.switchToIndeterminate()
        # Example has only a Windows EXE, so bail if we aren't on Windows
        if not PlatformUtil.isWindowsOS(): 
            self.log(Level.INFO, "Ignoring data source.  Not running on Windows")
            return IngestModule.ProcessResult.OK

        # Verify we have a disk image and not a folder of files
        if not isinstance(dataSource, Image):
            self.log(Level.INFO, "Ignoring data source.  Not an image")
            return IngestModule.ProcessResult.OK

        # Get disk image paths            
        imagePaths = dataSource.getPaths()
        
        # We'll save our output to a file in the reports folder, named based on EXE and data source ID
        reportFile = File(Case.getCurrentCase().getCaseDirectory() + "\\Reports" + "\\img_stat-" + str(dataSource.getId()) + ".txt")
        
        # Run the EXE, saving output to reportFile
        # We use ExecUtil because it will deal with the user cancelling the job
        self.log(Level.INFO, "Running program on data source")
        cmd = ArrayList()
        cmd.add(self.pathToEXE.toString())
        # Add each argument in its own line.  I.e. "-f foo" would be two calls to .add()
        cmd.add(imagePaths[0])
        
        processBuilder = ProcessBuilder(cmd)
        processBuilder.redirectOutput(reportFile)
        ExecUtil.execute(processBuilder, DataSourceIngestModuleProcessTerminator(self.context))
        
        # Add the report to the case, so it shows up in the tree
        # Do not add report to the case tree if the ingest is cancelled before finish.
        if not self.context.dataSourceIngestIsCancelled():
            Case.getCurrentCase().addReport(reportFile.toString(), "Run EXE", "img_stat output")
        else:
            if reportFile.exists():
                if not reportFile.delete():
                    self.log(LEVEL.warning,"Error deleting the incomplete report file")
            
        return IngestModule.ProcessResult.OK
