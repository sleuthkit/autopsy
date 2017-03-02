/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class UNCPathUtilities {

    private static Map<String, String> drives;
    private static final String MAPPED_DRIVES = "_mapped_drives.txt"; //NON-NLS
    private static final String TEMP_FOLDER = "TEMP"; //NON-NLS
    private static final String DATA_TRIGGER = "----------"; //NON-NLS
    private static final String OK_TXT = "OK"; //NON-NLS
    private static final String COLON = ":"; //NON-NLS
    private static final String UNC_PATH_START = "\\\\"; //NON-NLS
    private static final String C_DRIVE = "C:"; //NON-NLS
    private static final int DRIVE_LEN = 2;
    private static final int STARTING_OFFSET = 0;
    private static final int REPLACEMENT_SIZE = 2;
    private static final int FIRST_ITEM = 0;
    private final String nameString;

    /**
     * Constructor
     */
    public UNCPathUtilities() {
        // get UUID for this instance
        this.nameString = UUID.randomUUID().toString();
        drives = getMappedDrives();
    }

    /**
     * This method converts a passed in path to UNC if it is not already UNC.
     * The UNC path will end up in one of the following two forms:
     * "\\hostname\somefolder\otherfolder" or
     * "\\IP_ADDRESS\somefolder\otherfolder"
     *
     * This is accomplished by checking the mapped drives list the operating
     * system maintains and substituting where required. If the drive of the
     * path passed in does not exist in the cached mapped drives list, you can
     * force a rescan of the mapped drives list with rescanDrives(), then call
     * this method again. This would be of use if the end user added a mapped
     * drive while your dialog was up, for example.
     *
     * @param inputPath a String of the path to convert
     *
     * @return returns a successfully converted inputPath or null if unable to
     *         find a matching drive and convert it to UNC
     */
    synchronized public String mappedDriveToUNC(String inputPath) {
        if (inputPath != null) {
            // If it is a C:, do not attempt to convert. This is for the single-user case.
            if (inputPath.toUpperCase().startsWith(C_DRIVE)) {
                return null;
            }
            if (false == isUNC(inputPath)) {
                String uncPath = null;
                try {
                    String currentDrive = Paths.get(inputPath).getRoot().toString().substring(STARTING_OFFSET, REPLACEMENT_SIZE);
                    String uncMapping = drives.get(currentDrive.toUpperCase());
                    if (uncMapping != null) {
                        uncPath = uncMapping + inputPath.substring(REPLACEMENT_SIZE, inputPath.length());
                    }
                } catch (Exception ex) {
                    // Didn't work. Skip it.
                }
                return uncPath;
            } else {
                return inputPath;
            }
        } else {
            return null;
        }
    }

    /**
     * This method converts a passed in path to UNC if it is not already UNC.
     * The UNC path will end up in one of the following two forms:
     * \\\\hostname\\somefolder\\otherfolder or
     * \\\\IP_ADDRESS\\somefolder\\otherfolder
     *
     * This is accomplished by checking the mapped drives list the operating
     * system maintains and substituting where required. If the drive of the
     * path passed in does not exist in the cached mapped drives list, you can
     * force a rescan of the mapped drives list with rescanDrives(), then call
     * this method again. This would be of use if the end user added a mapped
     * drive while your dialog was up, for example.
     *
     * @param inputPath the path to convert
     *
     * @return returns a successfully converted inputPath or null if unable to
     *         find a matching drive and convert it to UNC
     */
    synchronized public Path mappedDriveToUNC(Path inputPath) {
        if (inputPath != null) {
            String uncPath = UNCPathUtilities.this.mappedDriveToUNC(inputPath.toString());
            if (uncPath == null) {
                return null;
            } else {
                return Paths.get(uncPath);
            }
        } else {
            return null;
        }
    }

    /**
     * Tests if the drive in the passed in path is a mapped drive.
     *
     * @param inputPath the Path to test.
     *
     * @return true if the passed in drive is mapped, false otherwise
     */
    synchronized public boolean isDriveMapped(Path inputPath) {
        if (inputPath != null) {
            return isDriveMapped(inputPath.toString());
        } else {
            return false;
        }
    }

    /**
     * Tests if the drive in the passed in path is a mapped drive.
     *
     * @param inputPath the Path to test.
     *
     * @return true if the passed in drive is mapped, false otherwise
     */
    synchronized public boolean isDriveMapped(String inputPath) {
        if (inputPath != null) {
            String shortenedPath = inputPath.substring(STARTING_OFFSET, DRIVE_LEN);
            for (String s : drives.keySet()) {
                if (shortenedPath.equals(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Takes a UNC path that may have an IP address in it and converts it to
     * hostname, if it can resolve the hostname. Given
     * "\\10.11.12.13\some\folder", the result will be
     * "\\TEDS_COMPUTER\some\folder" if the IP address 10.11.12.13 belongs to a
     * machine with the hostname TEDS_COMPUTER and the local machine is able to
     * resolve the hostname.
     *
     * @param inputPath the path to convert to a hostname UNC path
     *
     * @return the successfully converted path or null if unable to resolve
     */
    synchronized public Path ipToHostName(Path inputPath) {
        if (inputPath != null) {
            return Paths.get(ipToHostName(inputPath.toString()));
        } else {
            return null;
        }
    }

    /**
     * Takes a UNC path that may have an IP address in it and converts it to
     * hostname, if it can resolve the hostname. Given
     * "\\10.11.12.13\some\folder", the result will be
     * "\\TEDS_COMPUTER\some\folder" if the IP address 10.11.12.13 belongs to a
     * machine with the hostname TEDS_COMPUTER and the local machine is able to
     * resolve the hostname.
     *
     * @param inputPath a String of the path to convert to a hostname UNC path
     *
     * @return the successfully converted path or null if unable to resolve
     */
    synchronized public String ipToHostName(String inputPath) {
        if (inputPath != null) {
            String result = null;
            try {
                if (isUNC(Paths.get(inputPath))) {
                    String potentialIP = Paths.get(inputPath.substring(REPLACEMENT_SIZE)).getName(FIRST_ITEM).toString();
                    String hostname = InetAddress.getByName(potentialIP).getHostName();
                    result = inputPath.replaceAll(potentialIP, hostname);
                }
            } catch (Exception ex) {
                // Could not resolve hostname for IP address, return null result
            }
            return result;
        } else {
            return null;
        }
    }

    /**
     * Test if a Path is UNC. It is considered UNC if it begins with \\
     *
     * @param inputPath the path to check
     *
     * @return true if the passed in Path is UNC, false otherwise
     */
    synchronized public static boolean isUNC(Path inputPath) {
        if (inputPath != null) {
            return isUNC(inputPath.toString());
        } else {
            return false;
        }
    }

    /**
     * Test if a String path is UNC. It is considered UNC if it begins with \\
     *
     * @param inputPath the String of the path to check
     *
     * @return true if the passed in Path is UNC, false otherwise
     */
    synchronized public static boolean isUNC(String inputPath) {
        if (inputPath != null) {
            return inputPath.startsWith(UNC_PATH_START);
        } else {
            return false;
        }
    }

    /**
     * Updates the list of mapped drives this class contains. This list is used
     * to resolve mappedDriveToUNC and isDriveMapped calls. This is useful to
     * call if the user has potentially added mapped drives to their system
     * after the module calling mappedDriveToUNC has already begun running. Note
     * this uses system I/O, so call it with some care.
     *
     */
    synchronized public void rescanDrives() {
        drives = getMappedDrives();
    }

    /**
     * Populates the list of mapped drives this class contains. The list is used
     * to resolve mappedDriveToUNC and isDriveMapped calls. Note this uses
     * system I/O, so call it with some care.
     *
     * @return the hashmap
     */
    synchronized private Map<String, String> getMappedDrives() {
        Map<String, String> driveMap = new HashMap<>();

        if (PlatformUtil.isWindowsOS() == false) {
            return driveMap;
        }

        File mappedDrive = Paths.get(System.getenv(TEMP_FOLDER), nameString + MAPPED_DRIVES).toFile();
        try {
            Files.deleteIfExists(mappedDrive.toPath());
            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", "net", "use"); //NON-NLS
            builder.redirectOutput(mappedDrive);
            builder.redirectError(mappedDrive);
            Process p = builder.start(); // throws IOException
            p.waitFor(10, TimeUnit.SECONDS);
            try (Scanner scanner = new Scanner(mappedDrive)) {
                // parse the data and place it in the hashmap
                while (scanner.hasNext()) {
                    String entry1 = scanner.next();
                    if (entry1.startsWith(DATA_TRIGGER)) {
                        continue;
                    }
                    String entry2 = scanner.next();
                    if (entry2.startsWith(DATA_TRIGGER)) {
                        continue;
                    }
                    String entry3 = scanner.next();
                    if (entry3.startsWith(DATA_TRIGGER)) {
                        continue;
                    }
                    scanner.nextLine();
                    if (entry1.length() == DRIVE_LEN && !entry1.equals(OK_TXT) && entry1.endsWith(COLON)) {
                        driveMap.put(entry1, entry2); // if there was no leading status, populate drive
                    } else if (entry2.length() == DRIVE_LEN && entry2.endsWith(COLON)) {
                        driveMap.put(entry2, entry3); // if there was a leading status, populate drive
                    }
                }
            }
        } catch (IOException | InterruptedException | NoSuchElementException | IllegalStateException ex) {
            // if we couldn't do it, no big deal  
            Logger.getLogger(UNCPathUtilities.class.getName()).log(Level.WARNING, "Unable to parse 'net use' output", ex); //NON-NLS
        } finally {
            try {
                Files.deleteIfExists(mappedDrive.toPath());
            } catch (IOException ex) {
                // if we couldn't do it, no big deal  
            }
        }
        return driveMap;
    }
    
    /**
     * Converts a path to UNC, if possible. This is accomplished by checking the
     * mapped drives list the operating system maintains and substituting where
     * required. If the drive of the path passed in does not exist in the cached
     * mapped drives list, a rescan of the mapped drives list is forced, and
     * mapping is attempted one more time.
     *
     * @param indexDir the String of the absolute path to be converted to UNC,
     *                 if possible
     *
     * @return UNC path if able to convert to UNC, original input path otherwise
     */
    synchronized public String convertPathToUNC(String indexDir) {
        // if we can check for UNC paths, do so, otherwise just return the indexDir
        String result = mappedDriveToUNC(indexDir);
        if (result == null) {
            rescanDrives();
            result = mappedDriveToUNC(indexDir);
        }
        if (result == null) {
            return indexDir;
        }
        return result;
    }
}
