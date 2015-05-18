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
package org.sleuthkit.autopsy.modules.imageExtractionParsing;

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
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.imageExtractionParsing.ImageExtractorParserFileIngestModule.SupportedParsingFormats;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

public class MetadataExtractor {
    
    private static final Logger logger = Logger.getLogger(ImageExtractorParserModuleFactory.class.getName());
    private boolean filesToFire;
    
    /**
     * Calls the appropriate extraction method in this class.
     * @param format name of the format as determined by tika.detect()
     * @param abstractFile the file from which the metadata is to be extracted.
     * @return Returns true if any metadata was extracted from the file. Else returns false.
     */
    public boolean extractMetadata(SupportedParsingFormats format, AbstractFile abstractFile) {
        switch (format) {
            case JPEG:
                return extractMetaDataFromJPEG(abstractFile);
            default:
                return false;
        }
    }

    /**
     * Extracts metadata from JPEG files and posts it to blackboard.
     * @param abstractFile
     * @return Returns true if any metadata was extracted from the file. Else returns false.
     */
    private boolean extractMetaDataFromJPEG(AbstractFile abstractFile) {
        InputStream in = null;
        BufferedInputStream bin = null;
        filesToFire = false;

        try {
            in = new ReadContentInputStream(abstractFile);
            bin = new BufferedInputStream(in);
            String log = abstractFile.getName() + "\t";

            Collection<BlackboardAttribute> attributes = new ArrayList<>();
            Metadata metadata = ImageMetadataReader.readMetadata(bin, true);

            // Date
            ExifSubIFDDirectory exifDir = metadata.getDirectory(ExifSubIFDDirectory.class);
            if (exifDir != null) {
                Date date = exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (date != null) {
                    log = log + "Date: " + date.toString() + "\t";
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(), ImageExtractorParserModuleFactory.getModuleName(), date.getTime() / 1000));
                }
            }

            // GPS Stuff
            GpsDirectory gpsDir = metadata.getDirectory(GpsDirectory.class);
            if (gpsDir != null) {
                GeoLocation loc = gpsDir.getGeoLocation();
                if (loc != null) {
                    double latitude = loc.getLatitude();
                    double longitude = loc.getLongitude();
                    log = log + "location: " + loc.toString() + "\t";
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), ImageExtractorParserModuleFactory.getModuleName(), latitude));
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), ImageExtractorParserModuleFactory.getModuleName(), longitude));
                }

                Rational altitude = gpsDir.getRational(GpsDirectory.TAG_GPS_ALTITUDE);
                if (altitude != null) {
                    log = log + "altitude: " + altitude.toString() + "\t";
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID(), ImageExtractorParserModuleFactory.getModuleName(), altitude.doubleValue()));
                }
            }

            // Device info
            ExifIFD0Directory devDir = metadata.getDirectory(ExifIFD0Directory.class);
            if (devDir != null) {
                String model = devDir.getString(ExifIFD0Directory.TAG_MODEL);
                if (model != null && !model.isEmpty()) {
                    log = log + "cam model: " + model + "\t";
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(), ImageExtractorParserModuleFactory.getModuleName(), model));
                }

                String make = devDir.getString(ExifIFD0Directory.TAG_MAKE);
                if (make != null && !make.isEmpty()) {
                    log = log + "make: " + make + "\t";
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE.getTypeID(), ImageExtractorParserModuleFactory.getModuleName(), make));
                }
            }

            // Add the attributes, if there are any, to a new artifact
            if (!attributes.isEmpty()) {
                BlackboardArtifact bba = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF);
                bba.addAttributes(attributes);
                this.filesToFire = true;
            }

            // TODO Remove this logger.log()
            logger.log(Level.INFO, log);

            return filesToFire;
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to create blackboard artifact for exif metadata ({0}).", ex.getLocalizedMessage()); //NON-NLS
            return filesToFire;
        } catch (ImageProcessingException ex) {
            logger.log(Level.WARNING, "Failed to process the image file: {0}/{1}({2})", new Object[]{abstractFile.getParentPath(), abstractFile.getName(), ex.getLocalizedMessage()}); //NON-NLS
            return filesToFire;
        } catch (IOException ex) {
            logger.log(Level.WARNING, "IOException when parsing image file: " + abstractFile.getParentPath() + "/" + abstractFile.getName(), ex); //NON-NLS
            return filesToFire;
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
            }
        }
    }
    
}