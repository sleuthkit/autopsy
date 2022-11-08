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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.Action;
import org.openide.nodes.ChildFactory;

import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.HostsUpdatedEvent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.hosts.AssociatePersonsMenuAction;
import org.sleuthkit.autopsy.datamodel.hosts.MergeHostMenuAction;
import org.sleuthkit.autopsy.datamodel.hosts.RemoveParentPersonAction;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A node to be displayed in the UI tree for a host and data sources grouped in
 * this host.
 */
@NbBundle.Messages(value = {"HostGroupingNode_unknownHostNode_title=Unknown Host"})
public class HostNode extends DisplayableItemNode {

    /**
     * Provides the data source children for this host.
     */
    private static class HostGroupingChildren extends ChildFactory.Detachable<DataSourceGrouping> {

        private static final Logger logger = Logger.getLogger(HostGroupingChildren.class.getName());

        private final Host host;
        private final Function<DataSourceGrouping, Node> dataSourceToNode;
        
        /**
         * Main constructor.
         *
         * @param dataSourceToItem Converts a data source to a node.
         * @param host The host.
         */
        HostGroupingChildren(Function<DataSourceGrouping, Node> dataSourceToNode, Host host) {
            this.host = host;
            this.dataSourceToNode = dataSourceToNode;
        }
        
        /**
         * Listener for handling DATA_SOURCE_ADDED / HOST_DELETED events.
         * A host may have been deleted as part of a merge, which means its data sources could
         * have moved to a different host requiring a refresh.
         */
        private final PropertyChangeListener dataSourceAddedPcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())
                        || eventType.equals(Case.Events.HOSTS_DELETED.toString())) {
                    refresh(true);
                }
            }
        };
        
        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(dataSourceAddedPcl, null);
        
        @Override
        protected void addNotify() {
            super.addNotify();
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED, Case.Events.HOSTS_DELETED), weakPcl);
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED, Case.Events.HOSTS_DELETED), weakPcl);
        }

        @Override
        protected Node createNodeForKey(DataSourceGrouping key) {
            return this.dataSourceToNode.apply(key);
        }

        @Override
        protected boolean createKeys(List<DataSourceGrouping> toPopulate) {
            List<DataSource> dataSources = null;
            try {
                dataSources = Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().getDataSourcesForHost(host);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                String hostName = host == null || host.getName() == null ? "<unknown>" : host.getName();
                logger.log(Level.WARNING, String.format("Unable to get data sources for host: %s", hostName), ex);
            }

            if (dataSources != null) {
                toPopulate.addAll(dataSources.stream()
                        .filter(ds -> ds != null)
                        .map(DataSourceGrouping::new)
                        .sorted((a, b) -> getNameOrEmpty(a).compareToIgnoreCase(getNameOrEmpty(b)))
                        .collect(Collectors.toList()));
            }

            return true;
        }

        /**
         * Get name for data source in data source grouping node or empty
         * string.
         *
         * @param dsGroup The data source grouping.
         * @return The name or empty if none exists.
         */
        private String getNameOrEmpty(DataSourceGrouping dsGroup) {
            return (dsGroup == null || dsGroup.getDataSource() == null || dsGroup.getDataSource().getName() == null)
                    ? ""
                    : dsGroup.getDataSource().getName();
        }
    }

    private static final Logger logger = Logger.getLogger(HostNode.class.getName());
    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/host.png";
    private static final CreateSleuthkitNodeVisitor CREATE_TSK_NODE_VISITOR = new CreateSleuthkitNodeVisitor();

    /**
     * Means of creating just data source nodes underneath the host (i.e. no
     * results, reports, etc.)
     */
    private static final Function<DataSourceGrouping, Node> HOST_DATA_SOURCES = key -> {
        if (key.getDataSource() instanceof SleuthkitVisitableItem) {
            return ((SleuthkitVisitableItem) key.getDataSource()).accept(CREATE_TSK_NODE_VISITOR);
        } else {
            return null;
        }
    };

    /**
     * Shows data sources with results, reports, etc.
     */
    private static final Function<DataSourceGrouping, Node> HOST_GROUPING_CONVERTER = key -> {
        if (key == null || key.getDataSource() == null) {
            return null;
        }

        return new DataSourceGroupingNode(key.getDataSource());
    };

    /**
     * Listener for handling host change events.
     */
    private final PropertyChangeListener hostChangePcl = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (hostId != null && eventType.equals(Case.Events.HOSTS_UPDATED.toString()) && evt instanceof HostsUpdatedEvent) {
                ((HostsUpdatedEvent) evt).getHosts().stream()
                        .filter(h -> h != null && h.getHostId() == hostId)
                        .findFirst()
                        .ifPresent((newHost) -> {
                            setName(newHost.getName());
                            setDisplayName(newHost.getName());
                        });
            }
        }
    };
    
    private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(hostChangePcl, null);

    /*
     * Get the host name or 'unknown host' if null.
     *
     * @param host The host.
     * @return The display name.
     */
    private static String getHostName(Host host) {
        return (host == null || host.getName() == null)
                ? Bundle.HostGroupingNode_unknownHostNode_title()
                : host.getName();
    }

    private final Host host;
    private final Long hostId;

    /**
     * Main constructor for HostDataSources key where data source children
     * should be displayed without additional results, reports, etc.
     *
     * @param hosts The HostDataSources key.
     */
    HostNode(HostDataSources hosts) {
        this(Children.create(new HostGroupingChildren(HOST_DATA_SOURCES, hosts.getHost()), true), hosts.getHost());
    }

    /**
     * Main constructor for HostGrouping key where data sources should be
     * displayed with results, reports, etc.
     *
     * @param hostGrouping The HostGrouping key.
     */
    HostNode(HostGrouping hostGrouping) {
        this(Children.create(new HostGroupingChildren(HOST_GROUPING_CONVERTER, hostGrouping.getHost()), true), hostGrouping.getHost());
    }

    /**
     * Constructor.
     *
     * @param children The children for this host node.
     * @param host The host.
     */
    private HostNode(Children children, Host host) {
        this(children, host, getHostName(host));
    }

    /**
     * Constructor.
     *
     * @param children The children for this host node.
     * @param host The host.
     * @param displayName The displayName.
     */
    private HostNode(Children children, Host host, String displayName) {
        super(children,
                host == null ? Lookups.fixed(displayName) : Lookups.fixed(host, displayName));
                
        hostId = host == null ? null : host.getHostId();
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.HOSTS_UPDATED), weakPcl);
        super.setName(displayName);
        super.setDisplayName(displayName);
        this.setIconBaseWithExtension(ICON_PATH);
        this.host = host;
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

    @Messages({
        "HostNode_createSheet_nameProperty=Name",})
    @Override
    protected Sheet createSheet() {
        Sheet sheet = Sheet.createDefault();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>("Name", Bundle.HostNode_createSheet_nameProperty(), "", getDisplayName())); //NON-NLS

        return sheet;
    }

    @Override
    @Messages({"HostNode_actions_associateWithExisting=Associate with existing person...",
        "HostNode_actions_associateWithNew=Associate with new person...",
        "# {0} - hostName",
        "HostNode_actions_removeFromPerson=Remove from person ({0})"})
    public Action[] getActions(boolean context) {

        List<Action> actionsList = new ArrayList<>();
        
        // if there is a host, then provide actions
        if (this.host != null) {
            
            // Add the appropriate Person action
            Optional<Person> parent;
            try {
                parent = Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().getPerson(this.host);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Error fetching parent person of host: %s", this.host.getName() == null ? "<null>" : this.host.getName()), ex);
                return new Action[0];
            }

            // if there is a parent, only give option to remove parent person.
            if (parent.isPresent()) {
                actionsList.add(new RemoveParentPersonAction(this.host, parent.get()));
            } else {
                actionsList.add(new AssociatePersonsMenuAction(this.host));
            }
            
            // Add option to merge hosts
            actionsList.add(new MergeHostMenuAction(this.host));
        }
        return actionsList.toArray(new Action[actionsList.size()]);
    }
}
