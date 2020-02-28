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
from org.sleuthkit.datamodel.blackboardutils.attributes import GeoWaypoint
from org.sleuthkit.datamodel.blackboardutils.attributes import GeoTrackPoints
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

    # True - Verbose debugging messages sent to log file.
    # False - Verbose debugging turned off.
    debuglevel = False
    
    def getModuleDisplayName(self):
        return self.moduleName

    # TODO: Give it a description
    def getModuleDescription(self):
        return "Module that extracts GEO data from GPX files."

    def getModuleVersionNumber(self):
        return "1.1"

    def isDataSourceIngestModuleFactory(self):
        return True

    def createDataSourceIngestModule(self, ingestOptions):
        return GPXParserDataSourceIngestModule()

    
# Data Source-level ingest module. One gets created per data source.
class GPXParserDataSourceIngestModule(DataSourceIngestModule):

    _logger = Logger.getLogger(GPXParserDataSourceIngestModuleFactory.moduleName)

    def log(self, level, msg):
        self._logger.logp(level, self.__class__.__name__, inspect.stack()[1][3], msg)

    def __init__(self):
        self.context = None

    # Where any setup and configuration is done.
    def startUp(self, context):
        self.context = context

    # Where the analysis is done.
    def process(self, dataSource, progressBar):

        # We don't know how much work there is yet.
        progressBar.switchToIndeterminate()

        # This will work in 4.0.1 and beyond.
        # Use blackboard class to index blackboard artifacts for keyword search.
        blackboard = Case.getCurrentCase().getServices().getBlackboard()
        
        # Get the sleuthkitcase
        skCase = Case.getCurrentCase().getSleuthkitCase()

        # In the name and then count and read them.
        fileManager = Case.getCurrentCase().getServices().getFileManager()
        
        files = fileManager.findFiles(dataSource, "%.gpx")
        # TODO: Would like to change this to find files based on mimetype rather than extension.
        #files = findFiles(dataSource, "text/xml")
        #if (file.isMimeType('text/xml') == False):

        numFiles = len(files)
        if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.INFO, "found " + str(numFiles) + " files") 
        progressBar.switchToDeterminate(numFiles)
        fileCount = 0;

        # Get module name for adding attributes
        moduleName = GPXParserDataSourceIngestModuleFactory.moduleName
        
        for file in files:

            # Get the GeoArtifactsHelper
            geoArtifactHelper = GeoArtifactsHelper(skCase, moduleName, file) 
            
            # Check if the user pressed cancel while we were busy.
            if self.context.isJobCancelled():
                return IngestModule.ProcessResult.OK

            #self.log(Level.INFO, "GPX: Processing file: " + file.getName())
            fileCount += 1

            # Check if module folder is present. If not, create it.
            dirName = os.path.join(Case.getCurrentCase().getTempDirectory(), "GPX_Parser_Module")
            try:
                os.stat(dirName)
            except:
                os.mkdir(dirName)
            fileName = os.path.join(dirName, "tmp.gpx")

            # Check to see if temporary file exists. If it does, remove it.
            if os.path.exists(fileName):
                try:
                    os.remove(fileName)
                    if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.INFO, "GPX:\t" + "FILE DELETED " + fileName )
                except:
                    if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.INFO, "GPX:\t" + "FILE NOT DELETED " + fileName)

            # This writes the file to the local file system.
            localFile = File(fileName)
            ContentUtils.writeToFile(file, localFile)

            # Send to gpxpy for parsing.
            gpxfile = open(fileName)
            try:
                gpx = gpxpy.parse(gpxfile)
                if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.INFO, "GPX:\t" + "FILE PARSED")
            except:
                if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.SEVERE, "GPX:\t" + file.getName() + " - FILE NOT PARSED")
                continue
            
            if gpx:
                if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.INFO, "GPX: TRACKS")
                for track in gpx.tracks:                
                    for segment in track.segments:
                        geoPointList = ArrayList()
                        for point in segment.points:
                            
                            elevation = 0
                            if point.elevation != None:
                                elevation = point.elevation
                                
                            dateTime = 0                               
                            try: 
                                if (point.time != None):
                                    datetime = long(time.mktime(point.time.timetuple()))                                    
                            except:                            
                                pass

                            geoPointList.add(GeoWaypoint.GeoTrackPoint(point.latitude, point.longitude, elevation, 0, 0, 0, dateTime))
                                                                                                             
                        try:
                        # Add the trackpoint using the helper class
                            geoartifact = geoArtifactHelper.addTrack("Trackpoint", geoPointList)
                        except Blackboard.BlackboardException as e:
                            if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.SEVERE, "GPX: Error using geo artifact helper with blackboard " )
                        except TskCoreException as e:
                            if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.SEVERE, "GPX: Error using geo artifact helper tskcoreexception" )
                            
                if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.INFO, "GPX: WAYPOINTS") 
                for waypoint in gpx.waypoints:
                    attributes = ArrayList()
                    art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK)

                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), moduleName, waypoint.latitude))
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), moduleName, waypoint.longitude))                    
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FLAG.getTypeID(), moduleName, "Waypoint"))
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), moduleName, waypoint.name))
                    attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), moduleName, "GPXParser"))

                    art.addAttributes(attributes)
                    
                    try:
                    # Post the artifact to blackboard
                       skCase.getBlackboard().postArtifact(art, moduleName)
                    except Blackboard.BlackboardException as e:
                        if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.SEVERE, "GPX: Error using geo artifact helper with blackboard  for waypoints" )

                if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.INFO, "GPX: ROUTES")
                for route in gpx.routes:    
                    firstTimeThru = 0
                    startingPoint = list()
                    endingPoint = list()
                    for point in route.points:
                        # If first time in loop only populate starting point
                        if (firstTimeThru == 0):
                            startingPoint.append((point.latitude, point.longitude))
                            firstTimeThru = 1
                        else:
                            startingPoint.append((point.latitude, point.longitude))
                            endingPoint.append((point.latitude, point.longitude))
                    
                    if (len(endingPoint) > 0):
                    # get length of ending point as this ensures that we have equal points to process.                            
                        for i in range(0,len(endingPoint) -1):
                            attributes = ArrayList()
                            art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE)
                         
                            attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START.getTypeID(), moduleName, startingPoint[i][0]))
                            attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START.getTypeID(), moduleName, startingPoint[i][1]))
                            attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END.getTypeID(), moduleName, endingPoint[i][0]))
                            attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END.getTypeID(), moduleName, endingPoint[i][1]))

                            art.addAttributes(attributes)
                        
                            try:
                            # Post the artifact to blackboard
                               skCase.getBlackboard().postArtifact(art, moduleName)
                            except Blackboard.BlackboardException as e:
                                if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.SEVERE, "GPX: Error using geo artifact helper with blackboard  for waypoints" )
                    else:
                        if (len(startingPoint) > 0):
                            attributes = ArrayList()
                            art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE)
                         
                            attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START.getTypeID(), moduleName, startingPoint[0][0]))
                            attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START.getTypeID(), moduleName, startingPoint[0][1]))
                            attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END.getTypeID(), moduleName, startingPoint[0][0]))
                            attributes.add(BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END.getTypeID(), moduleName, startingPoint[0][1]))

                            art.addAttributes(attributes)
                        
                            try:
                            # Post the artifact to blackboard
                               skCase.getBlackboard().postArtifact(art, moduleName)
                            except Blackboard.BlackboardException as e:
                                if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.SEVERE, "GPX: Error using geo artifact helper with blackboard  for waypoints" )

                                
            # Update the progress bar.
            progressBar.progress(fileCount)
            if os.path.exists(fileName):
                try:
                    os.remove(fileName)
                    if GPXParserDataSourceIngestModuleFactory.debuglevel: self.log(Level.INFO, "GPX:\t" + "FILE DELETED")
                except:
                    self.log(Level.SEVERE, "GPX:\t" + "FILE NOT DELETED")

        # Post a message to the ingest messages inbox.
        message = IngestMessage.createMessage(IngestMessage.MessageType.DATA, "GPX Parser Data Source Ingest Module", "Found %d files" % fileCount)
        IngestServices.getInstance().postMessage(message)
        return IngestModule.ProcessResult.OK;
