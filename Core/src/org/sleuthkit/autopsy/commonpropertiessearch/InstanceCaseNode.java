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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Node used to group results by case.
 */
public final class InstanceCaseNode extends DisplayableItemNode {

    private static final Logger logger = Logger.getLogger(InstanceCaseNode.class.getName());

    final private String caseName;
    final private Map<String, CommonAttributeValueList> dataSourceToValueList;

    /**
     * Create a node with all instances for the given case, and the given
     * selection of metadata.
     *
     * @param caseName        the name of the case
     * @param attributeValues the map of data sources to
     *                        CommonAttributeValueLists to be included
     */
    public InstanceCaseNode(String caseName, Map<String, CommonAttributeValueList> attributeValues) {
        super(Children.create(new CommonAttributeDataSourceNodeFactory(attributeValues), true));
        this.caseName = caseName;
        this.dataSourceToValueList = attributeValues;
        this.setDisplayName(this.caseName);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/briefcase.png"); //NON-NLS
    }

    /**
     * Name of the case all child nodes are contained in.
     *
     * @return String case name
     */
    String getCaseName() {
        return this.caseName;
    }

    /**
     * Creates the Children of this node. By doing this here instead of in the
     * constructor, lazy creation of the Children is made possible.
     */
    void createChildren() {
        setChildren(Children.create(new CommonAttributeDataSourceNodeFactory(dataSourceToValueList), true));
    }

    /**
     * Get a list of metadata for the datasources which are children of this
     * object.
     *
     * @return List<Md5Metadata>
     */
    Map<String, CommonAttributeValueList> getDataSourceToValueList() {
        return this.dataSourceToValueList;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
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

    /**
     * ChildFactory which builds InstanceDataSourceNode from the metadata data
     * sources.
     */
    static class CommonAttributeDataSourceNodeFactory extends ChildFactory<String> {

        /**
         * Map of data sources, each of which is a parent node matching a case
         * name, containing children FileNodes.
         */
        private final Map<String, CommonAttributeValueList> metadata;

        CommonAttributeDataSourceNodeFactory(Map<String, CommonAttributeValueList> attributeValues) {
            this.metadata = new HashMap<>();
            this.metadata.putAll(attributeValues);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(this.metadata.keySet());
            return true;
        }

        @Override
        protected Node createNodeForKey(String dataSourceName) {
            return new InstanceDataSourceNode(dataSourceName, this.metadata.get(dataSourceName));
        }
    }

}
