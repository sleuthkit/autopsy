/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility and wrapper model around data required for Common Files Search
 * results. Subclass this to implement different selections of files from the
 * case.
 */
final public class CommonAttributeValueList {

    /**
     * The list of value nodes, which begins empty.
     */
    private final List<CommonAttributeValue> metadataList;

    /**
     * The backing list of value nodes, which will be dynamically loaded when
     * requested.
     */
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

    /**
     * Get the list of value nodes. Will be empty if displayDelayedMetadata()
     * has not been called for the parent InstanceCountNode
     *
     * @return metadataList the list of nodes
     */
    public List<CommonAttributeValue> getMetadataList() {
        return Collections.unmodifiableList(this.metadataList);
    }

    /**
     * Get the delayed set of value nodes. Only use for determining which values
     * and how many CommonAttributeValues actually exist in the list.
     *
     * @return metadataList the set of nodes
     */
    Set<CommonAttributeValue> getDelayedMetadataSet() {
        //Allows nodes to be de-duped
        return new HashSet<>(this.delayedMetadataList);
    }

    void removeMetaData(CommonAttributeValue commonVal) {
        this.delayedMetadataList.remove(commonVal);
    }

    /**
     * Return the size of the backing list, in case displayDelayedMetadata() has
     * not be called yet.
     *
     * @return int the number of matches for this value
     */
    int getCommonAttributeListSize() {
        return this.delayedMetadataList.size();
    }

    /**
     * Dynamically load the list CommonAttributeValue when called. Until called
     * metadataList should be empty. The parent node, InstanceCountNode, will
     * trigger the factory call and refresh.
     */
    public void displayDelayedMetadata() {
        if (metadataList.isEmpty()) {
            this.metadataList.addAll(this.delayedMetadataList);
        }
    }

    /**
     * A a value node to the list, to be loaded later.
     *
     * @param metadata the node to add
     */
    void addMetadataToList(CommonAttributeValue metadata) {
        delayedMetadataList.add(metadata);
    }
}
