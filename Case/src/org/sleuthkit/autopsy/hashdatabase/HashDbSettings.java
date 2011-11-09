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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Loads and stores Hash Database settings from/to a property file
 * @author pmartel
 */
public class HashDbSettings {

    private static final String PROP_PREFIX = "HASHDB";
    private static final String NSRL_PROP = "NSRL";
    private static final String KNOWN_BAD_PROP = "KNOWN_BAD";
    private File propertyFile;
    private HashDb NSRLDatabase, knownBadDatabase;

    /**
     * @param propertyFile file to load settings from
     * @throws IOException if there's an error loading the property file
     * @throws FileNotFoundException if the property file can't be found
     */
    public HashDbSettings(File propertyFile) throws IOException, FileNotFoundException {
        this.propertyFile = propertyFile;

        Properties temp = new Properties();
        InputStream loadStream = new FileInputStream(propertyFile);
        temp.load(loadStream);
        loadStream.close();

        String NSRL = getNSRL(temp);
        String knownBad = getKnownBad(temp);

        if (!NSRL.equals("")) {
            this.NSRLDatabase = new HashDb(NSRL);
        }

        if (!knownBad.equals("")) {
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
        Properties temp = new Properties();
        InputStream loadStream = new FileInputStream(propertyFile);
        temp.load(loadStream);
        loadStream.close();

        setNSRL(temp, this.NSRLDatabase != null ? this.NSRLDatabase.databasePath : "");
        setKnownBad(temp, this.knownBadDatabase != null ? this.knownBadDatabase.databasePath : "");

        String comments = "";
        OutputStream storeStream = new FileOutputStream(propertyFile);
        temp.store(storeStream, comments);
        storeStream.close();
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
    private static void setNSRL(Properties props, String databasePath) {
        setProp(props, NSRL_PROP, databasePath);
    }

    private static void setKnownBad(Properties props, String databasePath) {
        setProp(props, KNOWN_BAD_PROP, databasePath);
    }

    private static String getNSRL(Properties props) {
        return getProp(props, NSRL_PROP);
    }

    private static String getKnownBad(Properties props) {
        return getProp(props, KNOWN_BAD_PROP);
    }

    private static void setProp(Properties props, String propName, String propValue) {
        props.setProperty(fullProp(propName), propValue);
    }

    private static String getProp(Properties props, String propName) {
        return props.getProperty(fullProp(propName), "");
    }

    private static String fullProp(String propName) {
        return PROP_PREFIX + "_" + propName;
    }
}