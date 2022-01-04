/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.util.Collection;
import java.util.Collections;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;

/**
 * Children implementation for the root node of a ContentNode tree. Accepts a
 * list of root Content objects for the tree.
 */
public class RootContentChildren extends Children.Keys<Object> {

    private final Collection<? extends Object> contentKeys;
    private final CreateSleuthkitNodeVisitor createSleuthkitNodeVisitor = new CreateSleuthkitNodeVisitor();

    /**
     * @param contentKeys root Content objects for the Node tree
     */
    public RootContentChildren(Collection<? extends Object> contentKeys) {
        super();
        this.contentKeys = contentKeys;
    }

    @Override
    protected void addNotify() {
        setKeys(contentKeys);
    }

    @Override
    protected void removeNotify() {
        setKeys(Collections.<Object>emptySet());
    }

    /**
     * Refresh all content keys This creates new nodes of keys have changed.
     *
     * TODO ideally, nodes would respond to event from wrapped content object
     * but we are not ready for this.
     */
    public void refreshContentKeys() {
        contentKeys.forEach(this::refreshKey);
    }

    @Override
    protected Node[] createNodes(Object key) {
        Node node = createNode(key);
        if (node != null) {
            return new Node[]{node};
        } else {
            return new Node[]{((SleuthkitVisitableItem) key).accept(createSleuthkitNodeVisitor)};
        }
    }

    /**
     * Creates a node for one of the known object keys that is not a sleuthkit
     * item.
     *
     * @param key The node key.
     *
     * @return The generated node or null if no match found.
     */
    static Node createNode(Object key) {
        if (key instanceof Tags) {
            Tags tagsNodeKey = (Tags) key;
            return tagsNodeKey.new RootNode(tagsNodeKey.filteringDataSourceObjId());
        } else if (key instanceof DataSources) {
            DataSources dataSourcesKey = (DataSources) key;
            return new DataSourceFilesNode(dataSourcesKey.filteringDataSourceObjId());
        } else if (key instanceof DataSourceGrouping) {
            DataSourceGrouping dataSourceGrouping = (DataSourceGrouping) key;
            return new DataSourceGroupingNode(dataSourceGrouping.getDataSource());
        } else if (key instanceof Views) {
            Views v = (Views) key;
            return new ViewsNode(v.filteringDataSourceObjId());
        } else if (key instanceof Reports) {
            return new Reports.ReportsListNode();
        } else if (key instanceof OsAccounts) {
            OsAccounts osAccountsItem = (OsAccounts) key;
            return osAccountsItem.new OsAccountListNode();
        } else if (key instanceof PersonGrouping) {
            PersonGrouping personGrouping = (PersonGrouping) key;
            return new PersonNode(personGrouping.getPerson());
        } else if (key instanceof HostDataSources) {
            HostDataSources hosts = (HostDataSources) key;
            return new HostNode(hosts);
        } else if (key instanceof HostGrouping) {
            HostGrouping hostGrouping = (HostGrouping) key;
            return new HostNode(hostGrouping);
        } else if (key instanceof DataSourcesByType) {
            return new DataSourcesNode();
        } else if (key instanceof AnalysisResults) {
            AnalysisResults analysisResults = (AnalysisResults) key;
            return new AnalysisResults.RootNode(
                    analysisResults.getFilteringDataSourceObjId());
        } else if (key instanceof DataArtifacts) {
            DataArtifacts dataArtifacts = (DataArtifacts) key;
            return new DataArtifacts.RootNode(
                    dataArtifacts.getFilteringDataSourceObjId());
        } else {
            return null;
        }
    }
}
