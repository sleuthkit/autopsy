/*
 * Autopsy
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
package org.sleuthkit.autopsy.logicalimager.dsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for displaying a list of drives
 */
public final class DriveListUtils {

    /**
     * Convert a number of bytes to a human readable string
     *
     * @param bytes the number of bytes to convert
     * @param si    whether it takes 1000 or 1024 of a unit to reach the next
     *              unit
     *
     * @return a human readable string representing the number of bytes
     */
    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + " B"; //NON-NLS
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i"); //NON-NLS
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre); //NON-NLS
    }

    /**
     * Empty private constructor for util class
     */
    private DriveListUtils() {
        //empty private constructor for util class
    }

    /** Use the command <code>net</code> to determine what this drive is.
     * <code>net use</code> will return an error for anything which isn't a share.
     */
    public static boolean isNetworkDrive(String driveLetter) {
        List<String> cmd = Arrays.asList("cmd", "/c", "net", "use", driveLetter + ":");
        
        try {
            Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        
            p.getOutputStream().close();
            
            StringBuilder consoleOutput = new StringBuilder();
            
            String line;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                while ((line = in.readLine()) != null) {
                    consoleOutput.append(line).append("\r\n");
                }
            }
            
            int rc = p.waitFor();
            return rc == 0;
        } catch(IOException | InterruptedException e) {
            return false; // assume not a network drive
        }
    }
}
