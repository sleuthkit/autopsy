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

import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * A task that deletes a case produced via auto ingest.
 */
final class DeleteCaseInputAndOutputTask extends DeleteCaseTask {

    /**
     * Constructs a task that deletes a case produced via auto ingest.
     *
     * @param caseNodeData The case directory lock coordination service node
     *                     data for the case.
     * @param progress     A progress indicator.
     */
    DeleteCaseInputAndOutputTask(CaseNodeData caseNodeData, ProgressIndicator progress) {
        super(caseNodeData, progress);
    }

    @Override
    void deleteWhileHoldingAllLocks() throws InterruptedException {
        deleteInputDirectories();
        deleteCaseOutput();
    }

    @Override
    void deleteAfterManifestLocksReleased() throws InterruptedException {
        deleteManifestFileLockNodes();
    }
    
    @Override
    void deleteAfterCaseDirectoryLockReleased() throws InterruptedException {
        this.deleteCaseDirectoryLockNode();
    }

    @Override
    void deleteAfterCaseNameLockReleased() throws InterruptedException {
        this.deleteCaseNameLockNode();
    }

}
