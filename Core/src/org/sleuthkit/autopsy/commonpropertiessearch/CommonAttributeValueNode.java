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
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import static org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance.FILES_TYPE_ID;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * Represents the layer in the tree for the value (such as MD5) that was in
 * multiple places. Children are instances of that value.
 */
public class CommonAttributeValueNode extends DisplayableItemNode {

    private final String value;
    private final int commonFileCount;
    private final String cases;
    private final String dataSources;

    @NbBundle.Messages({
        "CommonAttributeValueNode.CommonAttributeValueNode.format=Value: %s"
    })
    /**
     * Create a Match node whose children will all have this object in common.
     *
     * @param data the common feature, and the children
     * @param type the data type
     */
    public CommonAttributeValueNode(CommonAttributeValue data, CorrelationAttributeInstance.Type type) {
        super(Children.create(
                new FileInstanceNodeFactory(data), true));
        this.commonFileCount = data.getInstanceCount();
        this.cases = data.getCases();
        this.dataSources = String.join(", ", data.getDataSources());
        this.value = data.getValue();
        //if the type is null (indicating intra-case) or files then make the node name the representitive file name
        if (type == null || type.getId() == FILES_TYPE_ID) {
            this.setDisplayName(data.getTokenFileName());
        } else {
            this.setDisplayName(String.format(Bundle.CommonAttributeValueNode_CommonAttributeValueNode_format(), this.value));
        }
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/fileset-icon-16.png"); //NON-NLS
    }

    /**
     * How many files are in common? This will be the number of children.
     *
     * @return int
     */
    int getCommonFileCount() {
        return this.commonFileCount;
    }

    String getCases() {
        return this.cases;
    }

    /**
     * Datasources where these matches occur.
     *
     * @return string delimited list of sources
     */
    String getDataSources() {
        return this.dataSources;
    }

    /**
     * Value which is common to these matches
     *
     * @return string the the value which is correlated on
     */
    public String getValue() {
        return this.value;
    }

    @NbBundle.Messages({
        "ValueNode.createSheet.noDescription= "
    })
    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        final String NO_DESCR = Bundle.ValueNode_createSheet_noDescription();
        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.nameColLbl"), NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.nameColLbl"), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), NO_DESCR, this.getDataSources()));

        return sheet;
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

    /**
     * Child generator for <code>SleuthkitCaseFileInstanceNode</code> of
     * <code>CommonAttributeValueNode</code>.
     */
    static class FileInstanceNodeFactory extends ChildFactory<AbstractCommonAttributeInstance> {

        private final CommonAttributeValue descendants;

        FileInstanceNodeFactory(CommonAttributeValue descendants) {
            this.descendants = descendants;
        }

        @Override
        protected boolean createKeys(List<AbstractCommonAttributeInstance> list) {
            // This is a bit of a hack to ensure that the AbstractFile instance
            // has been created before createNodesForKey() is called. Constructing
            // the AbstractFile in createNodesForKey() was resulting in UI lockups.
            this.descendants.getInstances().forEach((acai) -> {
                acai.getAbstractFile();
            });
            list.addAll(this.descendants.getInstances());
            return true;
        }

        @Override
        protected Node[] createNodesForKey(AbstractCommonAttributeInstance searchResult) {
            return searchResult.generateNodes();
        }

    }
}
