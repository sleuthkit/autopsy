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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * Used by the Common Files search feature to encapsulate instances of a given 
 * MD5s matched in the search.  These nodes will be children of <code>Md5Node</code>s.
 * 
 * Use this type for files which are not in the current case, but from the 
 * Central Repo.  Contrast with <code>SleuthkitCase</code> which should be used 
 * when the FileInstance was found in the case presently open in Autopsy.
 */
public class InterCaseCommonAttributeInstanceNode extends DisplayableItemNode {

    private final CorrelationAttributeInstance crFile;
    
    //this may not be the same file, but at least it is identical, 
    //  and we can use this to support certain actions in the tree table and crFile viewer
    private final AbstractFile md5Reference;
    
    public InterCaseCommonAttributeInstanceNode(CorrelationAttributeInstance content, AbstractFile md5Reference) {
        super(Children.LEAF, Lookups.fixed(content)); // Using md5Reference enables Other Occurances, but for the current file path
        this.crFile = content;
        this.setDisplayName(new File(this.crFile.getFilePath()).getName());
        this.md5Reference = md5Reference;
    }
    
    public CorrelationAttributeInstance getCentralRepoFile(){
        return this.crFile;
    }
    
    public Content getContent(){
        return this.md5Reference;
    }
    
    @Override
    public Action[] getActions(boolean context){
        List<Action> actionsList = new ArrayList<>();
        
        actionsList.addAll(Arrays.asList(super.getActions(true)));
        
        return actionsList.toArray(new Action[actionsList.size()]);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    public String getItemType() {
        //objects of type FileNode will co-occur in the treetable with objects
        //  of this type and they will need to provide the same key
        return IntraCaseCommonAttributeInstanceNode.class.getName();
    }
    
    @Override
    protected Sheet createSheet(){
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        
        if(sheetSet == null){
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }
        
        final CorrelationAttributeInstance centralRepoFile = this.getCentralRepoFile();
        
        final String fullPath = centralRepoFile.getFilePath();
        final File file = new File(fullPath);
        
        final String caseName = centralRepoFile.getCorrelationCase().getDisplayName();
        
        final String name = file.getName();
        final String parent = file.getParent();
        
        final String caseQualifiedDataSource = centralRepoFile.getCorrelationDataSource().getName();
        
        final String NO_DESCR = Bundle.CommonFilesSearchResultsViewerTable_noDescText();
        
        sheetSet.put(new NodeProperty<>(org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_filesColLbl(), org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_filesColLbl(), NO_DESCR, name));
        sheetSet.put(new NodeProperty<>(org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), NO_DESCR, parent));
        sheetSet.put(new NodeProperty<>(org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_hashsetHitsColLbl(), org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_hashsetHitsColLbl(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), NO_DESCR, caseQualifiedDataSource));
        sheetSet.put(new NodeProperty<>(org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_mimeTypeColLbl(), org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_mimeTypeColLbl(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_caseColLbl1(), org.sleuthkit.autopsy.commonfilesearch.Bundle.CommonFilesSearchResultsViewerTable_caseColLbl1(), NO_DESCR, caseName));

        return sheet;        
    }
}
