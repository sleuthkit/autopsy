from java.lang import System
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.datamodel import SleuthkitCase
from org.sleuthkit.autopsy.report import GeneralReportModuleAdapter

class SampleGeneralReportModule(GeneralReportModuleAdapter):

    def getName(self):
        return "Sample Report Module"

    def getDescription(self):
        return "A sample Jython report module"

    def getRelativeFilePath(self):
        return "sampleReport.txt"

    def generateReport(self, reportPath, progressBar):
            # There are two tasks to do.
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
            report = open(reportPath + '\\' + self.getFilePath(), 'w')
            report.write("file count = %d" % fileCount)
            report.close()
            progressBar.increment()
            progressBar.complete()

