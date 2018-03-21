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

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;

/**
 * Represents a common files match - two or more files which appear to be the
 * same file and appear as children of this node.
 */
public class CommonFileParentNode extends AbstractNode {

    private final String md5Hash;
    private final int commonFileCount;
    private final String dataSources;

    public CommonFileParentNode(Children children, String md5Hash, int commonFileCount, String dataSources) {
        super(children);
        this.commonFileCount = commonFileCount;
        this.dataSources = dataSources;
        this.md5Hash = md5Hash;
    }

    int getCommonFileCount() {
        return this.commonFileCount;
    }

    String getDataSources() {
        return this.dataSources;
    }
    
    public String getMd5(){
        return this.md5Hash;
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, this);

        final String NO_DESCR = Bundle.AbstractFsContentNode_noDesc_text();
        for (CommonFileParentNode.CommonFileParentPropertyType propType : CommonFileParentNode.CommonFileParentPropertyType.values()) {
            final String propString = propType.toString();
            sheetSet.put(new NodeProperty<>(propString, propString, NO_DESCR, map.get(propString)));
        }

        return sheet;
    }

    /**
     * Fill map with AbstractFile properties
     *
     * @param map map with preserved ordering, where property names/values are
     * put
     * @param node The item to get properties for.
     */
    static private void fillPropertyMap(Map<String, Object> map, CommonFileParentNode node) {
        map.put(CommonFileParentNode.CommonFileParentPropertyType.Md5Hash.toString(), StringUtils.defaultString(node.getMd5()));
        map.put(CommonFileParentNode.CommonFileParentPropertyType.InstanceCount.toString(), node.getCommonFileCount());
        map.put(CommonFileParentNode.CommonFileParentPropertyType.DataSources.toString(), node.getDataSources());
    }

    @NbBundle.Messages({
        "CommonFileParentPropertyType.instanceColLbl1=Instance Count",
        "CommonFileParentPropertyType.md5HashColLbl=MD5 Hash",
        "CommonFileParentPropertyType.dataSourcesColLbl=Data Sources"})
    public enum CommonFileParentPropertyType {

        Md5Hash(Bundle.CommonFileParentPropertyType_md5HashColLbl()),
        InstanceCount(Bundle.CommonFileParentPropertyType_instanceColLbl1()),
        DataSources(Bundle.CommonFileParentPropertyType_dataSourcesColLbl());

        final private String displayString;

        private CommonFileParentPropertyType(String displayString) {
            this.displayString = displayString;
        }

        @Override
        public String toString() {
            return displayString;
        }
    }
}
