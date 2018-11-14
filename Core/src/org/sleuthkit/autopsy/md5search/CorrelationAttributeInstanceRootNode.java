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
package org.sleuthkit.autopsy.md5search;

import org.sleuthkit.autopsy.commonfilesearch.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * Used by the Common Files search feature to encapsulate instances of a given 
 * MD5s matched in the search.  These nodes will be children of <code>Md5Node</code>s.
 * 
 * Use this type for files which are not in the current case, but from the 
 * Central Repo.  Contrast with <code>SleuthkitCase</code> which should be used 
 * when the FileInstance was found in the case presently open in Autopsy.
 */
final class CorrelationAttributeInstanceRootNode extends DisplayableItemNode {

    public CorrelationAttributeInstanceRootNode(Children children) {
        super(children);
    }

    //private final CorrelationAttributeInstance crFile;
    
    //CorrelationAttributeInstanceRootNode(CorrelationAttributeSearchResults data) {
        //super(Children.create(new FileInstanceNodeFactory(data), true));
    //}
    
    @Override
    public Action[] getActions(boolean context){
        List<Action> actionsList = new ArrayList<>();
        
        actionsList.addAll(Arrays.asList(super.getActions(true)));
        
        return actionsList.toArray(new Action[actionsList.size()]);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return null;
        //return visitor.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    public String getItemType() {
        //objects of type FileNode will co-occur in the treetable with objects
        //  of this type and they will need to provide the same key
        return CaseDBCommonAttributeInstanceNode.class.getName();
    }
    
    @NbBundle.Messages({
        "CorrelationAttributeInstanceNode.columnName.case=Case",
        "CorrelationAttributeInstanceNode.columnName.dataSource=Data Source",
        "CorrelationAttributeInstanceNode.columnName.known=Known",
        "CorrelationAttributeInstanceNode.columnName.path=Path",
        "CorrelationAttributeInstanceNode.columnName.comment=Comment",
        "CorrelationAttributeInstanceNode.columnName.device=Device"
    })
    @Override
    protected Sheet createSheet(){
        Sheet sheet = new Sheet();
        /*Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        
        if(sheetSet == null){
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }
        
        final CorrelationAttributeInstance centralRepoFile = this.getCorrelationAttributeInstance();
        
        final String caseName = centralRepoFile.getCorrelationCase().getDisplayName();
        final String dataSourceName = centralRepoFile.getCorrelationDataSource().getName();
        //DLG: final ? knownStatus
        final String fullPath = centralRepoFile.getFilePath();
        //DLG: final String comment
        //DLG: final String deviceId
        
        final File file = new File(fullPath);
        final String name = file.getName();
        final String parent = file.getParent();

        
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_case(),
                Bundle.CorrelationAttributeInstanceNode_columnName_case(), "", name));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_dataSource(),
                Bundle.CorrelationAttributeInstanceNode_columnName_dataSource(), "", parent));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_known(),
                Bundle.CorrelationAttributeInstanceNode_columnName_known(), "", ""));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_path(),
                Bundle.CorrelationAttributeInstanceNode_columnName_path(), "", dataSourceName));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_comment(),
                Bundle.CorrelationAttributeInstanceNode_columnName_comment(), "", ""));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_device(),
                Bundle.CorrelationAttributeInstanceNode_columnName_device(), "", caseName));*/

        return sheet;        
    }

    /**
     * Child generator for <code>SleuthkitCaseFileInstanceNode</code> of
     * <code>CommonAttributeValueNode</code>.
     */
    static class FileInstanceNodeFactory extends ChildFactory<CorrelationAttributeSearchResults> {

        private final CommonAttributeValue descendants;

        FileInstanceNodeFactory(CommonAttributeValue descendants) {
            this.descendants = descendants;
        }

        @Override
        protected boolean createKeys(List<CorrelationAttributeSearchResults> list) {
            return true;
        }
        
        /*@Override
        protected Node[] createNodesForKey(AbstractCommonAttributeInstance searchResult) {
            return null;
            //return searchResult.generateNodes();
        }*/
    }
}
