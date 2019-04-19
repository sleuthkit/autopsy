/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;

import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * Node used to group results by data source.
 */
public final class InstanceDataSourceNode extends DisplayableItemNode {

    private static final Logger logger = Logger.getLogger(InstanceDataSourceNode.class.getName());

    final private String dataSourceName;
    final private CommonAttributeValueList dataSourceToValueList;

    /**
     * Create a node with all instances for the given data source, and the given
     * selection of metadata.
     *
     * @param dataSourceName  the name of the dataSource
     * @param attributeValues the commonAttributeValueList containing the
     *                        results
     */
    public InstanceDataSourceNode(String dataSourceName, CommonAttributeValueList attributeValues) {
        super(Children.create(new FileInstanceNodeFactory(attributeValues), true));

        this.dataSourceName = dataSourceName;
        this.dataSourceToValueList = attributeValues;

        this.setDisplayName(this.dataSourceName);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/image.png"); //NON-NLS
    }

    /**
     * Get the name of the data source
     *
     * @return String data source name
     */
    String getDatasourceName() {
        return this.dataSourceName;
    }

    /**
     * Creates the Children of this node. By doing this here instead of in the
     * constructor, lazy creation of the Children is made possible.
     */
    void createChildren() {
        dataSourceToValueList.displayDelayedMetadata();
        setChildren(Children.create(new FileInstanceNodeFactory(dataSourceToValueList), true));
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
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }
        final String NO_DESCR = Bundle.InstanceCountNode_createSheet_noDescription();
        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.nameColLbl"), NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.nameColLbl"), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_localPath(), Bundle.CommonFilesSearchResultsViewerTable_localPath(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.mimeType"), NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.mimeType"), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_valueColLbl(), Bundle.CommonFilesSearchResultsViewerTable_valueColLbl(), NO_DESCR, ""));
        return sheet;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * ChildFactory which builds DisplayableItem from the metadata data sources.
     */
    static class FileInstanceNodeFactory extends ChildFactory<AbstractCommonAttributeInstance> {

        private final CommonAttributeValueList descendants;

        FileInstanceNodeFactory(CommonAttributeValueList descendants) {
            this.descendants = descendants;
        }

        @Override
        protected boolean createKeys(List<AbstractCommonAttributeInstance> list) {
            for (CommonAttributeValue value : descendants.getDelayedMetadataSet()) {
                // This is a bit of a hack to ensure that the AbstractFile instance
                // has been created before createNodesForKey() is called. Constructing
                // the AbstractFile in createNodesForKey() was resulting in UI lockups.
                value.getInstances().forEach((acai) -> {
                    acai.getAbstractFile();
                });

                list.addAll(value.getInstances());
            }
            return true;
        }

        @Override
        protected Node[] createNodesForKey(AbstractCommonAttributeInstance searchResult) {
            return searchResult.generateNodes();
        }

    }
}
