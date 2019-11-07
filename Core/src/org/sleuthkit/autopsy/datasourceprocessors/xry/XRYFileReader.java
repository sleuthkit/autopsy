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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.commons.io.FilenameUtils;

/**
 * Extracts XRY entities and determines the report type. An
 * example of an XRY entity would be:
 *
 * Calls #	1 
 * Call Type:	Missed 
 * Time:	1/2/2019 1:23:45 PM (Device) 
 * From 
 * Tel:         12345678
 *
 */
public class XRYFileReader {
    
    private static final Logger logger = Logger.getLogger(XRYFileReader.class.getName());

    //Assume UTF_16LE
    private static final Charset CHARSET = StandardCharsets.UTF_16LE;

    //Assume TXT extension
    private static final String EXTENSION = "txt";

    //Assume all XRY reports have the type on the 3rd line.
    private static final int LINE_WITH_REPORT_TYPE = 3;

    //Assume 0xFFFE is the BOM
    private static final int[] BOM = {0xFF, 0xFE};

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
            //Advance the reader to the line before the report type.
            for(int i = 0; i < LINE_WITH_REPORT_TYPE - 1; i++) {
                reader.readLine();
            }
            
            String reportTypeLine = reader.readLine();
            if(reportTypeLine != null && !reportTypeLine.isEmpty()) {
                return Optional.of(reportTypeLine);
            }
            return Optional.empty();
        }
    }
}
