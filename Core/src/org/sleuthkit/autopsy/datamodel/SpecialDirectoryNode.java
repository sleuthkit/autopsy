/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2021 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.DeleteDataSourceAction;
import org.sleuthkit.autopsy.datasourcesummary.ui.ViewSummaryInformationAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.FileSearchTreeAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.RunIngestModulesAction;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SpecialDirectory;

/**
 * Parent class for special directory types (Local and Virtual)
 */
public abstract class SpecialDirectoryNode extends AbstractAbstractFileNode<SpecialDirectory> {

    public SpecialDirectoryNode(SpecialDirectory sd) {
        super(sd);
    }

    /**
     * Right click action for this node
     *
     * @param popup
     *
     * @return
     */
    @Override
    @NbBundle.Messages({"SpecialDirectoryNode.getActions.viewInNewWin.text=View in New Window"
    })
    public Action[] getActions(boolean popup) {
        List<Action> actions = new ArrayList<>();
        actions.add(new NewWindowViewAction(
                Bundle.SpecialDirectoryNode_getActions_viewInNewWin_text(), this));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(ExportCSVAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(new FileSearchTreeAction(Bundle.ImageNode_getActions_openFileSearchByAttr_text(), content.getId()));
        if (content.isDataSource()) {
            actions.add(new ViewSummaryInformationAction(content.getId()));
            actions.add(new RunIngestModulesAction(Collections.<Content>singletonList(content)));
            actions.add(new DeleteDataSourceAction(content.getId()));
        } else {
            actions.add(new RunIngestModulesAction(content));
        }
        actions.addAll(ContextMenuExtensionPoint.getActions());
        actions.add(null);
        actions.addAll(Arrays.asList(super.getActions(true)));
        return actions.toArray(new Action[0]);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public String getItemType() {
        // use content.isDataSource if different column settings are desired
        return DisplayableItemNode.FILE_PARENT_NODE_KEY;
    }
}
