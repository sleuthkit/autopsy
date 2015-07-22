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

# Simple file-level ingest module for Autopsy.
# Search for TODO for the things that you need to change
# See http://sleuthkit.org/autopsy/docs/api-docs/3.1/index.html for documentation

import os
import sys
import jarray
import inspect
from java.lang import Class
from java.lang import System
from java.nio.file import Files
from jarray import zeros, array
from java.util.logging import Level
from java.sql  import DriverManager, SQLException
from org.sleuthkit.datamodel import SleuthkitCase
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import ReadContentInputStream
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import TskData
from org.sleuthkit.autopsy.ingest import IngestModule
from org.sleuthkit.autopsy.ingest.IngestModule import IngestModuleException
from org.sleuthkit.autopsy.ingest import DataSourceIngestModule
from org.sleuthkit.autopsy.ingest import FileIngestModule
from org.sleuthkit.autopsy.ingest import IngestModuleFactoryAdapter
from org.sleuthkit.autopsy.ingest import IngestMessage
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.ingest import ModuleDataEvent
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import Services
from org.sleuthkit.autopsy.casemodule.services import FileManager



################################################################################

DATABASE    = Case.getCurrentCase().getTempDirectory() + r"\temp"
JDBC_URL    = "jdbc:sqlite:%s"  % DATABASE
JDBC_DRIVER = "org.sqlite.JDBC"
            
TABLE_NAME      = "contacts"
SELECT_QUERY    = "SELECT * FROM %s ORDER BY ID" % TABLE_NAME


def getConnection(jdbc_url, driverName):
    """
        Given the name of a JDBC driver class and the url to be used 
        to connect to a database, attempt to obtain a connection to 
        the database.
    """
    try:
        Class.forName(driverName).newInstance()
    except Exception, msg:
        print msg
        sys.exit(-1)

    try:
        dbConn = DriverManager.getConnection(jdbc_url)
    except SQLException, msg:
        print msg
        sys.exit(-1)

    return dbConn

################################################################################

# Factory that defines the name and details of the module and allows Autopsy
# to create instances of the modules that will do the anlaysis.
class SQLiteIngestModuleFactory(IngestModuleFactoryAdapter):

    moduleName = "SQLite ingest Module"

    def getModuleDisplayName(self):
        return self.moduleName

    def getModuleDescription(self):
        return "SQLite module SQL."

    def getModuleVersionNumber(self):
        return "1.0"

    # Return true if module wants to get called for each file
    def isFileIngestModuleFactory(self):
        return True

    # can return null if isFileIngestModuleFactory returns false
    def createFileIngestModule(self, ingestOptions):
        return SQLiteIngestModule()


# File-level ingest module.  One gets created per thread.
class SQLiteIngestModule(FileIngestModule):

    _logger = Logger.getLogger(SQLiteIngestModuleFactory.moduleName)

    def log(self, level, msg):
        self._logger.logp(level, self.__class__.__name__, inspect.stack()[1][3], msg)

    # Where any setup and configuration is done
    # 'context' is an instance of org.sleuthkit.autopsy.ingest.IngestJobContext.
    # See: http://sleuthkit.org/autopsy/docs/api-docs/3.1/classorg_1_1sleuthkit_1_1autopsy_1_1ingest_1_1_ingest_job_context.html
    def startUp(self, context):
        self.filesFound = 0

        try:
            f = open(DATABASE, 'wb')
            f.close
        except IOError:
            raise IngestModuleException(IngestModule(), "Couldn't create %s file" % DATABASE)
        pass

    # Where the analysis is done.  Each file will be passed into here.
    # The 'file' object being passed in is of type org.sleuthkit.datamodel.AbstractFile.
    # See: http://www.sleuthkit.org/sleuthkit/docs/jni-docs/classorg_1_1sleuthkit_1_1datamodel_1_1_abstract_file.html
    def process(self, file):
        # Skip non-files
        if ((file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) or 
            (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS) or 
            (file.isFile() == False)):
            return IngestModule.ProcessResult.OK

        # We will flag files with .db in the name and make a blackboard artifact.
        if file.getName().lower().endswith(".db"):

            self.log(Level.INFO, "Found a database file: " + file.getName())
            self.filesFound+=1

            # create a jarray of bytes to read from db file
            bytes = zeros(file.getSize(), 'b')
            if file.canRead():
                file.read(bytes, 0, file.getSize())

            # save byte array as temp file to open it with jdbc
            barray = bytearray(bytes)
            f = open(DATABASE, 'wb')
            f.write(barray)
            f.close()

            # connet to db
            dbConn = getConnection(JDBC_URL, JDBC_DRIVER)
            stmt = dbConn.createStatement()

            # get information from db (name, email and phone)
            resultSet = stmt.executeQuery(SELECT_QUERY)
            while resultSet.next():
                name  = resultSet.getString("name")
                email = resultSet.getString("email")
                phone = resultSet.getString("phone")

                # Make an artifact on the blackboard, TSK_CONTACT and give it attributes for each of the fields
                art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT)
                att1 = BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME_PERSON.getTypeID(), 
                    SQLiteIngestModuleFactory.moduleName, name)
                att2 = BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL.getTypeID(), 
                    SQLiteIngestModuleFactory.moduleName, email)
                att3 = BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER.getTypeID(), 
                    SQLiteIngestModuleFactory.moduleName, phone)
                art.addAttribute(att1)
                art.addAttribute(att2)
                art.addAttribute(att3)


            stmt.close()
            dbConn.close()

            # Fire an event to notify the UI and others that there is a new artifact  
            IngestServices.getInstance().fireModuleDataEvent(
                ModuleDataEvent(SQLiteIngestModuleFactory.moduleName, 
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT, None));

        return IngestModule.ProcessResult.OK

    # Where any shutdown code is run and resources are freed.
    def shutDown(self):
        # As a final part of this example, we'll send a message to the ingest inbox with the number of files found (in this thread)
        
        message = IngestMessage.createMessage(
            IngestMessage.MessageType.DATA, SQLiteIngestModuleFactory.moduleName, 
                str(self.filesFound) + " files found")
        ingestServices = IngestServices.getInstance().postMessage(message)


