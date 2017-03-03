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
 * after the ingest process completes. 
 */
public class ImageWriter {
    
    private final Logger logger = Logger.getLogger(ImageWriter.class.getName());
    
    private final HashSet<Long> dataSourceIds = new HashSet<>();
    private final Object dataSourceIdsLock;  // Get this lock before accessing dataSourceIds
    
    private final HashSet<ScheduledFuture<?>> progressUpdaters = new HashSet<>();
    private final HashSet<Long> imagesBeingFinished = new HashSet<>(); 
    private final HashSet<ProgressHandle> progressBars = new HashSet<>();
    private final HashSet<Future<?>> finishTasksInProgress = new HashSet<>();
    private boolean isCancelled;
    private final Object currentTasksLock; // Get this lock before accessing imagesBeingFinished, progressBars, progressUpdaters, finishTasksInProgress or isCancelled
    
    private boolean listenerStarted;
    private ScheduledThreadPoolExecutor periodicTasksExecutor = null;
    private final boolean doUI;
    
    public ImageWriter(){
        dataSourceIdsLock = new Object();
        currentTasksLock = new Object();
        listenerStarted = false; 
        isCancelled = false;
        
        doUI = RuntimeProperties.coreComponentsAreActive();
        if(doUI){
            periodicTasksExecutor = new ScheduledThreadPoolExecutor(5, new ThreadFactoryBuilder().setNameFormat("image-writer-progress-update-%d").build()); //NON-NLS
        }
    }
    
    /**
     * Creates a listener on IngestJobEvents if it hasn't already been started. 
     * When a DataSourceAnalysisCompletedEvent arrives, if it matches
     * the data source ID of an image that is using Image Writer, then finish the image
     * (fill in any gaps). The AddImageTask for this data source must have included 
     * a non-empty imageWriterPath parameter to enable Image Writer.
     */
    private synchronized void startListener(){
        if(! listenerStarted){
            IngestManager.getInstance().addIngestJobEventListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if(evt.getPropertyName().equals(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED.toString())){
                        DataSourceAnalysisCompletedEvent event = (DataSourceAnalysisCompletedEvent)evt;

                        if(event.getDataSource() != null){
                            long imageId = event.getDataSource().getId();
                            String name = event.getDataSource().getName();

                            // Check whether we need to run finishImage for this data source
                            synchronized(dataSourceIdsLock){
                                if( ! ImageWriter.this.dataSourceIds.contains(imageId)){
                                    // Image writer was not used on this data source or we've already finished it
                                    return;
                                } else {
                                    // Remove the imageId from the list here so we can't get past this point twice
                                    // for the same image. Multiple DataSourceAnalysisCompletedEvent events can come from
                                    // the same image if more ingest modules are run later, but the imageId is only added
                                    // to the list during the intial task to add the image to the database.
                                    ImageWriter.this.dataSourceIds.remove(imageId);
                                }
                            }
                            logger.log(Level.INFO, String.format("Finishing VHD image for %s", 
                                    event.getDataSource().getName())); //NON-NLS

                            new Thread(() -> {
                                try{
                                    Image image = Case.getCurrentCase().getSleuthkitCase().getImageById(imageId);
                                    ProgressHandle progressHandle = null;
                                    ScheduledFuture<?> progressUpdateTask = null;
                                    
                                    if(doUI){
                                        progressHandle = ProgressHandle.createHandle("Image writer - " + name);
                                        progressHandle.start(100);
                                        progressUpdateTask = periodicTasksExecutor.scheduleAtFixedRate(
                                                new ProgressUpdateTask(progressHandle, image.getImageHandle()), 0, 250, TimeUnit.MILLISECONDS);
                                    }

                                    synchronized(currentTasksLock){
                                        ImageWriter.this.imagesBeingFinished.add(image.getImageHandle());
                                        
                                        if(doUI){
                                            if(isCancelled){
                                                progressUpdateTask.cancel(true);
                                                return;
                                            }
                                            ImageWriter.this.progressUpdaters.add(progressUpdateTask);
                                            ImageWriter.this.progressBars.add(progressHandle);
                                        }
                                    }

                                    // The added complexity here with the Future is because we absolutely need to make sure
                                    // the call to finishImageWriter returns before allowing the TSK data structures to be freed
                                    // during case close.
                                    Future<?> finishTask = Executors.newSingleThreadExecutor().submit(() -> {
                                        try{
                                            SleuthkitJNI.finishImageWriter(image.getImageHandle());
                                        } catch (TskCoreException ex){
                                            logger.log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
                                        }
                                    });
                                    synchronized(currentTasksLock){
                                        ImageWriter.this.finishTasksInProgress.add(finishTask);
                                    }
                                        
                                    // Wait for finishImageWriter to complete
                                    try{
                                        // The call to get() will happen twice if the user closes the case, which is ok
                                        finishTask.get();
                                    } catch (InterruptedException | ExecutionException ex){
                                        logger.log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
                                    }

                                    synchronized(currentTasksLock){
                                        ImageWriter.this.finishTasksInProgress.remove(finishTask);
                                        ImageWriter.this.imagesBeingFinished.remove(image.getImageHandle());
                                        
                                        if(doUI){
                                            progressUpdateTask.cancel(true);
                                            ImageWriter.this.progressUpdaters.remove(progressUpdateTask);
                                            progressHandle.finish();
                                            ImageWriter.this.progressBars.remove(progressHandle);
                                        }
                                    }
                                    
                                    logger.log(Level.INFO, String.format("Finished writing VHD image for %s", event.getDataSource().getName())); //NON-NLS
                                } catch (TskCoreException ex){
                                    logger.log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
                                }
                            }).start();
                        } else {
                            logger.log(Level.SEVERE, "DataSourceAnalysisCompletedEvent did not contain a dataSource object"); //NON-NLS
                        }
                    }
                }
            });
        }
        listenerStarted = true;
    }
    
    /**
     * Add a data source ID to the list of images to run finishImage on.
     * Also starts the listener if needed.
     * @param id The dataSource/Image ID
     */
    public void addDataSourceId(Long id){
        startListener();
        synchronized(dataSourceIdsLock){
            dataSourceIds.add(id);
        }
    }
    
    /**
     * Stop any open progress update task, finish the progress bars, and tell
     * the finishImage process to stop
     */
    public void close(){
        synchronized(currentTasksLock){
            isCancelled = true;
            
            for(ScheduledFuture<?> task:ImageWriter.this.progressUpdaters){
                task.cancel(true);
            }
            
            for(Long handle:imagesBeingFinished){
                SleuthkitJNI.cancelFinishImage(handle);
                logger.log(Level.SEVERE, "Case closed before VHD image could be finished"); //NON-NLS
            }
            
            // Wait for all the finish tasks to end
            for(Future<?> task:ImageWriter.this.finishTasksInProgress){
                try{
                    task.get();
                } catch (InterruptedException | ExecutionException ex){
                    logger.log(Level.SEVERE, "Error finishing VHD image", ex); //NON-NLS
                }
            }
            
            for(ProgressHandle progressHandle:ImageWriter.this.progressBars){
                progressHandle.finish();
            }
        }
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
