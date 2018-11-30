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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;

/**
 * Top-level node to store common file search results.  Current structure is:
 * - node for number of matches 
 * -- node for MD5/commmon attribute
 * --- node for instance. 
 */
final public class CommonAttributeSearchResultRootNode extends DisplayableItemNode {

    CommonAttributeSearchResultRootNode(CommonAttributeSearchResults metadataList) {
        super(Children.create(new InstanceCountNodeFactory(metadataList), true));
    }

        CommonAttributeSearchResultRootNode(CommonAttributeSearchResults2 metadataList) {
        super(Children.create(new CaseNameNodeFactory(metadataList), true));
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
     * Used to generate <code>InstanceCountNode</code>s.
     */
    static class InstanceCountNodeFactory extends ChildFactory<Integer>{

        private static final Logger LOGGER = Logger.getLogger(InstanceCountNodeFactory.class.getName());
        
        private final CommonAttributeSearchResults searchResults;
        
        /**
         * Build a factory which converts a <code>CommonAttributeSearchResults</code> 
         * object into <code>DisplayableItemNode</code>s.
         * @param searchResults 
         */
        InstanceCountNodeFactory(CommonAttributeSearchResults searchResults){
            this.searchResults = searchResults;
        }

        @Override
        protected boolean createKeys(List<Integer> list) {
            try {
                list.addAll(this.searchResults.getMetadata().keySet());
            } catch (EamDbException ex) {
                LOGGER.log(Level.SEVERE, "Unable to create keys.", ex);
            }
            return true;
        }
        
        @Override
        protected Node createNodeForKey(Integer instanceCount){
            CommonAttributeValueList attributeValues =  this.searchResults.getAttributeValuesForInstanceCount(instanceCount);
            return new InstanceCountNode(instanceCount, attributeValues);
        }
    }
    
        /**
     * Used to generate <code>InstanceCountNode</code>s.
     */
    static class CaseNameNodeFactory extends ChildFactory<String>{

        private static final Logger LOGGER = Logger.getLogger(InstanceCountNodeFactory.class.getName());
        
        private final CommonAttributeSearchResults2 searchResults;
        
        /**
         * Build a factory which converts a <code>CommonAttributeSearchResults</code> 
         * object into <code>DisplayableItemNode</code>s.
         * @param searchResults 
         */
        CaseNameNodeFactory(CommonAttributeSearchResults2 searchResults){
            this.searchResults = searchResults;
        }

        @Override
        protected boolean createKeys(List<String> list) {
            try {
                list.addAll(this.searchResults.getMetadata().keySet());
            } catch (EamDbException ex) {
                LOGGER.log(Level.SEVERE, "Unable to create keys.", ex);
            }
            return true;
        }
        
        @Override
        protected Node createNodeForKey(String caseName){
            Map<String, CommonAttributeValueList> dataSourceNameToInstances =  this.searchResults.getAttributeValuesForCaseName(caseName);
            return new InstanceCaseNode(caseName, dataSourceNameToInstances);
        }
    }
}
