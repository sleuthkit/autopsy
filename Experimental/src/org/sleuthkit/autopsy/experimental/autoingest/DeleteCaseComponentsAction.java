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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.AbstractAction;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.progress.AppFrameProgressBar;
import org.sleuthkit.autopsy.progress.TaskCancellable;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * An abstract class for an action that deletes components one or more
 * multi-user cases using a thread pool, one task per case. Uses the Template
 * Method design pattern to allow subclasses to specify the deletion task to be
 * performed.
 *
 * This cases to delete are discovered by querying the actions global context
 * lookup for CaseNodeData objects. See
 * https://platform.netbeans.org/tutorials/nbm-selection-1.html and
 * https://platform.netbeans.org/tutorials/nbm-selection-2.html for details.
 */
abstract class DeleteCaseComponentsAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final int NUMBER_OF_THREADS = 4;
    private static final String THREAD_NAME_SUFFIX = "-task-%d";  //NON-NLS
    private static final String PROGRESS_DISPLAY_NAME = "%s for %s"; //NON-NLS
    private final String taskDisplayName;
    private final ExecutorService executor;

    /**
     * Constructs an abstract class for an action that deletes components of one
     * or more multi-user cases using a thread pool, one task per case. Uses the
     * Template Method design pattern to allow subclasses to specify the
     * deletion task to be performed.
     *
     * @param menuItemText    The menu item text for the action.
     * @param taskDisplayName The task display name for the progress indicator
     *                        for the task, to be inserted in the first position
     *                        of "%s for %s", where the second substitution is
     *                        the case name.
     * @param taskName        The task name, to be inserted in the first
     *                        position of "%s-task-%d", where the second
     *                        substitution is the pool thread number.
     */
    DeleteCaseComponentsAction(String menuItemText, String taskDisplayName, String taskName) {
        super(menuItemText);
        this.taskDisplayName = taskDisplayName;
        String threadNameFormat = taskName + THREAD_NAME_SUFFIX;
        executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS, new ThreadFactoryBuilder().setNameFormat(threadNameFormat).build());
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Collection<CaseNodeData> selectedNodeData = new ArrayList<>(Utilities.actionsGlobalContext().lookupAll(CaseNodeData.class));
        for (CaseNodeData nodeData : selectedNodeData) {
            AppFrameProgressBar progress = new AppFrameProgressBar(String.format(PROGRESS_DISPLAY_NAME, taskDisplayName, nodeData.getDisplayName()));
            TaskCancellable taskCanceller = new TaskCancellable(progress);
            progress.setCancellationBehavior(taskCanceller);
            Future<?> future = executor.submit(getTask(nodeData, progress));
            taskCanceller.setFuture(future);
        }
    }

    /**
     * Uses the Template Method design pattern to allow subclasses to specify
     * the deletion task to be performed in a worker thread by this action.
     *
     * @param caseNodeData The case directory lock coordination service node
     *                     data for the case to be deleted.
     * @param progress     A progress indicator for the task.
     *
     * @return A case deletion task, ready to be executed.
     */
    abstract DeleteCaseTask getTask(CaseNodeData caseNodeData, ProgressIndicator progress);

    @Override
    public DeleteCaseComponentsAction clone() throws CloneNotSupportedException {
        super.clone();
        throw new CloneNotSupportedException();
    }

}
