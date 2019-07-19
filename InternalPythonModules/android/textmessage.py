"""
Autopsy Forensic Browser

Copyright 2016-2018 Basis Technology Corp.
Contact: carrier <at> sleuthkit <dot> org

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

from java.io import File
from java.lang import Class
from java.lang import ClassNotFoundException
from java.lang import Integer
from java.lang import Long
from java.sql import Connection
from java.sql import DriverManager
from java.sql import ResultSet
from java.sql import SQLException
from java.sql import Statement
from java.util.logging import Level
from java.util import ArrayList
from java.util import UUID
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import FileManager
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import MessageNotifyUtil
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.ingest import ModuleDataEvent
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import Blackboard
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel import Account
from org.sleuthkit.datamodel import Relationship

import traceback
import general


"""
Finds database with SMS/MMS messages and adds them to blackboard.
"""
class TextMessageAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        try:
            absFiles = fileManager.findFiles(dataSource, "mmssms.db")
            for abstractFile in absFiles:
                try:
                    jFile = File(Case.getCurrentCase().getTempDirectory(), str(abstractFile.getId()) + abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.__findTextsInDB(jFile.toString(), abstractFile, dataSource)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing text messages", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            # Error finding text messages.
            pass

    def __findTextsInDB(self, databasePath, abstractFile, dataSource):
        if not databasePath:
            return

        bbartifacts = list()
        try:
            Class.forName("org.sqlite.JDBC") # load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)
            statement = connection.createStatement()
        except (ClassNotFoundException) as ex:
            self._logger.log(Level.SEVERE, "Error loading JDBC driver", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
            return
        except (SQLException) as ex:
            # Error opening database.
            return

        # Create a 'Device' account using the data source device id
        datasourceObjId = dataSource.getDataSource().getId()
        ds = Case.getCurrentCase().getSleuthkitCase().getDataSource(datasourceObjId)
        deviceID = ds.getDeviceId()
        deviceAccountInstance = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.DEVICE, deviceID, general.MODULE_NAME, abstractFile)
        uuid = UUID.randomUUID().toString()

        resultSet = None
        try:
            resultSet = statement.executeQuery(
                "SELECT address, date, read, type, subject, body, thread_id FROM sms;")
            while resultSet.next():
                address = resultSet.getString("address") # may be phone number, or other addresses
                date = Long.valueOf(resultSet.getString("date")) / 1000
                read = resultSet.getInt("read") # may be unread = 0, read = 1
                subject = resultSet.getString("subject") # message subject
                body = resultSet.getString("body") # message body
                thread_id = "{0}-{1}".format(uuid, resultSet.getInt("thread_id"))
                attributes = ArrayList()
                artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE); #create Message artifact and then add attributes from result set.
                if resultSet.getString("type") == "1":
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, general.MODULE_NAME, "Incoming"))
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, general.MODULE_NAME, address))
                else:
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, general.MODULE_NAME, "Outgoing"))
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, general.MODULE_NAME, address))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, general.MODULE_NAME, date))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_READ_STATUS, general.MODULE_NAME, Integer(read)))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT, general.MODULE_NAME, subject))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, general.MODULE_NAME, body))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, general.MODULE_NAME, "SMS Message"))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_THREAD_ID, general.MODULE_NAME, thread_id))

                artifact.addAttributes(attributes)

                # Create an account
                msgAccountInstance = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.PHONE, address, general.MODULE_NAME, abstractFile);

                # create relationship between accounts
                Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().addRelationships(deviceAccountInstance, [msgAccountInstance], artifact,Relationship.Type.MESSAGE, date);

                bbartifacts.append(artifact)

        except SQLException as ex:
            # Unable to execute text messages SQL query against database.
            pass
        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error parsing text messages to blackboard", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        finally:

            if bbartifacts:
                Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifacts(bbartifacts, general.MODULE_NAME)

            try:

                if resultSet is not None:
                    resultSet.close()
                statement.close()
                connection.close()
            except Exception as ex:
                # Error closing database.
                pass
