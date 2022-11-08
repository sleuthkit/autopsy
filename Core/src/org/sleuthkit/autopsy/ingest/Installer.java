/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.openide.modules.ModuleInstall;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Initializes ingest manager when the module is loaded
 */
public class Installer extends ModuleInstall {

    private static final String LEGACY_INGEST_SETTINGS_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), "IngestModuleSettings").toString();
    private static final String LEGACY_INGEST_PROFILES_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), "IngestProfiles").toString();
    private static final String PROPERTIES_EXT = ".properties";

    private static final Logger logger = Logger.getLogger(Installer.class.getName());

    private static Installer instance;

    public synchronized static Installer getDefault() {
        if (instance == null) {
            instance = new Installer();
        }
        return instance;
    }

    private Installer() {
        super();
    }

    @Override
    public void restored() {
        upgradeSettings();
        // initialize ingest manager
        IngestManager.getInstance();
    }

    @Override
    public boolean closing() {
        //force ingest inbox closed on exit and save state as such
        IngestMessageTopComponent.findInstance().close();
        return true;
    }

    private void upgradeSettings() {
        File settingsFolder = new File(IngestJobSettings.getBaseSettingsPath());
        File legacySettingsFolder = new File(LEGACY_INGEST_SETTINGS_PATH);
        if (legacySettingsFolder.exists() && !settingsFolder.exists()) {
            for (File moduleSettingsFolder : legacySettingsFolder.listFiles()) {
                if (moduleSettingsFolder.isDirectory()) {
                    // get the settings name from the folder name (will be the same for any properties files).
                    String settingsName = moduleSettingsFolder.getName();

                    try {
                        Path configPropsPath = Paths.get(PlatformUtil.getUserConfigDirectory(), settingsName + PROPERTIES_EXT);
                        Path profilePropsPath = Paths.get(LEGACY_INGEST_PROFILES_PATH, settingsName + PROPERTIES_EXT);
                        boolean isProfile = profilePropsPath.toFile().exists();

                        // load properties
                        Properties configProps = loadProps(configPropsPath.toFile());

                        if (isProfile) {
                            configProps.putAll(loadProps(profilePropsPath.toFile()));
                        }

                        Map<String, String> moduleSettingsToSave = configProps.entrySet().stream()
                                .filter(e -> e.getKey() != null)
                                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue() == null ? null : e.getValue().toString(), (v1, v2) -> v1));

                        String resourceName = isProfile ? IngestProfiles.getExecutionContext(settingsName) : settingsName;
                        ModuleSettings.setConfigSettings(IngestJobSettings.getModuleSettingsResource(resourceName), moduleSettingsToSave);

                        FileUtils.copyDirectory(moduleSettingsFolder, IngestJobSettings.getSavedModuleSettingsFolder(settingsName).toFile());
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "There was an error upgrading settings for: " + settingsName, ex);
                    }
                }
            }
        }
    }

    private static Properties loadProps(File propFile) throws IOException {
        Properties props = new Properties();
        if (propFile.exists()) {
            try (InputStream inputStream = new FileInputStream(propFile)) {
                props.load(inputStream);
            }
        }
        return props;
    }
}
