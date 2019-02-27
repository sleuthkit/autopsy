/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;

/**
 * An action that deletes the auto ingest job input directories associated with
 * one or more multi-user cases. The coordination service nodes for the auto
 * ingest jobs are not deleted. This supports the use case where the directories
 * may need to be directed to reclaim space, but the option to restore the
 * directories without having the jobs be reprocessed is retained.
 *
 * This cases to delete are discovered by querying the actions global context
 * lookup for CaseNodeData objects. See
 * https://platform.netbeans.org/tutorials/nbm-selection-1.html and
 * https://platform.netbeans.org/tutorials/nbm-selection-2.html for details.
 */
final class DeleteCaseInputDirectoriesAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an action that deletes the auto ingest job input directories
     * associated with one or more multi-user cases. The coordination service
     * nodes for the auto ingest jobs are not deleted. This supports the use
     * case where the directories may need to be directed to reclaim space, but
     * the option to restore the directories without having the jobs be
     * reprocessed is retained.
     */
    @NbBundle.Messages({
        "DeleteCaseInputDirectoriesAction.menuItemText=Delete Input Directories"
    })
    DeleteCaseInputDirectoriesAction() {
        super(Bundle.DeleteCaseInputDirectoriesAction_menuItemText());
        setEnabled(false); // RJCTODO: Enable when implemented
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        final Collection<CaseNodeData> selectedNodeData = new ArrayList<>(Utilities.actionsGlobalContext().lookupAll(CaseNodeData.class));
//        if (!selectedNodeData.isEmpty()) {
//            /*
//             * RJCTODO: Create a background task that does the deletion and
//             * displays results in a dialog with a scrolling text pane.
//             */
//        }
    }

    @Override
    public DeleteCaseInputDirectoriesAction clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

}
