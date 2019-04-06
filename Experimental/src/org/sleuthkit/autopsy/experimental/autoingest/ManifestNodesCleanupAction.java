/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.event.ActionEvent;
import java.util.concurrent.FutureTask;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.progress.AppFrameProgressBar;
import org.sleuthkit.autopsy.progress.TaskCancellable;

/**
 * An action class that kicks off a cancellable manifest file nodes cleanup task
 * that runs in a background thread and reports progress using an application
 * frame progress bar.
 */
public class ManifestNodesCleanupAction  extends AbstractAction {

    private static final long serialVersionUID = 1L;

    @Override
    @NbBundle.Messages({
        "ManifestNodesCleanupAction.progressDisplayName=Cleanup Manifest File Znodes"
    })
    public void actionPerformed(ActionEvent event) {
        final AppFrameProgressBar progress = new AppFrameProgressBar(Bundle.ManifestNodesCleanupAction_progressDisplayName());        
        final TaskCancellable taskCanceller = new TaskCancellable(progress);
        progress.setCancellationBehavior(taskCanceller);
        final Runnable task = new ManifestNodesCleanupTask(progress);
        final FutureTask<Void> future = new FutureTask<>(task, null);
        taskCanceller.setFuture(future);
        new Thread(future).start();
    }

    @Override
    public ManifestNodesCleanupAction clone() throws CloneNotSupportedException {
        super.clone();
        throw new CloneNotSupportedException();
    }
    
}
