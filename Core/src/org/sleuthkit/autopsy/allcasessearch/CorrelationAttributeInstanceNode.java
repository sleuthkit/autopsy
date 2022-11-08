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
package org.sleuthkit.autopsy.allcasessearch;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * Used by the All Cases Search feature to encapsulate instances of a given
 * search match.
 */
public final class CorrelationAttributeInstanceNode extends DisplayableItemNode {

    private final CorrelationAttributeInstance instance;
    
    CorrelationAttributeInstanceNode(CorrelationAttributeInstance content) {
        super(Children.LEAF, Lookups.fixed(content));
        this.instance = content;
        this.setDisplayName(new File(this.instance.getFilePath()).getName());
    }
    
    /**
     * Get the CorrelationAttributeInstance attached to the node.
     * 
     * @return The CorrelationAttributeInstance object.
     */
    public CorrelationAttributeInstance getCorrelationAttributeInstance(){
        return this.instance;
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
        return CorrelationAttributeInstanceNode.class.getName();
    }
    
    @NbBundle.Messages({
        "CorrelationAttributeInstanceNode.columnName.name=Name",
        "CorrelationAttributeInstanceNode.columnName.case=Case",
        "CorrelationAttributeInstanceNode.columnName.dataSource=Data Source",
        "CorrelationAttributeInstanceNode.columnName.value=Value",        
        "CorrelationAttributeInstanceNode.columnName.known=Known",
        "CorrelationAttributeInstanceNode.columnName.path=Path",
        "CorrelationAttributeInstanceNode.columnName.comment=Comment",
        "CorrelationAttributeInstanceNode.columnName.device=Device"
    })
    @Override
    protected Sheet createSheet(){
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        
        if(sheetSet == null){
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }
        
        final CorrelationAttributeInstance centralRepoFile = this.getCorrelationAttributeInstance();
        
        final String path = centralRepoFile.getFilePath();
        final File file = new File(path);
        final String name = file.getName();
        final String caseName = centralRepoFile.getCorrelationCase().getDisplayName();
        final CorrelationDataSource dataSource = centralRepoFile.getCorrelationDataSource();
        final String dataSourceName = dataSource.getName();
        final String known = centralRepoFile.getKnownStatus().getName();
        final String comment = centralRepoFile.getComment();
        final String value = centralRepoFile.getCorrelationValue();
        final String device = dataSource.getDeviceID();
        
        final String NO_DESCR = "";
        
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_name(),
                Bundle.CorrelationAttributeInstanceNode_columnName_name(), NO_DESCR, name));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_case(),
                Bundle.CorrelationAttributeInstanceNode_columnName_case(), NO_DESCR, caseName));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_dataSource(),
                Bundle.CorrelationAttributeInstanceNode_columnName_dataSource(), NO_DESCR, dataSourceName));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_value(),
                Bundle.CorrelationAttributeInstanceNode_columnName_value(), NO_DESCR, value));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_known(),
                Bundle.CorrelationAttributeInstanceNode_columnName_known(), NO_DESCR, known));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_path(),
                Bundle.CorrelationAttributeInstanceNode_columnName_path(), NO_DESCR, path));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_comment(),
                Bundle.CorrelationAttributeInstanceNode_columnName_comment(), NO_DESCR, comment));
        sheetSet.put(new NodeProperty<>(
                Bundle.CorrelationAttributeInstanceNode_columnName_device(),
                Bundle.CorrelationAttributeInstanceNode_columnName_device(), NO_DESCR, device));

        return sheet;        
    }
}
