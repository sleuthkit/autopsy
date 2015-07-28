/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class HandleUNC {

    private static HandleUNC instance = null;
    private static Map<String, String> drives;
    private static final String MAPPED_DRIVES = "mapped_drives.txt"; //NON-NLS
    private static final String DATA_TRIGGER = "-------------------------------------------------------------------------------"; //NON-NLS
    private static final String OK_TXT = "OK"; //NON-NLS
    private static final String COLON = ":"; //NON-NLS
    private static final String UNC_PATH_START = "\\\\"; //NON-NLS
    private static final int MAX_LINE_SCANS = 500;
    private static final int DRIVE_LEN = 2;
    private static final int STARTING_OFFSET = 0;
    private static final int REPLACEMENT_SIZE = 2;
    private static final int FIRST_ITEM = 0;

    /**
     * Access method for singleton
     *
     * @return HandleUNC singleton object
     */
    public synchronized static HandleUNC getInstance() {
        if (instance == null) {
            instance = new HandleUNC();
        }
        return instance;
    }

    /**
     * Private constructor for singleton
     */
    private HandleUNC() {
        drives = getMappedDrives();
    }

    /**
     * Try to substitute in the path for a UNC string.
     *
     * @param path the path to substitute.
     * @return returns the original string if unsuccessful, the substituted
     * string if successful.
     */
    public String attemptUNCSubstitution(String path) {
        return attemptUNCSubstitution(Paths.get(path)).toString();
    }

    /**
     * Try to substitute in the path for a UNC string.
     *
     * @param path the path to substitute.
     * @return returns the original string if unsuccessful, the substituted
     * string if successful.
     */
    public Path attemptUNCSubstitution(Path path) {
        String uncPath = path.toString();
        if (false == isUNC(path)) {
            try {
                String currentDrive = path.getRoot().toString().substring(STARTING_OFFSET, REPLACEMENT_SIZE);
                String uncMapping = drives.get(currentDrive);
                if (uncMapping != null) {
                    uncPath = uncMapping + uncPath.substring(REPLACEMENT_SIZE, uncPath.length());
                }
            } catch (Exception ex) {
                // Didn't work. Skip it.
            }
        }
        return Paths.get(uncPath);
    }

    /**
     * Takes a UNC path that may have an IP address in it and converts it to
     * hostname, if it can.
     *
     * @param inputPath the path to convert to a hostname UNC path
     * @return the path that was passed in if it was hostname before, the
     * converted path if it was successfully converted
     */
    public Path ipToHostName(Path inputPath) {
        return Paths.get(ipToHostName(inputPath.toString()));
    }

    /**
     * Takes a UNC path that may have an IP address in it and converts it to
     * hostname, if it can.
     *
     * @param inputPath the path to convert to a hostname UNC path
     * @return the path that was passed in if it was hostname before, the
     * converted path if it was successfully converted
     */
    public String ipToHostName(String inputPath) {
        String result = inputPath;
        try {
            if (isUNC(Paths.get(inputPath))) {
                String potentialIP = Paths.get(inputPath.substring(REPLACEMENT_SIZE)).getName(FIRST_ITEM).toString();
                String hostname = InetAddress.getByName(potentialIP).getHostName();
                result = inputPath.replaceAll(potentialIP, hostname);
            }
        } catch (Exception ex) {
            // We didn't find a hostname for this potential IP address. Move on.            
        }
        return result;
    }

    /**
     * Test if a String is UNC
     *
     * @param inputPath
     * @return true if UNC, false otherwise
     */
    public static boolean isUNC(Path inputPath) {
        return isUNC(inputPath.toString());
    }

    /**
     * Test if a Path is UNC
     *
     * @param inputPath
     * @return true if UNC, false otherwise
     */
    public static boolean isUNC(String inputPath) {
        return inputPath.startsWith(UNC_PATH_START);
    }

    /**
     * Populate the mapped drives
     *
     * @return the hashmap
     */
    private static Map<String, String> getMappedDrives() {
        Map<String, String> driveMap = new HashMap<>();
        try {
            File mappedDrive = new File(MAPPED_DRIVES);
            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", "net", "use"); //NON-NLS
            builder.redirectOutput(mappedDrive);
            builder.redirectError(mappedDrive);
            Process p = builder.start(); // throws IOException
            p.waitFor(10, TimeUnit.SECONDS);
            try (Scanner scanner = new Scanner(mappedDrive)) {
                int safetyCount = 0;
                
                // scan past the header information until we find trigger
                while (scanner.hasNext() && !scanner.nextLine().equals(DATA_TRIGGER)) {
                    if (++safetyCount > MAX_LINE_SCANS) {
                        break;
                    }
                }
                // parse the data and place it in the hashmap
                while (scanner.hasNext()) {
                    String entry1 = scanner.next();
                    String entry2 = scanner.next();
                    String entry3 = scanner.next();
                    scanner.nextLine();
                    if (entry1.length() == DRIVE_LEN && !entry1.equals(OK_TXT) && entry1.endsWith(COLON)) {
                        driveMap.put(entry1, entry2); // if there was no leading status, populate drive
                    } else if (entry2.length() == DRIVE_LEN && entry2.endsWith(COLON)) {
                        driveMap.put(entry2, entry3); // if there was a leading status, populate drive
                    }
                }
            }
            Files.deleteIfExists(mappedDrive.toPath());
        } catch (IOException | InterruptedException ex) {
            // if we couldn't do it, no big deal  
            Logger.getLogger(HandleUNC.class.getName()).log(Level.WARNING, "Unable to parse 'net use' output", ex); //NON-NLS
        }
        return driveMap;
    }
}
