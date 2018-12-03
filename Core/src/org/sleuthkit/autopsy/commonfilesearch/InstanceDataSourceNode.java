/*
 *
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;

import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * Node used to indicate the number of matches found with the MD5 children of
 * this Node.
 */
public final class InstanceDataSourceNode extends DisplayableItemNode {

    private static final Logger logger = Logger.getLogger(InstanceDataSourceNode.class.getName());

    final private String dataSourceName;
    final private CommonAttributeValueList dataSourceToValueList;

    /**
     * Create a node with the given number of instances, and the given selection
     * of metadata.
     *
     * @param instanceCount
     * @param attributeValues
     */
    public InstanceDataSourceNode(String dataSourceName, CommonAttributeValueList attributeValues) {
        super(Children.create(new FileInstanceNodeFactory(attributeValues), true));

        this.dataSourceName = dataSourceName;
        this.dataSourceToValueList = attributeValues;

        this.setDisplayName(this.dataSourceName);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/fileset-icon-16.png"); //NON-NLS
    }

    /**
     * Number of matches found for each of the MD5 children.
     *
     * @return int match count
     */
    String getCaseName() {
        return this.dataSourceName;
    }

    /**
     * Creates the Children of this node. By doing this here instead of in the
     * constructor, lazy creation of the Children is made possible.
     */
    void createChildren() {
        dataSourceToValueList.displayDelayedMetadata(); //WJS-TODO move this to the child node
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
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_caseColLbl1(), Bundle.CommonFilesSearchResultsViewerTable_caseColLbl1(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_instancesColLbl(), Bundle.CommonFilesSearchResultsViewerTable_instancesColLbl(), NO_DESCR, this.getCaseName()));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_caseColLbl1(), Bundle.CommonFilesSearchResultsViewerTable_caseColLbl1(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_hashsetHitsColLbl(), Bundle.CommonFilesSearchResultsViewerTable_hashsetHitsColLbl(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_mimeTypeColLbl(), Bundle.CommonFilesSearchResultsViewerTable_mimeTypeColLbl(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_tagsColLbl1(), Bundle.CommonFilesSearchResultsViewerTable_tagsColLbl1(), NO_DESCR, ""));

        return sheet;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * Child generator for <code>SleuthkitCaseFileInstanceNode</code> of
     * <code>CommonAttributeValueNode</code>.
     */
    static class FileInstanceNodeFactory extends ChildFactory<AbstractCommonAttributeInstance> {

        private final CommonAttributeValueList descendants;

        FileInstanceNodeFactory(CommonAttributeValueList descendants) {
            this.descendants = descendants;
        }

        @Override
        protected boolean createKeys(List<AbstractCommonAttributeInstance> list) {
            for (CommonAttributeValue value : descendants.getDelayedMetadataList()) {
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
