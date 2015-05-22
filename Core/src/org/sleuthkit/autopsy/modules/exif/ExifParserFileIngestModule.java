/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.exif;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.lang.Rational;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

/**
 * Ingest module to parse image Exif metadata. Currently only supports JPEG
 * files. Ingests an image file and, if available, adds it's date, latitude,
 * longitude, altitude, device model, and device make to a blackboard artifact.
 */
public final class ExifParserFileIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(ExifParserFileIngestModule.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private AtomicInteger filesProcessed = new AtomicInteger(0);
    private volatile boolean filesToFire = false;
    private long jobId;
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private FileTypeDetector fileTypeDetector;
        
    ExifParserFileIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {    
        jobId = context.getJobId();
        refCounter.incrementAndGet(jobId);
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            logger.log(Level.SEVERE, NbBundle.getMessage(this.getClass(), "ExifParserFileIngestModule.startUp.fileTypeDetectorInitializationException.msg"), ex);
            throw new IngestModuleException(NbBundle.getMessage(this.getClass(), "ExifParserFileIngestModule.startUp.fileTypeDetectorInitializationException.msg"));
        }
    }

    
    @Override
    public ProcessResult process(AbstractFile content) {
        //skip unalloc
        if (content.getType().equals(TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return ProcessResult.OK;
        }
        
        if (content.isFile() == false) {
            return ProcessResult.OK;
        }

        // skip known
        if (content.getKnown().equals(TskData.FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }

        // update the tree every 1000 files if we have EXIF data that is not being being displayed 
        final int filesProcessedValue = filesProcessed.incrementAndGet();
        if ((filesToFire) && (filesProcessedValue % 1000 == 0)) {
            services.fireModuleDataEvent(new ModuleDataEvent(ExifParserModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF));
            filesToFire = false;
        }

        //skip unsupported
        if (!parsableFormat(content)) {
            return ProcessResult.OK;
        }

        return processFile(content);
    }

    ProcessResult processFile(AbstractFile f) {
        InputStream in = null;
        BufferedInputStream bin = null;

        try {
            in = new ReadContentInputStream(f);
            bin = new BufferedInputStream(in);

            Collection<BlackboardAttribute> attributes = new ArrayList<>();
            Metadata metadata = ImageMetadataReader.readMetadata(bin, true);

            // Date
            ExifSubIFDDirectory exifDir = metadata.getDirectory(ExifSubIFDDirectory.class);
            if (exifDir != null) {
                Date date = exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (date != null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(), ExifParserModuleFactory.getModuleName(), date.getTime() / 1000));
                }
            }

            // GPS Stuff
            GpsDirectory gpsDir = metadata.getDirectory(GpsDirectory.class);
            if (gpsDir != null) {
                GeoLocation loc = gpsDir.getGeoLocation();
                if (loc != null) {
                    double latitude = loc.getLatitude();
                    double longitude = loc.getLongitude();
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), ExifParserModuleFactory.getModuleName(), latitude));
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), ExifParserModuleFactory.getModuleName(), longitude));
                }

                Rational altitude = gpsDir.getRational(GpsDirectory.TAG_GPS_ALTITUDE);
                if (altitude != null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID(), ExifParserModuleFactory.getModuleName(), altitude.doubleValue()));
                }
            }

            // Device info
            ExifIFD0Directory devDir = metadata.getDirectory(ExifIFD0Directory.class);
            if (devDir != null) {
                String model = devDir.getString(ExifIFD0Directory.TAG_MODEL);
                if (model != null && !model.isEmpty()) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(), ExifParserModuleFactory.getModuleName(), model));
                }

                String make = devDir.getString(ExifIFD0Directory.TAG_MAKE);
                if (make != null && !make.isEmpty()) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE.getTypeID(), ExifParserModuleFactory.getModuleName(), make));
                }
            }

            // Add the attributes, if there are any, to a new artifact
            if (!attributes.isEmpty()) {
                BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF);
                bba.addAttributes(attributes);
                filesToFire = true;
            }

            return ProcessResult.OK;
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to create blackboard artifact for exif metadata ({0}).", ex.getLocalizedMessage()); //NON-NLS
            return ProcessResult.ERROR;
        } catch (ImageProcessingException ex) {
            logger.log(Level.WARNING, "Failed to process the image file: {0}/{1}({2})", new Object[]{f.getParentPath(), f.getName(), ex.getLocalizedMessage()}); //NON-NLS
            return ProcessResult.ERROR;
        } catch (IOException ex) {
            logger.log(Level.WARNING, "IOException when parsing image file: " + f.getParentPath() + "/" + f.getName(), ex); //NON-NLS
            return ProcessResult.ERROR;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (bin != null) {
                    bin.close();
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to close InputStream.", ex); //NON-NLS
                return ProcessResult.ERROR;
            }
        }
    }

    /**
     * Checks if should try to attempt to extract exif. Currently checks if JPEG
     * image (by signature)
     *
     * @param f file to be checked
     *
     * @return true if to be processed
     */
    private boolean parsableFormat(AbstractFile f) {
        return fileTypeDetector.getFileType(f).equals("image/jpeg");
    }

    @Override
    public void shutDown() {
        // We only need to check for this final event on the last module per job
        if (refCounter.decrementAndGet(jobId) == 0) {
            if (filesToFire) {
                //send the final new data event
                services.fireModuleDataEvent(new ModuleDataEvent(ExifParserModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF));
            }
        }
    }
}