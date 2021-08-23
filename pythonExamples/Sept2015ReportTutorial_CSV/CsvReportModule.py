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

# See http://sleuthkit.org/autopsy/docs/api-docs/latest/index.html for documentation

# Simple report module for Autopsy.
# Used as part of Python tutorials from Basis Technology - September 2015
#
# Writes a CSV file with all file names and MD5 hashes.



import os
import codecs
from java.lang import System
from java.util.logging import Level
from org.sleuthkit.datamodel import TskData
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.report import GeneralReportModuleAdapter
from org.sleuthkit.autopsy.report.ReportProgressPanel import ReportStatus
from org.sleuthkit.autopsy.casemodule.services import FileManager


# Class responsible for defining module metadata and logic
class CSVReportModule(GeneralReportModuleAdapter):

    moduleName = "CSV Hash Report"

    _logger = None
    def log(self, level, msg):
        if _logger == None:
            _logger = Logger.getLogger(self.moduleName)

        self._logger.logp(level, self.__class__.__name__, inspect.stack()[1][3], msg)

    def getName(self):
        return self.moduleName

    def getDescription(self):
        return "Writes CSV of file names and hash values"

    def getRelativeFilePath(self):
        return "hashes.csv"

    # TODO: Update this method to make a report
    # The 'baseReportDir' object being passed in is a string with the directory that reports are being stored in.   Report should go into baseReportDir + getRelativeFilePath().
    # The 'progressBar' object is of type ReportProgressPanel.
    #   See: http://sleuthkit.org/autopsy/docs/api-docs/latest/classorg_1_1sleuthkit_1_1autopsy_1_1report_1_1_report_progress_panel.html
    def generateReport(self, reportSettings, progressBar):

        # Open the output file.
        fileName = os.path.join(reportSettings.getReportDirectoryPath(), self.getRelativeFilePath())
        report = codecs.open(fileName, "w", "utf-8")

        # Query the database for the files (ignore the directories)
        sleuthkitCase = Case.getCurrentCase().getSleuthkitCase()
        files = sleuthkitCase.findAllFilesWhere("NOT meta_type = " + str(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue()))

        # Setup the progress bar
        progressBar.setIndeterminate(False)
        progressBar.start()
        progressBar.setMaximumProgress(len(files))

        for file in files:
            md5 = file.getMd5Hash()
            # md5 will be None if Hash Lookup module was not run
            if md5 is None:
                md5 = ""

            report.write(file.getUniquePath()  + "," + md5 + "\n")
            progressBar.increment()

        report.close()

        # Add the report to the Case, so it is shown in the tree
        Case.getCurrentCase().addReport(fileName, self.moduleName, "Hashes CSV")

        progressBar.complete(ReportStatus.COMPLETE)