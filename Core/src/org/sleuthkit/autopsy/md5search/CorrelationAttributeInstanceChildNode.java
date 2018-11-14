/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.md5search;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.commonfilesearch.CaseDBCommonAttributeInstanceNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * //DLG:
 */
public final class CorrelationAttributeInstanceChildNode extends DisplayableItemNode {
    private String caseName;
    private String dataSourceName;
    //DLG: final ? knownStatus
    private String fullPath;
    //DLG: final String comment
    //DLG: final String deviceId
    private String name;
    private String parent;

    public CorrelationAttributeInstanceChildNode(Children children) {
        super(children);
    }
    
    //CorrelationAttributeInstanceChildNode(Children children) {
    //    init(null);
    //}
    
    private void init(Map<String, Object> map) {
        caseName = (String)map.get("caseName");
        dataSourceName = (String)map.get("dataSourceName");
        fullPath = (String)map.get("fullPath");
        name = (String)map.get("name");
        parent = (String)map.get("parent");
    }
    
    @Override
    public Action[] getActions(boolean context){
        List<Action> actionsList = new ArrayList<>();
        
        actionsList.addAll(Arrays.asList(super.getActions(true)));
        
        return actionsList.toArray(new Action[actionsList.size()]);
    }

    /*@Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }*/

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    public String getItemType() {
        //objects of type FileNode will co-occur in the treetable with objects
        //  of this type and they will need to provide the same key
        return CorrelationAttributeInstanceChildNode.class.getName();
    }
    
    @NbBundle.Messages({
        "CorrelationAttributeInstanceChildNode.columnName.case=Case",
        "CorrelationAttributeInstanceChildNode.columnName.dataSource=Data Source",
        "CorrelationAttributeInstanceChildNode.columnName.known=Known",
        "CorrelationAttributeInstanceChildNode.columnName.path=Path",
        "CorrelationAttributeInstanceChildNode.columnName.comment=Comment",
        "CorrelationAttributeInstanceChildNode.columnName.device=Device"
    })
    @Override
    protected Sheet createSheet(){
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        
        if(sheetSet == null){
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        
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
                Bundle.CorrelationAttributeInstanceNode_columnName_device(), "", caseName));

        return sheet;        
    }
}
