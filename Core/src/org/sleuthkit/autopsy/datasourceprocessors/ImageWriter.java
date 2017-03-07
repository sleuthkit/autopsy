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
 */
public class ImageWriter implements PropertyChangeListener{
    
    private final Logger logger = Logger.getLogger(ImageWriter.class.getName());
    
    private final Long dataSourceId;
    private Long imageHandle = null;
    
    private Future<?> finishTask;
    ProgressHandle progressHandle = null;
    ScheduledFuture<?> progressUpdateTask = null;
    private boolean isCancelled;
    private boolean isStarted;
    private boolean isFinished;
    private final Object currentTasksLock; // Get this lock before accessing finishTask, progressHandle, progressUpdateTask, isCancelled,
                                           // isStarted, or isFinished
    
    private ScheduledThreadPoolExecutor periodicTasksExecutor = null;
    private final boolean doUI;
    
    private static int numFinishJobsInProgress = 0;
    
    /**
     * Create the Image Writer object.
     * After creation, startListeners() should be called.
     * @param dataSourceId 
     */
    public ImageWriter(Long dataSourceId){
        this.dataSourceId = dataSourceId;
        
        currentTasksLock = new Object();
        isCancelled = false;
        isStarted = false;
        isFinished = false;
        progressHandle = null;
        progressUpdateTask = null;
        finishTask = null;
        
        doUI = RuntimeProperties.runningWithGUI();
        if(doUI){
            periodicTasksExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("image-writer-progress-update-%d").build()); //NON-NLS
        }
    }
    
    /**
     * Add this ImageWriter object as a listener to the necessary events
     */
    public void subscribeToEvents(){
        IngestManager.getInstance().addIngestJobEventListener(this);
        Case.addEventSubscriber(Case.Events.CURRENT_CASE.toString(), this);
    }
    
    /**
     * Handle the events:
     * DATA_SOURCE_ANALYSIS_COMPLETED - start the finish image process and clean up after it is complete
     * CURRENT_CASE (case closing) - cancel the finish image process (if necessary)
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getPropertyName().equals(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED.toString())){
                        
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
            close();
        }
    }
    
    private void startFinishImage(String dataSourceName){
        synchronized(currentTasksLock){
            // If we've already started the finish process for this datasource, return.
            // Multiple DataSourceAnalysisCompletedEvent events can come from
            // the same image if more ingest modules are run later
            if(isStarted){
                return;
            } else {
                isStarted = true;
            }
        }

        logger.log(Level.INFO, String.format("Finishing VHD image for %s", 
                dataSourceName)); //NON-NLS

        try{
            Image image = Case.getCurrentCase().getSleuthkitCase().getImageById(dataSourceId);
            imageHandle = image.getImageHandle();

            synchronized(currentTasksLock){
                if(isCancelled){
                    return;
                }

                if(doUI){
                    progressHandle = ProgressHandle.createHandle("Image writer - " + dataSourceName);
                    progressHandle.start(100);
                    progressUpdateTask = periodicTasksExecutor.scheduleAtFixedRate(
                            new ProgressUpdateTask(progressHandle, image.getImageHandle()), 0, 250, TimeUnit.MILLISECONDS);
                }

                // The added complexity here with the Future is because we absolutely need to make sure
                // the call to finishImageWriter returns before allowing the TSK data structures to be freed
                // during case close.
                numFinishJobsInProgress++;
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
                // The call to get() will happen twice if the user closes the case, which is ok
                finishTask.get();
            } catch (InterruptedException | ExecutionException ex){
                logger.log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
            }
            numFinishJobsInProgress--;
            
            IngestManager.getInstance().removeIngestJobEventListener(this);
            Case.removeEventSubscriber(Case.Events.CURRENT_CASE.toString(), this);
            synchronized(currentTasksLock){
                if(doUI && ! isCancelled){
                    progressUpdateTask.cancel(true);
                    progressHandle.finish();
                }
                isFinished = true;
            }
            

            logger.log(Level.INFO, String.format("Finished writing VHD image for %s", dataSourceName)); //NON-NLS
        } catch (TskCoreException | IllegalStateException ex){
            logger.log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
        }
    }
    
    /**
     * Tell the finishImage process to stop and wait for it to do so.
     */
    private void close(){
        synchronized(currentTasksLock){
            isCancelled = true;
            
            if(imageHandle == null){
                // The case got closed during ingest (before the finish process could start)
                return;
            }
            
            if(!isFinished){
                SleuthkitJNI.cancelFinishImage(imageHandle);
                logger.log(Level.SEVERE, "Case closed before VHD image could be finished"); //NON-NLS
            
                // Wait for the finish task to end
                try{
                    finishTask.get();
                } catch (InterruptedException | ExecutionException ex){
                    logger.log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
                }
            
                // Stop the progress bar and progress bar update task.
                // The thread from startFinishImage will also stop these
                // once the task completes, but we have to make absolutely sure
                // this gets done before the Sleuthkit data structures are freed.
                if(progressUpdateTask != null){
                    progressUpdateTask.cancel(true);
                }
            
                if(progressHandle != null){
                    progressHandle.finish();
                }
            }
        }
    }
    
    /**
     * Get the number of images currently being finished.
     * @return number of images in progress
     */
    public static int numberOfJobsInProgress(){
        return numFinishJobsInProgress;
    }
    
    /**
     * Task to query the Sleuthkit processing to get the percentage done. 
     */
    private final class ProgressUpdateTask implements Runnable {
        long imageHandle;
        ProgressHandle progressHandle;
        
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
