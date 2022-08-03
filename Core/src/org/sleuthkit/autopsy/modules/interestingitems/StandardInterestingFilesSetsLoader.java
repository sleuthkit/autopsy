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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.OnStart;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * When the interesting items module loads, this runnable loads standard
 * interesting file set rules and performs upgrades.
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
    @Messages({
        "StandardInterestingFilesSetsLoader_cannotLoadStandard=Unable to properly read standard interesting files sets.",
        "StandardInterestingFilesSetsLoader_cannotLoadUserConfigured=Unable to properly read user-configured interesting files sets.",
        "StandardInterestingFilesSetsLoader_cannotUpdateInterestingFilesSets=Unable to write updated configuration for interesting files sets to config directory."
    })
    public void run() {
        upgradeConfig();
        
        Map<String, FilesSet> standardInterestingFileSets = null;
        try {
            standardInterestingFileSets = readStandardFileXML();
        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            handleError(Bundle.StandardInterestingFilesSetsLoader_cannotLoadStandard(), ex);
            return;
        }

        // Call FilesSetManager.getInterestingFilesSets() to get a Map<String, FilesSet> of the existing rule sets.
        Map<String, FilesSet> userConfiguredSettings = null;
        try {
            userConfiguredSettings = FilesSetsManager.getInstance().getInterestingFilesSets();
        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            LOGGER.log(Level.SEVERE, "Unable to properly read user-configured interesting files sets.", ex);
            handleError(Bundle.StandardInterestingFilesSetsLoader_cannotLoadStandard(), ex);
            return;
        }

        // Add each FilesSet read from the standard rules set XML files that is missing from the Map to the Map.
        copyOnNewer(standardInterestingFileSets, userConfiguredSettings, true);

        try {
            // Call FilesSetManager.setInterestingFilesSets with the updated Map.
            FilesSetsManager.getInstance().setInterestingFilesSets(userConfiguredSettings);
        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            handleError(Bundle.StandardInterestingFilesSetsLoader_cannotUpdateInterestingFilesSets(), ex);
        }
    }

    /**
     * Moves settings to new location.
     */
    private void upgradeConfig() {
        try {
            FilesSetsManager.getInstance().upgradeConfig();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "There was an error while upgrading config paths.", ex);
        }
    }

    /**
     * Responsible for handling top level exceptions and displaying to the user.
     *
     * @param message The message to display and log.
     * @param ex      The exception (if any) to log.
     */
    private static void handleError(String message, Exception ex) {
        LOGGER.log(Level.SEVERE, message, ex);
        if (RuntimeProperties.runningWithGUI()) {
            MessageNotifyUtil.Message.error(message);
        }
    }

    /**
     * Reads xml definitions for each file found in the standard interesting
     * file set config directory and marks the files set as a standard
     * interesting file if it isn't already.
     *
     * @return The mapping of files set keys to the file sets.
     */
    private static Map<String, FilesSet> readStandardFileXML() throws FilesSetsManager.FilesSetsManagerException {
        Map<String, FilesSet> standardInterestingFileSets = new HashMap<>();

        File configFolder = InstalledFileLocator.getDefault().locate(
                CONFIG_DIR, StandardInterestingFilesSetsLoader.class.getPackage().getName(), false);

        if (configFolder == null || !configFolder.exists() || !configFolder.isDirectory()) {
            throw new FilesSetsManager.FilesSetsManagerException("No standard interesting files set folder exists.");
        }

        File[] standardFileSets = configFolder.listFiles(DEFAULT_XML_FILTER);

        for (File standardFileSetsFile : standardFileSets) { //NON-NLS
            try {
                Map<String, FilesSet> thisFilesSet = InterestingItemsFilesSetSettings.readDefinitionsXML(standardFileSetsFile);

                // ensure that read resources are standard sets
                thisFilesSet = thisFilesSet.values()
                        .stream()
                        .map((filesSet) -> getAsStandardFilesSet(filesSet, true))
                        .collect(Collectors.toMap(FilesSet::getName, Function.identity()));

                copyOnNewer(thisFilesSet, standardInterestingFileSets);
            } catch (FilesSetsManager.FilesSetsManagerException ex) {
                LOGGER.log(Level.WARNING, String.format("There was a problem importing the standard interesting file set at: %s.",
                        standardFileSetsFile.getAbsoluteFile()), ex);
            }
        }
        return standardInterestingFileSets;
    }

    /**
     * gets a copy of the Files Set forcing the standard file set flag to what
     * is provided as a parameter.
     *
     * @param origFilesSet     The fileset to get a copy of.
     * @param standardFilesSet Whether or not the copy should be a standard
     *                         files set.
     *
     * @return The copy.
     */
    static FilesSet getAsStandardFilesSet(FilesSet origFilesSet, boolean standardFilesSet) {
        return new FilesSet(
                origFilesSet.getName(),
                origFilesSet.getDescription(),
                origFilesSet.ignoresKnownFiles(),
                origFilesSet.ingoresUnallocatedSpace(),
                origFilesSet.getRules(),
                standardFilesSet,
                origFilesSet.getVersionNumber()
        );
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
                if (appendCustom && srcFileSet.isStandardSet() != destFileSet.isStandardSet()) {
                    if (srcFileSet.isStandardSet()) {
                        addCustomFile(dest, destFileSet);
                        dest.put(key, srcFileSet);
                    } else {
                        addCustomFile(dest, srcFileSet);
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
     * @param srcFilesSet The FilesSet to append as custom. A non-readonly
     *                    filesset must be provided.
     */
    private static void addCustomFile(Map<String, FilesSet> dest, FilesSet srcFilesSet) {
        if (srcFilesSet.isStandardSet()) {
            LOGGER.log(Level.SEVERE, "An attempt to create a custom file that was a standard set.");
            return;
        }

        FilesSet srcToAdd = srcFilesSet;

        do {
            srcToAdd = getAsCustomFileSet(srcToAdd);
        } while (dest.containsKey(srcToAdd.getName()));

        dest.put(srcToAdd.getName(), srcToAdd);
    }

    /**
     * Gets a copy of the FilesSet as a non-standard files set with " (custom)"
     * appended.
     *
     * @param srcFilesSet The files set.
     *
     * @return The altered copy.
     */
    @Messages({
        "# {0} - filesSetName",
        "StandardInterestingFileSetsLoader.customSuffixed={0} (Custom)"
    })
    static FilesSet getAsCustomFileSet(FilesSet srcFilesSet) {
        String customKey = Bundle.StandardInterestingFileSetsLoader_customSuffixed(srcFilesSet.getName());
        return new FilesSet(
                customKey,
                srcFilesSet.getDescription(),
                srcFilesSet.ignoresKnownFiles(),
                srcFilesSet.ingoresUnallocatedSpace(),
                srcFilesSet.getRules(),
                false,
                srcFilesSet.getVersionNumber()
        );
    }
}
