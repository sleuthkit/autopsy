/** *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Sleuth Kit Labs. It is given in confidence by Sleuth Kit Labs
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2023 Sleuth Kit Labs, LLC. All rights reserved
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ************************************************************************** */
package com.basistech.df.cybertriage.autopsy.incidentoptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Handles persisting CT Settings.
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
