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
package org.sleuthkit.autopsy.casemodule;

import org.sleuthkit.autopsy.coordinationservice.CaseNodeData;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * A NetBeans Explorer View node that represents a multi-user case.
 */
final class MultiUserCaseNode extends AbstractNode {

    private final CaseNodeData caseNodeData;

    /**
     * Constructs a NetBeans Explorer View node that represents a multi-user
     * case.
     *
     * @param caseNodeData The coordination service node data for the case.
     */
    MultiUserCaseNode(CaseNodeData caseNodeData) {
        super(Children.LEAF);
        super.setName(caseNodeData.getDisplayName());
        setName(caseNodeData.getDisplayName());
        setDisplayName(caseNodeData.getDisplayName());
        this.caseNodeData = caseNodeData;
    }

    @NbBundle.Messages({
        "MultiUserCaseNode.column.name=Name",
        "MultiUserCaseNode.column.createTime=Create Time",
        "MultiUserCaseNode.column.path=Path"
    })
    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }
        sheetSet.put(new NodeProperty<>(Bundle.MultiUserCaseNode_column_name(),
                Bundle.MultiUserCaseNode_column_name(),
                Bundle.MultiUserCaseNode_column_name(),
                caseNodeData.getDisplayName()));
        sheetSet.put(new NodeProperty<>(Bundle.MultiUserCaseNode_column_createTime(),
                Bundle.MultiUserCaseNode_column_createTime(),
                Bundle.MultiUserCaseNode_column_createTime(),
                caseNodeData.getCreateDate()));
        sheetSet.put(new NodeProperty<>(Bundle.MultiUserCaseNode_column_path(),
                Bundle.MultiUserCaseNode_column_path(),
                Bundle.MultiUserCaseNode_column_path(),
                caseNodeData.getDirectory().toString()));
        return sheet;
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actions = new ArrayList<>();
        actions.add(new OpenMultiUserCaseAction(this.caseNodeData));
        actions.add(new OpenCaseAutoIngestLogAction(this.caseNodeData));
        return actions.toArray(new Action[actions.size()]);
    }

    @Override
    public Action getPreferredAction() {
        return new OpenMultiUserCaseAction(this.caseNodeData);
    }

    /**
     * Gets the coordintaion service case node data this Explorer View node
     * represents.
     *
     * @return The case node data.
     */
    CaseNodeData getCaseNodeData() {
        return this.caseNodeData;
    }
    
}
