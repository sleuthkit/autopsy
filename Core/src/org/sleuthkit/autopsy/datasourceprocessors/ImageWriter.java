/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Image;
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
public class ImageWriter implements PropertyChangeListener{
    
    private final Logger logger = Logger.getLogger(ImageWriter.class.getName());
    
    private final Long dataSourceId;
    
    private Long imageHandle = null;
    private Future<?> finishTask = null;
    private ProgressHandle progressHandle = null;
    private ScheduledFuture<?> progressUpdateTask = null;
    private boolean isCancelled = false;
    private boolean isStarted = false;
    private final Object currentTasksLock = new Object(); // Get this lock before accessing imageHandle, finishTask, progressHandle, progressUpdateTask,
                                                          // isCancelled, isStarted, or isFinished
    
    private ScheduledThreadPoolExecutor periodicTasksExecutor = null;
    private final boolean doUI;
    
    private static final ImageWriterService service = new ImageWriterService(); 
    
    private static final Set<ImageWriter> currentImageWriters = new HashSet<>();  // Contains all Image Writer objects that could be processing
    private static final Object currentImageWritersLock = new Object(); // Get this lock before accessing currentImageWriters
    
    /**
     * Create the Image Writer object.
     * After creation, startListeners() should be called.
     * @param dataSourceId 
     */
    public ImageWriter(Long dataSourceId){
        this.dataSourceId = dataSourceId;        
        doUI = RuntimeProperties.runningWithGUI();        
    }
    
    /**
     * Add this ImageWriter object as a listener to the necessary events
     */
    public void subscribeToEvents(){
        IngestManager.getInstance().addIngestJobEventListener(this);
        Case.addEventSubscriber(Case.Events.CURRENT_CASE.toString(), this);
    }
    
    /**
     * Deregister this object from the events. This is ok to call multiple times.
     */
    public void unsubscribeFromEvents(){
        IngestManager.getInstance().removeIngestJobEventListener(this);
        Case.removeEventSubscriber(Case.Events.CURRENT_CASE.toString(), this);        
    }
    
    /**
     * Handle the events:
     * DATA_SOURCE_ANALYSIS_COMPLETED - start the finish image process and clean up after it is complete
     * CURRENT_CASE (case closing) - do some cleanup
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
        else if(evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())){
            // Technically this could be a case open event (and not the expected case close event) but
            // that would probably mean something bad is going on, and we'd still want to
            // do the cleanup
            close();
        }
    }
    
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
            } else {
                isStarted = true;
            }
        }
        
        // Add this to the list of image writers that could be in progress
        // (this could get cancelled before the task is created)
        synchronized(currentImageWritersLock){
            currentImageWriters.add(this);
        }
        
        synchronized(currentTasksLock){
            if(isCancelled){
                return;
            }
            
            Image image;
            try{
                image = Case.getCurrentCase().getSleuthkitCase().getImageById(dataSourceId);
                imageHandle = image.getImageHandle();
            } catch (TskCoreException ex){
                logger.log(Level.SEVERE, "Error loading image", ex);
                
                // Stay subscribed to the events for now. Case close will clean everything up.
                imageHandle = null;
                return;
            }

            logger.log(Level.INFO, String.format("Finishing VHD image for %s", 
                    dataSourceName)); //NON-NLS           

            if(doUI){
                periodicTasksExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("image-writer-progress-update-%d").build()); //NON-NLS
                progressHandle = ProgressHandle.createHandle("Image writer - " + dataSourceName);
                progressHandle.start(100);
                progressUpdateTask = periodicTasksExecutor.scheduleAtFixedRate(
                        new ProgressUpdateTask(progressHandle, imageHandle), 0, 250, TimeUnit.MILLISECONDS);
            }

            // The added complexity here with the Future is because we absolutely need to make sure
            // the call to finishImageWriter returns before allowing the TSK data structures to be freed
            // during case close.
            finishTask = Executors.newSingleThreadExecutor().submit(() -> {
                try{
                    SleuthkitJNI.finishImageWriter(imageHandle);
                } catch (TskCoreException ex){
                    logger.log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
                }
            });
        }

        // Wait for finishImageWriter to complete
        try{
            // The call to get() can happen multiple times if the user closes the case, which is ok
            finishTask.get();
        } catch (InterruptedException | ExecutionException ex){
            logger.log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
        }
        
        synchronized(currentTasksLock){
            unsubscribeFromEvents();
            if(doUI){
                // Some of these may be called twice if the user closes the case
                progressUpdateTask.cancel(true);
                progressHandle.finish();
                periodicTasksExecutor.shutdown();
            }          
        }
        
        synchronized(currentImageWritersLock){
            currentImageWriters.remove(this);
        }

        logger.log(Level.INFO, String.format("Finished writing VHD image for %s", dataSourceName)); //NON-NLS
    }
    
    /**
     * Check if any finish image tasks are in progress.
     * @return true if there are finish image tasks in progress, false otherwise
     */
    public static boolean jobsAreInProgress(){
        synchronized(currentImageWritersLock){
            for(ImageWriter writer:currentImageWriters){
                if((writer.finishTask != null) && (! writer.finishTask.isDone())){
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Cancels all Image Writer jobs in progress.
     * Does not wait for them to finish. It does stop the progress update task,
     * so after this point all that needs to be tested is that the 
     * finishTask Futures are done.
     */
    public static void cancelAllJobs(){
        synchronized(currentImageWritersLock){
            for(ImageWriter writer:currentImageWriters){
                writer.cancelJob();
            }
        }
    }
    
    /**
     * Cancels a single job. 
     * Does not wait for the job to complete. 
     */
    private void cancelJob(){
        synchronized(currentTasksLock){
            // All of the following is redundant but safe to call on a complete job
            isCancelled = true;
            unsubscribeFromEvents();

            if(imageHandle != null){
                SleuthkitJNI.cancelFinishImage(imageHandle);
                    
                // Stop the progress bar update task.
                // The thread from startFinishImage will also stop it
                // once the task completes, but we have to make absolutely sure
                // this gets done before the Sleuthkit data structures are freed.
                // Since we've stopped the update task, we'll stop the associated progress
                // bar now, too.
                progressUpdateTask.cancel(true);
                progressHandle.finish();
            }            
        }
    }
    
    /**
     * Blocks while all finishImage tasks complete.
     * Also makes sure the progressUpdateTask is canceled.
     * Once this is done it will be safe to release the Sleuthkit resources.
     */
    public static void waitForAllJobsToFinish(){
        synchronized(currentImageWritersLock){
            for(ImageWriter writer:currentImageWriters){
                // Wait for the finish task to end
                if(writer.finishTask != null){
                    try{
                        writer.finishTask.get();
                    } catch (InterruptedException | ExecutionException ex){
                        Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
                    }
                    writer.progressUpdateTask.cancel(true);
                }
            }
        }        
    }
    
    /**
     * Clean up any Image Writer objects that haven't started yet.
     * This is just protecting against the unlikely event that, due to timing
     * issues, an Image Writer object will be trying to start up while the case is
     * closing.
     */
    private void close(){
        
        synchronized(currentTasksLock){
            if(imageHandle == null){
                isCancelled = true;  // Prevent the task from starting in case something strange is happening
                                     // with the event order
                this.unsubscribeFromEvents();
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
