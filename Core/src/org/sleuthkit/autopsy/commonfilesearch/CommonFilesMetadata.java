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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility and wrapper model around data required for Common Files Search results.
 * Subclass this to implement different selections of files from the case.
 */
final public class CommonFilesMetadata {
    
    private final Map<Integer, Md5MetadataList> metadata;
    
    /**
     * Create a metadata object which can be handed off to the node
     * factories.
     * 
     * @param metadata list of Md5Metadata indexed by size of Md5Metadata
     */
    CommonFilesMetadata(Map<Integer, Md5MetadataList> metadata){
        this.metadata = metadata;
    }

    /**
     * Find the meta data for the given md5.
     *
     * This is a convenience method - you can also iterate over
     * <code>getMetadata()</code>.
     *
     * @param md5 key
     * @return 
     */
    Md5MetadataList getMetadataForMd5(Integer instanceCount) {
        return this.metadata.get(instanceCount);
    }

    public Map<Integer, Md5MetadataList> getMetadata() {
        return Collections.unmodifiableMap(this.metadata);
    }

    /**
     * How many distinct file instances exist for this metadata?
     * @return number of file instances
     */
    public int size() {
                
        int count = 0;
        for (Md5MetadataList data : this.metadata.values()) {
            for(Md5Metadata md5 : data.getMetadataList()){
                count += md5.size();
            }
        }
        return count;
    }
}
