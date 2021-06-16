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
import java.util.stream.Collectors;
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A top-level structural node (child of the invisible root node) in the main
 * tree view when the user has selected the group by data type option. It
 * appears as the parent node of the "directory tree" nodes that are the roots
 * of the file trees for the individual data sources in a case. For example:
 * "Data Sources" -> "Data Source X", "Data Source Y", where "Data Sources" is
 * an instance of this node. The siblings of this node are the "Views, "Analysis
 * Results," "Os Accounts," "Tags," and "Reports" nodes.
 */
@Messages({
    "DataSourcesHostsNode_name=Data Sources"
})
public class DataSourcesNode extends DisplayableItemNode {

    /*
     * Custom Keys implementation that listens for new data sources being added.
     */
    public static class DataSourcesByTypeChildren extends ChildFactory.Detachable<HostDataSources> {

        private static final Set<Case.Events> UPDATE_EVTS = EnumSet.of(Case.Events.DATA_SOURCE_ADDED,
                Case.Events.HOSTS_ADDED,
                Case.Events.HOSTS_DELETED,
                Case.Events.HOSTS_UPDATED);

        private static final Set<String> UPDATE_EVT_STRS = UPDATE_EVTS.stream()
                .map(evt -> evt.name())
                .collect(Collectors.toSet());

        private static final Logger logger = Logger.getLogger(DataSourcesByTypeChildren.class.getName());

        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (UPDATE_EVT_STRS.contains(eventType)) {
                    refresh(true);
                }
            }
        };
        
        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);

        @Override
        protected void addNotify() {
            Case.addEventTypeSubscriber(UPDATE_EVTS, weakPcl);
        }

        @Override
        protected void finalize() throws Throwable{
            Case.removeEventTypeSubscriber(UPDATE_EVTS, weakPcl);
            super.finalize();
        }

        @Override
        protected boolean createKeys(List<HostDataSources> toPopulate) {
            try {
                Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().getAllHosts().stream()
                        .map(HostDataSources::new)
                        .sorted()
                        .forEach(toPopulate::add);

            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Error getting data sources: {0}", ex.getMessage()); // NON-NLS
            }

            return true;
        }

        @Override
        protected Node createNodeForKey(HostDataSources key) {
            return new HostNode(key);
        }

    }

    private static final String NAME = Bundle.DataSourcesHostsNode_name();

    /**
     * @return The name used to identify the node of this type with a lookup.
     */
    public static String getNameIdentifier() {
        return NAME;
    }

    /**
     * Main constructor.
     */
    DataSourcesNode() {
        super(Children.create(new DataSourcesByTypeChildren(), true), Lookups.singleton(NAME));
        setName(NAME);
        setDisplayName(NAME);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/image.png");
    }

    @Override
    public String getItemType() {
        return getClass().getName();
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
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "DataSourcesNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "DataSourcesNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "DataSourcesNode.createSheet.name.desc"),
                NAME));
        return sheet;
    }
}
