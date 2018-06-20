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
import java.util.List;

/**
 * Utility and wrapper model around data required for Common Files Search results.
 * Subclass this to implement different selections of files from the case.
 */
final public class Md5MetadataList {
    
    private final List<Md5Metadata> metadataList;
    private final List<Md5Metadata> delayedMetadataList;
    
    /**
     * Create a metadata object containing the list of metadata which can be handed off to the node
     * factories.
     * 
     * @param metadata list of Md5Metadata indexed by size of Md5Metadata
     */
    Md5MetadataList(List<Md5Metadata> metadata){
        this.metadataList = new ArrayList<Md5Metadata>();
        this.delayedMetadataList = metadata;
    }
    
    Md5MetadataList(){
        this.metadataList = new ArrayList<>();
        this.delayedMetadataList = new ArrayList<>();
    }

    public List<Md5Metadata> getMetadataList() {
        return Collections.unmodifiableList(this.metadataList);
    }
    
    public void displayDelayedMetadata() {
        this.metadataList.addAll(this.delayedMetadataList);
    }
    
    public void addMetadataToList(Md5Metadata metadata) {
        delayedMetadataList.add(metadata);
    }
}
