/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors.xry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

/**
 * Extracts XRY entities and determines the report type. An example of an XRY
 * entity would be:
 *
 * Calls #	1 
 * Call Type:	Missed 
 * Time:	1/2/2019 1:23:45 PM (Device) 
 * From 
 * Tel:         12345678
 */
public final class XRYFileReader implements AutoCloseable {

    ///Assume UTF_16LE
    private static final Charset CHARSET = StandardCharsets.UTF_16LE;

    //Assume all headers are 5 lines in length.
    private static final int HEADER_LENGTH_IN_LINES = 5;

    //Underlying reader for the xry file.
    private final BufferedReader reader;

    private final StringBuilder xryEntity;

    /**
     * Creates an XRYFileReader. As part of construction, the XRY file is opened
     * and the reader is advanced past the header. This leaves the reader
     * positioned at the start of the first XRY entity.
     *
     * The file is assumed to be encoded in UTF-16LE.
     *
     * @param xryFile XRY file to read. It is assumed that the caller has read
     * access to the path.
     * @throws IOException if an I/O error occurs.
     */
    public XRYFileReader(Path xryFile) throws IOException {
        reader = Files.newBufferedReader(xryFile, CHARSET);

        //Advance the reader to the start of the first XRY entity.
        for (int i = 0; i < HEADER_LENGTH_IN_LINES; i++) {
            reader.readLine();
        }

        xryEntity = new StringBuilder();
    }

    /**
     * Advances the reader until a valid XRY entity is detected or EOF is
     * reached.
     *
     * @return Indication that there is another XRY entity to consume or that
     * the file has been exhausted.
     * @throws IOException if an I/O error occurs.
     */
    public boolean hasNextEntity() throws IOException {
        //Entity has yet to be consumed.
        if (xryEntity.length() > 0) {
            return true;
        }

        String line;
        while ((line = reader.readLine()) != null) {
            if (marksEndOfEntity(line)) {
                if (xryEntity.length() > 0) {
                    //Found a non empty XRY entity.
                    return true;
                }
            } else {
                xryEntity.append(line).append("\n");
            }
        }

        //Check if EOF was hit before an entity delimiter was found.
        return xryEntity.length() > 0;
    }

    /**
     * Returns an XRY entity if there is one, otherwise an exception is thrown.
     *
     * @return A non-empty XRY entity.
     * @throws IOException if an I/O error occurs.
     * @throws NoSuchElementException if there are no more XRY entities to
     * consume.
     */
    public String nextEntity() throws IOException {
        if (hasNextEntity()) {
            String returnVal = xryEntity.toString();
            xryEntity.setLength(0);
            return returnVal;
        } else {
            throw new NoSuchElementException();
        }
    }

    /**
     * Closes any file handles this reader may have open.
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        reader.close();
    }

    /**
     * Determines if the line encountered during file reading signifies the end
     * of an XRY entity.
     *
     * @param line
     * @return
     */
    private boolean marksEndOfEntity(String line) {
        return line.isEmpty();
    }
}
