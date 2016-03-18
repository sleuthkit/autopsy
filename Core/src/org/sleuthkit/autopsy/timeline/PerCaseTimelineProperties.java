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
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Provides access to per-case timeline properties/settings.
 */
class PerCaseTimelineProperties {

    public static final String STALE_KEY = "stale"; //NON-NLS
    public static final String WAS_INGEST_RUNNING_KEY = "was_ingest_running"; // NON-NLS

    private final Case theCase;
    private final Path propertiesPath;

    PerCaseTimelineProperties(Case c) {
        this.theCase = c;
        propertiesPath = Paths.get(theCase.getModuleDirectory(), "Timeline", "timeline.properties"); //NON-NLS
    }

    public synchronized boolean isDbStale() throws IOException {
        String stale = getConfigSetting(STALE_KEY);
        return StringUtils.isBlank(stale) ? true : Boolean.valueOf(stale);
    }

    public synchronized void setDbStale(Boolean stale) throws IOException {
        setConfigSetting(STALE_KEY, stale.toString());
    }

    public synchronized boolean wasIngestRunning() throws IOException {
        String stale = getConfigSetting(WAS_INGEST_RUNNING_KEY);
        return StringUtils.isBlank(stale) ? true : Boolean.valueOf(stale);
    }

    public synchronized void setIngestRunning(Boolean stale) throws IOException {
        setConfigSetting(WAS_INGEST_RUNNING_KEY, stale.toString());
    }

    /**
     * Makes a new config file of the specified name. Do not include the
     * extension.
     *
     * @param moduleName - The name of the config file to make
     *
     * @return True if successfully created, false if already exists or an error
     *         is thrown.
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
     * Returns the given properties file's setting as specific by settingName.
     *
     * @param moduleName  - The name of the config file to read from.
     * @param settingName - The setting name to retrieve.
     *
     * @return - the value associated with the setting.
     *
     * @throws IOException
     */
    private synchronized String getConfigSetting(String settingName) throws IOException {
        return getProperties().getProperty(settingName);
    }

    /**
     * Sets the given properties file to the given settings.
     *
     * @param moduleName  - The name of the module to be written to.
     * @param settingName - The name of the setting to be modified.
     * @param settingVal  - the value to set the setting to.
     */
    private synchronized void setConfigSetting(String settingName, String settingVal) throws IOException {
        Path propertiesFile = getPropertiesPath();
        Properties props = getProperties(propertiesFile);
        props.setProperty(settingName, settingVal);

        try (OutputStream fos = Files.newOutputStream(propertiesFile)) {
            props.store(fos, ""); //NON-NLS
        }
    }

    /**
     * Returns the properties as specified by moduleName.
     *
     * @param moduleName
     * @param propertiesFile the value of propertiesFile
     *
     * @throws IOException
     * @return the java.util.Properties
     */
    private synchronized Properties getProperties() throws IOException {
        return getProperties(getPropertiesPath());
    }

    /**
     * Returns the properties as specified by moduleName.
     *
     * @param moduleName
     *
     * @return Properties file as specified by moduleName.
     *
     * @throws IOException
     */
    private synchronized Properties getProperties(final Path propertiesFile) throws IOException {
        try (InputStream inputStream = Files.newInputStream(propertiesFile)) {
            Properties props = new Properties();
            props.load(inputStream);
            return props;
        }
    }
}
