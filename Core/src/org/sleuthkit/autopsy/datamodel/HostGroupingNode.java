/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;

/**
 * A node to be displayed in the UI tree for a host and data sources grouped in
 * this host.
 */
@NbBundle.Messages(value = {"HostNode_unknownHostNode_title=Unknown Host"})
class HostGroupingNode extends DisplayableItemNode {

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/host.png";

    /**
     * Filters and sorts data source groupings to be displayed in the tree.
     *
     * @param dataSources The data source grouping data.
     * @return The data source groupings to be displayed.
     */
    private static List<DataSourceGrouping> getSortedFiltered(Collection<DataSourceGrouping> dataSources) {
        return (dataSources == null) ? Collections.emptyList()
                : dataSources.stream()
                        .filter(ds -> ds != null && ds.getDataSource() != null)
                        .sorted((a, b) -> {
                            String aStr = a.getDataSource().getName() == null ? "" : a.getDataSource().getName();
                            String bStr = b.getDataSource().getName() == null ? "" : b.getDataSource().getName();
                            return aStr.compareToIgnoreCase(bStr);
                        })
                        .collect(Collectors.toList());
    }

    private final boolean isLeaf;

    /**
     * Main constructor.
     *
     * @param host The host grouping data.
     */
    HostGroupingNode(HostGrouping host) {
        this(host, getSortedFiltered(host == null ? null : host.getDataSources()));
    }

    private HostGroupingNode(HostGrouping hostGroup, List<DataSourceGrouping> dataSources) {
        super(new RootContentChildren(dataSources), hostGroup == null ? null : Lookups.singleton(hostGroup));

        String safeName = (hostGroup == null || hostGroup.getHost() == null || hostGroup.getHost().getName() == null)
                ? Bundle.HostNode_unknownHostNode_title()
                : hostGroup.getHost().getName();

        super.setName(safeName);
        super.setDisplayName(safeName);
        this.setIconBaseWithExtension(ICON_PATH);
        this.isLeaf = CollectionUtils.isEmpty(dataSources);
    }

    @Override
    public boolean isLeafTypeNode() {
        return isLeaf;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

}
