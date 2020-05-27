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
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.openide.modules.OnStart;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * When the interesting items module loads, this runnable loads standard
 * interesting file set rules.
 */
@OnStart
public class StandardInterestingFileSetsLoader implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(StandardInterestingFileSetsLoader.class.getName());

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
        
        Map<String, FilesSet> userConfiguredSettings = null;
        try {
            userConfiguredSettings = FilesSetsManager.getInstance().getInterestingFilesSets();
        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            LOGGER.log(Level.SEVERE, "Unable to properly read user-configured interesting files sets.", ex);
        }
        
        if (userConfiguredSettings == null) {
            return;
        }
        
        // TODO the rest of this
            
            // Call InterestingItemsFilesSetSettings.readDefinitionsXML for each file in the InterestingFileSetRules directory,
            // setting the read only flag of each (actually one) FilesSet in the returned Map<String, FilesSet> objects and adding
            // the Maps objects to a local Map<String, FilesSet> object.
            
            
            
            //Call FilesSetManager.getInterestingFilesSets and add the Map<String, FilesSet> to the local Map<String, FilesSet> from step “b.”
//The ordering of “b” and “c” avoids overwriting any file set rules defined by the user that incidentally have the same rule set name as the standard rule set.
//Call FilesSetManager.setInterestingFilesSets with the Map<String, FilesSet> from step “c.” 

    }
    
    /**
     * Reads xml definitions for each file found in the standard interesting file set config directory and marks the files set as readonly.
     * @param rulesConfigDir The user configuration directory for standard interesting file set rules.  This is assumed to be non-null.
     * @return The mapping of files set keys to the file sets.
     */
    private static Map<String, FilesSet> readStandardFileXML(File rulesConfigDir) {
        Map<String, FilesSet> standardInterestingFileSets = new HashMap<>();
        if (rulesConfigDir.exists()) {
            for (File standardFileSetsFile : rulesConfigDir.listFiles(DEFAULT_XML_FILTER)) {
                try {
                    Map<String, FilesSet> thisFilesSet = InterestingItemsFilesSetSettings.readDefinitionsXML(standardFileSetsFile);
                    thisFilesSet.values().stream().forEach(filesSet -> filesSet.setReadOnly(true));
                    
                    standardInterestingFileSets.putAll(thisFilesSet);
                } catch (FilesSetsManager.FilesSetsManagerException ex) {
                    LOGGER.log(Level.WARNING, String.format("There was a problem importing the standard interesting file set at: %s.", 
                            standardFileSetsFile.getAbsoluteFile()), ex);
                }
            }
        }
        return standardInterestingFileSets;
    }

    /**
     * Add the InterestingFileSetRules directory to the user’s app data config directory for Autopsy if not already present.
     * @param rulesConfigDir The user configuration directory for standard interesting file set rules.  This is assumed to be non-null.
     */
    private static void copyRulesDirectory(File rulesConfigDir) {       
        if (rulesConfigDir.exists()) {
            LOGGER.info(String.format("%s settings directory already exists.  Not going to perform copy of class resource standard interesting files to directory.", 
                    rulesConfigDir.getAbsolutePath()));
        }

        rulesConfigDir.mkdirs();

        if (!rulesConfigDir.exists()) {
            LOGGER.severe(
                    String.format("Unable to create directory at %s.  Failed to copy standard interesting file set rules to this directory.",
                            rulesConfigDir.getAbsolutePath()));
            return;
        }

        // taken from https://stackoverflow.com/a/19459180
        URL url = StandardInterestingFileSetsLoader.class.getClassLoader().getResource(CONFIG_DIR);
        File resourceDirectory = null;
        try {
            resourceDirectory = new File(url.toURI());
        } catch (URISyntaxException ignored) {
            resourceDirectory = new File(url.getPath());
        }

        if (resourceDirectory == null || !resourceDirectory.exists()) {
            LOGGER.severe(
                    String.format("Unable to find resource directory for standard interesting file sets, %s.",
                            (rulesConfigDir != null) ? rulesConfigDir.getAbsolutePath() : "<null>"));
            return;
        }

        try {
            FileUtils.copyDirectory(resourceDirectory, rulesConfigDir);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, String.format("There was an error copying %s to %s.", 
                    resourceDirectory.getAbsolutePath(), rulesConfigDir.getAbsolutePath()), ex);
        }
    }

}
