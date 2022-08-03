"""
Autopsy Forensic Browser

Copyright 2016-2021 Basis Technology Corp.
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
import struct
import os

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
                    self.__findGeoLocationsInFile(jFile, abstractFile, context)
                except Exception as ex:
                    self._logger.log(Level.SEVERE, "Error parsing cached location files", ex)
                    self._logger.log(Level.SEVERE, traceback.format_exc())
        except TskCoreException as ex:
            # Error finding cached location files.
            pass

    def __findGeoLocationsInFile(self, file, abstractFile, context):

        try:
            # code to parse the cache.wifi and cache.cell taken from https://forensics.spreitzenbarth.de/2011/10/28/decoding-cache-cell-and-cache-wifi-files/
            cacheFile = open(str(file), 'rb')
            (version, entries) = struct.unpack('>hh', cacheFile.read(4))
            # Check the number of entries * 32 (entry record size) to see if it is bigger then the file, this is a indication the file is malformed or corrupted
            if ((entries * 32) < abstractFile.getSize()):            
                i = 0
                self._logger.log(Level.INFO, "Number of Entries is " + str(entries) + " File size is " + str(abstractFile.getSize()))
                while i < entries:
                    key = cacheFile.read(struct.unpack('>h', cacheFile.read(2))[0])
                    (accuracy, confidence, latitude, longitude, readtime) = struct.unpack('>iiddQ', cacheFile.read(32))
                    timestamp = readtime/1000
                    i = i + 1

                    attributes = ArrayList()
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, general.MODULE_NAME, latitude))
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, general.MODULE_NAME, longitude))
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, general.MODULE_NAME, timestamp))
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, general.MODULE_NAME,
                        abstractFile.getName() + " Location History"))

                    artifact = abstractFile.newDataArtifact(BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK), attributes)
                    #Not storing these for now.
                    #    artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), AndroidModuleFactorymodule.moduleName, accuracy))
                    #    artifact.addAttribute(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID(), AndroidModuleFactorymodule.moduleName, confidence))
                    try:
                        blackboard = Case.getCurrentCase().getSleuthkitCase().getBlackboard()
                        blackboard.postArtifact(artifact, general.MODULE_NAME, context.getJobId())
                    except Blackboard.BlackboardException as ex:
                        self._logger.log(Level.SEVERE, "Unable to index blackboard artifact " + str(artifact.getArtifactID()), ex)
                        self._logger.log(Level.SEVERE, traceback.format_exc())
                        MessageNotifyUtil.Notify.error("Failed to index GPS trackpoint artifact for keyword search.", artifact.getDisplayName())
                cacheFile.close()
            else:
                self._logger.log(Level.WARNING, "Number of entries in file exceeds file size of file " + os.path.join(abstractFile.getParentPath(), abstractFile.getName()))
        except Exception as ex:
            self._logger.log(Level.SEVERE, "Error parsing Cached GPS locations to blackboard", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())

    def toDouble(byteArray):
        return ByteBuffer.wrap(byteArray).getDouble()
