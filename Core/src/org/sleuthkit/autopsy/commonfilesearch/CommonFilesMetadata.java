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
import java.util.Map;

/**
 * Utility and wrapper model around data required for Common Files Search
 * results. Subclass this to implement different selections of files from the
 * case.
 */
final class CommonFilesMetadata {

    private final Map<String, Md5Metadata> metadata;

    /**
     * Create meta dat object which can be handed off to the node factories
     *
     * @param metadata map of md5 to parent-level node meta data
     * @param dataSourcesMap map of obj_id to data source name
     */
    CommonFilesMetadata(Map<String, Md5Metadata> metadata) {
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
    Md5Metadata getMetadataForMd5(String md5) {
        return this.metadata.get(md5);
    }

    Map<String, Md5Metadata> getMetadata() {
        return Collections.unmodifiableMap(this.metadata);
    }

    int size() {
        int count = 0;
        for (Md5Metadata data : this.metadata.values()) {
            count += data.size();
        }
        return count;
    }
}
