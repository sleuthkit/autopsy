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


# Report module for Autopsy.
#
# Search for TODO for the things that you need to change
# See http://sleuthkit.org/autopsy/docs/api-docs/3.1/index.html for documentation

from java.lang import System
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.report import GeneralReportModuleAdapter
import os

# TODO: Rename this to something more specific
class SampleGeneralReportModule(GeneralReportModuleAdapter):

    # TODO: Rename this.  Will be shown to users when making a report
    def getName(self):
        return "Sample Jython Report Module"

    # TODO: rewrite this
    def getDescription(self):
        return "A sample Jython report module"

    # TODO: Update this to reflect where the report file will be written to
    def getRelativeFilePath(self):
        return "sampleReport.txt"

    # TODO: Update this method to make a report
    def generateReport(self, baseReportDir, progressBar):
        
            # For an example, we write a file with the number of files created in the past 2 weeks
            # Configure progress bar for 2 tasks
            progressBar.setIndeterminate(False)
            progressBar.start()
            progressBar.setMaximumProgress(2)

            # Get files by created in last two weeks.
            fileCount = 0
            autopsyCase = Case.getCurrentCase()
            sleuthkitCase = autopsyCase.getSleuthkitCase()
            currentTime = System.currentTimeMillis() / 1000
            minTime = currentTime - (14 * 24 * 60 * 60)
            otherFiles = sleuthkitCase.findFilesWhere("crtime > %d" % minTime)
            for otherFile in otherFiles:
                    fileCount += 1
            progressBar.increment()
            
            # Write the result to the report file.
            report = open(os.path.join(baseReportDir, self.getRelativeFilePath()), 'w')
            report.write("file count = %d" % fileCount)
            Case.getCurrentCase().addReport(report.name, "SampleGeneralReportModule", "Sample Python Report");
            report.close()
            
            progressBar.increment()
            progressBar.complete()
