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
    
    public CommonFileNode(AbstractFile fsContent) {
        super(Children.LEAF);
        this.content = fsContent;
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
        fillPropertyMap(map, content);

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
     * @param content The content to get properties for.
     */
    static public void fillPropertyMap(Map<String, Object> map, AbstractFile content) {
        map.put(CommonFilePropertyType.Name.toString(), content.getName());
        map.put(CommonFilePropertyType.Md5Hash.toString(), StringUtils.defaultString(content.getMd5Hash()));
    }
    
    @NbBundle.Messages({
        "CommonFilePropertyType.nameColLbl=Name",
        "CommonFilePropertyType.md5HashColLbl=MD5 Hash"})
    public enum CommonFilePropertyType {
        
        Name(Bundle.CommonFilePropertyType_nameColLbl()),
        Md5Hash(Bundle.CommonFilePropertyType_md5HashColLbl());
        
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
