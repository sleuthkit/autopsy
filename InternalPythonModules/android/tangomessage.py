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
from java.lang import Long
from java.lang import String
from java.sql import Connection
from java.sql import DriverManager
from java.sql import ResultSet
from java.sql import SQLException
from java.sql import Statement
from java.util.logging import Level
from java.util import ArrayList
from org.apache.commons.codec.binary import Base64
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import FileManager
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import MessageNotifyUtil
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import Blackboard
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel import Account

import traceback
import general

"""
Locates database for the Tango app and adds info to blackboard.
"""
class TangoMessageAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        try:
           
            absFiles = fileManager.findFiles(dataSource, "tc.db")
            for abstractFile in absFiles:
                try:
                    jFile = File(Case.getCurrentCase().getTempDirectory(), str(abstractFile.getId()) + abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.__findTangoMessagesInDB(jFile.toString(), abstractFile, dataSource)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing Tango messages", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            # Error finding Tango messages.
            pass

    def __findTangoMessagesInDB(self, databasePath, abstractFile, dataSource):
        if not databasePath:
            return

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

        resultSet = None
        try:
            resultSet = statement.executeQuery(
                "SELECT conv_id, create_time, direction, payload FROM messages ORDER BY create_time DESC;")

            while resultSet.next():
                conv_id = resultSet.getString("conv_id") # seems to wrap around the message found in payload after decoding from base-64
                create_time = Long.valueOf(resultSet.getString("create_time")) / 1000
                if resultSet.getString("direction") == "1": # 1 incoming, 2 outgoing
                    direction = "Incoming"
                else:
                    direction = "Outgoing"
                payload = resultSet.getString("payload")

                attributes = ArrayList()
                artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE) #create a call log and then add attributes from result set.
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, general.MODULE_NAME, create_time))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, general.MODULE_NAME, direction))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, general.MODULE_NAME, TangoMessageAnalyzer.decodeMessage(conv_id, payload)))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, general.MODULE_NAME, "Tango Message"))

                artifact.addAttributes(attributes)
                try:
                    # index the artifact for keyword search
                    blackboard = Case.getCurrentCase().getSleuthkitCase().getBlackboard()
                    blackboard.postArtifact(artifact, MODULE_NAME)
                except Blackboard.BlackboardException as ex:
                    self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + str(artifact.getArtifactID()), ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                    MessageNotifyUtil.Notify.error("Failed to index Tango message artifact for keyword search.", artifact.getDisplayName())

        except SQLException as ex:
            # Unable to execute Tango messages SQL query against database.
            pass
        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error parsing Tango messages to the blackboard", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        finally:
            try:
                if resultSet is not None:
                    resultSet.close()
                statement.close()
                connection.close()
            except Exception as ex:
                # Error closing database.
                pass

    # take the message string which is wrapped by a certain string, and return the text enclosed.
    @staticmethod
    def decodeMessage(wrapper, message):
        result = ""
        decoded = Base64.decodeBase64(message)
        try:
            Z = String(decoded, "UTF-8")
            result = Z.split(wrapper)[1]
        except Exception as ex:
            # Error decoding a Tango message.
            pass
        return result
