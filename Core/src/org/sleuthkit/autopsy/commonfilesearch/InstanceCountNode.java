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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * Node used to indicate the number of matches found with the MD5 children 
 * of this Node.
 */
final public class InstanceCountNode extends DisplayableItemNode {

    final private int instanceCount;
    final private List<CommonAttributeValue> attributeValues;

    /**
     * Create a node with the given number of instances, and the given
     * selection of metadata.
     * @param instanceCount
     * @param attributeValues 
     */
    @NbBundle.Messages({
        "InstanceCountNode.displayName=Files with %s instances (%s)"
    })
    public InstanceCountNode(int instanceCount, List<CommonAttributeValue> attributeValues) {
        super(Children.create(new CommonAttributeValueNodeFactory(attributeValues), true));

        this.instanceCount = instanceCount;
        this.attributeValues = attributeValues;
        
        this.setDisplayName(String.format(Bundle.InstanceCountNode_displayName(), Integer.toString(instanceCount), attributeValues.size()));
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/fileset-icon-16.png"); //NON-NLS
    }

    /**
     * Number of matches found for each of the MD5 children.
     * @return int match count
     */
    int getInstanceCount() {
        return this.instanceCount;
    }

    /**
     * Get a list of metadata for the MD5s which are children of this object.
     * @return List<Md5Metadata>
     */
    List<CommonAttributeValue> getAttributeValues() {
        return Collections.unmodifiableList(this.attributeValues);
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

    @NbBundle.Messages({"InstanceCountNode.createSheet.noDescription= "})
    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }
        
        final String NO_DESCR = Bundle.InstanceCountNode_createSheet_noDescription();
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_filesColLbl(), Bundle.CommonFilesSearchResultsViewerTable_filesColLbl(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_instancesColLbl(), Bundle.CommonFilesSearchResultsViewerTable_instancesColLbl(), NO_DESCR, this.getInstanceCount()));
        return sheet;
    }


    /**
     * ChildFactory which builds CommonFileParentNodes from the
     * CommonFilesMetaaData models.
     */
    static class CommonAttributeValueNodeFactory extends ChildFactory<String> {

        /**
         * List of models, each of which is a parent node matching a single md5,
         * containing children FileNodes.
         */
        // maps sting version of value to value Object (??)
        private final Map<String, CommonAttributeValue> metadata;

        CommonAttributeValueNodeFactory(List<CommonAttributeValue> attributeValues) {
            this.metadata = new HashMap<>();

            Iterator<CommonAttributeValue> iterator = attributeValues.iterator();
            while (iterator.hasNext()) {
                CommonAttributeValue attributeValue = iterator.next();
                this.metadata.put(attributeValue.getValue(), attributeValue);
            }
        }

        @Override
        protected boolean createKeys(List<String> list) {
            // @@@ We should just use CommonAttributeValue as the key...
            list.addAll(this.metadata.keySet());
            return true;
        }
        
        @Override
        protected Node createNodeForKey(String attributeValue) {
            CommonAttributeValue md5Metadata = this.metadata.get(attributeValue);
            return new CommonAttributeValueNode(md5Metadata);
        }
    }
}