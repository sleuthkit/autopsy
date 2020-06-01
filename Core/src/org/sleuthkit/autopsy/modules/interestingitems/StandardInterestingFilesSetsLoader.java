/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.interestingitems;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.openide.modules.OnStart;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * When the interesting items module loads, this runnable loads standard
 * interesting file set rules.
 */
@OnStart
public class StandardInterestingFilesSetsLoader implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(StandardInterestingFilesSetsLoader.class.getName());

    private static final String CONFIG_DIR = "InterestingFileSetRules";

    private static final FilenameFilter DEFAULT_XML_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(".xml");
        }
    };

    @Override
    public void run() {
        File rulesConfigDir = new File(PlatformUtil.getUserConfigDirectory(), CONFIG_DIR);

        copyRulesDirectory(rulesConfigDir);

        Map<String, FilesSet> standardInterestingFileSets = readStandardFileXML(rulesConfigDir);

        // Call FilesSetManager.getInterestingFilesSets() to get a Map<String, FilesSet> of the existing rule sets.
        Map<String, FilesSet> userConfiguredSettings = null;
        try {
            userConfiguredSettings = FilesSetsManager.getInstance().getInterestingFilesSets();
        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            LOGGER.log(Level.SEVERE, "Unable to properly read user-configured interesting files sets.", ex);
        }

        if (userConfiguredSettings == null) {
            return;
        }

        // Add each FilesSet read from the standard rules set XML files that is missing from the Map to the Map.
        copyOnNewer(standardInterestingFileSets, userConfiguredSettings, true);

        try {
            // Call FilesSetManager.setInterestingFilesSets with the updated Map.
            FilesSetsManager.getInstance().setInterestingFilesSets(userConfiguredSettings);
        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            LOGGER.log(Level.SEVERE, "Unable to write updated configuration for interesting files sets to config directory.", ex);
        }
    }

    /**
     * Reads xml definitions for each file found in the standard interesting
     * file set config directory and marks the files set as readonly.
     *
     * @param rulesConfigDir The user configuration directory for standard
     *                       interesting file set rules. This is assumed to be
     *                       non-null.
     *
     * @return The mapping of files set keys to the file sets.
     */
    private static Map<String, FilesSet> readStandardFileXML(File rulesConfigDir) {
        Map<String, FilesSet> standardInterestingFileSets = new HashMap<>();
        if (rulesConfigDir.exists()) {
            for (File standardFileSetsFile : rulesConfigDir.listFiles(DEFAULT_XML_FILTER)) {
                try {
                    Map<String, FilesSet> thisFilesSet = InterestingItemsFilesSetSettings.readDefinitionsXML(standardFileSetsFile);
                    copyOnNewer(standardInterestingFileSets, thisFilesSet);
                } catch (FilesSetsManager.FilesSetsManagerException ex) {
                    LOGGER.log(Level.WARNING, String.format("There was a problem importing the standard interesting file set at: %s.",
                            standardFileSetsFile.getAbsoluteFile()), ex);
                }
            }
        }
        return standardInterestingFileSets;
    }
    
    
    private static List<String> getResourceFolderContents(String directory) {
        // taken from https://stackoverflow.com/questions/11012819/how-can-i-get-a-resource-folder-from-inside-my-jar-file
        final File jarFile = new File(StandardInterestingFilesSetsLoader.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        List<String> toRet = new ArrayList<>();
        
        if(jarFile.isFile()) {
            JarFile jar = null;
            try {
                jar = new JarFile(jarFile);
                final Enumeration<JarEntry> entries = jar.entries(); //gives ALL entries in jar
                while(entries.hasMoreElements()) {
                    final String name = entries.nextElement().getName();
                    if (name.startsWith(directory + "/")) { //filter according to the path
                        toRet.add(name);
                    }
                }
            jar.close();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "There was an error reading contents of jar file.", ex);
            } finally {
                if (jar != null) {
                    try {
                        jar.close();                        
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, "There was an error closing jar resources while loading contents.", ex);
                    }
                }
            }
        } else {
            LOGGER.log(Level.WARNING, "Jar file could not be opened.  No standard interesting files sets will be loaded.");
        }
        
        return toRet;
    }
    

    /**
     * Add the InterestingFileSetRules directory to the user’s app data config
     * directory for Autopsy if not already present.
     *
     * @param rulesConfigDir The user configuration directory for standard
     *                       interesting file set rules. This is assumed to be
     *                       non-null.
     */
    private static void copyRulesDirectory(File rulesConfigDir) {
        
        try {
            for (String resourceFile : getResourceFolderContents(DEFAULT_XML_FILTER)) {
                updateStandardFilesSetConfigFile(rulesConfigDir, resourceFile);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, String.format("There was an error copying %s to %s.",
                    resourceDirectory.getAbsolutePath(), rulesConfigDir.getAbsolutePath()), ex);
        }
    }

    /**
     * Updates the standard interesting files set config file if there is no
     * corresponding files set on disk or the files set on disk has an older
     * version.
     *
     * @param rulesConfigDir The directory for standard interesting files sets.
     * @param resourceInputStream  The standard interesting files set resource file
     *                       located within the jar.
     */
    private static void updateStandardFilesSetConfigFile(File rulesConfigDir, InputStream resourceInputStream, String resourceName) {
        File configDirFile = new File(rulesConfigDir, resourceName);
        
        Map<String, FilesSet> resourceFilesSet = null;
        try {
            resourceFilesSet = InterestingItemsFilesSetSettings.readDefinitionsXML(resourceFile);
        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            LOGGER.log(Level.SEVERE, "Unable to read FilesSet data from resource file: " + resourceName, ex);
            return;
        }
            
        if (configDirFile.exists()) {
            Map<String, FilesSet> configDirFilesSet = null;
            try {
                configDirFilesSet = InterestingItemsFilesSetSettings.readDefinitionsXML(configDirFile);
            } catch (FilesSetsManager.FilesSetsManagerException ex) {
                LOGGER.log(Level.WARNING, "Unable to read FilesSet data from config file: " + resourceName, ex);
            }

            copyOnNewer(configDirFilesSet, resourceFilesSet);
        }

        try {
            InterestingItemsFilesSetSettings.writeDefinitionsFile(configDirFile.getAbsolutePath(), resourceFilesSet);
        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            String configDirFilePath = configDirFile.getAbsolutePath();
            LOGGER.log(Level.WARNING, "Unable to write FilesSet data to disk at: " + configDirFilePath, ex);
        }
    }

    /**
     * Copies the entries in the src map to the destination map if the src item
     * has a newer version than what is in dest or no equivalent entry exists
     * within the dest map.
     *
     * @param src  The source map.
     * @param dest The destination map.
     */
    private static void copyOnNewer(Map<String, FilesSet> src, Map<String, FilesSet> dest) {
        copyOnNewer(src, dest, false);
    }

    /**
     * Copies the entries in the src map to the destination map if the src item
     * has a newer version than what is in dest or no equivalent entry exists
     * within the dest map.
     *
     * @param src          The source map.
     * @param dest         The destination map.
     * @param appendCustom On conflict, if one of the items is readonly and one
     *                     is not, this flag can be set so the item that is not
     *                     readonly will have " (custom)" appended.
     */
    private static void copyOnNewer(Map<String, FilesSet> src, Map<String, FilesSet> dest, boolean appendCustom) {
        for (Map.Entry<String, FilesSet> srcEntry : src.entrySet()) {
            String key = srcEntry.getKey();
            FilesSet srcFileSet = srcEntry.getValue();
            FilesSet destFileSet = dest.get(key);
            if (destFileSet != null) {
                // If and only if there is a naming conflict with a user-defined rule set, append “(Custom)” 
                // to the user-defined rule set and add it back to the Map.  
                if (appendCustom && srcFileSet.isReadOnly() != destFileSet.isReadOnly()) {
                    if (srcFileSet.isReadOnly()) {
                        addCustomFile(dest, key, destFileSet);
                    } else {
                        addCustomFile(dest, key, srcFileSet);
                        src.put(key, destFileSet);
                    }
                    continue;
                }

                // Replace each FilesSet read from the standard rules set XML files that has a newer version 
                // number than the corresponding FilesSet in the Map with the updated FilesSet. 
                if (destFileSet.getVersionNumber() >= srcEntry.getValue().getVersionNumber()) {
                    continue;
                }
            }

            dest.put(srcEntry.getKey(), srcEntry.getValue());
        }
    }

    /**
     * Adds an entry to the destination map where the name will be the same as
     * the key with " (custom)" appended.
     *
     * @param dest        The destination map.
     * @param key         The key that will be used for the basis of the name
     *                    and the key in the hashmap ("custom" will be
     *                    appended).
     * @param srcFilesSet The FilesSet to append as custom. A non-readonly
     *                    filesset must be provided.
     */
    @Messages({
        "# {0} - filesSetName",
        "StandardInterestingFileSetsLoader.customSuffixed={0} (Custom)"
    })
    private static void addCustomFile(Map<String, FilesSet> dest, String key, FilesSet srcFilesSet) {
        if (srcFilesSet.isReadOnly()) {
            LOGGER.log(Level.SEVERE, "An attempt to create a custom file that was not readonly");
            return;
        }

        String customKey = Bundle.StandardInterestingFileSetsLoader_customSuffixed(key);
        FilesSet customFilesSet = new FilesSet(
                customKey,
                srcFilesSet.getDescription(),
                srcFilesSet.ignoresKnownFiles(),
                srcFilesSet.ingoresUnallocatedSpace(),
                srcFilesSet.getRules(),
                false,
                srcFilesSet.getVersionNumber()
        );
        dest.put(customKey, customFilesSet);
    }

}
