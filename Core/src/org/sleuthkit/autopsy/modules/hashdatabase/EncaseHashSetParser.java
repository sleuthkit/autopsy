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

import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parser for Encase format hash sets (*.hash)
 */
class EncaseHashSetParser implements HashSetParser {

    private final byte[] encaseHeader = {(byte) 0x48, (byte) 0x41, (byte) 0x53, (byte) 0x48, (byte) 0x0d, (byte) 0x0a, (byte) 0xff, (byte) 0x00,
        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00};
    private final String filename;         // Name of the input file (saved for logging)
    private InputStream inputStream;       // File stream for file being imported
    private final long expectedHashCount;  // Number of hashes we expect to read from the file
    private int totalHashesRead = 0;       // Number of hashes that have been read

    /**
     * Opens the import file and parses the header. If this is successful, the
     * file will be set up to call getNextHash() to read the hash values.
     *
     * @param filename The Encase hash set
     * @throws TskCoreException There was an error opening/reading the file or
     * it is not the correct format
     */
    EncaseHashSetParser(String filename) throws TskCoreException {
        try {
            this.filename = filename;
            inputStream = new BufferedInputStream(new FileInputStream(filename));

            // Read in and test the 16 byte header
            byte[] header = new byte[16];
            readBuffer(header, 16);
            if (!Arrays.equals(header, encaseHeader)) {
                close();
                throw new TskCoreException("File " + filename + " does not have an Encase header");
            }

            // Read in the expected number of hashes (little endian)
            byte[] sizeBuffer = new byte[4];
            readBuffer(sizeBuffer, 4);
            expectedHashCount = ((sizeBuffer[3] & 0xff) << 24) | ((sizeBuffer[2] & 0xff) << 16)
                    | ((sizeBuffer[1] & 0xff) << 8) | (sizeBuffer[0] & 0xff);

            // Read in a bunch of nulls
            byte[] filler = new byte[0x3f4];
            readBuffer(filler, 0x3f4);

            // Read in the hash set name
            byte[] nameBuffer = new byte[0x50];
            readBuffer(nameBuffer, 0x50);

            // Read in the hash set type
            byte[] typeBuffer = new byte[0x28];
            readBuffer(typeBuffer, 0x28);

            // At this point we're past the header and ready to read in the hashes
        } catch (IOException ex) {
            close();
            throw new TskCoreException("Error reading " + filename, ex);
        } catch (TskCoreException ex) {
            close();
            throw ex;
        }
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
     * Check if there are more hashes to read
     *
     * @return true if we've read all expected hash values, false otherwise
     */
    @Override
    public boolean doneReading() {
        return (totalHashesRead >= expectedHashCount);
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
        if (inputStream == null) {
            throw new TskCoreException("Attempting to read from null inputStream");
        }

        byte[] hashBytes = new byte[16];
        byte[] divider = new byte[2];
        try {

            readBuffer(hashBytes, 16);
            readBuffer(divider, 2);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            totalHashesRead++;
            return sb.toString();
        } catch (IOException ex) {
            throw new TskCoreException("Ran out of data while reading Encase hash set " + filename, ex);
        }
    }

    /**
     * Closes the import file
     */
    @Override
    public final void close() {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ex) {
                Logger.getLogger(EncaseHashSetParser.class.getName()).log(Level.SEVERE, "Error closing Encase hash set " + filename, ex);
            } finally {
                inputStream = null;
            }
        }
    }

    private void readBuffer(byte[] buffer, int length) throws TskCoreException, IOException {
        if (inputStream == null) {
            throw new TskCoreException("readBuffer called on null inputStream");
        }
        if (length != inputStream.read(buffer)) {
            throw new TskCoreException("Ran out of data unexpectedly while parsing Encase file " + filename);
        }
    }
}
