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
package org.sleuthkit.autopsy.contentviewers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

public class SQLiteTableRowFactory extends ChildFactory<Integer> {

    private final ArrayList<Map<String, Object>> rows;

    public SQLiteTableRowFactory(ArrayList<Map<String, Object>> rows) {
        this.rows = rows;
    }

    @Override
    protected boolean createKeys(List<Integer> keys) {
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                keys.add(i);
            }
        }
        return true;
    }

    @Override
    protected Node createNodeForKey(Integer key) {
        if (Objects.isNull(rows) || rows.isEmpty() || key >= rows.size()) {
            return null;
        }

        return new SQLiteTableRowNode(rows.get(key));
    }

}

class SQLiteTableRowNode extends AbstractNode {

    private final Map<String, Object> row;

    public SQLiteTableRowNode(Map<String, Object> row) {
        super(Children.LEAF);
        this.row = row;
    }

    @Override
    protected Sheet createSheet() {

        Sheet s = super.createSheet();
        Sheet.Set properties = s.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            s.put(properties);
        }

        for (Map.Entry<String, Object> col : row.entrySet()) {
            String colName = col.getKey();
            String colVal = col.getValue().toString();

            properties.put(new NodeProperty<>(colName, colName, colName, colVal)); // NON-NLS
        }

        return s;
    }
}
