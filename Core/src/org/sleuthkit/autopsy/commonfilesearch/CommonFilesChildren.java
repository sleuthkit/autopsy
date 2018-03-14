/*
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

import java.util.HashMap;
import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.CommonFileNode;

/**
 * Makes nodes for common files search results.
 */
final class CommonFilesChildren extends Children.Keys<AbstractFile> {

    private final java.util.Map<String, Integer> instanceCountMap;
    private final java.util.Map<String, String> dataSourceMap;

    CommonFilesChildren(boolean lazy, List<AbstractFile> fileList, java.util.Map<String, Integer> instanceCountMap, java.util.Map<String, String> dataSourceMap) {
        super(lazy);
        this.setKeys(fileList);

        this.instanceCountMap = instanceCountMap;
        this.dataSourceMap = dataSourceMap;
    }

    @Override
    protected Node[] createNodes(AbstractFile t) {
        
        final String md5Hash = t.getMd5Hash();
        
        int instanceCount = this.instanceCountMap.get(md5Hash);
        String dataSources = this.dataSourceMap.get(md5Hash);
        
        Node[] node = new Node[1];
        node[0] = new CommonFileNode(t, instanceCount, dataSources);
        
        return node;
    }
}
