/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.pictureanalyzer.impls;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.pictureanalyzer.PictureAnalyzerIngestModuleFactory;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.modules.pictureanalyzer.spi.PictureProcessor;
import org.sleuthkit.datamodel.Score;

/**
 * Extracts EXIF metadata from JPEG, TIFF, and WAV files. Currently only date,
 * latitude, longitude, altitude, device model, and device make are extracted.
 *
 * User content suspected artifacts are also created by this processor.
 */
@ServiceProvider(service = PictureProcessor.class)
public class EXIFProcessor implements PictureProcessor {

    private static final Logger logger = Logger.getLogger(EXIFProcessor.class.getName());

    @Override
    @NbBundle.Messages({
        "ExifProcessor.userContent.description=EXIF metadata data exists for this file."
    })
    public void process(IngestJobContext context, AbstractFile file) {
        final String MODULE_NAME = PictureAnalyzerIngestModuleFactory.getModuleName();

        try (BufferedInputStream bin = new BufferedInputStream(new ReadContentInputStream(file));) {

            final Collection<BlackboardAttribute> attributes = new ArrayList<>();
            final Metadata metadata = ImageMetadataReader.readMetadata(bin);

            // Date
            final ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exifDir != null) {

                // set the timeZone for the current datasource.
                TimeZone timeZone = null;
                try {
                    Content dataSource = file.getDataSource();
                    if ((dataSource != null) && (dataSource instanceof Image)) {
                        Image image = (Image) dataSource;
                        timeZone = TimeZone.getTimeZone(image.getTimeZone());
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.INFO, "Error getting time zones", ex); //NON-NLS
                }

                final Date date = exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, timeZone);
                if (date != null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED, MODULE_NAME, date.getTime() / 1000));
                }
            }

            if (context.fileIngestIsCancelled()) {
                return;
            }

            // GPS Stuff
            final GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir != null) {
                final GeoLocation loc = gpsDir.getGeoLocation();
                if (loc != null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, MODULE_NAME, loc.getLatitude()));
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, MODULE_NAME, loc.getLongitude()));
                }

                final Rational altitude = gpsDir.getRational(GpsDirectory.TAG_ALTITUDE);
                if (altitude != null) {
                    double alt = altitude.doubleValue();
                    if (Double.isInfinite(alt)) {
                        alt = 0.0;
                    }
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE, MODULE_NAME, alt));
                }
            }

            if (context.fileIngestIsCancelled()) {
                return;
            }

            // Device info
            final ExifIFD0Directory devDir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (devDir != null) {
                final String model = devDir.getString(ExifIFD0Directory.TAG_MODEL);
                if (StringUtils.isNotBlank(model)) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL, MODULE_NAME, model));
                }

                final String make = devDir.getString(ExifIFD0Directory.TAG_MAKE);
                if (StringUtils.isNotBlank(make)) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE, MODULE_NAME, make));
                }
            }

            if (context.fileIngestIsCancelled()) {
                return;
            }

            final Blackboard blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();

            if (!attributes.isEmpty() && !blackboard.artifactExists(file, BlackboardArtifact.Type.TSK_METADATA_EXIF, attributes)) {
                List<BlackboardArtifact> artifacts = new ArrayList<>();
                final BlackboardArtifact exifArtifact = (file.newAnalysisResult(
                        BlackboardArtifact.Type.TSK_METADATA_EXIF,
                        Score.SCORE_NONE,
                        null, null, null,
                        attributes)).getAnalysisResult();
                artifacts.add(exifArtifact);

                final BlackboardArtifact userSuspectedArtifact = file.newAnalysisResult(
                        BlackboardArtifact.Type.TSK_USER_CONTENT_SUSPECTED,
                        Score.SCORE_UNKNOWN,
                        null, null, null,
                        Arrays.asList(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT,
                                MODULE_NAME,
                                Bundle.ExifProcessor_userContent_description())))
                        .getAnalysisResult();
                artifacts.add(userSuspectedArtifact);

                try {
                    blackboard.postArtifacts(artifacts, MODULE_NAME, context.getJobId());
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, String.format("Error posting TSK_METADATA_EXIF and TSK_USER_CONTENT_SUSPECTED artifacts for %s (object ID = %d)", file.getName(), file.getId()), ex); //NON-NLS
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error creating TSK_METADATA_EXIF and TSK_USER_CONTENT_SUSPECTED artifacts for %s (object ID = %d)", file.getName(), file.getId()), ex); //NON-NLS
        } catch (IOException | ImageProcessingException ex) {
            logger.log(Level.WARNING, String.format("Error parsing %s (object ID = %d), presumed corrupt", file.getName(), file.getId()), ex); //NON-NLS
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, String.format("Error processing %s (object ID = %d)", file.getName(), file.getId()), ex); //NON-NLS
        }
    }

    @Override
    public Set<String> mimeTypes() {
        return new HashSet<String>() {
            {
                add("audio/x-wav");
                add("image/jpeg");
                add("image/tiff");
            }
        };
    }
}
