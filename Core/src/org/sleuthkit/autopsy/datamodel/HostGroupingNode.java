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

import com.google.common.eventbus.Subscribe;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Host;

/**
 * A node to be displayed in the UI tree for a host and data sources grouped in
 * this host.
 */
@NbBundle.Messages(value = {"HostNode_unknownHostNode_title=Unknown Host"})
class HostGroupingNode extends DisplayableItemNode {

    private static class HostChildren extends ChildFactory<DataSource> {

        private final Set<DataSource> dataSources = new HashSet<>();

        @Override
        protected boolean createKeys(List<DataSource> toPopulate) {
            toPopulate.addAll(dataSources);
            return true;
        }

        @Override
        protected DataSourceGroupingNode createNodeForKey(DataSource key) {
            return key == null ? null : new DataSourceGroupingNode(key);
        }

        private void refresh(Set<DataSource> newDataSources) {
            dataSources.clear();
            if (newDataSources != null) {
                dataSources.addAll(newDataSources);
            }
            super.refresh(true);
        }
    }

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/host.png";

    private final Host host;
    private final HostChildren hostChildren;
    
    /**
     * Filters and sorts data source groupings to be displayed in the tree.
     *
     * @param dataSources The data source grouping data.
     * @return The data source groupings to be displayed.
     */
//    private static List<DataSourceGrouping> getSortedFiltered(Collection<DataSourceGrouping> dataSources) {
//        return (dataSources == null) ? Collections.emptyList()
//                : dataSources.stream()
//                        .filter(ds -> ds != null && ds.getDataSource() != null)
//                        .sorted((a, b) -> {
//                            String aStr = a.getDataSource().getName() == null ? "" : a.getDataSource().getName();
//                            String bStr = b.getDataSource().getName() == null ? "" : b.getDataSource().getName();
//                            return aStr.compareToIgnoreCase(bStr);
//                        })
//                        .collect(Collectors.toList());
//    }
    HostGroupingNode(Host host) {
        this(host, new HostChildren());
    }
    
    private HostGroupingNode(Host host, HostChildren hostChildren) {
        super(Children.create(hostChildren, false), host == null ? null : Lookups.singleton(host));

        String safeName = (host == null || host.getName() == null)
                ? Bundle.HostNode_unknownHostNode_title()
                : host.getName();

        super.setName(safeName);
        super.setDisplayName(safeName);
        this.setIconBaseWithExtension(ICON_PATH);
        this.host = host;
        this.hostChildren = hostChildren;
    }
    
    @Subscribe
    void update(Map<Long, Set<DataSource>> hostDataSourceMapping) {
        Long id = this.host == null ? null : host.getId();
        Set<DataSource> dataSources = hostDataSourceMapping == null ? null : hostDataSourceMapping.get(id);
        this.hostChildren.refresh(dataSources);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
