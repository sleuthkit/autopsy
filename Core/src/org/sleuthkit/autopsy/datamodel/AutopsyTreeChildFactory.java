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
package org.sleuthkit.autopsy.datamodel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Child factory to create the top level children of the autopsy tree
 *
 */
public final class AutopsyTreeChildFactory extends ChildFactory.Detachable<Object> {

    private static final Logger logger = Logger.getLogger(AutopsyTreeChildFactory.class.getName());

    /**
     * Listener for handling DATA_SOURCE_ADDED events.
     */
    private final PropertyChangeListener pcl = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())
                    && Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
                refreshChildren();
            }
        }
    };

    @Override
    protected void addNotify() {
        super.addNotify();
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
    }

    @Override
    protected void removeNotify() {
        super.removeNotify();
        Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
    }

    private DataSourceGrouping getDs(List<DataSource> dataSources, int idx) {
        return new DataSourceGrouping(dataSources.get(idx % dataSources.size()));
    }

    private OwnerHostTree getTree(SleuthkitCase tskCase) throws TskCoreException {
        List<DataSource> dataSources = tskCase.getDataSources();
        if (CollectionUtils.isEmpty(dataSources)) {
            return new OwnerHostTree(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        return new OwnerHostTree(
                Arrays.asList(
                        new OwnerNodeData("Owner1", Arrays.asList(
                                new HostNodeData("Host1.1", Arrays.asList(getDs(dataSources, 0), getDs(dataSources, 1))),
                                new HostNodeData("Host1.2", Collections.emptyList())),
                                Arrays.asList(getDs(dataSources, 2))
                        ),
                        new OwnerNodeData("Owner1", Arrays.asList(
                                new HostNodeData("Host1.1", Arrays.asList(getDs(dataSources, 0), getDs(dataSources, 1))),
                                new HostNodeData("Host2.3", Collections.emptyList())),
                                Arrays.asList(getDs(dataSources, 3))
                        )
                ),
                Arrays.asList(
                        new HostNodeData("Host0.1", Arrays.asList(getDs(dataSources, 4))),
                        new HostNodeData("Host0.2", Arrays.asList(getDs(dataSources, 5)))
                ),
                Arrays.asList(getDs(dataSources, 6), getDs(dataSources, 7))
        );
    }

    private List<AutopsyVisitableItem> getNodes(OwnerHostTree tree) {
        List<OwnerNodeData> owners = (tree == null) ? null : tree.getOwners();
        List<HostNodeData> hosts = (tree == null) ? null : tree.getHosts();
        List<DataSourceGrouping> dataSources = (tree == null) ? null : tree.getDataSources();

        if (CollectionUtils.isNotEmpty(owners)) {
            List<AutopsyVisitableItem> toReturn = owners.stream()
                    .filter(o -> o != null)
                    .sorted((a, b) -> compareIgnoreCase(a.getName(), b.getName()))
                    .collect(Collectors.toList());

            if (CollectionUtils.isNotEmpty(hosts) || CollectionUtils.isNotEmpty(dataSources)) {
                toReturn.add(OwnerNodeData.getUnknown(hosts, dataSources));
            }

            return toReturn;
        } else if (CollectionUtils.isNotEmpty(hosts)) {
            List<AutopsyVisitableItem> toReturn = hosts.stream()
                    .filter(h -> h != null)
                    .sorted((a, b) -> compareIgnoreCase(a.getName(), b.getName()))
                    .collect(Collectors.toList());

            if (CollectionUtils.isNotEmpty(dataSources)) {
                toReturn.add(HostNodeData.getUnknown(dataSources));
            }

            return toReturn;
        } else {
            Stream<DataSourceGrouping> dataSourceSafe = dataSources == null ? Stream.empty() : dataSources.stream();
            return dataSourceSafe
                    .filter(d -> d != null && d.getDataSource() != null)
                    .sorted((a, b) -> compareIgnoreCase(a.getDataSource().getName(), b.getDataSource().getName()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Creates keys for the top level children.
     *
     * @param list list of keys created
     * @return true, indicating that the key list is complete
     */
    @Override
    protected boolean createKeys(List<Object> list) {
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();

            if (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
                list.addAll(getNodes(getTree(tskCase)));
                list.add(new Reports());
            } else {
                List<AutopsyVisitableItem> keys = new ArrayList<>(Arrays.asList(
                        new DataSources(),
                        new Views(tskCase),
                        new Results(tskCase),
                        new Tags(),
                        new Reports()));

                list.addAll(keys);
            }
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "Error getting datas sources list from the database.", tskCoreException);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
        }
        return true;
    }

    /**
     * Creates nodes for the top level Key
     *
     * @param key
     *
     * @return Node for the key, null if key is unknown.
     */
    @Override
    protected Node createNodeForKey(Object key) {

        if (key instanceof SleuthkitVisitableItem) {
            return ((SleuthkitVisitableItem) key).accept(new CreateSleuthkitNodeVisitor());
        } else if (key instanceof AutopsyVisitableItem) {
            return ((AutopsyVisitableItem) key).accept(new RootContentChildren.CreateAutopsyNodeVisitor());
        }  else {
            logger.log(Level.SEVERE, "Unknown key type ", key.getClass().getName());
            return null;
        }
    }

    /**
     * Refresh the children
     */
    public void refreshChildren() {
        refresh(true);

    }

    private static int compareIgnoreCase(String s1, String s2) {
        return (s1 == null ? "" : s1).compareToIgnoreCase(s2 == null ? "" : s2);
    }

    public static class OwnerNodeData implements AutopsyVisitableItem {
        public static OwnerNodeData getUnknown(List<HostNodeData> hosts, List<DataSourceGrouping> dataSources) {
            return new OwnerNodeData(true, null, hosts, dataSources);
        }

        private final String name;
        private final List<HostNodeData> hosts;
        private final List<DataSourceGrouping> dataSources;
        private final boolean unknown;

        public OwnerNodeData(String name, List<HostNodeData> hosts, List<DataSourceGrouping> dataSources) {
            this(name == null, name, hosts, dataSources);
        }

        private OwnerNodeData(boolean isUnknown, String name, List<HostNodeData> hosts, List<DataSourceGrouping> dataSources) {
            this.unknown = isUnknown;
            this.name = name;
            this.hosts = hosts;
            this.dataSources = dataSources;
        }

        public String getName() {
            return name;
        }
        
        public Object getData() {
            // TODO
            return name;
        }

        public List<HostNodeData> getHosts() {
            return hosts;
        }

        public List<DataSourceGrouping> getDataSources() {
            return dataSources;
        }

        public boolean isUnknown() {
            return unknown;
        }

        @Override
        public <T> T accept(AutopsyItemVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static class HostNodeData implements AutopsyVisitableItem {
        public static HostNodeData getUnknown(List<DataSourceGrouping> dataSources) {
            return new HostNodeData(true, null, dataSources);
        }

        private final String name;
        private final List<DataSourceGrouping> dataSources;
        private final boolean unknown;

        public HostNodeData(String name, List<DataSourceGrouping> dataSources) {
            this(name == null, name, dataSources);
        }

        private HostNodeData(boolean isUnknown, String name, List<DataSourceGrouping> dataSources) {
            this.unknown = isUnknown;
            this.name = name;
            this.dataSources = (dataSources == null) ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<DataSourceGrouping>(dataSources));
        }

        public List<DataSourceGrouping> getDataSources() {
            return this.dataSources;
        }

        public String getName() {
            return name;
        }
        
        public Object getData() {
            // TODO
            return name;
        }

        public boolean isUnknown() {
            return unknown;
        }

        @Override
        public <T> T accept(AutopsyItemVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    @Messages({
        "HostNode_unknownHostNode_title=Unknown Host"
    })
    static class HostNode extends DisplayableItemNode {
        private static RootContentChildren getChildren(List<DataSourceGrouping> dataSources) {
            Stream<DataSourceGrouping> dsNodes = (dataSources == null)
                    ? Stream.empty()
                    : dataSources.stream()
                            .filter(ds -> ds != null && ds.getDataSource() != null)
                            .sorted((a, b) -> compareIgnoreCase(a.getDataSource().getName(), b.getDataSource().getName()));
                            
            return new RootContentChildren(dsNodes.collect(Collectors.toList()));
        }

        private HostNode(HostNodeData host, List<DataSourceGrouping> dataSources) {
            super(getChildren(dataSources), host == null ? null : Lookups.singleton(host));
            String safeName = (host == null || host.getName() == null || host.isUnknown()) ? Bundle.HostNode_unknownHostNode_title() : host.getName();
            super.setName(safeName);
            super.setDisplayName(safeName);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/image.png");
        }

        HostNode(HostNodeData host) {
            this(host, host == null ? null : host.getDataSources());
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
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

    private static class OwnerHostTree {

        private final List<OwnerNodeData> owners;
        private final List<HostNodeData> hosts;
        private final List<DataSourceGrouping> dataSources;

        public OwnerHostTree(List<OwnerNodeData> owners, List<HostNodeData> hosts, List<DataSourceGrouping> dataSources) {
            this.owners = owners;
            this.hosts = hosts;
            this.dataSources = dataSources;
        }

        public List<OwnerNodeData> getOwners() {
            return owners;
        }

        public List<HostNodeData> getHosts() {
            return hosts;
        }

        public List<DataSourceGrouping> getDataSources() {
            return dataSources;
        }

    }

    @Messages({
        "OwnerNode_unknownHostNode_title=Unknown Owner"
    })
    static class OwnerNode extends DisplayableItemNode {
        private static RootContentChildren getChildren(List<HostNodeData> hosts, List<DataSourceGrouping> dataSources) {
            List<HostNodeData> childNodes = (hosts == null)
                    ? Collections.emptyList()
                    : hosts.stream()
                            .filter(h -> h != null)
                            .sorted((a, b) -> compareIgnoreCase(a.getName(), b.getName()))
                            .collect(Collectors.toList());

            if (CollectionUtils.isNotEmpty(hosts)) {
                childNodes.add(HostNodeData.getUnknown(dataSources));
            }

            return new RootContentChildren(childNodes);
        }

        OwnerNode(OwnerNodeData owner) {
            this(owner, (owner != null) ? owner.getHosts() : null, (owner != null) ? owner.getDataSources() : null);
        }

        OwnerNode(OwnerNodeData owner, List<HostNodeData> hosts, List<DataSourceGrouping> dataSources) {
            super(getChildren(hosts, dataSources), owner == null ? null : Lookups.singleton(owner));
            String safeName = (owner == null || owner.getName() == null || owner.isUnknown()) ? Bundle.OwnerNode_unknownHostNode_title() : owner.getName();
            super.setName(safeName);
            super.setDisplayName(safeName);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/image.png");
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
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
}
