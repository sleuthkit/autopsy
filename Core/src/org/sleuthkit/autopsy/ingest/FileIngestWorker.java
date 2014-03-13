/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Performs file ingest tasks for one or more ingest jobs on a worker thread.
 */
class FileIngestWorker extends SwingWorker<Object, Void> {

    private static final Logger logger = Logger.getLogger(FileIngestWorker.class.getName());
    private final long id;
    private ProgressHandle progress;

    FileIngestWorker(long threadId) {
        this.id = threadId;
    }

    @Override
    protected Object doInBackground() throws Exception {
        logger.log(Level.INFO, String.format("Starting file ingest thread {0}", this.id));

        // Set up a progress bar with cancel capability.
        final String displayName = "File Ingest";
        progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
            @Override
            public boolean cancel() {
                logger.log(Level.INFO, "Filed ingest cancelled by user.");
                if (progress != null) {
                    progress.setDisplayName(displayName + " (Cancelling...)");
                }
                return FileIngestWorker.this.cancel(true);
            }
        });
        progress.start();
        progress.switchToIndeterminate();
        IngestScheduler.FileScheduler fileScheduler = IngestScheduler.getInstance().getFileScheduler();
        int totalEnqueuedFiles = fileScheduler.getFilesEnqueuedEst();
        progress.switchToDeterminate(totalEnqueuedFiles);

        // Process all scheduled files.
        int processedFiles = 0;
        while (fileScheduler.hasNext()) {
            if (isCancelled()) {
                logger.log(Level.INFO, "Terminating file ingest thread {0} due to ingest cancellation", this.id);
                return null;
            }

            IngestScheduler.FileScheduler.FileTask task = fileScheduler.next();
            AbstractFile file = task.getFile();
            progress.progress(file.getName(), processedFiles);
            FileIngestPipeline pipeline = task.getIngestJob().getFileIngestPipeline(this.id);
            pipeline.ingestFile(file);
            
            // Update the progress bar.
            int newTotalEnqueuedFiles = fileScheduler.getFilesEnqueuedEst();
            if (newTotalEnqueuedFiles > totalEnqueuedFiles) {
                totalEnqueuedFiles = newTotalEnqueuedFiles + 1;
                progress.switchToIndeterminate();
                progress.switchToDeterminate(totalEnqueuedFiles);
            }
            if (processedFiles < totalEnqueuedFiles) {
                // RCTODO: What is this comment saying? 
                //fix for now to handle the same datasource Content enqueued twice
                ++processedFiles;
            }
        }

        return null;
    }

    @Override
    protected void done() {
        try {
            super.get();
            // RJCTODO: Pass thread id and this.isCancelled() back to manager to shut down thread's pipeline in all jobs
        } catch (CancellationException | InterruptedException e) {
            handleInterruption();
        } catch (Exception ex) {
            handleInterruption();
            logger.log(Level.SEVERE, "Fatal error during file ingest.", ex);
        } finally {
            progress.finish();
        }
    }

    private void handleInterruption() {
        // RJCTODO: Pass thread id and this.isCancelled() back to manager to shut down thread's pipeline in all jobs            
        //empty queues
        IngestScheduler.getInstance().getFileScheduler().empty(); // RJCTODO: Is this right?
    }
}
