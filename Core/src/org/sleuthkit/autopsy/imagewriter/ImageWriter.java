/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagewriter;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisCompletedEvent;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * The ImageWriter class is used to complete VHD copies created from local disks
 * after the ingest process completes. The AddImageTask for this data source must have included 
 * a non-empty imageWriterPath parameter to enable Image Writer.
 * 
 * Most of the cancellation/cleanup is handled through ImageWriterService
 */
class ImageWriter implements PropertyChangeListener{
    
    private final Logger logger = Logger.getLogger(ImageWriter.class.getName());
    
    private final Long dataSourceId;
    private final ImageWriterSettings settings;
    
    private Long imageHandle = null;
    private Future<Integer> finishTask = null;
    private ProgressHandle progressHandle = null;
    private ScheduledFuture<?> progressUpdateTask = null;
    private boolean isCancelled = false;
    private boolean isStarted = false;
    private final Object currentTasksLock = new Object(); // Get this lock before accessing imageHandle, finishTask, progressHandle, progressUpdateTask,
                                                          // isCancelled, isStarted, or isFinished
    
    private ScheduledThreadPoolExecutor periodicTasksExecutor = null;
    private final boolean doUI;
    private SleuthkitCase caseDb = null;
    
    /**
     * Create the Image Writer object.
     * After creation, startListeners() should be called.
     * @param dataSourceId 
     */
    ImageWriter(Long dataSourceId, ImageWriterSettings settings){
        this.dataSourceId = dataSourceId;     
        this.settings = settings;
        doUI = RuntimeProperties.runningWithGUI();    
        
        // We save the reference to the sleuthkit case here in case getOpenCase() is set to
        // null before Image Writer finishes. The user can still elect to wait for image writer
        // (in ImageWriterService.closeCaseResources) even though the case is closing. 
        try{
            caseDb = Case.getOpenCase().getSleuthkitCase();
        } catch (NoCurrentCaseException ex){
            logger.log(Level.SEVERE, "Unable to load case. Image writer will be cancelled.");
            this.isCancelled = true;
        }
    }
    
    /**
     * Add this ImageWriter object as a listener to the necessary events
     */
    void subscribeToEvents(){
        IngestManager.getInstance().addIngestJobEventListener(this);
    }
    
    /**
     * Deregister this object from the events. This is ok to call multiple times.
     */
    void unsubscribeFromEvents(){
        IngestManager.getInstance().removeIngestJobEventListener(this);       
    }
    
    /**
     * Handle the events:
     * DATA_SOURCE_ANALYSIS_COMPLETED - start the finish image process and clean up after it is complete
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if(evt instanceof DataSourceAnalysisCompletedEvent){
                        
            DataSourceAnalysisCompletedEvent event = (DataSourceAnalysisCompletedEvent)evt;

            if(event.getDataSource() != null){
                long imageId = event.getDataSource().getId();
                String name = event.getDataSource().getName();
                
                // Check that the event corresponds to this datasource
                if(imageId != dataSourceId){
                    return;
                }
                new Thread(() -> {
                    startFinishImage(name);
                }).start();

            } else {
                logger.log(Level.SEVERE, "DataSourceAnalysisCompletedEvent did not contain a dataSource object"); //NON-NLS
            }
        }
    }
    
    @Messages({
        "# {0} - data source name", 
        "ImageWriter.progressBar.message=Finishing acquisition of {0}"
    })
    private void startFinishImage(String dataSourceName){
      
        synchronized(currentTasksLock){
            if(isCancelled){
                return;
            }
            
            // If we've already started the finish process for this datasource, return.
            // Multiple DataSourceAnalysisCompletedEvent events can come from
            // the same image if more ingest modules are run later
            if(isStarted){
                return;
            }
            
            Image image;
            try{
                image = Case.getOpenCase().getSleuthkitCase().getImageById(dataSourceId);
                imageHandle = image.getImageHandle();
            } catch (NoCurrentCaseException ex){
                // This exception means that getOpenCase() failed because no case was open.
                // This can happen when the user closes the case while ingest is ongoing - canceling
                // ingest fires off the DataSourceAnalysisCompletedEvent while the case is in the 
                // process of closing. 
                logger.log(Level.WARNING, String.format("Case closed before ImageWriter could start the finishing process for %s",
                        dataSourceName));
                return;
            } catch (TskCoreException ex){
                logger.log(Level.SEVERE, "Error loading image", ex);
                return;
            }

            logger.log(Level.INFO, String.format("Finishing VHD image for %s", 
                    dataSourceName)); //NON-NLS           

            if(doUI){
                periodicTasksExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("image-writer-progress-update-%d").build()); //NON-NLS
                progressHandle = ProgressHandle.createHandle(Bundle.ImageWriter_progressBar_message(dataSourceName));
                progressHandle.start(100);
                progressUpdateTask = periodicTasksExecutor.scheduleWithFixedDelay(
                        new ProgressUpdateTask(progressHandle, imageHandle), 0, 250, TimeUnit.MILLISECONDS);
            }

            // The added complexity here with the Future is because we absolutely need to make sure
            // the call to finishImageWriter returns before allowing the TSK data structures to be freed
            // during case close.
            finishTask = Executors.newSingleThreadExecutor().submit(new Callable<Integer>(){
                @Override
                public Integer call() throws TskCoreException{
                    try{
                        int result = SleuthkitJNI.finishImageWriter(imageHandle);
                        
                        // We've decided to always update the path to the VHD, even if it wasn't finished.
                        // This supports the case where an analyst has partially ingested a device
                        // but has to stop before completion. They will at least have part of the image.
                        if(settings.getUpdateDatabasePath()){
                            caseDb.updateImagePath(settings.getPath(), dataSourceId);
                        }
                        return result;
                    } catch (TskCoreException ex){
                        logger.log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
                        return -1;
                    }
                }
            });
            
            // Setting this means that finishTask and all the UI updaters are initialized (if running UI)
            isStarted = true;
        }

        // Wait for finishImageWriter to complete
        int result = 0;
        try{
            // The call to get() can happen multiple times if the user closes the case, which is ok
            result = finishTask.get();
        } catch (InterruptedException | ExecutionException ex){
            logger.log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
        }
        
        synchronized(currentTasksLock){
            if(doUI){
                // Some of these may be called twice if the user closes the case
                progressUpdateTask.cancel(true);
                progressHandle.finish();
                periodicTasksExecutor.shutdown();
            }          
        }

        if(result == 0){
            logger.log(Level.INFO, String.format("Successfully finished writing VHD image for %s", dataSourceName)); //NON-NLS
        } else {
            logger.log(Level.INFO, String.format("Finished VHD image for %s with errors", dataSourceName)); //NON-NLS
        }
    }
    
    /**
     * If a task hasn't been started yet, set the cancel flag so it can no longer
     * start.
     * This is intended to be used in case close so a job doesn't suddenly start
     * up during cleanup.
     */
    void cancelIfNotStarted(){
        synchronized(currentTasksLock){
            if(! isStarted){
                isCancelled = true;
            }
        }
    }
    
    /**
     * Check if the finishTask process is running.
     * @return true if the finish task is still going on, false if it is finished or
     *         never started
     */
    boolean jobIsInProgress(){
        synchronized(currentTasksLock){
            return((isStarted) && (! finishTask.isDone()));
        }
    }
    
    /**
     * Cancels a single job. 
     * Does not wait for the job to complete. Safe to call with Image Writer in any state.
     */
    void cancelJob(){
        synchronized(currentTasksLock){
            // All of the following is redundant but safe to call on a complete job
            isCancelled = true;

            if(isStarted){
                SleuthkitJNI.cancelFinishImage(imageHandle);
                    
                // Stop the progress bar update task.
                // The thread from startFinishImage will also stop it
                // once the task completes, but we don't have a guarantee on 
                // when that happens.
                // Since we've stopped the update task, we'll stop the associated progress
                // bar now, too.
                if(doUI){
                    progressUpdateTask.cancel(true);
                    progressHandle.finish();
                }
            }            
        }
    }
    
    /**
     * Blocks while all finishImage tasks complete.
     * Also makes sure the progressUpdateTask is canceled.
     */
    void waitForJobToFinish(){
        synchronized(currentTasksLock){
            // Wait for the finish task to end
            if(isStarted){
                try{
                    finishTask.get();
                } catch (InterruptedException | ExecutionException ex){
                    Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
                }
                if(doUI){
                    progressUpdateTask.cancel(true);
                }
            }   
        }
    }
    
    /**
     * Task to query the Sleuthkit processing to get the percentage done. 
     */
    private final class ProgressUpdateTask implements Runnable {
        final long imageHandle;
        final ProgressHandle progressHandle;
        
        ProgressUpdateTask(ProgressHandle progressHandle, long imageHandle){
            this.imageHandle = imageHandle;
            this.progressHandle = progressHandle;
        }
            
        @Override
        public void run() {
            try {
                int progress = SleuthkitJNI.getFinishImageProgress(imageHandle);
                progressHandle.progress(progress);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Unexpected exception in ProgressUpdateTask", ex); //NON-NLS
            }
        }
    }
}
