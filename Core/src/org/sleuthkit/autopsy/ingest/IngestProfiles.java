/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.sleuthkit.autopsy.ingest.profile.IngestProfilePaths;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Class for managing the access to the
 */
public final class IngestProfiles {

    private static final String PROFILE_NAME_KEY = "Profile_Name";
    private static final String PROFILE_DESC_KEY = "Profile_Description";
    private static final String PROFILE_FILTER_KEY = "Profile_Filter";
    private static final String SETTINGS_FILE_PREFIX = IngestProfilePaths.getInstance().getIngestProfilePrefix();
    private static final Logger logger = Logger.getLogger(IngestProfiles.class.getName());

    /**
     * @return Prefix to append to an ingest profile name when saving to disk or
     *         using with ingest job settings.
     */
    static String getIngestProfilePrefix() {
        return SETTINGS_FILE_PREFIX;
    }

    /**
     * Return the execution context name (to be used with IngestJobSettings)
     *
     * @param profileName The profile name.
     *
     * @return The execution context to use with IngestJobSettings.
     */
    static String getExecutionContext(String profileName) {
        return SETTINGS_FILE_PREFIX + profileName;
    }

    /**
     * Returns a profile name with no prefix (if included).
     *
     * @param executionContext The execution context.
     *
     * @return The sanitized profileName.
     */
    private static String getSanitizedProfile(String executionContext) {
        return (executionContext != null && executionContext.startsWith(getIngestProfilePrefix()))
                ? executionContext.substring(getIngestProfilePrefix().length())
                : executionContext;
    }

    /**
     * Returns the file location of the root settings file for this ingest
     * profile.
     *
     * @param profileName The profile name.
     *
     * @return The file location for the root settings of that profile.
     */
    private static File getRootSettingsFile(String profileName) {
        return Paths.get(
                PlatformUtil.getUserConfigDirectory(), 
                IngestJobSettings.getModuleSettingsResource(
                        getExecutionContext(getSanitizedProfile(profileName))) + ".properties"
                ).toFile();
    }

    /**
     * Returns the settings directory for the profile containing ingest module
     * specific settings for the ingest profile.
     *
     * @param profileName The profile name.
     *
     * @return The directory.
     */
    private static File getSettingsDirectory(String profileName) {
        return IngestJobSettings.getSavedModuleSettingsFolder(getExecutionContext(getSanitizedProfile(profileName))).toFile();
    }

    /**
     * Gets the collection of profiles which currently exist.
     *
     * @return profileList
     */
    public synchronized static List<IngestProfile> getIngestProfiles() {
        File dir = new File(IngestJobSettings.getBaseSettingsPath());
        // find all settings files for ingest profiles (starts with ingest profiles prefix)
        File[] directoryListing = dir.listFiles((file) -> file.getName() != null && file.getName().startsWith(getIngestProfilePrefix()) && file.isFile());
        List<IngestProfile> profileList = new ArrayList<>();
        if (directoryListing != null) {
            for (File child : directoryListing) {
                String resourceName = FilenameUtils.removeExtension(child.getName());
                String profileName = getSanitizedProfile(resourceName);
                String moduleSettingsResource = IngestJobSettings.getModuleSettingsResource(resourceName);
                String desc = ModuleSettings.getConfigSetting(moduleSettingsResource, PROFILE_DESC_KEY);
                String fileIngestFilter = ModuleSettings.getConfigSetting(moduleSettingsResource, PROFILE_FILTER_KEY);
                profileList.add(new IngestProfile(profileName, desc, fileIngestFilter));
            }
        }
        return profileList;
    }

    /**
     * Saves the list of profiles which currently exist to disk.
     */
    synchronized static void setProfiles(List<IngestProfile> profiles) {
        for (IngestProfile profile : profiles) {
            IngestProfile.saveProfile(profile);
        }
    }

    /**
     * An individual Ingest Profile, consists of a name, a description, and a
     * FileIngestFilter. The name can be used to find the ModuleSettings for
     * this profile.
     */
    public static final class IngestProfile {

        private final String name;
        private final String description;
        private final String fileIngestFilter;

        /**
         * Creates a new IngestProfile
         *
         * @param name           - unique name of the profile
         * @param desc           - optional description of profile
         * @param selectedFilter - the File Ingest Filter used for this profile
         */
        IngestProfile(String name, String desc, String selectedFilter) {
            this.name = name;
            this.description = desc;
            this.fileIngestFilter = selectedFilter;
        }

        /**
         * The string value of an IngestProfile is simply its name
         *
         * @return getName();
         */
        @Override
        public String toString() {
            return getName();
        }

        /**
         * The unique name field for this Ingest Profile.
         *
         * @return the name
         */
        String getName() {
            return name;
        }

        /**
         * The optional user defined description of this Ingest Profile.
         *
         * @return the description
         */
        public String getDescription() {
            return description;
        }

        /**
         * The file ingest filter which was selected to be used.
         *
         * @return the fileIngestFilter
         */
        public String getFileIngestFilter() {
            return fileIngestFilter;
        }

        /**
         * Deletes all of the files which are currently storing a profile.
         *
         * @param selectedProfile
         */
        synchronized static void deleteProfile(IngestProfile selectedProfile) {
            deleteProfile(selectedProfile.getName());
        }
        
        /**
         * Deletes all of the files which are currently storing a profile.
         *
         * @param profile name
         */
        synchronized static void deleteProfile(String profileName) {
            try {
                File rootSettingsFile = getRootSettingsFile(profileName);
                File settingsDirectory = getSettingsDirectory(profileName);
                Files.deleteIfExists(rootSettingsFile.toPath());
                FileUtils.deleteDirectory(settingsDirectory);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error deleting directory for profile " + profileName, ex);
            }
        }        

        /**
         * Renames the files and directories associated with a profile
         *
         * @param oldName the name of the profile you want to rename
         * @param newName the name which you want the profile to have
         */
        synchronized static void renameProfile(String oldName, String newName) {
            if (!oldName.equals(newName)) { //if renameProfile was called with the new name being the same as the old name, it is complete already
                File oldRootSettings = getRootSettingsFile(oldName);
                File newRootSettings = getRootSettingsFile(newName);
                oldRootSettings.renameTo(newRootSettings);

                File oldSettingsFolder = getSettingsDirectory(oldName);
                File newSettingsFolder = getSettingsDirectory(newName);
                oldSettingsFolder.renameTo(newSettingsFolder);
            }
        }

        /**
         * Save a Ingest profile file in the profile folder.
         *
         * @param profile
         */
        synchronized static void saveProfile(IngestProfile profile) {
            String context = IngestJobSettings.getModuleSettingsResource(getExecutionContext(profile.getName()));
            ModuleSettings.setConfigSetting(context, PROFILE_NAME_KEY, profile.getName());
            ModuleSettings.setConfigSetting(context, PROFILE_DESC_KEY, profile.getDescription());
            ModuleSettings.setConfigSetting(context, PROFILE_FILTER_KEY, profile.getFileIngestFilter());
        }
    }
}
