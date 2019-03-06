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

import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * An action that completely deletes one or more multi-user cases, including any
 * associated auto ingest job input directories and all coordination service
 * nodes.
 */
final class DeleteCaseInputAndOutputAction extends DeleteCaseAction {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an action that completely deletes one or more multi-user
     * cases, including any associated auto ingest job input directories and
     * coordination service nodes.
     */
    @Messages({
        "DeleteCaseInputAndOutputAction.menuItemText=Delete Input and Output",
        "DeleteCaseInputAndOutputAction.progressDisplayName=Delete Input and Output",
        "DeleteCaseInputAndOutputAction.taskName=input-and-output"
    })
    DeleteCaseInputAndOutputAction() {
        super(Bundle.DeleteCaseInputAndOutputAction_menuItemText(), Bundle.DeleteCaseInputAndOutputAction_progressDisplayName(), Bundle.DeleteCaseInputAndOutputAction_taskName());
    }

    @Override
    DeleteCaseTask getTask(CaseNodeData caseNodeData, ProgressIndicator progress) {
        return new DeleteCaseInputAndOutputTask(caseNodeData, progress);
    }

    @Override
    public DeleteCaseInputAndOutputAction clone() throws CloneNotSupportedException {
        super.clone();
        throw new CloneNotSupportedException();
    }

}
