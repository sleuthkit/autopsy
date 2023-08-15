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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.modules.Places;

/**
 * CT settings that don't include license information.
 */
public class CTSettings {

    private static final String DEFAULT_FILE_REPO_PATH = getAppDataLocalDirectory();

    // taken from com.basistech.df.cybertriage.utils.SystemProperties
    private static String getAppDataLocalDirectory() {

        Logger LOGGER = java.util.logging.Logger.getLogger(CTSettings.class.getCanonicalName());
        if (Objects.nonNull(Places.getUserDirectory()) && Places.getUserDirectory().getAbsolutePath().endsWith("testuserdir")) { // APP is in testing .. this should return the test path
            LOGGER.log(Level.INFO, "Application Data (test mode) Path: " + Places.getUserDirectory().getAbsolutePath());
            return Places.getUserDirectory().getAbsolutePath();
        } else {
            Path localAppPath = Paths.get(System.getenv("LOCALAPPDATA"), "cybertriage");
            try {
                Files.createDirectories(localAppPath);
                LOGGER.log(Level.INFO, "Application Data Path: " + localAppPath.toString());
                return localAppPath.toString();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "IO Error, defaulting to user dir", ex);
                return Places.getUserDirectory().getAbsolutePath(); // In case of an IO Error
            }
        }
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
