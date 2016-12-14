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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Utility methods for working with drives.
 */
public class DriveUtils {

    private static final String PDISK = "\\\\.\\physicaldrive"; //NON-NLS
    private static final String DEV = "/dev/"; //NON-NLS
    private static final String PARTITION = "\\\\.\\";
    private static final String COLON = ":";

    /**
     * Determines whether or not a given path is for a physical drive.
     *
     * @param path The path to test.
     *
     * @return True or false.
     */
    public static boolean isPhysicalDrive(String path) {
        return path.toLowerCase().startsWith(PDISK) || path.toLowerCase().startsWith(DEV);
    }

    /**
     * Determines whether or not a given path is for a local drive or partition.
     *
     * @param path The path to test.
     *
     * @return True or false.
     */
    public static boolean isPartition(String path) {
        return path.toLowerCase().startsWith(PARTITION) && path.toLowerCase().endsWith(COLON);
    }

    /**
     * Determines whether or not a drive exists by reading the first byte and
     * checking if it is a -1.
     *
     * @param path The path to test.
     *
     * @return True or false.
     */
    public static boolean driveExists(String path) {
        BufferedInputStream br = null;
        try {
            File tmp = new File(path);
            br = new BufferedInputStream(new FileInputStream(tmp));
            int b = br.read();
            return b != -1;
        } catch (Exception ex) {
            return false;
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ex) {
            }
        }
    }

    /**
     * Prevents instantiation.
     */
    private DriveUtils() {

    }
}
