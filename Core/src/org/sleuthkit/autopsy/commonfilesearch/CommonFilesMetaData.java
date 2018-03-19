/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility and wrapper around data required for Common Files Search results
 */
public class CommonFilesMetaData {

    private static final Logger LOGGER = Logger.getLogger(CommonFilesPanel.class.getName());
    
    private final List<AbstractFile> dedupedFiles;
    private final Map<String, Integer> instanceCountMap;
    private final Map<Long, String> dataSourceMap;
    private final Map<String, String> dataSourcesMap;

    CommonFilesMetaData(List<AbstractFile> theDedupedFiles, Map<String, String> theDataSourceMap, Map<String, Integer> theInstanceCountMap) {
        dedupedFiles = theDedupedFiles;
        instanceCountMap = theInstanceCountMap;
        dataSourcesMap = theDataSourceMap;
    }

    public List<AbstractFile> getFilesList() {
        return Collections.unmodifiableList(dedupedFiles);
    }

    public Map<String, Integer> getInstanceMap() {
        return Collections.unmodifiableMap(instanceCountMap);
    }

    public Map<String, String> getDataSourceMap() {
        return Collections.unmodifiableMap(dataSourcesMap);
    }

    /**
     * De-dupe list of abstract files and count instances of dupes. Also
     * collates data sources.
     *
     * Assumes files are sorted by md5 and that there is at least two of any
     * given file (no singles are included, only sets of 2 or more).
     *
     * @param files objects to dedupe
     * @return object with deduped file list and maps of files to data sources
     * and number instances
     */
    static CommonFilesMetaData CollateFiles(List<AbstractFile> files) {
        List<AbstractFile> deDupedFiles = new ArrayList<>();
        java.util.Map<String, String> dataSourceMap = new HashMap<>();
        java.util.Map<String, Integer> instanceCountMap = new HashMap<>();

        AbstractFile previousFile = null;
        String previousMd5 = "";
        int instanceCount = 0;

        Set<String> dataSources = new HashSet<>();

        for (AbstractFile file : files) {

            String currentMd5 = file.getMd5Hash();
            if (currentMd5.equals(previousMd5)) {
                instanceCount++;
                addDataSource(dataSources, file);
            } else {
                if (previousFile != null) {
                    deDupedFiles.add(previousFile);
                    instanceCountMap.put(previousMd5, instanceCount);
                    dataSourceMap.put(previousMd5, String.join(", ", dataSources));
                }
                previousFile = file;
                previousMd5 = currentMd5;
                instanceCount = 1;
                dataSources.clear();
                addDataSource(dataSources, file);
            }
        }
        CommonFilesMetaData data = new CommonFilesMetaData(deDupedFiles, dataSourceMap, instanceCountMap);
        return data;
    }

    private static void addDataSource(Set<String> dataSources, AbstractFile file) {
        try {
            dataSources.add(file.getDataSource().getName());
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, String.format("Unable to determine data source for file with ID: {0}.", file.getId()), ex);
            dataSources.add("Unknown Source");
        }
    }
}
