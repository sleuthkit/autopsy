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


# Sample report module for Autopsy.  Use as a starting point for new modules.
#
# Search for TODO for the things that you need to change
# See http://sleuthkit.org/autopsy/docs/api-docs/latest/index.html for documentation

import os
from java.lang import System
from java.util.logging import Level
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.report import GeneralReportModuleAdapter
from org.sleuthkit.autopsy.report.ReportProgressPanel import ReportStatus


# TODO: Rename the class to something more specific
class SampleGeneralReportModule(GeneralReportModuleAdapter):

    # TODO: Rename this.  Will be shown to users when making a report
    moduleName = "Sample Report Module"

    _logger = None
    def log(self, level, msg):
        if _logger == None:
            _logger = Logger.getLogger(self.moduleName)

        self._logger.logp(level, self.__class__.__name__, inspect.stack()[1][3], msg)

    def getName(self):
        return self.moduleName

    # TODO: Give it a useful description
    def getDescription(self):
        return "A sample Jython report module"

    # TODO: Update this to reflect where the report file will be written to
    def getRelativeFilePath(self):
        return "sampleReport.txt"

    # TODO: Update this method to make a report
    # The 'reportSettings' object being passed in is an instance of org.sleuthkit.autopsy.report.GeneralReportSettings.
    # GeneralReportSettings.getReportDirectoryPath() is the directory that reports are being stored in.
    # Report should go into GeneralReportSettings.getReportDirectoryPath() + getRelativeFilePath().
    # The 'progressBar' object is of type ReportProgressPanel.
    #   See: http://sleuthkit.org/autopsy/docs/api-docs/latest/classorg_1_1sleuthkit_1_1autopsy_1_1report_1_1_report_progress_panel.html
    def generateReport(self, reportSettings, progressBar):

        # For an example, we write a file with the number of files created in the past 2 weeks
        # Configure progress bar for 2 tasks
        progressBar.setIndeterminate(False)
        progressBar.start()
        progressBar.setMaximumProgress(2)

        # Find epoch time of when 2 weeks ago was
        currentTime = System.currentTimeMillis() / 1000
        minTime = currentTime - (14 * 24 * 60 * 60) # (days * hours * minutes * seconds)

        # Query the database for files that meet our criteria
        sleuthkitCase = Case.getCurrentCase().getSleuthkitCase()
        files = sleuthkitCase.findAllFilesWhere("crtime > %d" % minTime)

        fileCount = 0
        for file in files:
            fileCount += 1
            # Could do something else here and write it to HTML, CSV, etc.

        # Increment since we are done with step #1
        progressBar.increment()

        # Write the count to the report file.
        fileName = os.path.join(reportSettings.getReportDirectoryPath(), self.getRelativeFilePath())
        report = open(fileName, 'w')
        report.write("file count = %d" % fileCount)
        report.close()

        # Add the report to the Case, so it is shown in the tree
        Case.getCurrentCase().addReport(fileName, self.moduleName, "File Count Report")

        progressBar.increment()

        # Call this with ERROR if report was not generated
        progressBar.complete(ReportStatus.COMPLETE)