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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility and wrapper model around data required for Common Files Search results.
 * Subclass this to implement different selections of files from the case.
 */
public class CommonFilesMetaData {
    
    private final Map<String, Md5MetaData> metadata;
    private final Map<Long, String> dataSourceIdToNameMap;

    CommonFilesMetaData(Map<String, Md5MetaData> metadata, Map<Long,String> dataSourcesMap) {
        this.metadata = metadata;
        this.dataSourceIdToNameMap = dataSourcesMap;
    }
    
    public Md5MetaData getMetaDataForMd5(String md5){
        return this.metadata.get(md5);
    }
    
    public Map<String, Md5MetaData> getMataData(){
        return Collections.unmodifiableMap(this.metadata);
    }
    
    public Map<Long, String> getDataSourceIdToNameMap() {
        return Collections.unmodifiableMap(this.dataSourceIdToNameMap);
    }

    int size() {
        int count = 0;
        for(Md5MetaData data : this.metadata.values()){
            count += data.size();
        }        
        return count;
    }
}
