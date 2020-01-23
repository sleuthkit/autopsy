/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import src.Files.ConvertDat;
import src.Files.CsvWriter;
import src.Files.DatFile;
import src.Files.DJIAssistantFile;
import src.Files.Exception.FileEnd;
import src.Files.Exception.NotDatFile;

/**
 * Dump DJI DAT files to csv file using the DatCon.jar.
 *
 */
final class DATDumper {

    /**
     * Construct a DATDumper.
     */
    DATDumper() {
        // This constructor is intentionally empty. Nothing special is needed here.
    }

    /**
     * Dump the DJI DAT file to a csv file.
     *
     * @param datFilePath       Path to input DAT file
     * @param outputFilePath    Output file path
     * @param overWriteExisting True to overwrite an existing csv file with
     *                          outputFilePath
     *
     * @throws DroneIngestException
     */
    void dumpDATFile(String datFilePath, String outputFilePath, boolean overWriteExisting) throws DroneIngestException {
        // Validate the input and output file paths.
        validateOutputFile(outputFilePath, overWriteExisting);
        if (!isDATFile(datFilePath)) {
            throw new DroneIngestException(String.format("Not a DAT file!  DAT = %s", datFilePath)); //NON-NLS
        }

        DatFile datFile = null;
        try (CsvWriter writer = new CsvWriter(outputFilePath)) {
            // Creates a version specific DatFile object
            datFile = DatFile.createDatFile(datFilePath);
            datFile.reset();
            // preAnalyze does an inital pass of the DAT file to gather some
            // information about the file.
            datFile.preAnalyze();

            // Creates a version specific ConvertDat object
            ConvertDat convertDat = datFile.createConVertDat();

            // The lower the sample rate the smaller the output csv file will be 
            // however the date will be less precise. For our purposes we are going 
            // a sample rate of 1.
            convertDat.sampleRate = 1;

            // Setting the tickRangeLower and upper values reduces some of the 
            // noise invalid data in the output file.
            if (datFile.gpsLockTick != -1) {
                convertDat.tickRangeLower = datFile.gpsLockTick;
            }

            if (datFile.lastMotorStopTick != -1) {
                convertDat.tickRangeUpper = datFile.lastMotorStopTick;
            }

            convertDat.setCsvWriter(writer);
            convertDat.createRecordParsers();
            datFile.reset();

            // Analyze does the work of parsing the data, everything prior was
            // setup
            convertDat.analyze(true);

        } catch (IOException | NotDatFile | FileEnd ex) {
            throw new DroneIngestException(String.format("Failed to dump DAT file to csv.  DAT = %s, CSV = %s", datFilePath, outputFilePath), ex); //NON-NLS
        } finally {
            if (datFile != null) {
                datFile.close();
            }
        }
    }

    /**
     * Validate that if the given csv file exists that the overWriteExsiting
     * param is true. Throws an exception if the file exists and
     * overWriteExisting is false.
     *
     * @param outputFileName    Absolute path for the output csv file
     * @param overWriteExisting True to over write an existing file.
     *
     * @throws DroneIngestException Throws exception if overWriteExisting is
     *                              true and outputFileName exists
     */
    private void validateOutputFile(String outputFileName, boolean overWriteExisting) throws DroneIngestException {
        File csvFile = new File(outputFileName);

        if (csvFile.exists()) {
            if (overWriteExisting) {
                csvFile.delete();
            } else {
                throw new DroneIngestException(String.format("Unable to dump DAT file. overWriteExsiting is false and DAT output csv file exists: %s", outputFileName)); //NON-NLS
            }
        }
    }

    /**
     * Validate that the DAT file exists and it is in a known format that can be
     * dumped.
     *
     * @param datFilePath Absolute path to DAT file
     *
     * @throws DroneIngestException
     */
    public boolean isDATFile(String datFilePath) throws DroneIngestException {
        File datFile = new File(datFilePath);

        if (!datFile.exists()) {
            throw new DroneIngestException(String.format("Unable to dump DAT file DAT file does not exist: %s", datFilePath)); //NON-NLS
        }

        try {
            return DatFile.isDatFile(datFilePath) || DJIAssistantFile.isDJIDat(datFile);
        } catch (FileNotFoundException ex) {
            throw new DroneIngestException(String.format("Unable to dump DAT file. File not found %s", datFilePath), ex); //NON-NLS
        }
    }
}
