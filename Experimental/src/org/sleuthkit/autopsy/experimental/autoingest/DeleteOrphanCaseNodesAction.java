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

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * An action class that kicks off a cancellable orphaned case nodes deletion
 * task that runs in a background thread and reports progress using an
 * application frame progress bar.
 */
final class DeleteOrphanCaseNodesAction extends BackgroundTaskAction {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of an action class that kicks off a cancellable
     * orphaned case nodes deletion task that runs in a background thread and
     * reports progress using an application frame progress bar.
     */
    @NbBundle.Messages({
        "DeleteOrphanCaseNodesAction.progressDisplayName=Cleanup Case Znodes"
    })
    DeleteOrphanCaseNodesAction() {
        super(Bundle.DeleteOrphanCaseNodesAction_progressDisplayName(), Bundle.DeleteOrphanCaseNodesAction_progressDisplayName());
    }

    @Override
    Runnable getTask(ProgressIndicator progress) {
        return new DeleteOrphanCaseNodesTask(progress);
    }

    @Override
    public DeleteOrphanCaseNodesAction clone() throws CloneNotSupportedException {
        super.clone();
        throw new CloneNotSupportedException();
    }

}
