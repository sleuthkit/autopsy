/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.List;
import java.util.logging.Level;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Nodes for the images
 */
public class DataSourcesNode extends DisplayableItemNode {

    public static final String NAME = NbBundle.getMessage(DataSourcesNode.class, "DataSourcesNode.name");

    // NOTE: The images passed in via argument will be ignored.
    @Deprecated
    public DataSourcesNode(List<Content> images) {
        super(new DataSourcesNodeChildren(), Lookups.singleton(NAME));
        init();
    }

    public DataSourcesNode() {
        super(new DataSourcesNodeChildren(), Lookups.singleton(NAME));
        init();
    }

    private void init() {
        setName(NAME);
        setDisplayName(NAME);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/image.png"); //NON-NLS
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    /*
     * Custom Keys implementation that listens for new data sources being added.
     */
    public static class DataSourcesNodeChildren extends AbstractContentChildren<Content> {

        private static final Logger logger = Logger.getLogger(DataSourcesNodeChildren.class.getName());

        List<Content> currentKeys;

        public DataSourcesNodeChildren() {
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
            setKeys(Collections.<Content>emptySet());
        }

        private void reloadKeys() {
            try {
                currentKeys = Case.getOpenCase().getDataSources();
                setKeys(currentKeys);
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Error getting data sources: {0}", ex.getMessage()); // NON-NLS
                setKeys(Collections.<Content>emptySet());
            }
        }

        /**
         * Refresh all content keys This creates new nodes of keys have changed.
         */
        public void refreshContentKeys() {
            for (Content key : currentKeys) {
                refreshKey(key);
            }
        }
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "DataSourcesNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "DataSourcesNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "DataSourcesNode.createSheet.name.desc"),
                NAME));
        return s;
    }
}
