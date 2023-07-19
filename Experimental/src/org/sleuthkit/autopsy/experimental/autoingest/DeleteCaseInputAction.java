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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.experimental.autoingest.DeleteCaseTask.DeleteOptions;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * An action that deletes the auto ingest job input directories associated with
 * one or more multi-user cases. The coordination service nodes for the auto
 * ingest jobs are not deleted. This supports the use case where the directories
 * may need to be directed to reclaim space, but the option to restore the
 * directories without having the jobs be reprocessed is retained.
 */
final class DeleteCaseInputAction extends DeleteCaseComponentsAction {

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
        "DeleteCaseInputAction.menuItemText=Delete Input",
        "DeleteCaseInputAction.progressDisplayName=Delete Input",
        "DeleteCaseInputAction.taskName=input"
    })
    DeleteCaseInputAction() {
        super(Bundle.DeleteCaseInputAction_menuItemText(), Bundle.DeleteCaseInputAction_progressDisplayName(), Bundle.DeleteCaseInputAction_taskName());
    }

    @NbBundle.Messages({
        "DeleteCaseInputAction.confirmationText=Are you sure you want to delete the following for the case(s):\n\tManifest files\n\tData sources\n"
    })
    @Override
    public void actionPerformed(ActionEvent event) {
        if (MessageNotifyUtil.Message.confirm(Bundle.DeleteCaseInputAction_confirmationText())) {
            super.actionPerformed(event);
        }
    }

    @Override
    DeleteCaseTask getTask(CaseNodeData caseNodeData, ProgressIndicator progress) {
        return new DeleteCaseTask(caseNodeData, DeleteOptions.DELETE_INPUT, progress);
    }

}
