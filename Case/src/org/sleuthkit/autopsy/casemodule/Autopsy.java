/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.io.IOException;
import org.sleuthkit.autopsy.hashdatabase.HashDbSettings;
import org.sleuthkit.datamodel.SleuthkitJNI;

/**
 * Class to consolidate application-wide settings.
 */
public class Autopsy {
    private final static String propFilePath = System.getProperty("netbeans.user") + File.separator + "autopsy.properties";
    private static boolean verboseLogging = false;
    
    /**
     * Gets the property file where the user properties such as Recent Cases
     * and selected Hash Databases are stored.
     * @return A new file handle
     */
    public static File getPropertyFile() {
        return new File(propFilePath);
    }
    
    /**
     * Get the hash database settings as read from the property file.
     * @return A new hash database settings object.
     * @throws IOException if the property file can't be found
     */
    public static HashDbSettings getHashDbSettings() throws IOException {
        return new HashDbSettings(getPropertyFile());
    }

    /**
     * Activate verbose logging for Sleuth Kit
     */
    public static void startVerboseLogging() {
        verboseLogging = true;
        String logPath = System.getProperty("netbeans.user") + File.separator + "sleuthkit.txt";
        
        SleuthkitJNI.startVerboseLogging(logPath);
    }
    
    /**
     * Checks if verbose logging has been enabled.
     * @return true if verbose logging has been enabled.
     */
    public static boolean verboseLoggingIsSet() {
        return verboseLogging;
    }
    
}
