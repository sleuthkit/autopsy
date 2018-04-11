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
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Encapsulates data being pushed to Common Files component in top right pane.
 */
public class CommonFileChildNode extends FileNode {

    private final String dataSource;

    public CommonFileChildNode(AbstractFile fsContent, String dataSource) {
        super(fsContent);
        this.content = fsContent;
        this.dataSource = dataSource;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public AbstractFile getContent() {
        return this.content;
    }

    String getDataSource() {
        return this.dataSource;
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
        for (CommonFilePropertyType propType : CommonFilePropertyType.values()) {
            final String propString = propType.toString();
            sheetSet.put(new NodeProperty<>(propString, propString, NO_DESCR, map.get(propString)));
        }

        this.addTagProperty(sheetSet);

        return sheet;
    }

    /**
     * Fill map with AbstractFile properties
     *
     * @param map map with preserved ordering, where property names/values are
     * put
     * @param node The item to get properties for.
     */
    static private void fillPropertyMap(Map<String, Object> map, CommonFileChildNode node) {

        map.put(CommonFilePropertyType.File.toString(), node.getName());
        map.put(CommonFilePropertyType.ParentPath.toString(), node.getContent().getParentPath());
        map.put(CommonFilePropertyType.HashsetHits.toString(), getHashSetHitsForFile(node.getContent()));
        map.put(CommonFilePropertyType.DataSource.toString(), node.getDataSource());
    }

    @NbBundle.Messages({
        "CommonFilePropertyType.fileColLbl=File",
        "CommonFilePropertyType.pathColLbl=Parent Path",
        "CommonFilePropertyType.hashsetHitsColLbl=Hash Set Hits",
        "CommonFilePropertyType.dataSourceColLbl=Data Source"
    })
    public enum CommonFilePropertyType {

        File(Bundle.CommonFilePropertyType_fileColLbl()),
        ParentPath(Bundle.CommonFilePropertyType_pathColLbl()),
        HashsetHits(Bundle.CommonFilePropertyType_hashsetHitsColLbl()),
        DataSource(Bundle.CommonFilePropertyType_dataSourceColLbl());

        final private String displayString;

        private CommonFilePropertyType(String displayString) {
            this.displayString = displayString;
        }

        @Override
        public String toString() {
            return displayString;
        }
    }   
    
}
