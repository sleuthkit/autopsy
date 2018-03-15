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
import java.util.Set;
import org.openide.util.Exceptions;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility and wrapper around data required for Common Files Search results
 */
public class CommonFilesMetaData {

    private List<AbstractFile> dedupedFiles;
    private java.util.Map<String, Integer> instanceCountMap;
    private java.util.Map<String, String> dataSourceMap;

    /**
     * De-dupe list of abstract files and count instances of dupes.  
     * Also collates data sources.
     * 
     * Assumes files are sorted by md5 and that there is at least two of any 
     * given file (no singles are included, only sets of 2 or more).
     * @param files objects to dedupe
     * @return object with deduped file list and maps of files to data sources and number instances
     */
    static CommonFilesMetaData DeDupeFiles(List<AbstractFile> files) {

        CommonFilesMetaData data = new CommonFilesMetaData();

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
                
                try {
                    dataSources.add(file.getDataSource().getName());
                } catch (TskCoreException ex) {
                    //TODO finish this
                    Exceptions.printStackTrace(ex);
                }
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
                                try {
                    dataSources.add(file.getDataSource().getName());
                } catch (TskCoreException ex) {
                    //TODO finish this
                    Exceptions.printStackTrace(ex);
                }
            }
        }

        data.setDedupedFiles(deDupedFiles);
        data.setDataSourceMap(dataSourceMap);
        data.setInstanceCountMap(instanceCountMap);

        return data;
    }

    /**
     * @return the dedupedFiles
     */
    public List<AbstractFile> getDedupedFiles() {
        return Collections.unmodifiableList(dedupedFiles);
    }

    /**
     * @param dedupedFiles the dedupedFiles to set
     */
    public void setDedupedFiles(List<AbstractFile> dedupedFiles) {
        this.dedupedFiles = dedupedFiles;
    }

    /**
     * @return the instanceCountMap
     */
    public java.util.Map<String, Integer> getInstanceCountMap() {
        return Collections.unmodifiableMap(instanceCountMap);
    }

    /**
     * @param instanceCountMap the instanceCountMap to set
     */
    public void setInstanceCountMap(java.util.Map<String, Integer> instanceCountMap) {
        this.instanceCountMap = instanceCountMap;
    }

    /**
     * @return the dataSourceMap
     */
    public java.util.Map<String, String> getDataSourceMap() {
        return Collections.unmodifiableMap(dataSourceMap);
    }

    /**
     * @param dataSourceMap the dataSourceMap to set
     */
    public void setDataSourceMap(java.util.Map<String, String> dataSourceMap) {
        this.dataSourceMap = dataSourceMap;
    }
}
