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
from java.sql import Connection
from java.sql import DatabaseMetaData
from java.sql import DriverManager
from java.sql import ResultSet
from java.sql import SQLException
from java.sql import Statement
from java.util.logging import Level
from java.util import ArrayList
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
Locates a variety of different contacts databases, parses them, and populates the blackboard.
"""
class ContactAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    def analyze(self, dataSource, fileManager, context):
        try:

            absFiles = fileManager.findFiles(dataSource, "contacts.db")
            absFiles.addAll(fileManager.findFiles(dataSource, "contacts2.db"))
            if absFiles.isEmpty():
                return
            for abstractFile in absFiles:
                try:
                    jFile = File(Case.getCurrentCase().getTempDirectory(), str(abstractFile.getId()) + abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.__findContactsInDB(str(jFile.toString()), abstractFile, dataSource)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing Contacts", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            # Error finding Contacts.
            pass

    """
    Will create artifact from a database given by the path
    The fileId will be the abstract file associated with the artifacts
    """
    def __findContactsInDB(self, databasePath, abstractFile, dataSource):
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

        deviceAccountInstance = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance  (Account.Type.DEVICE, deviceID, general.MODULE_NAME, abstractFile)

        resultSet = None
        try:
            # get display_name, mimetype(email or phone number) and data1 (phonenumber or email address depending on mimetype)
            # sorted by name, so phonenumber/email would be consecutive for a person if they exist.
            # check if contacts.name_raw_contact_id exists. Modify the query accordingly.
            columnFound = False
            metadata = connection.getMetaData()
            columnListResultSet = metadata.getColumns(None, None, "contacts", None)
            while columnListResultSet.next():
                if columnListResultSet.getString("COLUMN_NAME") == "name_raw_contact_id":
                    columnFound = True
                    break
            if columnFound:
                resultSet = statement.executeQuery(
                    "SELECT mimetype, data1, name_raw_contact.display_name AS display_name \n"
                    + "FROM raw_contacts JOIN contacts ON (raw_contacts.contact_id=contacts._id) \n"
                    + "JOIN raw_contacts AS name_raw_contact ON(name_raw_contact_id=name_raw_contact._id) "
                    + "LEFT OUTER JOIN data ON (data.raw_contact_id=raw_contacts._id) \n"
                    + "LEFT OUTER JOIN mimetypes ON (data.mimetype_id=mimetypes._id) \n"
                    + "WHERE mimetype = 'vnd.android.cursor.item/phone_v2' OR mimetype = 'vnd.android.cursor.item/email_v2'\n"
                    + "ORDER BY name_raw_contact.display_name ASC;")
            else:
                resultSet = statement.executeQuery(
                    "SELECT mimetype, data1, raw_contacts.display_name AS display_name \n"
                    + "FROM raw_contacts JOIN contacts ON (raw_contacts.contact_id=contacts._id) \n"
                    + "LEFT OUTER JOIN data ON (data.raw_contact_id=raw_contacts._id) \n"
                    + "LEFT OUTER JOIN mimetypes ON (data.mimetype_id=mimetypes._id) \n"
                    + "WHERE mimetype = 'vnd.android.cursor.item/phone_v2' OR mimetype = 'vnd.android.cursor.item/email_v2'\n"
                    + "ORDER BY raw_contacts.display_name ASC;")

            attributes = ArrayList()
            artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT)
            oldName = ""
            while resultSet.next():
                name = resultSet.getString("display_name")
                data1 = resultSet.getString("data1") # the phone number or email
                mimetype = resultSet.getString("mimetype") # either phone or email
                attributes = ArrayList()
                if name != oldName:
                    artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT)
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, general.MODULE_NAME, name))
                if mimetype == "vnd.android.cursor.item/phone_v2":
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, general.MODULE_NAME, data1))
                    acctType = Account.Type.PHONE
                else:
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL, general.MODULE_NAME, data1))
                    acctType = Account.Type.EMAIL

                artifact.addAttributes(attributes)

                # Create an account instance
                contactAccountInstance = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance  (acctType, data1, general.MODULE_NAME, abstractFile);

                # create relationship between accounts
                Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().addRelationships(deviceAccountInstance, [contactAccountInstance], artifact,Relationship.Type.CONTACT,  0);

                oldName = name

                bbartifacts.append(artifact)

        except SQLException as ex:
            # Unable to execute contacts SQL query against database.
            pass
        except TskCoreException as ex:
            self._logger.log(Level.SEVERE, "Error posting to blackboard", ex)
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
