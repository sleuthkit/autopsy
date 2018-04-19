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

import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.Md5Node;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;

/**
 * Wrapper node for <code>Md5Node</code> used to display common files search
 * results in the top right pane. Calls <code>Md5NodeFactory</code>.
 */
final public class CommonFilesNode extends DisplayableItemNode {

    CommonFilesNode(CommonFilesMetaData metaDataList) {
        super(Children.create(new Md5NodeFactory(metaDataList), true), Lookups.singleton(CommonFilesNode.class));
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

    /**
     * ChildFactory which builds CommonFileParentNodes from the
     * CommonFilesMetaaData models.
     */
    static class Md5NodeFactory extends ChildFactory<String> {

        /**
         * List of models, each of which is a parent node matching a single md5,
         * containing children FileNodes.
         */
        private CommonFilesMetaData metadata;

        Md5NodeFactory(CommonFilesMetaData theMetaDataList) {
            this.metadata = theMetaDataList;
        }

        protected void removeNotify() {
            metadata = null;
        }

        @Override
        protected Node createNodeForKey(String md5) {
            Md5MetaData metaData = this.metadata.getMetaDataForMd5(md5);
            return new Md5Node(metaData);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(this.metadata.getMataData().keySet());
            return true;
        }
    }
}
