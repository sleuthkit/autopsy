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
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import Blackboard
from org.sleuthkit.autopsy.casemodule.services import FileManager
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import MessageNotifyUtil
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException

import traceback
import general

"""
Finds database with SMS/MMS messages and adds them to blackboard.
"""
class TextMessageAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger("TextMessageAnalyzer")

    def analyze(self, dataSource, fileManager, context):
        try:
            absFiles = fileManager.findFiles(dataSource, "mmssms.db")
            for abstractFile in absFiles:
                try:
                    jFile = File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.__findTextsInDB(jFile.toString(), abstractFile)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing text messages", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, "Error finding text messages", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())

    def __findTextsInDB(self, databasePath, abstractFile):
        if not databasePath:
            return

        try:
            Class.forName("org.sqlite.JDBC") # load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)
            statement = connection.createStatement()
        except (ClassNotFoundException, SQLException) as ex:
            self._logger.log(Level.SEVERE, "Error opening database", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
            return

        try:
            resultSet = statement.executeQuery(
                "SELECT address, date, read, type, subject, body FROM sms;")
            while resultSet.next():
                address = resultSet.getString("address") # may be phone number, or other addresses
                date = Long.valueOf(resultSet.getString("date")) / 1000
                read = resultSet.getInt("read") # may be unread = 0, read = 1
                subject = resultSet.getString("subject") # message subject
                body = resultSet.getString("body") # message body
                artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE); #create Message artifact and then add attributes from result set.
                if resultSet.getString("type") == "1":
                    artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, general.MODULE_NAME, "Incoming"))
                    artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, general.MODULE_NAME, address))
                else:
                    artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, general.MODULE_NAME, "Outgoing"))
                    artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, general.MODULE_NAME, address))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, general.MODULE_NAME, date))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_READ_STATUS, general.MODULE_NAME, Integer(read)))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT, general.MODULE_NAME, subject))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, general.MODULE_NAME, body))
                artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, general.MODULE_NAME, "SMS Message"))

                try:
                    # index the artifact for keyword search
                    blackboard = Case.getCurrentCase().getServices().getBlackboard()
                    blackboard.indexArtifact(artifact)
                except Blackboard.BlackboardException as ex:
                    self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                    MessageNotifyUtil.Notify.error("Failed to index text message artifact for keyword search.", artifact.getDisplayName())

        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error parsing text messages to Blackboard", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())
        finally:
            try:
                if resultSet is not None:
                    resultSet.close()
                statement.close()
                connection.close()
            except Exception as ex:
                self._logger.log(Level.SEVERE, "Error closing database", ex)
                self._logger.log(Level.SEVERE, traceback.format_exc())
