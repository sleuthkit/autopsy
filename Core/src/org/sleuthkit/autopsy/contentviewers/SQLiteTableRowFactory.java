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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * Factory class to generate nodes for SQLite table rows
 */

class SQLiteTableRowFactory extends ChildFactory<Integer> {

    private final List<Map<String, Object>> rows;
    private final List<Action> colActions;

    SQLiteTableRowFactory(List<Map<String, Object>> rows, List<Action> actions ) {
        this.rows = rows;
        this.colActions = actions;
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

        return new SQLiteTableRowNode(rows.get(key), this.colActions );
    }

}

/**
 * 
 * Node for SQLite table row
 */
class SQLiteTableRowNode extends AbstractNode {

    private final Map<String, Object> row;
    private final List<Action> nodeActions;
    
    SQLiteTableRowNode(Map<String, Object> row, List<Action> actions) {
        super(Children.LEAF);
        this.row = row;
        this.nodeActions = actions;
    }

    @Override
    protected Sheet createSheet() {

        Sheet sheet = super.createSheet();
        Sheet.Set properties = sheet.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            sheet.put(properties);
        }

        for (Map.Entry<String, Object> col : row.entrySet()) {
            String colName = col.getKey();
            String colVal = col.getValue().toString();
            properties.put(new NodeProperty<>(colName, colName, colName, colVal)); // NON-NLS
        }

        return sheet;
    }
    
     @Override
        public Action[] getActions(boolean context) {
            List<Action> actions = new ArrayList<>();
            
            actions.addAll(nodeActions);
            actions.add(null);
            actions.addAll(Arrays.asList(super.getActions(context)));
           
            return actions.toArray(new Action[actions.size()]);
        }
              
}
