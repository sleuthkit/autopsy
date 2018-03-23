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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.sleuthkit.datamodel.AbstractFile;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.CommonFileParentNode;

/**
 * Makes nodes for common files search results.
 */
final class CommonFilesChildren extends ChildFactory<String> {

    private CommonFilesMetaData metaData;

    CommonFilesChildren(CommonFilesMetaData theMetaData) {
        super();
        this.metaData = theMetaData;
    }

    protected void removeNotify() {
        metaData = null;
    }

    @Override
    protected Node createNodeForKey(String md5) {

        List<AbstractFile> children = this.metaData.getChildrenForFile(md5);

        int instanceCount = children.size();
        String dataSources = selectDataSources(children);

        return new CommonFileParentNode(Children.create(new CommonFilesDescendants(children, this.metaData.getDataSourceIdToNameMap()), true), md5, instanceCount, dataSources);
    }

    @Override
    protected boolean createKeys(List<String> toPopulate) {
        final Map<String, List<org.sleuthkit.datamodel.AbstractFile>> filesMap = this.metaData.getFilesMap();
        Collection<String> files = filesMap.keySet();
        toPopulate.addAll(files);
        return true;
    }

    private String selectDataSources(List<AbstractFile> children) {

        Map<Long, String> dataSources = this.metaData.getDataSourceIdToNameMap();

        Set<String> dataSourceStrings = new HashSet<>();

        for (AbstractFile child : children) {

            String dataSource = dataSources.get(child.getDataSourceObjectId());

            dataSourceStrings.add(dataSource);
        }

        return String.join(", ", dataSourceStrings);
    }
}
