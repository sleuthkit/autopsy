/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.HashSet;
import java.util.TreeMap;
import org.apache.commons.io.FileUtils;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

public class IngestProfileMap {

    private static final String PROFILE_FOLDER = "IngestProfiles";
    private static final String PROFILE_NAME_KEY = "Profile_Name";
    private static final String PROFILE_DESC_KEY = "Profile_Description";
    private static final String PROFILE_FILTER_KEY = "Profile_Filter";
    private TreeMap<String, IngestProfile> profileMap = null;
    private static final Object PROFILE_LOCK = new Object();

    /**
     * Gets the collection of profiles which currently exist.
     *
     * @return profileList
     */
    public TreeMap<String, IngestProfile> getIngestProfileMap() {
        if (profileMap == null) {
            loadProfileList();
        }
        return profileMap;
    }

    /**
     * Read the stored profile definitions from their directory.
     */
    private void readFilesFromDirectory() {
        synchronized (PROFILE_LOCK) {
            File dir = Paths.get(PlatformUtil.getUserConfigDirectory(), PROFILE_FOLDER).toFile();
            File[] directoryListing = dir.listFiles();

            profileMap = new TreeMap<>();
            if (directoryListing != null) {
                for (File child : directoryListing) {
                    String name = child.getName().split("\\.")[0];
                    String context = PROFILE_FOLDER + File.separator + name;
                    String desc = ModuleSettings.getConfigSetting(context, PROFILE_DESC_KEY);
                    String fileIngestFilter = ModuleSettings.getConfigSetting(context, PROFILE_FILTER_KEY);
                    profileMap.put(name, new IngestProfile(name, desc, fileIngestFilter));
                }
            }
        }
    }

    /**
     * Loads the list of profiles from disk.
     */
    void loadProfileList() {
        readFilesFromDirectory();
    }

    /**
     * Saves the list of profiles which currently exist to disk.
     */
    void saveProfileList() {
        //save last used profile
        for (IngestProfile profile : getIngestProfileMap().values()) {
            IngestProfile.saveProfile(profile);
        }
    }

    /**
     * An individual Ingest Profile, consists of a name, a description, and a
     * FileIngestFilter. The name can be used to find the ModuleSettings for
     * this profile.
     */
   public static class IngestProfile {

        private final String name;
        private final String description;
        private final String fileIngestFilter;

        /**
         * The key for Enabled ingest modules
         *
         * @return the ENABLED_MODULES_KEY
         */
        static String getEnabledModulesKey() {
            return IngestJobSettings.getEnabledModulesKey();
        }

        IngestProfile(String name, String desc, String selected) {
            this.name = name;
            this.description = desc;
            this.fileIngestFilter = selected;
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
        String getFileIngestFilter() {
            return fileIngestFilter;
        }

        /**
         * Deletes all of the files which are currently storing a profile.
         *
         * @param selectedProfile
         */
        static void deleteProfile(IngestProfile selectedProfile) {
            synchronized (PROFILE_LOCK) {
                try {
                    Files.deleteIfExists(Paths.get(PlatformUtil.getUserConfigDirectory(), PROFILE_FOLDER, selectedProfile.getName() + ".properties"));
                    Files.deleteIfExists(Paths.get(PlatformUtil.getUserConfigDirectory(), selectedProfile.getName() + ".properties"));
                    FileUtils.deleteDirectory(IngestJobSettings.getSavedModuleSettingsFolder(selectedProfile.getName() + File.separator).toFile());
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

        /**
         * Renames the files and directories associated with a profile
         *
         * @param oldName the name of the profile you want to rename
         * @param newName the name which you want the profile to have
         */
        static void renameProfile(String oldName, String newName) {
            if (!oldName.equals(newName)) { //if renameProfile was called with the new name being the same as the old name, it is complete already
                synchronized (PROFILE_LOCK) {
                    File oldFile = Paths.get(PlatformUtil.getUserConfigDirectory(), PROFILE_FOLDER, oldName + ".properties").toFile();
                    File newFile = Paths.get(PlatformUtil.getUserConfigDirectory(), PROFILE_FOLDER, newName + ".properties").toFile();
                    oldFile.renameTo(newFile);
                    oldFile = Paths.get(PlatformUtil.getUserConfigDirectory(), oldName + ".properties").toFile();
                    newFile = Paths.get(PlatformUtil.getUserConfigDirectory(), newName + ".properties").toFile();
                    oldFile.renameTo(newFile);
                    oldFile = IngestJobSettings.getSavedModuleSettingsFolder(oldName + File.separator).toFile();
                    newFile = IngestJobSettings.getSavedModuleSettingsFolder(newName + File.separator).toFile();
                    oldFile.renameTo(newFile);
                }
            }
        }

        /**
         * Gets the module names for a given key.
         *
         * @param key The key string.
         */
        HashSet<String> getModuleNames(String key) {
            synchronized (PROFILE_LOCK) {
                if (ModuleSettings.settingExists(this.getName(), key) == false) {
                    ModuleSettings.setConfigSetting(this.getName(), key, "");
                }
                HashSet<String> moduleNames = new HashSet<>();
                String modulesSetting = ModuleSettings.getConfigSetting(this.getName(), key);
                if (!modulesSetting.isEmpty()) {
                    String[] settingNames = modulesSetting.split(", ");
                    for (String name : settingNames) {
                        // Map some old core module names to the current core module names.
                        switch (name) {
                            case "Thunderbird Parser": //NON-NLS
                            case "MBox Parser": //NON-NLS
                                moduleNames.add("Email Parser"); //NON-NLS
                                break;
                            case "File Extension Mismatch Detection": //NON-NLS
                                moduleNames.add("Extension Mismatch Detector"); //NON-NLS
                                break;
                            case "EWF Verify": //NON-NLS
                            case "E01 Verify": //NON-NLS
                                moduleNames.add("E01 Verifier"); //NON-NLS
                                break;
                            case "Archive Extractor": //NON-NLS
                                moduleNames.add("Embedded File Extractor"); //NON-NLS
                                break;
                            default:
                                moduleNames.add(name);
                        }
                    }
                }
                return moduleNames;
            }
        }

        /**
         * Save a Ingest profile file in the profile folder.
         *
         * @param profile
         */
        static void saveProfile(IngestProfile profile) {
            synchronized (PROFILE_LOCK) {
                String context = PROFILE_FOLDER + File.separator + profile.getName();
                ModuleSettings.setConfigSetting(context, PROFILE_NAME_KEY, profile.getName());
                ModuleSettings.setConfigSetting(context, PROFILE_DESC_KEY, profile.getDescription());
                ModuleSettings.setConfigSetting(context, PROFILE_FILTER_KEY, profile.getFileIngestFilter());
            }
        }
    }
}
