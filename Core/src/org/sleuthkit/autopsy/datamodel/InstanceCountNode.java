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
package org.sleuthkit.autopsy.datamodel;

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
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.commonfilesearch.Md5Metadata;

/**
 *
 */
final public class InstanceCountNode extends DisplayableItemNode {

    final private int instanceCount;
    final private List<Md5Metadata> metadataList;

    public InstanceCountNode(int instanceCount, List<Md5Metadata> md5Metadata) {
        super(Children.create(new Md5NodeFactory(md5Metadata), true), Lookups.singleton(InstanceCountNode.class));

        this.instanceCount = instanceCount;
        this.metadataList = md5Metadata;
        
        this.setDisplayName(Integer.toString(instanceCount));
    }

    int getInstanceCount() {
        return this.instanceCount;
    }

    List<Md5Metadata> getMetadata() {
        return this.metadataList;
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

        final String NO_DESCR = org.sleuthkit.autopsy.datamodel.Bundle.AbstractFsContentNode_noDesc_text();
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
        map.put(InstanceCountNodePropertyType.Instance.toString(), node.instanceCount);
    }
    
    @NbBundle.Messages({
        "InstanceCountNodeProeprtyType.instanceCountColLbl1=Instance Count"
    })
    public enum InstanceCountNodePropertyType{
        Instance(Bundle.InstanceCountNodeProeprtyType_instanceCountColLbl1());
        
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
        private Map<String, Md5Metadata> metadata;

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
            Md5Metadata metadata = this.metadata.get(md5);
            return new Md5Node(metadata);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(this.metadata.keySet());
            return true;
        }
    }
}
