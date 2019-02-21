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
 * An action that deletes everything except the auto ingest job input
 * directories for one or more multi-user cases. This supports the use case
 * where a case needs to be reprocessed, so the input directories are not
 * deleted even though the coordination service nodes for the auto ingest jobs
 * are deleted.
 *
 * This cases to delete are discovered by querying the actions global context
 * lookup for CaseNodeData objects. See
 * https://platform.netbeans.org/tutorials/nbm-selection-1.html and
 * https://platform.netbeans.org/tutorials/nbm-selection-2.html for details.
 */
final class DeleteCasesForReprocessingAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    @NbBundle.Messages({
        "DeleteCasesForReprocessingAction.menuItemText=Delete for Reprocessing"
    })
    DeleteCasesForReprocessingAction() {
        super(Bundle.DeleteCasesForReprocessingAction_menuItemText());
        setEnabled(false); // RJCTODO: Enable when implemented
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final Collection<CaseNodeData> selectedNodeData = new ArrayList<>(Utilities.actionsGlobalContext().lookupAll(CaseNodeData.class));
        if (!selectedNodeData.isEmpty()) {
            /*
             * RJCTODO: Create a background task that does the deletion and
             * displays results in a dialog with a scrolling text pane.
             */
        }
    }

    @Override
    public DeleteCasesForReprocessingAction clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

}
