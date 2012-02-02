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
package org.sleuthkit.autopsy.hashdatabase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.AutopsyPropFile;
import org.sleuthkit.autopsy.coreutils.Log;

/**
 * Loads and stores Hash Database settings from/to a property file
 * @author pmartel
 */
public class HashDbSettings {

    private static final String PROP_PREFIX = "LBL_HashDB";
    private static final String NSRL_PROP = "NSRL";
    private static final String KNOWN_BAD_PROP = "KNOWN_BAD";
    private static final AutopsyPropFile apf = AutopsyPropFile.getInstance();
    private HashDb NSRLDatabase, knownBadDatabase;

    /**
     * @param propertyFile file to load settings from
     * @throws IOException if there's an error loading the property file
     * @throws FileNotFoundException if the property file can't be found
     */
    public HashDbSettings() throws IOException, FileNotFoundException {
        String NSRL = getNSRL();
        String knownBad = getKnownBad();

        if (NSRL != null && !NSRL.equals("")) {
            this.NSRLDatabase = new HashDb(NSRL);
        }

        if (knownBad != null && !knownBad.equals("")) {
            this.knownBadDatabase = new HashDb(knownBad);
        }
    }

    /**
     * Writes settings to the property file
     * @throws IOException if there's an error loading or writing to the
     * property file
     * @throws FileNotFoundException if the property file can't be found
     */
    void save() throws IOException, FileNotFoundException {
        setNSRL(this.NSRLDatabase != null ? this.NSRLDatabase.databasePath : "");
        setKnownBad(this.knownBadDatabase != null ? this.knownBadDatabase.databasePath : "");
    }

    /**
     * Returns the path the the selected NSRL hash database, or null if there is
     * none selected.
     * @return path or null
     */
    public String getNSRLDatabasePath() {
        return this.NSRLDatabase != null ? this.NSRLDatabase.databasePath : null;
    }

    /**
     * Returns the path the the selected Known Bad hash database, or null if
     * there is none selected.
     * @return path or null
     */
    public String getKnownBadDatabasePath() {
        return this.knownBadDatabase != null ? this.knownBadDatabase.databasePath : null;
    }

    /**
     * Gets NSRL database if there is one
     * @return database (can be null)
     */
    HashDb getNSRLDatabase() {
        return this.NSRLDatabase;
    }
    
    /**
     * Get the hash database settings as read from the property file.
     * @return A new hash database settings object.
     * @throws IOException if the property file can't be found
     */
    public static HashDbSettings getHashDbSettings() throws IOException {
        return new HashDbSettings();
    }

    /**
     * Gets known bad database if there is one
     * @return database (can be null)
     */
    HashDb getKnownBadDatabase() {
        return this.knownBadDatabase;
    }

    /**
     * Set NSRL database 
     * @param nsrl database, or null to clear
     */
    void setNSRLDatabase(HashDb nsrl) {
        this.NSRLDatabase = nsrl;
    }

    /**
     * Set known bad database 
     * @param knownBad known bad database, or null to clear
     */
    void setKnownBadDatabase(HashDb knownBad) {
        this.knownBadDatabase = knownBad;
    }

    // helper functions:
    private static void setNSRL(String databasePath) {
        apf.setProperty(fullProp(NSRL_PROP), databasePath);
    }

    private static void setKnownBad(String databasePath) {
        apf.setProperty(fullProp(KNOWN_BAD_PROP), databasePath);
    }

    private static String getNSRL() {
        return apf.getProperty(fullProp(NSRL_PROP));
    }

    private static String getKnownBad() {
        return apf.getProperty(fullProp(KNOWN_BAD_PROP));
    }

    private static String fullProp(String propName) {
        return PROP_PREFIX + "_" + propName;
    }
}