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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * Represents the layer in the tree for the value (such as MD5) that was in multiple places. 
 * Children are instances of that value. 
 */
public class CommonAttributeValueNode extends DisplayableItemNode {

    private static final Logger LOGGER = Logger.getLogger(CommonAttributeValueNode.class.getName());

    private final String value;
    private final int commonFileCount;
    private final String cases;
    private final String dataSources;

    @NbBundle.Messages({
        "Md5Node.Md5Node.format=MD5: %s"
    })
    /**
     * Create a Match node whose children will all have this object in common.
     * @param data the common feature, and the children
     */
    public CommonAttributeValueNode(CommonAttributeValue data) {
        super(Children.create(
                new FileInstanceNodeFactory(data), true));
        
        this.commonFileCount = data.getInstanceCount();
        this.cases = data.getCases();
        // @@ We seem to be doing this string concat twice.  We also do it in getDataSources()
        this.dataSources = String.join(", ", data.getDataSources());
        this.value = data.getValue();
        
        this.setDisplayName(String.format(Bundle.Md5Node_Md5Node_format(), this.value));
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/fileset-icon-16.png"); //NON-NLS
    }

    /**
     * How many files are in common?  This will be the number of children.
     * @return int
     */
    int getCommonFileCount() {
        return this.commonFileCount;
    }
    
    String getCases(){
        return this.cases;
    }

    /**
     * Datasources where these matches occur.
     * @return string delimited list of sources
     */
    String getDataSources() {
        return this.dataSources;
    }

    /**
     * MD5 which is common to these matches
     * @return string md5 hash
     */
    public String getValue() {
        return this.value;
    }

    @NbBundle.Messages({"Md5Node.createSheet.noDescription= "})
    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        final String NO_DESCR = Bundle.Md5Node_createSheet_noDescription();
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_filesColLbl(), Bundle.CommonFilesSearchResultsViewerTable_filesColLbl(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_hashsetHitsColLbl(), Bundle.CommonFilesSearchResultsViewerTable_hashsetHitsColLbl(), NO_DESCR, ""));
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
    static class FileInstanceNodeFactory extends ChildFactory<AbstractCommonAttributeSearchResult> {

        private final CommonAttributeValue descendants;

        FileInstanceNodeFactory(CommonAttributeValue descendants) {
            this.descendants = descendants;
        }

        @Override
        protected boolean createKeys(List<AbstractCommonAttributeSearchResult> list) {
            list.addAll(this.descendants.getInstances());
            return true;
        }
        
        @Override
        protected Node[] createNodesForKey(AbstractCommonAttributeSearchResult searchResult) {
            return searchResult.generateNodes();
        }

        
    }
}
