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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.ChildFactory;

import org.openide.nodes.Children;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A node to be displayed in the UI tree for a host and data sources grouped in
 * this host.
 */
@NbBundle.Messages(value = {"HostNode_unknownHostNode_title=Unknown Host"})
class HostGroupingNode extends DisplayableItemNode {

    /**
     * Provides the data source children for this host.
     */
    private static class HostChildren extends ChildFactory.Detachable<DataSourceGrouping> {

        private static final Logger logger = Logger.getLogger(HostChildren.class.getName());

        private final Host host;

        /**
         * Main constructor.
         *
         * @param host The host.
         */
        HostChildren(Host host) {
            this.host = host;
        }

        /**
         * Listener for handling DATA_SOURCE_ADDED and DATA_SOURCE_DELETED
         * events.
         */
        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                    refresh(true);
                }
            }
        };

        @Override
        protected void addNotify() {
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
        }

        @Override
        protected void removeNotify() {
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
        }

        @Override
        protected DataSourceGroupingNode createNodeForKey(DataSourceGrouping key) {
            return (key == null || key.getDataSource() == null) ? null : new DataSourceGroupingNode(key.getDataSource());
        }

        @Override
        protected boolean createKeys(List<DataSourceGrouping> toPopulate) {
            Set<DataSource> dataSources = null;
            try {
                dataSources = Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().getDataSourcesForHost(host);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                String hostName = host == null || host.getName() == null ? "<unknown>" : host.getName();
                logger.log(Level.WARNING, String.format("Unable to get data sources for host: %s", hostName), ex);
            }

            if (dataSources != null) {
                dataSources.stream()
                        .filter(ds -> ds != null)
                        .map(DataSourceGrouping::new)
                        .sorted((a, b) -> getNameOrEmpty(a).compareToIgnoreCase(getNameOrEmpty(b)))
                        .forEach(toPopulate::add);
            }

            return true;
        }

        private String getNameOrEmpty(DataSourceGrouping dsGroup) {
            return (dsGroup == null || dsGroup.getDataSource() == null || dsGroup.getDataSource().getName() == null)
                    ? ""
                    : dsGroup.getDataSource().getName();
        }
    }

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/host.png";

    /**
     * Main constructor.
     *
     * @param host The host.
     */
    HostGroupingNode(Host host) {
        super(Children.create(new HostChildren(host), false), host == null ? null : Lookups.singleton(host));

        String safeName = (host == null || host.getName() == null)
                ? Bundle.HostNode_unknownHostNode_title()
                : host.getName();

        super.setName(safeName);
        super.setDisplayName(safeName);
        this.setIconBaseWithExtension(ICON_PATH);
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
