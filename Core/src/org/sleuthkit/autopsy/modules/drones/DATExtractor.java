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
package org.sleuthkit.autopsy.modules.drones;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.blackboardutils.GeoArtifactsHelper;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.blackboardutils.attributes.GeoTrackPoints;
import org.sleuthkit.datamodel.blackboardutils.attributes.GeoTrackPoints.TrackPoint;

/**
 * Extract drone position data from DJI Phantom drones.
 *
 * Module uses DatCon.jar to dump FLYXXX.DAT file to a CSV file which is stored
 * in the case temp directory. Artifacts are created by parsing the csv file.
 *
 */
final class DATExtractor extends DroneExtractor {

    private static final Logger logger = Logger.getLogger(DATExtractor.class.getName());

    private static final String HEADER_LONG = "IMU_ATTI(0):Longitude";  //NON-NLS
    private static final String HEADER_LAT = "IMU_ATTI(0):Latitude"; //NON-NLS
    private static final String HEADER_VELOCITY = "IMU_ATTI(0):velComposite"; //NON-NLS
    private static final String HEADER_DATETILE = "GPS:dateTimeStamp"; //NON-NLS
    private static final String HEADER_ALTITUDE = "GPS(0):heightMSL"; //NON-NLS
    private static final String HEADER_DISTANCE_FROM_HP = "IMU_ATTI(0):distanceHP"; //NON-NLS
    private static final String HEADER_DISTANCE_TRAVELED = "IMU_ATTI(0):distanceTravelled"; //NON-NLS

    /**
     * Construct a DATExtractor.
     *
     * @throws DroneIngestException
     */
    DATExtractor() throws DroneIngestException {
        super();
    }

    @Messages({
        "DATExtractor_process_message=Processing DJI DAT file: %s"
    })
    @Override
    void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) throws DroneIngestException {
        List<AbstractFile> datFiles = findDATFiles(dataSource);

        DATDumper dumper = new DATDumper();

        try {
            for (AbstractFile DATFile : datFiles) {
                if (context.dataSourceIngestIsCancelled()) {
                    break;
                }

                progressBar.progress(String.format(Bundle.DATExtractor_process_message(), DATFile.getName()));

                // Copy the DAT file into the case temp folder
                File tempDATFile = getTemporaryFile(context, DATFile);

                // Create a path for the csv file
                String csvFilePath = getCSVPathForDAT(DATFile);

                try {
                    if (!dumper.isDATFile(tempDATFile.getAbsolutePath())) {
                        logger.log(Level.WARNING, String.format("%s is not a valid DAT file", DATFile.getName())); //NON-NLS
                        continue;
                    }
                    // Dump the DAT file to a csv file
                    dumper.dumpDATFile(tempDATFile.getAbsolutePath(), csvFilePath, true);

                    if (context.dataSourceIngestIsCancelled()) {
                        break;
                    }

                    // Process the csv file
                    GeoTrackPoints trackPoints = processCSVFile(context, DATFile, csvFilePath);

                    if (trackPoints != null && !trackPoints.isEmpty()) {
                        (new GeoArtifactsHelper(getSleuthkitCase(), getName(), "DatCon", DATFile, context.getJobId())).addTrack(DATFile.getName(), trackPoints, null);
                    } else {
                        logger.log(Level.INFO, String.format("No trackpoints with valid longitude or latitude found in %s", DATFile.getName())); //NON-NLS
                    }

                } catch (TskCoreException | BlackboardException ex) {
                    logger.log(Level.WARNING, String.format("Exception thrown while processing DAT file %s", DATFile.getName()), ex); //NON-NLS
                } finally {
                    tempDATFile.delete();
                    (new File(csvFilePath)).delete();
                }
            }
        } finally {
            FileUtil.deleteDir(getExtractorTempPath().toFile());
        }
    }

    @NbBundle.Messages({
        "DATFileExtractor_Extractor_Name=DAT File Extractor"
    })

    @Override
    String getName() {
        return Bundle.DATFileExtractor_Extractor_Name();
    }

    /**
     * Find any files that have the file name FLYXXX.DAT where the X are digit
     * characters.
     *
     * @param dataSource Data source to search
     *
     * @return List of found files or empty list if none where found
     *
     * @throws DroneIngestException
     */
    private List<AbstractFile> findDATFiles(Content dataSource) throws DroneIngestException {
        List<AbstractFile> fileList = new ArrayList<>();

        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        // findFiles use the SQL wildcard # in the file name
        try {
            fileList = fileManager.findFiles(dataSource, "FLY___.DAT"); //NON-NLS
        } catch (TskCoreException ex) {
            throw new DroneIngestException("Unable to find drone DAT files.", ex); //NON-NLS
        }

        return fileList;
    }

    /**
     * Return an absolute path for the given DAT file.
     *
     * @param file DAT file
     *
     * @return Absolute csv file path
     */
    private String getCSVPathForDAT(AbstractFile file) {
        String tempFileName = file.getName() + file.getId() + ".csv"; //NON-NLS
        return Paths.get(getExtractorTempPath().toString(), tempFileName).toString();
    }

    /**
     * Process the csv dump of the drone DAT file.
     *
     * Create artifacts for all rows that have a valid longitude and latitude.
     *
     * @param context     current case job context
     * @param DATFile     Original DAT file
     * @param csvFilePath Path of csv file to process
     *
     * @throws DroneIngestException
     */
    private GeoTrackPoints processCSVFile(IngestJobContext context, AbstractFile DATFile, String csvFilePath) throws DroneIngestException {
        GeoTrackPoints trackPoints = new GeoTrackPoints();
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(csvFilePath)))) {
            // First read in the header line and process
            String line = reader.readLine();
            Map<String, Integer> headerMap = makeHeaderMap(line.split(",")); //NON-NLS

            while ((line = reader.readLine()) != null) {
                if (context.dataSourceIngestIsCancelled()) {
                    return null;
                }

                String[] values = line.split(","); //NON-NLS
                TrackPoint point = createTrackPoint(headerMap, values);
                if (point != null) {
                    trackPoints.addPoint(point);
                }
            }

        } catch (IOException ex) {
            throw new DroneIngestException(String.format("Failed to read DAT csvFile %s created for AbstractFile: %s", csvFilePath, DATFile.getId()), ex); //NON-NLS
        }

        return trackPoints;
    }

    /**
     * Create a lookup to quickly match the column header with its array index
     *
     * @param headers
     *
     * @return Map of column names with the column index
     */
    private Map<String, Integer> makeHeaderMap(String[] headers) {
        Map<String, Integer> map = new HashMap<>();

        for (int index = 0; index < headers.length; index++) {
            map.put(headers[index], index);

        }

        return map;
    }

    /**
     * Returns a list of BlackboardAttributes generated from the String array.
     *
     * If longitude and latitude are not valid, assume we the row is not
     * interesting and return a null collection.
     *
     * @param columnLookup column header lookup map
     * @param values       Row data
     *
     * @return Collection of BlackboardAttributes for row or null collection if
     *         longitude or latitude was not valid
     *
     * @throws DroneIngestException
     */
    private TrackPoint createTrackPoint(Map<String, Integer> columnLookup, String[] values) throws DroneIngestException {

        Double latitude = getDoubleValue(columnLookup.get(HEADER_LAT), values);
        Double longitude = getDoubleValue(columnLookup.get(HEADER_LONG), values);

        if (longitude == null || latitude == null) {
            // Assume the row is not valid\has junk
            return null;
        }

        return new TrackPoint(latitude,
                longitude,
                getDoubleValue(columnLookup.get(HEADER_ALTITUDE), values),
                null,
                getDoubleValue(columnLookup.get(HEADER_VELOCITY), values),
                getDoubleValue(columnLookup.get(HEADER_DISTANCE_FROM_HP), values),
                getDoubleValue(columnLookup.get(HEADER_DISTANCE_TRAVELED), values),
                getDateTimeValue(columnLookup, values));
    }

    /**
     * Returns the waypoint timestamp in java/unix epoch seconds.
     *
     * The format of the date time string is 2016-09-26T18:26:19Z.
     *
     * @param headerMap
     * @param values
     *
     * @return Epoch seconds or null if no dateTime value was found
     */
    private Long getDateTimeValue(Map<String, Integer> headerMap, String[] values) {
        Integer index = headerMap.get(HEADER_DATETILE);
        if (index == null || index == -1 || index > values.length) {
            return null;
        }

        String value = values[index];
        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            ZonedDateTime zdt = ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
            return zdt.toLocalDateTime().toEpochSecond(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * Returns the string value at the given index parsed as a double.
     *
     * @param index  Index to string array
     * @param values Array of string values
     *
     * @return Double value or null if the index is out of bounds of the string
     *         array or the string value at index was not a double.
     */
    private Double getDoubleValue(Integer index, String[] values) {
        if (index == null || index == -1 || index > values.length) {
            return null;
        }

        String value = values[index];
        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
