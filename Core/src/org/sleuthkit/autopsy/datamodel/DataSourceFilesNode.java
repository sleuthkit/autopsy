/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * A structural node in the main tree view when the user has selected the group
 * by persons/hosts option. Instances of this node appear as children of a node
 * representing a data source association with a host, and as a parent of a data
 * source node. For example: "Host X" -> "Data Source Y" -> "Data Source Files"
 * -> "Data Source Y", where "Data Source Files" is an instance of this node.
 */
public class DataSourceFilesNode extends DisplayableItemNode {

    private static final String NAME = NbBundle.getMessage(DataSourceFilesNode.class, "DataSourcesNode.name");

    /**
     * @return The name used to identify the node of this type with a lookup.
     */
    public static String getNameIdentifier() {
        return NAME;
    }

    private final String displayName;

    // NOTE: The images passed in via argument will be ignored.
    @Deprecated
    public DataSourceFilesNode(List<Content> images) {
        this(0);
    }

    public DataSourceFilesNode() {
        this(0);
    }

    public DataSourceFilesNode(long dsObjId) {
        super(Children.create(new DataSourcesNodeChildren(dsObjId), true), Lookups.singleton(NAME));
        displayName = (dsObjId > 0) ? NbBundle.getMessage(DataSourceFilesNode.class, "DataSourcesNode.group_by_datasource.name") : NAME;
        init();
    }

    private void init() {
        setName(NAME);
        setDisplayName(displayName);
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
        private final long datasourceObjId;

        List<Content> currentKeys;

        public DataSourcesNodeChildren() {
            this(0);
        }

        public DataSourcesNodeChildren(long dsObjId) {
            super("ds_" + Long.toString(dsObjId));
            this.currentKeys = new ArrayList<>();
            this.datasourceObjId = dsObjId;
        }

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
        protected void onAdd() {
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
        }

        @Override
        protected void onRemove() {
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
            currentKeys.clear();
        }

        @Override
        protected List<Content> makeKeys() {
            try {
                if (datasourceObjId == 0) {
                    currentKeys = Case.getCurrentCaseThrows().getDataSources();
                } else {
                    Content content = Case.getCurrentCaseThrows().getSleuthkitCase().getDataSource(datasourceObjId);
                    currentKeys = new ArrayList<>(Arrays.asList(content));
                }

                Collections.sort(currentKeys, new Comparator<Content>() {
                    @Override
                    public int compare(Content content1, Content content2) {
                        String content1Name = content1.getName().toLowerCase();
                        String content2Name = content2.getName().toLowerCase();
                        return content1Name.compareTo(content2Name);
                    }

                });

            } catch (TskCoreException | NoCurrentCaseException | TskDataException ex) {
                logger.log(Level.SEVERE, "Error getting data sources: {0}", ex.getMessage()); // NON-NLS
            }

            return currentKeys;
        }
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
