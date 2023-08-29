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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.commons.lang3.StringUtils;
import org.openide.modules.Places;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * CT settings that don't include license information. This code must be kept
 * in-sync with code in CT Autopsy Importer NBM.
 */
public class CTSettings {

    private static final Logger LOGGER = Logger.getLogger(CTSettings.class.getCanonicalName());
    private static final String DEFAULT_FILE_REPO_PATH = getAppDataLocalDirectory();

    private static final String CYBERTRIAGE_FOLDER = "cybertriage";
    private static final String CYBERTRIAGE_DOT_FOLDER = "." + CYBERTRIAGE_FOLDER;

    // based on com.basistech.df.cybertriage.utils.SystemProperties
    private static String getAppDataLocalDirectory() {
        if (Objects.nonNull(Places.getUserDirectory()) && Places.getUserDirectory().getAbsolutePath().endsWith("testuserdir")) { // APP is in testing .. this should return the test path
            LOGGER.log(Level.INFO, "Application Data (test mode) Path: " + Places.getUserDirectory().getAbsolutePath());
            return Places.getUserDirectory().getAbsolutePath();
        }

        // try to use LOCALAPPDATA on windows
        String localDataStr = System.getenv("LOCALAPPDATA");
        if (StringUtils.isNotBlank(localDataStr)) {
            Path localAppPath = Paths.get(localDataStr, CYBERTRIAGE_FOLDER);
            try {
                Files.createDirectories(localAppPath);
                LOGGER.log(Level.INFO, "Application Data Path: " + localAppPath.toString());
                return localAppPath.toString();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "IO Error using " + localAppPath.toString(), ex);
            }
        }

        // try to use ~/.cybertriage anywhere else
        if (!PlatformUtil.isWindowsOS()) {
            String homePathStr = System.getenv("HOME");
            if (StringUtils.isNotBlank(homePathStr)) {
                Path localAppPath = Paths.get(homePathStr, CYBERTRIAGE_DOT_FOLDER);
                try {
                    Files.createDirectories(localAppPath);
                    LOGGER.log(Level.INFO, "Non-windows Application Data Path: " + localAppPath.toString());
                    return localAppPath.toString();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "IO Error using " + localAppPath.toString(), ex);
                }
            }
        }

        // defer to user directory otherwise
        return Places.getUserDirectory().getAbsolutePath(); // In case of an IO Error
    }

    public static String getDefaultFileRepoPath() {
        return DEFAULT_FILE_REPO_PATH;
    }

    static CTSettings getDefaultSettings() {
        return new CTSettings()
                .setFileRepoPath(DEFAULT_FILE_REPO_PATH);
    }

    private String fileRepoPath = DEFAULT_FILE_REPO_PATH;

    public String getFileRepoPath() {
        return fileRepoPath;
    }

    public CTSettings setFileRepoPath(String fileRepoPath) {
        this.fileRepoPath = fileRepoPath;
        return this;
    }
}
