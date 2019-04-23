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
 * An action that deletes everything except the auto ingest job input
 * directories for one or more multi-user cases. This supports the use case
 * where a case needs to be reprocessed, so the input directories are not
 * deleted even though the coordination service nodes for the auto ingest jobs
 * are deleted.
 */
final class DeleteCaseOutputAction extends DeleteCaseComponentsAction {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an action that deletes everything except the auto ingest job
     * input directories for one or more multi-user cases. This supports the use
     * case where a case needs to be reprocessed, so the input directories are
     * not deleted even though the coordination service nodes for the auto
     * ingest jobs are deleted.
     */
    @NbBundle.Messages({
        "DeleteCaseOutputAction.menuItemText=Delete Output",
        "DeleteCaseOutputAction.progressDisplayName=Delete Output",
        "DeleteCaseOutputAction.taskName=output"        
    })
    DeleteCaseOutputAction() {
        super(Bundle.DeleteCaseOutputAction_menuItemText(), Bundle.DeleteCaseOutputAction_progressDisplayName(), Bundle.DeleteCaseOutputAction_taskName());
    }

    @NbBundle.Messages({
        "DeleteCaseOutputAction.confirmationText=Are you sure you want to delete the following for the case(s):\n\tManifest file znodes\n\tCase database\n\tCore.properties file\n\tCase directory\n\tCase znodes"
    })
    @Override
    public void actionPerformed(ActionEvent event) {
        if (MessageNotifyUtil.Message.confirm(Bundle.DeleteCaseOutputAction_confirmationText())) {
            super.actionPerformed(event);
        }
    }    
    
    @Override
    DeleteCaseTask getTask(CaseNodeData caseNodeData, ProgressIndicator progress) {
        return new DeleteCaseTask(caseNodeData, DeleteOptions.DELETE_OUTPUT, progress);
    }

}
