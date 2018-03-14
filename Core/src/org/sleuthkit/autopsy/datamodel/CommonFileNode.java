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
package org.sleuthkit.autopsy.datamodel;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Encapsulates data being pushed to Common Files component in top right pane.
 */
public class CommonFileNode extends AbstractNode {

    private final AbstractFile content;
    
    private final int commonFileCount;
    
    private final String dataSources;
    
    public CommonFileNode(AbstractFile fsContent, int commonFileCount, String dataSources) {
        super(Children.LEAF);
        this.content = fsContent;
        this.commonFileCount = commonFileCount;
        this.dataSources = dataSources;
    }
    
    int getCommonFileCount(){
        return this.commonFileCount;
    }
    
    AbstractFile getContent(){
        return this.content;
    }
    
    String getDataSources(){
        return this.dataSources;
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, this);

        final String NO_DESCR = Bundle.AbstractFsContentNode_noDesc_text();
        for (CommonFilePropertyType propType : CommonFilePropertyType.values()) {
            final String propString = propType.toString();
            ss.put(new NodeProperty<>(propString, propString, NO_DESCR, map.get(propString)));
        }

        // add tags property to the sheet
        //addTagProperty(ss);

        return s;
    }
    
    /**
     * Fill map with AbstractFile properties
     *
     * @param map     map with preserved ordering, where property names/values
     *                are put
     * @param node The item to get properties for.
     */
    static public void fillPropertyMap(Map<String, Object> map, CommonFileNode node) {
        map.put(CommonFilePropertyType.Name.toString(), node.getContent().getName());
        map.put(CommonFilePropertyType.InstanceCount.toString(), node.getCommonFileCount());
        map.put(CommonFilePropertyType.Md5Hash.toString(), StringUtils.defaultString(node.getContent().getMd5Hash()));
        map.put(CommonFilePropertyType.DataSources.toString(), node.getDataSources());
    }
    
    @NbBundle.Messages({
        "CommonFilePropertyType.nameColLbl=Name",
        "CommonFilePropertyType.instanceColLbl1=Instance Count",
        "CommonFilePropertyType.md5HashColLbl=MD5 Hash",
        "CommonFilePropertyType.dataSourcesColLbl=Data Sources"})
    public enum CommonFilePropertyType {
        
        Name(Bundle.CommonFilePropertyType_nameColLbl()),
        InstanceCount(Bundle.CommonFilePropertyType_instanceColLbl1()),
        Md5Hash(Bundle.CommonFilePropertyType_md5HashColLbl()),
        DataSources(Bundle.CommonFilePropertyType_dataSourcesColLbl());
        
        final private String displayString;
        
        private CommonFilePropertyType(String displayString){
            this.displayString = displayString;
        }
        
        @Override
        public String toString() {
            return displayString;
        }
    }
}
