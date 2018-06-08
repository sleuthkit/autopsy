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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepositoryFile;
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
public class CentralRepositoryFileInstanceNode extends DisplayableItemNode {

    private final CentralRepositoryFile crFile;
    
    //this may not be the same file, but at least it is identical, 
    //  and we can use this to support certain actions in the tree table and crFile viewer
    private final AbstractFile md5Reference;
    
    public CentralRepositoryFileInstanceNode(CentralRepositoryFile content, AbstractFile md5Reference) {
        super(Children.LEAF, Lookups.fixed(content));

        this.crFile = content;
        this.setDisplayName(new File(this.crFile.getFilePath()).getName());
        this.md5Reference = md5Reference;
    }
    
    public CentralRepositoryFile getCentralRepoFile(){
        return this.crFile;
    }
    
    public Content getContent(){
        return this.md5Reference;
    }
    
    @Override
    public Action[] getActions(boolean context){
        List<Action> actionsList = new ArrayList<>();
        
        actionsList.addAll(Arrays.asList(super.getActions(true)));
        //TODO probably can support more than just this
        
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
        return SleuthkitCaseFileInstanceNode.class.getName();
    }
    
    @Override
    protected Sheet createSheet(){
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        
        if(sheetSet == null){
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }
        
        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, this);
        
        final String NO_DESCR = Bundle.AbstractFsContentNode_noDesc_text();
        for (CentralRepoFileInstancesPropertyType propType : CentralRepoFileInstancesPropertyType.values()) {
            final String propString = propType.toString();
            final Object property = map.get(propString);
            final NodeProperty<Object> nodeProperty = new NodeProperty<>(propString, propString, NO_DESCR, property);
            sheetSet.put(nodeProperty);
        }
        
        return sheet;        
    }

    /**
     * Fill map with CentralRepoFileInstance properties
     *
     * @param map map with preserved ordering, where property names/values are
     * put
     * @param node The item to get properties for.
     */
    private static void fillPropertyMap(Map<String, Object> map, CentralRepositoryFileInstanceNode node) {
        
        final CentralRepositoryFile centralRepoFile = node.getCentralRepoFile();
        
        final String fullPath = centralRepoFile.getFilePath();
        final File file = new File(fullPath);
        
        final String name = file.getName();
        final String parent = file.getParent();
        
        map.put(CentralRepositoryFileInstanceNode.CentralRepoFileInstancesPropertyType.File.toString(), name);
        map.put(CentralRepositoryFileInstanceNode.CentralRepoFileInstancesPropertyType.ParentPath.toString(), parent);
        map.put(CentralRepositoryFileInstanceNode.CentralRepoFileInstancesPropertyType.HashsetHits.toString(), "");
        map.put(CentralRepositoryFileInstanceNode.CentralRepoFileInstancesPropertyType.DataSource.toString(), centralRepoFile.getCorrelationDataSource().getName());
        map.put(CentralRepositoryFileInstanceNode.CentralRepoFileInstancesPropertyType.MimeType.toString(), "");
    }
    
    /**
     * Encapsulates the columns to be displayed for reach row represented by an 
     * instance of this object.
     */
    @NbBundle.Messages({
        "CentralRepoFileInstancesPropertyType.fileColLbl=File",
        "CentralRepoFileInstancesPropertyType.pathColLbl=Parent Path",
        "CentralRepoFileInstancesPropertyType.hashsetHitsColLbl=Hash Set Hits",
        "CentralRepoFileInstancesPropertyType.dataSourceColLbl=Data Source",
        "CentralRepoFileInstancesPropertyType.mimeTypeColLbl=MIME Type"
    })
    public enum CentralRepoFileInstancesPropertyType {
        
        File(Bundle.CentralRepoFileInstancesPropertyType_fileColLbl()),
        ParentPath(Bundle.CentralRepoFileInstancesPropertyType_pathColLbl()),
        HashsetHits(Bundle.CentralRepoFileInstancesPropertyType_hashsetHitsColLbl()),
        DataSource(Bundle.CentralRepoFileInstancesPropertyType_dataSourceColLbl()),
        MimeType(Bundle.CentralRepoFileInstancesPropertyType_mimeTypeColLbl());

        final private String displayString;

        private CentralRepoFileInstancesPropertyType(String displayString) {
            this.displayString = displayString;
        }

        @Override
        public String toString() {
            return displayString;
        }
    }
        
}
