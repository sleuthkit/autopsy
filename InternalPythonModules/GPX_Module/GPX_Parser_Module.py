"""
Autopsy Forensic Browser

Copyright 2019-2021 Basis Technology Corp.
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

import os
import jarray
import inspect
import time
import calendar
from datetime import datetime

from java.lang import System
from java.util.logging import Level
from java.io import File
from java.util import ArrayList

from org.sleuthkit.datamodel import SleuthkitCase
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import ReadContentInputStream
from org.sleuthkit.datamodel import Blackboard
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel.blackboardutils import GeoArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.attributes import GeoWaypoints
from org.sleuthkit.datamodel.blackboardutils.attributes.GeoWaypoints import Waypoint
from org.sleuthkit.datamodel.blackboardutils.attributes import GeoTrackPoints
from org.sleuthkit.datamodel.blackboardutils.attributes.GeoTrackPoints import TrackPoint
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestModule
from org.sleuthkit.autopsy.ingest.IngestModule import IngestModuleException
from org.sleuthkit.autopsy.ingest import FileIngestModule
from org.sleuthkit.autopsy.ingest import IngestModuleFactoryAdapter
from org.sleuthkit.autopsy.ingest import IngestMessage
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import Services
from org.sleuthkit.autopsy.casemodule.services import FileManager
from org.sleuthkit.autopsy.ingest import ModuleDataEvent

# Based on gpxpy module: https://github.com/tkrajina/gpxpy
import gpxpy
import gpxpy.gpx
import gpxpy.parser

# to get a random filename to prevent race conditions
import uuid

# Factory that defines the name and details of the module and allows Autopsy
# to create instances of the modules that will do the analysis.


class GPXParserFileIngestModuleFactory(IngestModuleFactoryAdapter):

    moduleName = "GPX Parser"

    def getModuleDisplayName(self):
        return self.moduleName

    def getModuleDescription(self):
        return "Module that extracts GEO data from GPX files."

    def getModuleVersionNumber(self):
        return "1.2"

    def isFileIngestModuleFactory(self):
        return True

    def createFileIngestModule(self, ingestOptions):
        return GPXParserFileIngestModule()


# File level ingest module.
class GPXParserFileIngestModule(FileIngestModule):

    logger = Logger.getLogger(
        GPXParserFileIngestModuleFactory.moduleName)
    writeDebugMsgs = False

    def log(self, level, msg):
        self.logger.logp(level, self.__class__.__name__,
                         inspect.stack()[1][3], msg)

    def __init__(self):
        self.context = None
        self.fileCount = 0

        # Get the module name, it will be needed for adding attributes
        self.moduleName = GPXParserFileIngestModuleFactory.moduleName

        # Get the case database and its blackboard.
        self.skCase = Case.getCurrentCase().getSleuthkitCase()
        self.blackboard = self.skCase.getBlackboard()

        # Check if a folder for this module is present in the case Temp directory.
        # If not, create it.
        self.dirName = os.path.join(
            Case.getCurrentCase().getTempDirectory(), "GPX_Parser_Module")
        try:
            os.stat(self.dirName)
        except:
            os.mkdir(self.dirName)

    # Where any setup and configuration is done.

    def startUp(self, context):
        self.context = context
        self.fileCount = 0

    # Where the file analysis is done.
    def process(self, file):
        if not file.getName().lower().endswith(".gpx"):
            return IngestModule.ProcessResult.OK

        # Create a temp file name. It appears that we cannot close and delete
        # this file, but we can overwrite it for each file we need to process.
        fileName = os.path.join(self.dirName, uuid.uuid4().hex + ".gpx")

        # Create a GeoArtifactsHelper for this file.
        geoArtifactHelper = GeoArtifactsHelper(
            self.skCase, self.moduleName, None, file, self.context.getJobId())

        if self.writeDebugMsgs:
            self.log(Level.INFO, "Processing " + file.getUniquePath() +
                     " (objID = " + str(file.getId()) + ")")

        # Write the file so that it can be parsed by gpxpy.
        localFile = File(fileName)
        ContentUtils.writeToFile(file, localFile)

        # Send the file to gpxpy for parsing.
        gpxfile = open(fileName)
        try:
            gpx = gpxpy.parse(gpxfile)
            if self.writeDebugMsgs:
                self.log(Level.INFO, "Parsed " + file.getUniquePath() +
                         " (objID = " + str(file.getId()) + ")")
        except Exception as e:
            self.log(Level.WARNING, "Error parsing file " + file.getUniquePath() +
                     " (objID = " + str(file.getId()) + "):" + str(e))
            return IngestModule.ProcessResult.ERROR

        if gpx:
            if self.writeDebugMsgs:
                self.log(Level.INFO, "Processing tracks from " +
                         file.getUniquePath() + " (objID = " + str(file.getId()) + ")")

            for track in gpx.tracks:
                for segment in track.segments:
                    geoPointList = GeoTrackPoints()
                    for point in segment.points:

                        elevation = 0
                        if point.elevation != None:
                            elevation = point.elevation

                        timeStamp = 0
                        try:
                            if (point.time != None):
                                timeStamp = long(time.mktime(
                                    point.time.timetuple()))
                        except Exception as e:
                            self.log(Level.WARNING, "Error getting track timestamp from " +
                                     file.getUniquePath() + " (objID = " + str(file.getId()) + "):" + str(e))

                        geoPointList.addPoint(TrackPoint(
                            point.latitude, point.longitude, elevation, None, 0, 0, 0, timeStamp))

                    try:
                        if not geoPointList.isEmpty():
                            geoArtifactHelper.addTrack("Track", geoPointList, None)
                    except Blackboard.BlackboardException as e:
                        self.log(Level.SEVERE, "Error posting GPS track artifact for " +
                                 file.getUniquePath() + " (objID = " + str(file.getId()) + "):" + e.getMessage())
                    except TskCoreException as e:
                        self.log(Level.SEVERE, "Error creating GPS track artifact for " +
                                 file.getUniquePath() + " (objID = " + str(file.getId()) + "):" + e.getMessage())

            if self.writeDebugMsgs:
                self.log(Level.INFO, "Processing waypoints from " +
                         file.getUniquePath() + " (objID = " + str(file.getId()) + ")")

            for waypoint in gpx.waypoints:

                try:
                    attributes = ArrayList()
                    attributes.add(BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), self.moduleName, waypoint.latitude))
                    attributes.add(BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), self.moduleName, waypoint.longitude))
                    attributes.add(BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FLAG.getTypeID(), self.moduleName, "Waypoint"))
                    attributes.add(BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), self.moduleName, waypoint.name))
                    attributes.add(BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), self.moduleName, "GPXParser"))

                    art = file.newDataArtifact(BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK), attributes)

                    self.blackboard.postArtifact(art, self.moduleName, self.context.getJobId())

                except Blackboard.BlackboardException as e:
                    self.log(Level.SEVERE, "Error posting GPS bookmark artifact for " +
                             file.getUniquePath() + " (objID = " + str(file.getId()) + "):" + e.getMessage())
                except TskCoreException as e:
                    self.log(Level.SEVERE, "Error creating GPS bookmark artifact for " +
                             file.getUniquePath() + " (objID = " + str(file.getId()) + "):" + e.getMessage())

            if self.writeDebugMsgs:
                self.log(Level.INFO, "Processing routes from " +
                         file.getUniquePath() + " (objID = " + str(file.getId()) + ")")

            for route in gpx.routes:

                geoWaypoints = GeoWaypoints()

                for point in route.points:
                    geoWaypoints.addPoint(
                        Waypoint(point.latitude, point.longitude, point.elevation, point.name))

                try:
                    if not geoWaypoints.isEmpty():
                        geoArtifactHelper.addRoute(None, None, geoWaypoints, None)
                except Blackboard.BlackboardException as e:
                    self.log("Error posting GPS route artifact for " + file.getUniquePath() +
                             " (objID = " + str(file.getId()) + "):" + e.getMessage())
                except TskCoreException as e:
                    self.log(Level.SEVERE, "Error creating GPS route artifact for " +
                             file.getUniquePath() + " (objID = " + str(file.getId()) + "):" + e.getMessage())

        self.fileCount += 1
        return IngestModule.ProcessResult.OK

    def shutDown(self):
        message = IngestMessage.createMessage(
            IngestMessage.MessageType.DATA, GPXParserFileIngestModuleFactory.moduleName,
            str(self.fileCount) + " files found")
        ingestServices = IngestServices.getInstance().postMessage(message)
