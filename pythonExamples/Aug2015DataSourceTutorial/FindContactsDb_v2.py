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
# Looks for files of a given name, opens then in SQLite, queries the DB,
# and makes artifacts

import jarray
import inspect
from java.lang import Class
from java.lang import System
from java.util.logging import Level
from java.util import ArrayList
from java.io import File
from org.sleuthkit.datamodel import SleuthkitCase
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.autopsy.ingest import IngestModule
from org.sleuthkit.autopsy.ingest.IngestModule import IngestModuleException
from org.sleuthkit.autopsy.ingest import DataSourceIngestModule
from org.sleuthkit.autopsy.ingest import IngestModuleFactoryAdapter
from org.sleuthkit.autopsy.ingest import IngestMessage
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.ingest import ModuleDataEvent
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel.Blackboard import BlackboardException
from org.sleuthkit.autopsy.casemodule import NoCurrentCaseException
from org.sleuthkit.datamodel import Account
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from java.sql import ResultSet
from java.sql import SQLException
from org.sleuthkit.autopsy.coreutils import AppSQLiteDB

# Factory that defines the name and details of the module and allows Autopsy
# to create instances of the modules that will do the analysis.
class ContactsDbIngestModuleFactory(IngestModuleFactoryAdapter):

    # TODO - Replace with your modules name
    moduleName = "Contacts Db Analyzer"

    def getModuleDisplayName(self):
        return self.moduleName

    def getModuleDescription(self):
        return "Sample module that parses contacts.db"

    def getModuleVersionNumber(self):
        return "1.0"

    def isDataSourceIngestModuleFactory(self):
        return True

    def createDataSourceIngestModule(self, ingestOptions):
        return ContactsDbIngestModule()


# Data Source-level ingest module.  One gets created per data source.
class ContactsDbIngestModule(DataSourceIngestModule):

    _logger = Logger.getLogger(ContactsDbIngestModuleFactory.moduleName)

    def log(self, level, msg):
        self._logger.logp(level, self.__class__.__name__, inspect.stack()[1][3], msg)

    def __init__(self):
        self.context = None

    # Where any setup and configuration is done
    # 'context' is an instance of org.sleuthkit.autopsy.ingest.IngestJobContext.
    # See: http://sleuthkit.org/autopsy/docs/api-docs/latest/classorg_1_1sleuthkit_1_1autopsy_1_1ingest_1_1_ingest_job_context.html
    def startUp(self, context):
        self.context = context

    # Where the analysis is done.
    # The 'data_source' object being passed in is of type org.sleuthkit.datamodel.Content.
    # See: http://www.sleuthkit.org/sleuthkit/docs/jni-docs/latest/interfaceorg_1_1sleuthkit_1_1datamodel_1_1_content.html
    # 'progress_bar' is of type org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress
    # See: http://sleuthkit.org/autopsy/docs/api-docs/latest/classorg_1_1sleuthkit_1_1autopsy_1_1ingest_1_1_data_source_ingest_module_progress.html
    def process(self, data_source, progress_bar):

        # we don't know how much work there is yet
        progress_bar.switchToIndeterminate()

        # Find files named contacts.db anywhere in the data source.
        # TODO - replace with your database name and parent path.
        app_databases = AppSQLiteDB.findAppDatabases(data_source, "contacts.db", True, "")
                
        num_databases = len(app_databases)
        progress_bar.switchToDeterminate(num_databases)
        databases_processed = 0

        try:
            # Iterate through all the database files returned
            for app_database in app_databases:

                # Check if the user pressed cancel while we were busy
                if self.context.isJobCancelled():
                    return IngestModule.ProcessResult.OK

                self.log(Level.INFO, "Processing file: " + app_database.getDBFile().getName())
                
                # Query the contacts table in the database and get all columns. 
                try:
                    # TODO - replace with your query
                    result_set = app_database.runQuery("SELECT * FROM contacts")
                except SQLException as e:
                    self.log(Level.INFO, "Error querying database for contacts table (" + e.getMessage() + ")")
                    return IngestModule.ProcessResult.OK

                try:
                    #Get the current case for the CommunicationArtifactsHelper.
                    current_case = Case.getCurrentCaseThrows()
                except NoCurrentCaseException as ex:
                    self.log(Level.INFO, "Case is closed (" + ex.getMessage() + ")")
                    return IngestModule.ProcessResult.OK
                        
                # Create an instance of the helper class
                # TODO - Replace with your parser name and Account.Type
                helper = CommunicationArtifactsHelper(current_case.getSleuthkitCase(), 
                                ContactsDbIngestModuleFactory.moduleName, app_database.getDBFile(), Account.Type.DEVICE, context.getJobId()) 

                # Iterate through each row and create artifacts
                while result_set.next():
                    try: 
                        # TODO - Replace these calls with your column names and types
                        # Ex of other types: result_set.getInt("contact_type") or result_set.getLong("datetime")
                        name  = result_set.getString("name")
                        email = result_set.getString("email")
                        phone = result_set.getString("phone")
                    except SQLException as e:
                        self.log(Level.INFO, "Error getting values from contacts table (" + e.getMessage() + ")")
                    
                    helper.addContact(name, phone, "", "", email)
                
                app_database.close()
                databases_processed += 1
                progress_bar.progress(databases_processed)
        except TskCoreException as e:
            self.log(Level.INFO, "Error inserting or reading from the Sleuthkit case (" + e.getMessage() + ")")
        except BlackboardException as e:
            self.log(Level.INFO, "Error posting artifact to the Blackboard (" + e.getMessage() + ")")

        # After all databases, post a message to the ingest messages in box.
        # TODO - update your module name here
        message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                ContactsDbIngestModuleFactory.moduleName, "Found %d files" % num_databases)
        IngestServices.getInstance().postMessage(message)
        
        return IngestModule.ProcessResult.OK
