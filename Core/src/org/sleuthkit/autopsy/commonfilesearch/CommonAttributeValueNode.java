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
 * Represents a common files match - two or more files which appear to be the
 * same file and appear as children of this node. This node will simply contain
 * the MD5 of the matched files, the data sources those files were found within,
 * and a count of the instances represented by the md5.
 */
public class CommonAttributeValueNode extends DisplayableItemNode {

    private static final Logger LOGGER = Logger.getLogger(CommonAttributeValueNode.class.getName());

    private final String md5Hash;
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
        
        this.commonFileCount = data.size();
        this.cases = data.getCases();
        this.dataSources = String.join(", ", data.getDataSources());
        this.md5Hash = data.getMd5();
        
        this.setDisplayName(String.format(Bundle.Md5Node_Md5Node_format(), this.md5Hash));
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
    public String getMd5() {
        return this.md5Hash;
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
    static class FileInstanceNodeFactory extends ChildFactory<AbstractCommonAttributeInstanceNode> {

        private final CommonAttributeValue descendants;

        FileInstanceNodeFactory(CommonAttributeValue descendants) {
            this.descendants = descendants;
        }

        @Override
        protected Node[] createNodesForKey(AbstractCommonAttributeInstanceNode file) {
            return file.generateNodes();
        }

        @Override
        protected boolean createKeys(List<AbstractCommonAttributeInstanceNode> list) {
            list.addAll(this.descendants.getMetadata());
            return true;
        }
    }
}
