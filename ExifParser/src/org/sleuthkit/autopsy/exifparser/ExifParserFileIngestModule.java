/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.exifparser;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.lang.Rational;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstract;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

/**
 * Ingest module to parse image Exif metadata. Currently only supports JPEG files.
 * Ingests an image file and, if available, adds it's date, latitude, longitude,
 * altitude, device model, and device make to a blackboard artifact.
 */
public final class ExifParserFileIngestModule implements IngestModuleAbstractFile {

    private IngestServices services;
    
    final public static String MODULE_NAME = "Exif Parser";
    final public static String MODULE_VERSION = "1.0";
    
    private String args;
    
    private static final Logger logger = Logger.getLogger(ExifParserFileIngestModule.class.getName());
    private static ExifParserFileIngestModule defaultInstance = null;
    private static int messageId = 0;

    //file ingest modules require a private constructor
    //to ensure singleton instances
    private ExifParserFileIngestModule() {
       
    }

    //default instance used for module registration
    public static synchronized ExifParserFileIngestModule getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new ExifParserFileIngestModule();
        }
        return defaultInstance;
    }

    @Override
    public IngestModuleAbstractFile.ProcessResult process(AbstractFile content) {
        
        //skip unalloc
        if(content.getType().equals(TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return IngestModuleAbstractFile.ProcessResult.OK;
        }
        
        return processFile(content);
    }
    
    public IngestModuleAbstractFile.ProcessResult processFile(AbstractFile f) {
        InputStream in = null;
        BufferedInputStream bin = null;
        
        try {
            in = new ReadContentInputStream(f);
            bin = new BufferedInputStream(in);
            
            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();
            Metadata metadata = ImageMetadataReader.readMetadata(bin, true);
            
            // Date
            ExifSubIFDDirectory exifDir = metadata.getDirectory(ExifSubIFDDirectory.class);
            if(exifDir != null) {
                Date date = exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if(date != null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), MODULE_NAME, date.getTime()/1000));
                }
            }
            
            // GPS Stuff
            GpsDirectory gpsDir = metadata.getDirectory(GpsDirectory.class);
            
            if(gpsDir != null) {
                Rational altitude = gpsDir.getRational(GpsDirectory.TAG_GPS_ALTITUDE);
                GeoLocation loc = gpsDir.getGeoLocation();
                if(loc!=null) {
                    double latitude = loc.getLatitude();
                    double longitude = loc.getLongitude();
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), MODULE_NAME, latitude));
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), MODULE_NAME, longitude));
                }
                if(altitude!=null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID(), MODULE_NAME, altitude.doubleValue()));
                }
            }
           
            
            // Device info
            ExifIFD0Directory devDir = metadata.getDirectory(ExifIFD0Directory.class);
            if(devDir != null) {
                String model = devDir.getString(ExifIFD0Directory.TAG_MODEL);
                String make = devDir.getString(ExifIFD0Directory.TAG_MAKE);
                
                if(model!=null && !model.isEmpty()) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(), MODULE_NAME, model));
                } if(make!=null && !make.isEmpty()) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE.getTypeID(), MODULE_NAME, make));
                }
            }
            
            // Add the attributes, if there are any, to a new artifact
            if(!attributes.isEmpty()) {
                BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF);
                bba.addAttributes(attributes);
                services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF));
            }
            
            return IngestModuleAbstractFile.ProcessResult.OK;
            
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to create blackboard artifact for exif metadata (" + ex.getLocalizedMessage() + ").");
        } catch (ImageProcessingException ex) {
            logger.log(Level.WARNING, "Failed to process the image file: " + f.getName() + "(" + ex.getLocalizedMessage() + ")");
        } catch (IOException ex) {
            logger.log(Level.WARNING, "IOException when parsing image file: " +  f.getName(), ex);
        } finally {
            try {
                if(in!=null) { in.close(); }
                if(bin!=null) { bin.close(); }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to close InputStream.", ex);
            }
        }
        
        // If we got here, there was an error
        return IngestModuleAbstractFile.ProcessResult.ERROR;
    }
    
    private boolean parsableFormat(FsContent f) {
        // Get the name, extension
        String name = f.getName();
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex == -1) {
            return false;
        }
        String ext = name.substring(dotIndex).toLowerCase();
        if(ext.equals(".jpeg") || ext.equals(".jpg")) {
            return true;
        }
        
        return false;
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "completed exif parsing " + this.toString());

        //module specific cleanup due to completion here
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public String getArguments() {
        return args;
    }

    @Override
    public void setArguments(String args) {
        this.args = args;
    }

    
    
    @Override
    public String getName() {
        return "Exif Image Parser";
    }
    
    @Override
    public String getDescription() {
        return "Ingests .jpg and .jpeg files and retrieves their metadata.";
    }

    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
        logger.log(Level.INFO, "init() " + this.toString());
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");

        //module specific cleanup due to interruption here
    }

    @Override
    public IngestModuleAbstract.ModuleType getType() {
        return IngestModuleAbstract.ModuleType.AbstractFile;
    }

     @Override
    public boolean hasSimpleConfiguration() {
        return false;
    }
    
    @Override
    public boolean hasAdvancedConfiguration() {
        return false;
    }

    @Override
    public javax.swing.JPanel getSimpleConfiguration() {
        return null;
    }
    
    @Override
    public javax.swing.JPanel getAdvancedConfiguration() {
        return null;
    }
    
    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
    
    @Override
    public void saveAdvancedConfiguration() {
    }
    
    @Override
    public void saveSimpleConfiguration() {
    }
}