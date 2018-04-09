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
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * 
 * Child factory for Device nodes 
 */
final class DeviceNodeChildren extends Children.Keys<String> {

    private static final Logger LOGGER = Logger.getLogger(DeviceNodeChildren.class.getName());
    private List<String> currentKeys;

    public DeviceNodeChildren() {
        super();
        this.currentKeys = new ArrayList<>();
    }

    private final PropertyChangeListener pcl = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                reloadKeys();
            }
        }
    };

    @Override
    protected void addNotify() {
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
        reloadKeys();
    }

    @Override
    protected void removeNotify() {
        Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
        currentKeys.clear();
        setKeys(Collections.<String>emptySet());
    }

    private void reloadKeys() {
        try {
            currentKeys.clear();
            List<Content> dataSources = Case.getOpenCase().getDataSources();
            Set<String> deviceIds = new HashSet<>();
            
            for (Content content : dataSources) {
                    DataSource ds = (DataSource) content;
                    deviceIds.add(ds.getDeviceId());
            }

            currentKeys.addAll(deviceIds);
            setKeys(currentKeys);
        } catch (TskCoreException | IllegalStateException| NoCurrentCaseException ex) {
            LOGGER.log(Level.SEVERE, "Error getting data sources: {0}", ex.getMessage()); // NON-NLS
            setKeys(Collections.<String>emptySet());
        }
    }

    @Override
    protected Node[] createNodes(String key) {
        return new Node[]{new DeviceNode(key)};
    }
}
