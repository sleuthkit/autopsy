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
from java.io import FileInputStream
from java.io import InputStream
from java.lang import Class
from java.lang import ClassNotFoundException
from java.math import BigInteger
from java.nio import ByteBuffer
from java.util.logging import Level
from java.util import ArrayList
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

import traceback
import general

"""
Parses cache files that Android maintains for Wifi and cell towers. Adds GPS points to blackboard.
"""
class CacheLocationAnalyzer(general.AndroidComponentAnalyzer):

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)

    """
    cache.cell stores mobile tower GPS locations and cache.wifi stores GPS
    and MAC info from Wifi points.
    """
    def analyze(self, dataSource, fileManager, context):
        try:
            abstractFiles = fileManager.findFiles(dataSource, "cache.cell")
            abstractFiles.addAll(fileManager.findFiles(dataSource, "cache.wifi"))
            for abstractFile in abstractFiles:
                if abstractFile.getSize() == 0:
                    continue
                try:
                    jFile = File(Case.getCurrentCase().getTempDirectory(), str(abstractFile.getId()) + abstractFile.getName())
                    ContentUtils.writeToFile(abstractFile, jFile, context.dataSourceIngestIsCancelled)
                    self.__findGeoLocationsInFile(jFile, abstractFile)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing cached location files", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            # Error finding cached location files.
            pass

    def __findGeoLocationsInFile(self, file, abstractFile):

        tempBytes = bytearray([0] * 2) # will temporarily hold bytes to be converted into the correct data types

        try:
            inputStream = FileInputStream(file)

            inputStream.read(tempBytes) # version

            tempBytes = bytearray([0] * 2)
            inputStream.read(tempBytes) # number of location entries

            iterations = BigInteger(tempBytes).intValue()

            for i in range(iterations): # loop through every entry
                tempBytes = bytearray([0] * 2)
                inputStream.read(tempBytes)

                tempBytes = bytearray([0])
                inputStream.read(tempBytes)

                while BigInteger(tempBytes).intValue() != 0: # pass through non important values until the start of accuracy(around 7-10 bytes)
                    if 0 > inputStream.read(tempBytes):
                        break # we've passed the end of the file, so stop

                tempBytes = bytearray([0] * 3)
                inputStream.read(tempBytes)
                if BigInteger(tempBytes).intValue() <= 0: # This refers to a location that could not be calculated
                    tempBytes = bytearray([0] * 28) # read rest of the row's bytes
                    inputStream.read(tempBytes)
                    continue
                accuracy = "" + BigInteger(tempBytes).intValue()

                tempBytes = bytearray([0] * 4)
                inputStream.read(tempBytes)
                confidence = "" + BigInteger(tempBytes).intValue()

                tempBytes = bytearray([0] * 8)
                inputStream.read(tempBytes)
                latitude = CacheLocationAnalyzer.toDouble(bytes)

                tempBytes = bytearray([0] * 8)
                inputStream.read(tempBytes)
                longitude = CacheLocationAnalyzer.toDouble(bytes)

                tempBytes = bytearray([0] * 8)
                inputStream.read(tempBytes)
                timestamp = BigInteger(tempBytes).longValue() / 1000

                attributes = ArrayList()
                artifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT)
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, AndroidAnalyzer.MODULE_NAME, latitude))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, AndroidAnalyzer.MODULE_NAME, longitude))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, AndroidModuleFactorymodule.Name, timestamp))
                attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, AndroidAnalyzer.MODULE_NAME,
                    file.getName() + "Location History"))

                artifact.addAttributes(attributes)
                #Not storing these for now.
                #    artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), AndroidModuleFactorymodule.moduleName, accuracy))
                #    artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID(), AndroidModuleFactorymodule.moduleName, confidence))
                try:
                    # index the artifact for keyword search
                    blackboard = Case.getCurrentCase().getSleuthkitCase().getBlackboard()
                    blackboard.postArtifact(artifact, MODULE_NAME)
                except Blackboard.BlackboardException as ex:
                    self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + str(artifact.getArtifactID()), ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
                    MessageNotifyUtil.Notify.error("Failed to index GPS trackpoint artifact for keyword search.", artifact.getDisplayName())

        except SQLException as ex:
            # Unable to execute Cached GPS locations SQL query against database.
            pass
        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error parsing Cached GPS locations to blackboard", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())

    def toDouble(byteArray):
        return ByteBuffer.wrap(byteArray).getDouble()
