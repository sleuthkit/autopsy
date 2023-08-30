/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package com.basistech.df.cybertriage.autopsy.incidentoptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Handles persisting CT Settings. This code must be kept in-sync with code in
 * CT Autopsy Importer NBM.
 */
public class CTSettingsPersistence {

    private static final String CT_SETTINGS_DIR = "CyberTriage";
    private static final String CT_SETTINGS_FILENAME = "CyberTriageSettings.json";

    private static final Logger logger = Logger.getLogger(CTSettingsPersistence.class.getName());

    private static final CTSettingsPersistence instance = new CTSettingsPersistence();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static CTSettingsPersistence getInstance() {
        return instance;
    }

    public synchronized boolean saveCTSettings(CTSettings ctSettings) {
        if (ctSettings != null) {

            File settingsFile = getCTSettingsFile();
            settingsFile.getParentFile().mkdirs();
            try {
                objectMapper.writeValue(settingsFile, ctSettings);
                return true;
            } catch (IOException ex) {
                logger.log(Level.WARNING, "There was an error writing CyberTriage settings to file: " + settingsFile.getAbsolutePath(), ex);
            }
        }

        return false;
    }

    public synchronized CTSettings loadCTSettings() {

        CTSettings settings = null;
        File settingsFile = getCTSettingsFile();
        if (settingsFile.isFile()) {
            try {
                settings = objectMapper.readValue(settingsFile, CTSettings.class);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "There was an error reading CyberTriage settings to file: " + settingsFile.getAbsolutePath(), ex);
            }
        }

        return settings == null
                ? CTSettings.getDefaultSettings()
                : settings;

    }

    private File getCTSettingsFile() {
        return Paths.get(PlatformUtil.getModuleConfigDirectory(), CT_SETTINGS_DIR, CT_SETTINGS_FILENAME).toFile();
    }
}
