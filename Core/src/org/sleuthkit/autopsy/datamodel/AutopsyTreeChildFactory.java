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
    
    
    
    private DataSource getDs(List<DataSource> dataSources, int idx) {
        return dataSources.get(idx % dataSources.size());
    }

    private OwnerHostTree getTree(SleuthkitCase tskCase) throws TskCoreException {
        List<DataSource> dataSources = tskCase.getDataSources();
        if (CollectionUtils.isEmpty(dataSources)) {
            return new OwnerHostTree(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
        
        return new OwnerHostTree(
                Arrays.asList(
                        new Owner("Owner1", Arrays.asList(
                                new Host("Host1.1", Arrays.asList(getDs(dataSources, 0), getDs(dataSources, 1))),
                                new Host("Host1.2", Collections.emptyList())),
                                Arrays.asList(getDs(dataSources,2))
                        ),
                        new Owner("Owner1", Arrays.asList(
                                new Host("Host1.1", Arrays.asList(getDs(dataSources, 0), getDs(dataSources, 1))),
                                new Host("Host2.3", Collections.emptyList())),
                                Arrays.asList(getDs(dataSources,3))
                        )
                ),
                Arrays.asList(
                        new Host("Host0.1", Arrays.asList(getDs(dataSources, 4))),
                        new Host("Host0.2", Arrays.asList(getDs(dataSources, 5)))
                ),
                Arrays.asList(getDs(dataSources, 6), getDs(dataSources, 7))
        );
    }

    private List<Object> getNodes(OwnerHostTree tree) {
        List<Owner> owners = (tree == null) ? null : tree.getOwners();
        List<Host> hosts = (tree == null) ? null : tree.getHosts();
        List<DataSource> dataSources = (tree == null) ? null : tree.getDataSources();

        if (CollectionUtils.isNotEmpty(owners)) {
            List<Object> toReturn = owners.stream()
                    .filter(o -> o != null)
                    .sorted((a, b) -> StringUtils.compareIgnoreCase(a.getName(), b.getName()))
                    .map(o -> new OwnerNode(o))
                    .collect(Collectors.toList());

            if (CollectionUtils.isNotEmpty(hosts) || CollectionUtils.isNotEmpty(dataSources)) {
                toReturn.add(OwnerNode.getUnknownOwner(hosts, dataSources));
            }

            return toReturn;
        } else if (CollectionUtils.isNotEmpty(hosts)) {
            List<Object> toReturn = hosts.stream()
                    .filter(h -> h != null)
                    .sorted((a, b) -> StringUtils.compareIgnoreCase(a.getName(), b.getName()))
                    .map(h -> new HostNode(h))
                    .collect(Collectors.toList());

            if (CollectionUtils.isNotEmpty(dataSources)) {
                toReturn.add(HostNode.getUnknownHost(dataSources));
            }

            return toReturn;
        } else {
            Stream<DataSource> dataSourceSafe = dataSources == null ? Stream.empty() : dataSources.stream();
            return dataSourceSafe
                    .filter(d -> d != null)
                    .sorted((a, b) -> StringUtils.compareIgnoreCase(a.getName(), b.getName()))
                    .map(d -> new DataSourceGroupingNode(d))
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
        } else {
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

    public static class Owner {

        private final String name;
        private final List<Host> hosts;
        private final List<DataSource> dataSources;

        public Owner(String name, List<Host> hosts, List<DataSource> dataSources) {
            this.name = name;
            this.hosts = hosts;
            this.dataSources = dataSources;
        }

        public String getName() {
            return name;
        }

        public List<Host> getHosts() {
            return hosts;
        }

        public List<DataSource> getDataSources() {
            return dataSources;
        }

    }

    public static class Host {
        private final String name;
        private final List<DataSource> dataSources;
        
        public Host(String name, List<DataSource> dataSources) {

            this.name = name;
            this.dataSources = (dataSources == null) ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<DataSource>(dataSources));
        }

        public List<DataSource> getDataSources() {
            return this.dataSources;
        }

        public String getName() {
            return name;
        }
    }

    @Messages({
        "HostNode_unknownHostNode_title=Unknown Host"
    })
    static class HostNode extends DisplayableItemNode {

        public static HostNode getUnknownHost(List<DataSource> dataSources) {
            return new HostNode(null, dataSources);
        }


        
        private static RootContentChildren getChildren(List<DataSource> dataSources) {
            Stream<DataSourceGroupingNode> dsNodes = (dataSources == null)
                    ? Stream.empty()
                    : dataSources.stream()
                            .filter(ds -> ds != null)
                            .sorted((a, b) -> StringUtils.compareIgnoreCase(a.getName(), b.getName()))
                            .map(d -> new DataSourceGroupingNode(d));

            return new RootContentChildren(dsNodes.collect(Collectors.toList()));
        }

        private HostNode(Host host, List<DataSource> dataSources) {
            super(getChildren(dataSources), host == null ? null : Lookups.singleton(host));
            String safeName = (host == null || host.getName() == null) ? Bundle.HostNode_getUnknownHostNode_title() : host.getName();
            super.setName(safeName);
            super.setDisplayName(safeName);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/image.png");
        }

        HostNode(Host host) {
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

        private final List<Owner> owners;
        private final List<Host> hosts;
        private final List<DataSource> dataSources;

        public OwnerHostTree(List<Owner> owners, List<Host> hosts, List<DataSource> dataSources) {
            this.owners = owners;
            this.hosts = hosts;
            this.dataSources = dataSources;
        }

        public List<Owner> getOwners() {
            return owners;
        }

        public List<Host> getHosts() {
            return hosts;
        }

        public List<DataSource> getDataSources() {
            return dataSources;
        }

    }

    @Messages({
        "OwnerNode_unknownHostNode_title=Unknown Owner"
    })
    static class OwnerNode extends DisplayableItemNode {

        public static OwnerNode getUnknownOwner(List<Host> hosts, List<DataSource> dataSources) {
            return new OwnerNode(null, hosts, dataSources);
        }

        private static RootContentChildren getChildren(List<Host> hosts, List<DataSource> dataSources) {
            List<HostNode> childNodes = (hosts == null)
                    ? Collections.emptyList()
                    : hosts.stream()
                            .filter(h -> h != null)
                            .sorted((a, b) -> StringUtils.compareIgnoreCase(a.getName(), b.getName()))
                            .map(h -> new HostNode(h))
                            .collect(Collectors.toList());

            if (CollectionUtils.isNotEmpty(hosts)) {
                childNodes.add(HostNode.getUnknownHost(dataSources));
            }

            return new RootContentChildren(childNodes);
        }

        OwnerNode(Owner owner) {
            this(owner, (owner != null) ? owner.getHosts() : null, (owner != null) ? owner.getDataSources() : null);
        }

        OwnerNode(Owner owner, List<Host> hosts, List<DataSource> dataSources) {
            super(getChildren(hosts, dataSources), Lookups.singleton(owner));
            String safeName = (owner == null || owner.getName() == null) ? Bundle.OwnerNode_unknownHostNode_title() : owner.getName();
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
