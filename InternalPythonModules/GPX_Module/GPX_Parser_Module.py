"""
Autopsy Forensic Browser

Copyright 2019-2020 Basis Technology Corp.
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
from org.sleuthkit.datamodel.blackboardutils.attributes import TskGeoWaypointsUtil
from org.sleuthkit.datamodel.blackboardutils.attributes.TskGeoWaypointsUtil import GeoWaypointList
from org.sleuthkit.datamodel.blackboardutils.attributes.TskGeoWaypointsUtil.GeoWaypointList import GeoWaypoint
from org.sleuthkit.datamodel.blackboardutils.attributes import TskGeoTrackpointsUtil
from org.sleuthkit.datamodel.blackboardutils.attributes.TskGeoTrackpointsUtil import GeoTrackPointList
from org.sleuthkit.datamodel.blackboardutils.attributes.TskGeoTrackpointsUtil.GeoTrackPointList import GeoTrackPoint
from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestModule
from org.sleuthkit.autopsy.ingest.IngestModule import IngestModuleException
from org.sleuthkit.autopsy.ingest import DataSourceIngestModule
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

# Factory that defines the name and details of the module and allows Autopsy
# to create instances of the modules that will do the analysis.
class GPXParserDataSourceIngestModuleFactory(IngestModuleFactoryAdapter):

    moduleName = "GPX Parser"
    
    def getModuleDisplayName(self):
        return self.moduleName

    def getModuleDescription(self):
        return "Module that extracts GEO data from GPX files."

    def getModuleVersionNumber(self):
        return "1.2"

    def isDataSourceIngestModuleFactory(self):
        return True

    def createDataSourceIngestModule(self, ingestOptions):
        return GPXParserDataSourceIngestModule()

    
# Data Source-level ingest module. One gets created per data source.
class GPXParserDataSourceIngestModule(DataSourceIngestModule):

    logger = Logger.getLogger(GPXParserDataSourceIngestModuleFactory.moduleName)
    writeDebugMsgs = False

    def log(self, level, msg):
        self.logger.logp(level, self.__class__.__name__, inspect.stack()[1][3], msg)

    def __init__(self):
        self.context = None

    # Where any setup and configuration is done.
    def startUp(self, context):
        self.context = context

    # Where the analysis is done.
    def process(self, dataSource, progressBar):

        # We don't know how much work there is yet.
        progressBar.switchToIndeterminate()
        
        # Get the case database and its blackboard.
        skCase = Case.getCurrentCase().getSleuthkitCase()
        blackboard = skCase.getBlackboard()

        # Get any files with a .gpx extension.
        # It would perhaps be better to get these files by MIME type instead.
        # RC: It would also be better if this were a file level ingest module so it could process files extracted from archives.
        fileManager = Case.getCurrentCase().getServices().getFileManager()        
        files = fileManager.findFiles(dataSource, "%.gpx")

        # Update the progress bar now that we know how much work there is to do.
        numFiles = len(files)
        if self.writeDebugMsgs: self.log(Level.INFO, "Found " + str(numFiles) + " GPX files") 
        progressBar.switchToDeterminate(numFiles)

        # Get the module name, it will be needed for adding attributes
        moduleName = GPXParserDataSourceIngestModuleFactory.moduleName

        # Check if a folder for this module is present in the case Temp directory. 
        # If not, create it.
        dirName = os.path.join(Case.getCurrentCase().getTempDirectory(), "GPX_Parser_Module")
        try:
            os.stat(dirName)
        except:
            os.mkdir(dirName)

        # Create a temp file name. It appears that we cannot close and delete 
        # this file, but we can overwrite it for each file we need to process.
        fileName = os.path.join(dirName, "tmp.gpx")
        
        fileCount = 0;
        for file in files:

            # Create a GeoArtifactsHelper for this file.
            geoArtifactHelper = GeoArtifactsHelper(skCase, moduleName, None, file) 
            
            # Check if the user pressed cancel while we were busy.
            if self.context.isJobCancelled():
                return IngestModule.ProcessResult.OK

            if self.writeDebugMsgs: self.log(Level.INFO, "Processing " + file.getUniquePath() + " (objID = " + str(file.getId()) + ")")
            fileCount += 1

            # Write the file so that it can be parsed by gpxpy.
            localFile = File(fileName)
            ContentUtils.writeToFile(file, localFile)

            # Send the file to gpxpy for parsing.
            gpxfile = open(fileName)
            try:
                gpx = gpxpy.parse(gpxfile)
                if self.writeDebugMsgs: self.log(Level.INFO, "Parsed " + file.getUniquePath() + " (objID = " + str(file.getId()) + ")")
            except Exception as e:
                self.log(Level.WARNING, "Error parsing file " + file.getUniquePath() + " (objID = " + str(file.getId()) + "):" + str(e))
                continue
            
            if gpx:
                if self.writeDebugMsgs: self.log(Level.INFO, "Processing tracks from " + file.getUniquePath() + " (objID = " + str(file.getId()) + ")")
                for track in gpx.tracks:                
                    for segment in track.segments:
                        geoPointList = TskGeoTrackpointsUtil.GeoTrackPointList()
                        for point in segment.points:

                            elevation = 0
                            if point.elevation != None:
                                elevation = point.elevation
                                
                            timeStamp = 0                               
                            try: 
                                if (point.time != None):
                                    timeStamp = long(time.mktime(point.time.timetuple()))                                    
                            except Exception as e:                            
                                self.log(Level.WARNING, "Error getting track timestamp from " + file.getUniquePath() + " (objID = " + str(file.getId()) + "):" + str(e))

                            geoPointList.addPoint(GeoTrackPoint(point.latitude, point.longitude, elevation, None, 0, 0, 0, timeStamp))
                                                                                                             
                        try:
                            geoArtifactHelper.addTrack("Track", geoPointList, None)
                        except Blackboard.BlackboardException as e:
                            self.log(Level.SEVERE, "Error posting GPS track artifact for " + file.getUniquePath() + " (objID = " + str(file.getId()) + "):" +  e.getMessage())
                        except TskCoreException as e:
                            self.log(Level.SEVERE, "Error creating GPS track artifact for " + file.getUniquePath() + " (objID = " + str(file.getId()) + "):" +  e.getMessage())
                            
                if self.writeDebugMsgs: self.log(Level.INFO, "Processing waypoints from " + file.getUniquePath() + " (objID = " + str(file.getId()) + ")") 
                for waypoint in gpx.waypoints:
                    
                    try:
                        art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK)

                        attributes = ArrayList()
                        attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), moduleName, waypoint.latitude))
                        attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), moduleName, waypoint.longitude))                    
                        attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FLAG.getTypeID(), moduleName, "Waypoint"))
                        attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), moduleName, waypoint.name))
                        attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), moduleName, "GPXParser"))
                        art.addAttributes(attributes)

                        blackboard.postArtifact(art, moduleName)

                    except Blackboard.BlackboardException as e:
                        self.log(Level.SEVERE, "Error posting GPS bookmark artifact for " + file.getUniquePath() + " (objID = " + str(file.getId()) + "):" +  e.getMessage())
                    except TskCoreException as e:
                        self.log(Level.SEVERE, "Error creating GPS bookmark artifact for " + file.getUniquePath() + " (objID = " + str(file.getId()) + "):" +  e.getMessage())

                if self.writeDebugMsgs: self.log(Level.INFO, "Processing routes from " + file.getUniquePath() + " (objID = " + str(file.getId()) + ")")
                for route in gpx.routes:    

                    geoWaypointList = TskGeoWaypointsUtil.GeoWaypointList()

                    for point in route.points:
                        geoWaypointList.addPoint(point.latitude, point.longitude, elevation, point.name)
                    
                    try:
                        geoArtifactHelper.addRoute(None, None, geoWaypointList, None)
                    except Blackboard.BlackboardException as e:
                        self.log("Error posting GPS route artifact for " + file.getUniquePath() + " (objID = " + str(file.getId()) + "):" +  e.getMessage())
                    except TskCoreException as e:
                        self.log(Level.SEVERE, "Error creating GPS route artifact for " + file.getUniquePath() + " (objID = " + str(file.getId()) + "):" +  e.getMessage())
                                
            # Update the progress bar.
            progressBar.progress(fileCount)

        # Post a message to the ingest messages inbox.
        message = IngestMessage.createMessage(IngestMessage.MessageType.DATA, moduleName, "Processed %d files" % fileCount)
        IngestServices.getInstance().postMessage(message)
        return IngestModule.ProcessResult.OK;
