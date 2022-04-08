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

import java.io.File;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.Iterator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parser for Hashkeeper hash sets (*.hsh)
 */
public class HashkeeperHashSetParser implements HashSetParser {

    private String filename;
    private InputStreamReader inputStreamReader;
    private CSVParser csvParser;
    private final long expectedHashCount;  // Number of hashes we expect to read from the file
    private final Iterator<CSVRecord> recordIterator;
    private final int hashColumnIndex;     // The index of the hash column

    HashkeeperHashSetParser(String filename) throws TskCoreException {
        this.filename = filename;

        try {
            // Estimate the total number of hashes in the file
            File importFile = new File(filename);
            long fileSize = importFile.length();
            expectedHashCount = fileSize / 75 + 1; // As a rough estimate, assume 75 bytes per line. We add one to prevent this from being zero

            // Create the parser
            inputStreamReader = new InputStreamReader(new FileInputStream(filename)); //NON-NLS
            
            csvParser = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).build().parse(inputStreamReader);
            
            if (!csvParser.getHeaderMap().keySet().contains("hash")) {
                close();
                throw new TskCoreException("Hashkeeper file format invalid - does not contain 'hash' column");
            }

            // For efficiency, store the index of the hash column
            hashColumnIndex = csvParser.getHeaderMap().get("hash");

            // Make an iterator to loop over the entries
            recordIterator = csvParser.getRecords().listIterator();

            // We're ready to use recordIterator to get each hash
        } catch (IOException ex) {
            close();
            throw new TskCoreException("Error reading " + filename, ex);
        }
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
        if (recordIterator.hasNext()) {
            CSVRecord record = recordIterator.next();
            String hash = record.get(hashColumnIndex);

            if (hash.length() != 32) {
                throw new TskCoreException("Hash has incorrect length: " + hash);
            }

            return (hash);
        }
        return null;
    }

    /**
     * Check if there are more hashes to read
     *
     * @return true if we've read all expected hash values, false otherwise
     */
    @Override
    public boolean doneReading() {
        return (!recordIterator.hasNext());
    }

    /**
     * Get the expected number of hashes in the file. This number can be an
     * estimate.
     *
     * @return The expected hash count
     */
    @Override
    public long getExpectedHashCount() {
        return expectedHashCount;
    }

    /**
     * Closes the import file
     */
    @Override
    public final void close() {
        if (inputStreamReader != null) {
            try {
                inputStreamReader.close();
            } catch (IOException ex) {
                Logger.getLogger(HashkeeperHashSetParser.class.getName()).log(Level.SEVERE, "Error closing Hashkeeper hash set " + filename, ex);
            } finally {
                inputStreamReader = null;
            }
        }
    }
}
