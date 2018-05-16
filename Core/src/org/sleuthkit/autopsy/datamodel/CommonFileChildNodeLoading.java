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
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;

/**
 * A dummy node used by the child factory to display while children are being
 * generated
 */
public class CommonFileChildNodeLoading extends DisplayableItemNode {

    public CommonFileChildNodeLoading(Children children) {
        super(children);
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
        return getClass().getName();
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
        map.put(CommonFileChildLoadingPropertyType.File.toString(), "Loading...");

        final String NO_DESCR = Bundle.AbstractFsContentNode_noDesc_text();
        for (CommonFileChildLoadingPropertyType propType : CommonFileChildLoadingPropertyType.values()) {
            final String propString = propType.toString();
            sheetSet.put(new NodeProperty<>(propString, propString, NO_DESCR, map.get(propString)));
        }

        return sheet;
    }

    /**
     * Represents the sole column for the 'dummy' loading node.
     */
    @NbBundle.Messages({
        "CommonFileChildLoadingPropertyType.fileColLbl=File"
    })
    public enum CommonFileChildLoadingPropertyType {

        File(Bundle.CommonFileChildLoadingPropertyType_fileColLbl());

        final private String displayString;

        private CommonFileChildLoadingPropertyType(String displayString) {
            this.displayString = displayString;
        }

        @Override
        public String toString() {
            return displayString;
        }
    }
}
