/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.Tag;

/**
 * This class is used to represent the "Node" for the pool.
 */
public class PoolNode extends AbstractContentNode<Pool> {

    /**
     * Helper so that the display name and the name used in building the path
     * are determined the same way.
     *
     * @param pool Pool to get the name of
     *
     * @return short name for the pool
     */
    static String nameForPool(Pool pool) {
        return pool.getType().getName();
    }

    /**
     *
     * @param pool underlying Content instance
     */
    public PoolNode(Pool pool) {
        super(pool);

        // set name, display name, and icon
        String poolName = nameForPool(pool);
        this.setDisplayName(poolName);

        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/pool-icon.png"); //NON-NLS
    }

    /**
     * Right click action for volume node
     *
     * @param popup
     *
     * @return
     */
    @Override
    public Action[] getActions(boolean popup) {
        List<Action> actionsList = new ArrayList<>();

        for (Action a : super.getActions(true)) {
            actionsList.add(a);
        }

        return actionsList.toArray(new Action[actionsList.size()]);
        
    }

    @NbBundle.Messages({
        "PoolNode.createSheet.name.name=Name",
        "PoolNode.createSheet.name.displayName=Name",
        "PoolNode.createSheet.name.desc=no description",
        "PoolNode.createSheet.type.name=Type",
        "PoolNode.createSheet.type.displayName=Type",
        "PoolNode.createSheet.type.desc=no description",
    })
    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        Pool pool = this.getContent();
        sheetSet.put(new NodeProperty<>(Bundle.PoolNode_createSheet_name_name(),
                Bundle.PoolNode_createSheet_name_displayName(),
                Bundle.PoolNode_createSheet_name_desc(),
                this.getDisplayName()));
        sheetSet.put(new NodeProperty<>(Bundle.PoolNode_createSheet_type_name(),
                Bundle.PoolNode_createSheet_type_displayName(),
                Bundle.PoolNode_createSheet_type_desc(),
                pool.getType().getName()));

        return sheet;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    /**
     * Reads and returns a list of all tags associated with this content node.
     *
     * Null implementation of an abstract method.
     *
     * @return list of tags associated with the node.
     */
    @Override
    protected List<Tag> getAllTagsFromDatabase() {
        return new ArrayList<>();
    }
}
