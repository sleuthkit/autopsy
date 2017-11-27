/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parser for idx files and md5sum files (*.idx or *.txt) This parsers lines
 * that start with md5 hashes and ignores any others
 */
class IdxHashSetParser implements HashSetParser {

    private final String filename;        // Name of the input file (saved for logging)
    private BufferedReader reader;        // Input file
    private final long totalHashes;       // Estimated number of hashes
    private boolean doneReading = false;  // Flag for if we've hit the end of the file

    IdxHashSetParser(String filename) throws TskCoreException {
        this.filename = filename;
        try {
            reader = new BufferedReader(new FileReader(filename));
        } catch (FileNotFoundException ex) {
            throw new TskCoreException("Error opening file " + filename, ex);
        }

        // Estimate the total number of hashes in the file since counting them all can be slow
        File importFile = new File(filename);
        long fileSize = importFile.length();
        totalHashes = fileSize / 0x33 + 1; // IDX file lines are generally 0x33 bytes long. We add one to prevent this from being zero
                                           // MD5sum output lines should be close enough to that (0x20 byte hash + filename)
    }

    /**
     * Get the next hash to import
     *
     * @return The hash as a string, or null if the end of file was reached
     * without error
     * @throws TskCoreException
     */
    @Override
    public String getNextHash() throws TskCoreException {
        String line;

        try {
            while ((line = reader.readLine()) != null) {

                // idx files have a pipe after the hash, md5sum files should have a space
                String[] parts = line.split("\\|| ");

                String hashStr = parts[0].toLowerCase();
                if (!hashStr.matches("^[0-9a-f]{32}$")) {
                    continue;
                }

                return hashStr;
            }
        } catch (IOException ex) {
            throw new TskCoreException("Error reading file " + filename, ex);
        }

        // We've run out of data
        doneReading = true;
        return null;
    }

    /**
     * Check if there are more hashes to read
     *
     * @return true if we've read all expected hash values, false otherwise
     */
    @Override
    public boolean doneReading() {
        return doneReading;
    }

    /**
     * Get the expected number of hashes in the file. This number can be an
     * estimate.
     *
     * @return The expected hash count
     */
    @Override
    public long getExpectedHashCount() {
        return totalHashes;
    }

    /**
     * Closes the import file
     */
    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException ex) {
            Logger.getLogger(IdxHashSetParser.class.getName()).log(Level.SEVERE, "Error closing file " + filename, ex);
        }
    }
}
