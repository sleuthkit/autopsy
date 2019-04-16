/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.multiusercasesbrowser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData.DeletedFlags;
import org.sleuthkit.autopsy.casemodule.multiusercasesbrowser.MultiUserCaseBrowserCustomizer.Column;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * A NetBeans Explorer View node that represents a multi-user case in a
 * multi-user cases browser panel.
 */
final class MultiUserCaseNode extends AbstractNode {

    private final CaseNodeData caseNodeData;
    private final MultiUserCaseBrowserCustomizer customizer;

    /**
     * Constructs a NetBeans Explorer View node that represents a multi-user
     * case in a multi-user cases browser panel.
     *
     * @param caseNodeData The coordination service node data for the case.
     * @param customizer   A browser customizer that supplies the actions and
     *                     property sheet properties of the node.
     */
    MultiUserCaseNode(CaseNodeData caseNodeData, MultiUserCaseBrowserCustomizer customizer) {
        super(Children.LEAF, Lookups.fixed(caseNodeData));
        super.setName(caseNodeData.getDisplayName());
        super.setDisplayName(caseNodeData.getDisplayName());
        this.caseNodeData = caseNodeData;
        this.customizer = customizer;
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }
        for (Column property : customizer.getColumns()) {
            String propName = property.getDisplayName();
            switch (property) {
                case CREATE_DATE:
                    sheetSet.put(new NodeProperty<>(propName, propName, propName, caseNodeData.getCreateDate()));
                    break;
                case DIRECTORY:
                    sheetSet.put(new NodeProperty<>(propName, propName, propName, caseNodeData.getDirectory().toString()));
                    break;
                case LAST_ACCESS_DATE:
                    sheetSet.put(new NodeProperty<>(propName, propName, propName, caseNodeData.getLastAccessDate()));
                    break;
                case MANIFEST_FILE_ZNODES_DELETE_STATUS:
                    sheetSet.put(new NodeProperty<>(propName, propName, propName, isDeleted(DeletedFlags.MANIFEST_FILE_NODES)));
                    break;
                case DATA_SOURCES_DELETE_STATUS:
                    sheetSet.put(new NodeProperty<>(propName, propName, propName, isDeleted(DeletedFlags.DATA_SOURCES)));
                    break;
                case TEXT_INDEX_DELETE_STATUS:
                    sheetSet.put(new NodeProperty<>(propName, propName, propName, isDeleted(DeletedFlags.TEXT_INDEX)));
                    break;
                case CASE_DB_DELETE_STATUS:
                    sheetSet.put(new NodeProperty<>(propName, propName, propName, isDeleted(DeletedFlags.CASE_DB)));
                    break;
                case CASE_DIR_DELETE_STATUS:
                    sheetSet.put(new NodeProperty<>(propName, propName, propName, isDeleted(DeletedFlags.CASE_DIR)));
                    break;
                default:
                    break;
            }
        }
        return sheet;
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actions = new ArrayList<>();
        actions.addAll(customizer.getActions(caseNodeData));
        actions.addAll(Arrays.asList(super.getActions(context)));
        return actions.toArray(new Action[actions.size()]);
    }

    @Override
    public Action getPreferredAction() {
        return customizer.getPreferredAction(caseNodeData);
    }

    /**
     * Interprets the deletion status of part of a case.
     *
     * @param flag         The coordination service node data deleted items flag
     *                     to interpret.
     *
     * @return A string stating "True" or "False."
     */
    @NbBundle.Messages({
        "MultiUserCaseNode.columnValue.true=True",
        "MultiUserCaseNode.column.createTime=False",
    })    
    private String isDeleted(CaseNodeData.DeletedFlags flag) {
        return caseNodeData.isDeletedFlagSet(flag) ? "True" : "False";
    }

}
