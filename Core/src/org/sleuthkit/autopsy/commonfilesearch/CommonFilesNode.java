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
package org.sleuthkit.autopsy.commonfilesearch;

import org.sleuthkit.autopsy.datamodel.InstanceCountNode;
import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;

/**
 * Wrapper node for <code>Md5Node</code> used to display common files search
 * results in the top right pane. Calls <code>Md5NodeFactory</code>.
 */
final public class CommonFilesNode extends DisplayableItemNode {
    

    CommonFilesNode(CommonFilesMetadata metadataList) {
        super(Children.create(new InstanceCountNodeFactory(metadataList), true));
    }

    @NbBundle.Messages({
        "CommonFilesNode.getName.text=Common Files"})
    @Override
    public String getName() {
        return Bundle.CommonFilesNode_getName_text();
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
    
    static class InstanceCountNodeFactory extends ChildFactory<Integer>{

        private final CommonFilesMetadata metadata;
        
        InstanceCountNodeFactory(CommonFilesMetadata metadata){
            this.metadata = metadata;
        }
        
        @Override
        protected boolean createKeys(List<Integer> list) {
            list.addAll(this.metadata.getMetadata().keySet());
            return true;
        }
        
        @Override
        protected Node createNodeForKey(Integer instanceCount){
            List<Md5Metadata> md5Metadata =  this.metadata.getMetadataForMd5(instanceCount);
            return new InstanceCountNode(instanceCount, md5Metadata);
        }        
    }
}
