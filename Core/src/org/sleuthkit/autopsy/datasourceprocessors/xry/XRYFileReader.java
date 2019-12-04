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
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.logging.Level;
import java.util.NoSuchElementException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.commons.io.FilenameUtils;

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
final class XRYFileReader implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(XRYFileReader.class.getName());

    //Assume UTF_16LE
    private static final Charset CHARSET = StandardCharsets.UTF_16LE;

    //Assume the header begins with 'xry export'.
    private static final String START_OF_HEADER = "xry export";

    //Assume all XRY reports have the type on the 3rd line
    //relative to the start of the header.
    private static final int LINE_WITH_REPORT_TYPE = 3;

    //Assume all headers are 5 lines in length.
    private static final int HEADER_LENGTH_IN_LINES = 5;

    //Assume TXT extension
    private static final String EXTENSION = "txt";

    //Assume 0xFFFE is the BOM
    private static final int[] BOM = {0xFF, 0xFE};

    //Entity to be consumed during file iteration.
    private final StringBuilder xryEntity;

    //Underlying reader for the xry file.
    private final BufferedReader reader;

    //Reference to the original xry file.
    private final Path xryFilePath;

    /**
     * Creates an XRYFileReader. As part of construction, the XRY file is opened
     * and the reader is advanced past the header. This leaves the reader
     * positioned at the start of the first XRY entity.
     *
     * The file is assumed to be encoded in UTF-16LE and is NOT verified to be
     * an XRY file before reading. It is expected that the isXRYFile function
     * has been called on the path beforehand. Otherwise, the behavior is
     * undefined.
     *
     * @param xryFile XRY file to read. It is assumed that the caller has read
     * access to the path.
     * @throws IOException if an I/O error occurs.
     */
    public XRYFileReader(Path xryFile) throws IOException {
        reader = Files.newBufferedReader(xryFile, CHARSET);
        xryFilePath = xryFile;

        //Advance the reader to the start of the header.
        advanceToHeader(reader);

        //Advance the reader past the header to the start 
        //of the first XRY entity.
        for (int i = 1; i < HEADER_LENGTH_IN_LINES; i++) {
            reader.readLine();
        }

        xryEntity = new StringBuilder();
    }

    /**
     * Extracts the report type from the XRY file.
     *
     * @return The XRY report type
     * @throws IOException if an I/O error occurs.
     * @throws IllegalArgumentExcepton If the XRY file does not have a report
     * type. This is a misuse of the API. The validity of the Path should have
     * been checked with isXRYFile before creating an XRYFileReader.
     */
    public String getReportType() throws IOException {
        Optional<String> reportType = getType(xryFilePath);
        if (reportType.isPresent()) {
            return reportType.get();
        }

        throw new IllegalArgumentException(xryFilePath.toString() + " does not "
                + "have a report type.");
    }

    /**
     * Returns the raw path of the XRY report file.
     *
     * @return
     * @throws IOException
     */
    public Path getReportPath() throws IOException {
        return xryFilePath;
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
                xryEntity.append(line).append('\n');
            }
        }

        //Check if EOF was hit before an entity delimiter was found.
        return xryEntity.length() > 0;
    }

    /**
     * Returns an XRY entity if there is one, otherwise an exception is thrown.
     * Clients should test for another entity by calling hasNextEntity().
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
     * Peek at the next XRY entity without consuming it. If there are not more
     * XRY entities left, an exception is thrown. Clients should test for
     * another entity by calling hasNextEntity().
     *
     * @return A non-empty XRY entity.
     * @throws IOException if an I/O error occurs.
     * @throws NoSuchElementException if there are no more XRY entities to peek.
     */
    public String peek() throws IOException {
        if (hasNextEntity()) {
            return xryEntity.toString();
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

    /**
     * Checks if the Path is an XRY file. In order to be an XRY file, it must
     * have a txt extension, a 0xFFFE BOM (for UTF-16LE), and a non-empty report
     * type. The encoding is not verified any further than checking the BOM. To
     * get the report type, the file is read with a UTF-16LE decoder. If a
     * failure directly related to the decoding is encountered, it is logged and
     * the file is assumed not to be an XRY file. A direct consequence is that
     * there may be false positives.
     *
     * All other I/O exceptions are propagated up. If the Path represents a
     * symbolic link, this function will not follow it.
     *
     * @param file Path to test. It is assumed that the caller has read access
     * to the file.
     * @return Indicates whether the Path is a XRY file.
     *
     * @throws IOException if an I/O error occurs
     */
    public static boolean isXRYFile(Path file) throws IOException {
        String parsedExtension = FilenameUtils.getExtension(file.toString());

        //A XRY file should have a txt extension.
        if (!EXTENSION.equals(parsedExtension)) {
            return false;
        }

        BasicFileAttributes attr = Files.readAttributes(file,
                BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

        //Do not follow symbolic links. XRY files cannot be a directory.
        if (attr.isSymbolicLink() || attr.isDirectory()) {
            return false;
        }

        //Check 0xFFFE BOM
        if (!isXRYBOM(file)) {
            return false;
        }

        try {
            Optional<String> reportType = getType(file);
            //All valid XRY reports should have a type.
            return reportType.isPresent();
        } catch (MalformedInputException ex) {
            logger.log(Level.WARNING, String.format("File at path [%s] had "
                    + "0xFFFE BOM but was not encoded in UTF-16LE.", file.toString()), ex);
            return false;
        }
    }

    /**
     * Checks the leading bytes of the Path to verify they match the expected
     * 0xFFFE BOM.
     *
     * @param file Path to check. It is assumed that the caller has read access
     * to the file.
     *
     * @return Indication if the leading bytes match.
     * @throws IOException if an I/O error occurs.
     */
    private static boolean isXRYBOM(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            for (int bomByte : BOM) {
                if (in.read() != bomByte) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Reads the report type from the Path. It is assumed that the Path will
     * have a UTF-16LE encoding. A MalformedInputException will be thrown if
     * there is a decoding error.
     *
     * @param file
     * @return
     * @throws IOException
     */
    private static Optional<String> getType(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, CHARSET)) {
            //Header may not start at the beginning of the file.
            advanceToHeader(reader);

            //Advance the reader to the line before the report type.
            for (int i = 1; i < LINE_WITH_REPORT_TYPE - 1; i++) {
                reader.readLine();
            }

            String reportTypeLine = reader.readLine();
            if (reportTypeLine != null && !reportTypeLine.isEmpty()) {
                return Optional.of(reportTypeLine);
            }
            return Optional.empty();
        }
    }

    /**
     * Advances the reader to the start of the header. The XRY Export header may
     * not be the first n lines of the file. It may be preceded by new lines or
     * white space.
     *
     * This function will consume the first line of the header, which will be
     * 'XRY Export'.
     *
     * @param reader BufferedReader pointing to the xry file
     * @throws IOException if an I/O error occurs
     */
    private static void advanceToHeader(BufferedReader reader) throws IOException {
        String line;
        if((line = reader.readLine()) == null) {
            return;
        }
        
        String normalizedLine = line.trim().toLowerCase();
        if (normalizedLine.equals(START_OF_HEADER)) {
            return;
        }

        /**
         * The first line may have 0xFFFE BOM prepended to it, which will cause
         * the equality check to fail. This bit a logic will try to remove those
         * bytes and attempt another check.
         */
        byte[] normalizedBytes = normalizedLine.getBytes(CHARSET);
        if (normalizedBytes.length > 2) {
            normalizedLine = new String(normalizedBytes, 2,
                    normalizedBytes.length - 2, CHARSET);
            if (normalizedLine.equals(START_OF_HEADER)) {
                return;
            }
        }

        /**
         * All other lines will need to match completely.
         */
        while ((line = reader.readLine()) != null) {
            normalizedLine = line.trim().toLowerCase();
            if (normalizedLine.equals(START_OF_HEADER)) {
                return;
            }
        }
    }
}
