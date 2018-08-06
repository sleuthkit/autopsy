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
 * Utility and wrapper model around data required for Common Files Search
 * results. Subclass this to implement different selections of files from the
 * case.
 */
final public class CommonAttributeValueList {

    private final List<CommonAttributeValue> metadataList;
    private final List<CommonAttributeValue> delayedMetadataList;

    /**
     * Create a metadata object containing the list of metadata which can be
     * handed off to the node factories.
     *
     * @param metadata list of Md5Metadata indexed by size of Md5Metadata
     */
    CommonAttributeValueList(List<CommonAttributeValue> metadata) {
        this.metadataList = new ArrayList<>();
        this.delayedMetadataList = metadata;
    }

    CommonAttributeValueList() {
        this.metadataList = new ArrayList<>();
        this.delayedMetadataList = new ArrayList<>();
    }

    public List<CommonAttributeValue> getMetadataList() {
        return Collections.unmodifiableList(this.metadataList);
    }
    
    public int getCommonAttributeListSize() {
        return  this.delayedMetadataList.size();
    }

    public void displayDelayedMetadata() {
        if (metadataList.isEmpty()) {
            this.metadataList.addAll(this.delayedMetadataList);
        }
    }

    public void addMetadataToList(CommonAttributeValue metadata) {
        delayedMetadataList.add(metadata);
    }
}
