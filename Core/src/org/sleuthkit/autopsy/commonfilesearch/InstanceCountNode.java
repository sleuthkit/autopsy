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
import java.util.LinkedHashMap;
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
    final private List<Md5Metadata> metadataList;

    /**
     * Create a node with the given number of instances, and the given
     * selection of metadata.
     * @param instanceCount
     * @param md5Metadata 
     */
    @NbBundle.Messages({
        "InstanceCountNode.displayName=Match with %s instances"
    })
    public InstanceCountNode(int instanceCount, List<Md5Metadata> md5Metadata) {
        super(Children.create(new Md5NodeFactory(md5Metadata), true));

        this.instanceCount = instanceCount;
        this.metadataList = md5Metadata;
        
        this.setDisplayName(String.format(Bundle.InstanceCountNode_displayName(), Integer.toString(instanceCount)));
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
    List<Md5Metadata> getMetadata() {
        return Collections.unmodifiableList(this.metadataList);
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

        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, this);

        final String NO_DESCR = "";
        for (InstanceCountNode.InstanceCountNodePropertyType propType : InstanceCountNode.InstanceCountNodePropertyType.values()) {
            final String propString = propType.toString();
            sheetSet.put(new NodeProperty<>(propString, propString, NO_DESCR, map.get(propString)));
        }

        return sheet;
    }

    /**
     * Fill map with AbstractFile properties
     *
     * @param map map with preserved ordering, where property names/values are
     * put
     * @param node The item to get properties for.
     */
    static private void fillPropertyMap(Map<String, Object> map, InstanceCountNode node) {
        map.put(InstanceCountNodePropertyType.Match.toString(), node.getInstanceCount());
    }
    
    /**
     * Fields which will appear in the tree table.
     */
    @NbBundle.Messages({
        "InstanceCountNodeProeprtyType.matchCountColLbl1=Match"
    })
    public enum InstanceCountNodePropertyType{
        
        Match(Bundle.InstanceCountNodeProeprtyType_matchCountColLbl1());
        
        final private String displayString;
        
        private InstanceCountNodePropertyType(String displayName){
            this.displayString = displayName;
        }
        
        @Override
        public String toString(){
            return this.displayString;
        }
    }

    /**
     * ChildFactory which builds CommonFileParentNodes from the
     * CommonFilesMetaaData models.
     */
    static class Md5NodeFactory extends ChildFactory<String> {

        /**
         * List of models, each of which is a parent node matching a single md5,
         * containing children FileNodes.
         */
        private final Map<String, Md5Metadata> metadata;

        Md5NodeFactory(List<Md5Metadata> metadata) {
            this.metadata = new HashMap<>();

            Iterator<Md5Metadata> iterator = metadata.iterator();
            while (iterator.hasNext()) {
                Md5Metadata md5Metadata = iterator.next();
                this.metadata.put(md5Metadata.getMd5(), md5Metadata);
            }
        }

        @Override
        protected Node createNodeForKey(String md5) {
            Md5Metadata md5Metadata = this.metadata.get(md5);
            return new Md5Node(md5Metadata);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(this.metadata.keySet());
            return true;
        }
    }
}