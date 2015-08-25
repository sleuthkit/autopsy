/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.makernotes.CasioType1MakernoteDirectory;
import com.drew.metadata.exif.makernotes.FujifilmMakernoteDirectory;
import com.drew.metadata.exif.makernotes.KodakMakernoteDirectory;
import com.drew.metadata.exif.makernotes.NikonType2MakernoteDirectory;
import com.drew.metadata.exif.makernotes.PanasonicMakernoteDirectory;
import com.drew.metadata.exif.makernotes.PentaxMakernoteDirectory;
import com.drew.metadata.exif.makernotes.SanyoMakernoteDirectory;
import com.drew.metadata.exif.makernotes.SonyType1MakernoteDirectory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
    private final AtomicInteger filesProcessed = new AtomicInteger(0);
    private volatile boolean filesToFire = false;
    private volatile boolean facesDetected = false;
    private final List<BlackboardArtifact> listOfFacesDetectedArtifacts = new ArrayList<>();
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
        if ((filesProcessedValue % 1000 == 0)) {
            if (filesToFire) {
                services.fireModuleDataEvent(new ModuleDataEvent(ExifParserModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF));
                filesToFire = false;
            }
            if (facesDetected) {
                services.fireModuleDataEvent(new ModuleDataEvent(ExifParserModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_FACE_DETECTED, listOfFacesDetectedArtifacts));
                listOfFacesDetectedArtifacts.removeAll(listOfFacesDetectedArtifacts);
                facesDetected = false;
            }
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
            Metadata metadata = ImageMetadataReader.readMetadata(bin);

            // Date
            ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exifDir != null) {
                Date date = exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (date != null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(), ExifParserModuleFactory.getModuleName(), date.getTime() / 1000));
                }
            }

            // GPS Stuff
            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir != null) {
                GeoLocation loc = gpsDir.getGeoLocation();
                if (loc != null) {
                    double latitude = loc.getLatitude();
                    double longitude = loc.getLongitude();
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), ExifParserModuleFactory.getModuleName(), latitude));
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), ExifParserModuleFactory.getModuleName(), longitude));
                }

                Rational altitude = gpsDir.getRational(GpsDirectory.TAG_ALTITUDE);
                if (altitude != null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID(), ExifParserModuleFactory.getModuleName(), altitude.doubleValue()));
                }
            }

            // Device info
            ExifIFD0Directory devDir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
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

            if (containsFace(metadata)) {
                listOfFacesDetectedArtifacts.add(f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_FACE_DETECTED));
                facesDetected = true;
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
     * Checks if this metadata contains any tags related to facial information.
     * NOTE: Cases with this metadata containing tags like enabled red-eye
     * reduction settings, portrait settings, etc are also assumed to contain
     * facial information. The method returns true. The return value of this
     * method does NOT guarantee actual presence of face.
     *
     * @param metadata the metadata which needs to be parsed for possible facial
     *                 information.
     *
     * @return returns true if the metadata contains any tags related to facial
     *         information.
     */
    private boolean containsFace(Metadata metadata) {
        Directory d = metadata.getFirstDirectoryOfType(CanonMakernoteDirectory.class);
        if (d != null) {
            if (d.containsTag(CanonMakernoteDirectory.TAG_FACE_DETECT_ARRAY_1)
                    && d.getString(CanonMakernoteDirectory.TAG_FACE_DETECT_ARRAY_1) != null) {
                return true;
            }
            if (d.containsTag(CanonMakernoteDirectory.TAG_FACE_DETECT_ARRAY_2)
                    && d.getString(CanonMakernoteDirectory.TAG_FACE_DETECT_ARRAY_2) != null) {
                return true;
            }
        }

        d = metadata.getFirstDirectoryOfType(CasioType1MakernoteDirectory.class);
        if (d != null) {
            try {
                if (d.containsTag(CasioType1MakernoteDirectory.TAG_FLASH_MODE)
                        && d.getInt(CasioType1MakernoteDirectory.TAG_FLASH_MODE) == 0x04) { //0x04 = "Red eye reduction"
                    return true;
                }
            } catch (MetadataException ex) {
                // move on and check next directory
            }
        }

        d = metadata.getFirstDirectoryOfType(FujifilmMakernoteDirectory.class);
        if (d != null) {
            if (d.containsTag(FujifilmMakernoteDirectory.TAG_FACES_DETECTED)
                    && d.getString(FujifilmMakernoteDirectory.TAG_FACES_DETECTED) != null) {
                return true;
            }
        }

        d = metadata.getFirstDirectoryOfType(KodakMakernoteDirectory.class);
        if (d != null) {
            try {
                if (d.containsTag(KodakMakernoteDirectory.TAG_FLASH_MODE)
                        && d.getInt(KodakMakernoteDirectory.TAG_FLASH_MODE) == 0x03) { //0x03 = "Red Eye"
                    return true;
                }
            } catch (MetadataException ex) {
                /// move on and check next directory
            }
        }

        d = metadata.getFirstDirectoryOfType(NikonType2MakernoteDirectory.class);
        if (d != null) {
            if (d.containsTag(NikonType2MakernoteDirectory.TAG_SCENE_MODE)
                    && d.getString(NikonType2MakernoteDirectory.TAG_SCENE_MODE) != null
                    && (d.getString(NikonType2MakernoteDirectory.TAG_SCENE_MODE).equals("BEST FACE") // NON-NLS
                    || (d.getString(NikonType2MakernoteDirectory.TAG_SCENE_MODE).equals("SMILE")))) { // NON-NLS
                return true;
            }
        }

        d = metadata.getFirstDirectoryOfType(PanasonicMakernoteDirectory.class);
        if (d != null) {
            if (d.containsTag(PanasonicMakernoteDirectory.TAG_FACES_DETECTED)
                    && d.getString(PanasonicMakernoteDirectory.TAG_FACES_DETECTED) != null) {
                return true;
            }
        }

        d = metadata.getFirstDirectoryOfType(PentaxMakernoteDirectory.class);
        if (d != null) {
            try {
                if (d.containsTag(PentaxMakernoteDirectory.TAG_FLASH_MODE)
                        && d.getInt(PentaxMakernoteDirectory.TAG_FLASH_MODE) == 6) { // 6 = Red-eye Reduction
                    return true;
                }
            } catch (MetadataException ex) {
                // move on and check next directory
            }
        }

        d = metadata.getFirstDirectoryOfType(SanyoMakernoteDirectory.class);
        if (d != null) {
            if (d.containsTag(SanyoMakernoteDirectory.TAG_MANUAL_FOCUS_DISTANCE_OR_FACE_INFO)
                    && d.getString(SanyoMakernoteDirectory.TAG_MANUAL_FOCUS_DISTANCE_OR_FACE_INFO) != null) {
                return true;
            }
        }

        d = metadata.getFirstDirectoryOfType(SonyType1MakernoteDirectory.class);
        if (d != null) {
            try {
                if (d.containsTag(SonyType1MakernoteDirectory.TAG_AF_MODE)
                        && d.getInt(SonyType1MakernoteDirectory.TAG_AF_MODE) == 15) { //15 = "Face Detected"
                    return true;
                }
                if (d.containsTag(SonyType1MakernoteDirectory.TAG_EXPOSURE_MODE)
                        && d.getInt(SonyType1MakernoteDirectory.TAG_EXPOSURE_MODE) == 14) { //14 = "Smile shutter"
                    return true;
                }
            } catch (MetadataException ex) {
                // move on and check next directory
            }
        }

        return false;
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
        try {
            String mimeType = fileTypeDetector.getFileType(f);
            if (mimeType != null) {
                return fileTypeDetector.getFileType(f).equals("image/jpeg");
            } else {
                return false;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to detect file type", ex); //NON-NLS
            return false;
        }
    }

    @Override
    public void shutDown() {
        // We only need to check for this final event on the last module per job
        if (refCounter.decrementAndGet(jobId) == 0) {
            if (filesToFire) {
                //send the final new data event
                services.fireModuleDataEvent(new ModuleDataEvent(ExifParserModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF));
            }
            if (facesDetected) {
                //send the final new data event
                services.fireModuleDataEvent(new ModuleDataEvent(ExifParserModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_FACE_DETECTED, listOfFacesDetectedArtifacts));
            }
        }
    }
}
