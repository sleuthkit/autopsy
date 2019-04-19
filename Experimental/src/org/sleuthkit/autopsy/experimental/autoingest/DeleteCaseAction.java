/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp. Contact: carrier <at> sleuthkit
 * <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.event.ActionEvent;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * An action that completely deletes one or more multi-user cases. Only the
 * components created by the application are deleted: the case output and the
 * coordination service nodes. Note that manifest file coordination service
 * nodes are only marked as deleted by setting the processing status field for
 * the corresponding auto ingest job to DELETED. This is done to avoid imposing
 * the requirement that the manifests be deleted before deleting the cases,
 * since at this time manifests are not considered to be case components created
 * by the application.
 */
final class DeleteCaseAction extends DeleteCaseComponentsAction {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs action that completely deletes one or more multi-user cases.
     * Only the components created by the application are deleted: the case
     * output and the coordination service nodes. Note that manifest file
     * coordination service nodes are only marked as deleted by setting the
     * processing status field for the corresponding auto ingest job to DELETED.
     * This is done to avoid imposing the requirement that the manifests be
     * deleted before deleting the cases, since at this time manifests are not
     * considered to be case components created by the application.
     */
    @NbBundle.Messages({
        "DeleteCaseAction.menuItemText=Delete Case(s)",
        "DeleteCaseAction.progressDisplayName=Delete Case(s)",
        "DeleteCaseAction.taskName=app-input-and-output"
    })
    DeleteCaseAction() {
        super(Bundle.DeleteCaseAction_menuItemText(), Bundle.DeleteCaseAction_progressDisplayName(), Bundle.DeleteCaseAction_taskName());
    }

    @NbBundle.Messages({
        "DeleteCaseAction.confirmationText=Are you sure you want to delete the following for the case(s):\n\tManifest file znodes\n\tCase database\n\tCore.properties file\n\tCase directory\n\tCase znodes"
    })
    @Override
    public void actionPerformed(ActionEvent event) {
        if (MessageNotifyUtil.Message.confirm(Bundle.DeleteCaseAction_confirmationText())) {
            super.actionPerformed(event);
        }
    }

    @Override
    DeleteCaseTask getTask(CaseNodeData caseNodeData, ProgressIndicator progress) {
        return new DeleteCaseTask(caseNodeData, DeleteCaseTask.DeleteOptions.DELETE_CASE, progress);
    }
}
