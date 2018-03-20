/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides access to per-case timeline properties (key-value store).
 */
class PerCaseTimelineProperties {

    private static final String STALE_KEY = "stale"; //NON-NLS
    private static final String WAS_INGEST_RUNNING_KEY = "was_ingest_running"; // NON-NLS

    private final Case autoCase;
    private final Path propertiesPath;

    PerCaseTimelineProperties(Case c) {
        Objects.requireNonNull(c, "Case must not be null");
        this.autoCase = c;
        propertiesPath = Paths.get(autoCase.getModuleDirectory(), "Timeline", "timeline.properties"); //NON-NLS
    }

    /**
     * Is the DB stale, i.e. does it need to be updated because new datasources
     * (eg) have been added to the case.
     *
     * @return true if the db is stale
     *
     * @throws IOException if there is a problem reading the state from disk
     */
    public synchronized boolean isDBStale() throws TskCoreException {
        try {
            String stale = getProperty(STALE_KEY);
            return StringUtils.isBlank(stale) ? true : Boolean.valueOf(stale);
        } catch (IOException iOException) {
            throw new TskCoreException("Error reading staleness of timeline DB", iOException);
        }
    }

    /**
     * record the state of the events db as stale(true) or not stale(false).
     *
     * @param stale the new state of the event db. true for stale, false for not
     * stale.
     *
     * @throws IOException if there was a problem writing the state to disk.
     */
    public synchronized void setDbStale(Boolean stale) throws IOException {
        setProperty(STALE_KEY, stale.toString());
    }

    /**
     * Was ingest running the last time the database was updated?
     *
     * @return true if ingest was running the last time the db was updated
     *
     * @throws IOException if there was a problem reading from disk
     */
    public synchronized boolean wasIngestRunning() throws IOException {
        String stale = getProperty(WAS_INGEST_RUNNING_KEY);
        return StringUtils.isBlank(stale) ? true : Boolean.valueOf(stale);
    }

    /**
     * record whether ingest was running during the last time the database was
     * updated
     *
     * @param ingestRunning true if ingest was running
     *
     * @throws IOException if there was a problem writing to disk
     */
    public synchronized void setIngestRunning(Boolean ingestRunning) throws IOException {
        setProperty(WAS_INGEST_RUNNING_KEY, ingestRunning.toString());
    }

    /**
     * Get a {@link Path} to the properties file. If the file does not exist, it
     * will be created.
     *
     * @return the Path to the properties file.
     *
     * @throws IOException if there was a problem creating the properties file
     */
    private synchronized Path getPropertiesPath() throws IOException {

        if (!Files.exists(propertiesPath)) {
            Path parent = propertiesPath.getParent();
            Files.createDirectories(parent);
            Files.createFile(propertiesPath);
        }
        return propertiesPath;
    }

    /**
     * Returns the property with the given key.
     *
     * @param propertyKey - The property key to get the value for.
     *
     * @return - the value associated with the property.
     *
     * @throws IOException if there was a problem reading the property from disk
     */
    private synchronized String getProperty(String propertyKey) throws IOException {
        return getProperties().getProperty(propertyKey);
    }

    /**
     * Sets the given property to the given value.
     *
     * @param propertyKey - The key of the property to be modified.
     * @param propertyValue - the value to set the property to.
     *
     * @throws IOException if there was a problem writing the property to disk
     */
    private synchronized void setProperty(String propertyKey, String propertyValue) throws IOException {
        Path propertiesFile = getPropertiesPath();
        Properties props = getProperties(propertiesFile);
        props.setProperty(propertyKey, propertyValue);

        try (OutputStream fos = Files.newOutputStream(propertiesFile)) {
            props.store(fos, ""); //NON-NLS
        }
    }

    /**
     * Get a {@link Properties} object used to store the timeline properties.
     *
     * @return a properties object
     *
     * @throws IOException if there was a problem reading the .properties file
     */
    private synchronized Properties getProperties() throws IOException {
        return getProperties(getPropertiesPath());
    }

    /**
     * Gets a {@link Properties} object populated form the given .properties
     * file.
     *
     * @param propertiesFile a path to the .properties file to load
     *
     * @return a properties object
     *
     * @throws IOException if there was a problem reading the .properties file
     */
    private synchronized Properties getProperties(final Path propertiesFile) throws IOException {
        try (InputStream inputStream = Files.newInputStream(propertiesFile)) {
            Properties props = new Properties();
            props.load(inputStream);
            return props;
        }
    }
}
