"""
Autopsy Forensic Browser

Copyright 2016 Basis Technology Corp.
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
from java.io import IOException
from java.lang import Class
from java.lang import ClassNotFoundException
from java.lang import String
from java.sql import Connection
from java.sql import DriverManager
from java.sql import ResultSet
from java.sql import SQLException
from java.sql import Statement
from java.util.logging import Level
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import Blackboard
from org.sleuthkit.autopsy.casemodule.services import FileManager
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import MessageNotifyUtil
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.ingest import ModuleDataEvent
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel.BlackboardAttribute import ATTRIBUTE_TYPE
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel import Account

import traceback
import general

deviceAccountInstance = None

"""
Locates a variety of different call log databases, parses them, and populates the blackboard.
"""
class CallLogAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    # the names of tables that potentially hold call logs in the dbs
    _tableNames = ["calls", "logs"]

    class CallDirection:

        def __init__(self, type, displayName):
            self.type = type
            self.displayName = displayName

        def getDisplayName(self):
            return self.displayName

    INCOMING = CallDirection(1, "Incoming")
    OUTGOING = CallDirection(2, "Outgoing")
    MISSED = CallDirection(3, "Missed")

    @staticmethod
    def fromType(t):
        return {
            1: CallLogAnalyzer.INCOMING,
            2: CallLogAnalyzer.OUTGOING,
            3: CallLogAnalyzer.MISSED
        }.get(t, None)

    def analyze(self, dataSource, fileManager, context):
        try:

            # Create a 'Device' account using the data source device id
            datasourceObjId = dataSource.getDataSource().getId()
            ds = Case.getCurrentCase().getSleuthkitCase().getDataSource(datasourceObjId)
            deviceID = ds.getDeviceId()

            global deviceAccountInstance
            deviceAccountInstance = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.DEVICE, deviceID, general.MODULE_NAME, dataSource)

            absFiles = fileManager.findFiles(dataSource, "logs.db")
            absFiles.addAll(fileManager.findFiles(dataSource, "contacts.db"))
            absFiles.addAll(fileManager.findFiles(dataSource, "contacts2.db"))
            for abstractFile in absFiles:
                try:
                    file = File(Case.getCurrentCase().getTempDirectory(), str(abstractFile.getId()) + abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, file, context.dataSourceIngestIsCancelled)
                    self.__findCallLogsInDB(file.toString(), abstractFile)
                except IOException as ex:
                    self._logger.log(Level.SEVERE, "Error writing temporary call log db to disk", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, "Error finding call logs", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())

    def __findCallLogsInDB(self, databasePath, abstractFile):
        if not databasePath:
            return

        bbartifacts = list()
        try:
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)
            statement = connection.createStatement()


            for tableName in CallLogAnalyzer._tableNames:
                try:
                    resultSet = statement.executeQuery("SELECT number, date, duration, type, name FROM " + tableName + " ORDER BY date DESC;")
                    self._logger.log(Level.INFO, "Reading call log from table {0} in db {1}", [tableName, databasePath])
                    while resultSet.next():
                        date = resultSet.getLong("date") / 1000
                        direction = CallLogAnalyzer.fromType(resultSet.getInt("type"))
                        directionString = direction.getDisplayName() if direction is not None else ""
                        number = resultSet.getString("number")
                        duration = resultSet.getLong("duration") # duration of call is in seconds
                        name = resultSet.getString("name") # name of person dialed or called. None if unregistered

                        try:
                            artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG) # create a call log and then add attributes from result set.
                            if direction == CallLogAnalyzer.OUTGOING:
                                artifact.addAttribute(BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, general.MODULE_NAME, number))
                            else: # Covers INCOMING and MISSED
                                artifact.addAttribute(BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, general.MODULE_NAME, number))

                            artifact.addAttribute(BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_START, general.MODULE_NAME, date))
                            artifact.addAttribute(BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_END, general.MODULE_NAME, duration + date))
                            artifact.addAttribute(BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DIRECTION, general.MODULE_NAME, directionString))
                            artifact.addAttribute(BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, general.MODULE_NAME, name))

                            # Create an account
                            calllogAccountInstance = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.PHONE, number, general.MODULE_NAME, abstractFile);

                            # create relationship between accounts
                            Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().addRelationships(deviceAccountInstance, [calllogAccountInstance], artifact, date);

                            bbartifacts.append(artifact)

                            try:
                                # index the artifact for keyword search
                                blackboard = Case.getCurrentCase().getServices().getBlackboard()
                                blackboard.indexArtifact(artifact)
                            except Blackboard.BlackboardException as ex:
                                self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex)
                                self._logger.log(Level.SEVERE, traceback.format_exc())
                                MessageNotifyUtil.Notify.error("Failed to index call log artifact for keyword search.", artifact.getDisplayName())

                        except TskCoreException as ex:
                            self._logger.log(Level.SEVERE, "Error posting call log record to the blackboard", ex)
                            self._logger.log(Level.SEVERE, traceback.format_exc())
                except SQLException as ex:
                    self._logger.log(Level.WARNING, String.format("Could not read table %s in db %s", tableName, databasePath), ex)
        except SQLException as ex:
            self._logger.log(Level.SEVERE, "Could not parse call log; error connecting to db " + databasePath, ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        finally:
            if bbartifacts:
                IngestServices.getInstance().fireModuleDataEvent(ModuleDataEvent(general.MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG, bbartifacts))

