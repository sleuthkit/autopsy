/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

package org.sleuthkit.autopsy.casemodule;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.corecomponentinterfaces.DSPCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DSPProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskException;

/*
 * A background task (swingworker) that adds the given image to 
 * database using the Sleuthkit JNI interface.
 * 
 * It updates the given ProgressMonitor as it works through adding the image,
 * and et the end, calls the specified Callback.
 */
public class AddImageTask extends SwingWorker<Integer, Integer> {

        private Logger logger = Logger.getLogger(AddImageTask.class.getName());
        
        private Case currentCase;
        // true if the process was requested to stop
        private boolean cancelled = false;
        //true if revert has been invoked.
        private boolean reverted = false;
        private boolean hasCritError = false;
        
        private boolean  addImageDone = false;
        
        private List<String> errorList = new ArrayList<String>();
        
        private DSPProgressMonitor progressMonitor;
        private DSPCallback callbackObj;
        
        private final List<Content> newContents = Collections.synchronizedList(new ArrayList<Content>());
        
        private SleuthkitJNI.CaseDbHandle.AddImageProcess addImageProcess;
        private Thread dirFetcher;
   
        private String imagePath;
        private String dataSourcetype;
        String timeZone;
        boolean noFatOrphans;
            
        
        /*
         * A Swingworker that updates the progressMonitor with the name of the 
         * directory currently being processed  by the AddImageTask 
         */
        private class CurrentDirectoryFetcher implements Runnable {

            DSPProgressMonitor progressMonitor;
            SleuthkitJNI.CaseDbHandle.AddImageProcess process;

            CurrentDirectoryFetcher(DSPProgressMonitor aProgressMonitor, SleuthkitJNI.CaseDbHandle.AddImageProcess proc) {
                this.progressMonitor = aProgressMonitor;
                this.process = proc;
            }

            /**
             * @return the currently processing directory
             */
            @Override
            public void run() {
                try {
                    while (!Thread.currentThread().isInterrupted()) { 
                        
                        progressMonitor.setText(process.currentDirectory()); 
                            
                        Thread.sleep(2 * 1000);
                    }
                    return;
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
   
         
        protected AddImageTask(String imgPath, String tz, boolean noOrphans, DSPProgressMonitor aProgressMonitor, DSPCallback cbObj ) {
           
            currentCase = Case.getCurrentCase();
            
            
            this.imagePath = imgPath;
            this.timeZone = tz;
            this.noFatOrphans = noOrphans;
            
            this.callbackObj = cbObj;
            this.progressMonitor = aProgressMonitor;
        }

        /**
         * Starts the addImage process, but does not commit the results.
         *
         * @return
         *
         * @throws Exception
         */
        @Override
        protected Integer doInBackground() {
             
            this.setProgress(0);

            errorList.clear();
            try {
                //lock DB for writes in EWT thread
                //wait until lock acquired in EWT
                EventQueue.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        SleuthkitCase.dbWriteLock();
                    }
                });
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, "Errors occurred while running add image, could not acquire lock. ", ex);
                return 0;

            } catch (InvocationTargetException ex) {
                logger.log(Level.WARNING, "Errors occurred while running add image, could not acquire lock. ", ex);
                return 0;
            }
            
            addImageProcess = currentCase.makeAddImageProcess(timeZone, true, noFatOrphans);
            dirFetcher = new Thread( new CurrentDirectoryFetcher(progressMonitor, addImageProcess));
          
            try {
                progressMonitor.setIndeterminate(true);
                progressMonitor.setProgress(0);
               
                dirFetcher.start();
                
                addImageProcess.run(new String[]{this.imagePath});
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Core errors occurred while running add image. ", ex);
                //critical core/system error and process needs to be interrupted
                hasCritError = true;
                errorList.add(ex.getMessage());
            } catch (TskDataException ex) {
                logger.log(Level.WARNING, "Data errors occurred while running add image. ", ex);
                errorList.add(ex.getMessage());
            } finally {
                // process is over, doesn't need to be dealt with if cancel happens

            }

            return 0;
        }

        /**
         * Commit the finished AddImageProcess, and cancel the CleanupTask that
         * would have reverted it.
         *
         * @param settings property set to get AddImageProcess and CleanupTask
         *                 from
         *
         * @throws Exception if commit or adding the image to the case failed
         */
        private void commitImage() throws Exception {

            long imageId = 0;
            try {
                imageId = addImageProcess.commit();
            } catch (TskException e) {
                logger.log(Level.WARNING, "Errors occured while committing the image", e);
                errorList.add(e.getMessage());
            } finally {
                //commit done, unlock db write in EWT thread
                //before doing anything else
                SleuthkitCase.dbWriteUnlock();

                if (imageId != 0) {
                    Image newImage = Case.getCurrentCase().addImage(imagePath, imageId, timeZone);

                    //while we have the image, verify the size of its contents
                    String verificationErrors = newImage.verifyImageSize();
                    if (verificationErrors.equals("") == false) {
                        //data error (non-critical)
                        errorList.add(verificationErrors);
                    }

                    // Add the image to the list of new content
                    newContents.add(newImage);
                    
                }

                logger.log(Level.INFO, "Image committed, imageId: " + imageId);
                logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
            }
        }

        /**
         *
         * (called by EventDispatch Thread after doInBackground finishes)
         * 
         * Must Not return without invoking the callBack, unless the caller canceled
         */
        @Override
        protected void done() {
                   
            setProgress(100);
            
            // cancel the directory fetcher
            dirFetcher.interrupt();

            addImageDone = true; 
            // attempt actions that might fail and force the process to stop
            if (cancelled || hasCritError) {
                logger.log(Level.INFO, "Handling errors or interruption that occured in add image process");
                revert();
            }
            if (!errorList.isEmpty()) {
                logger.log(Level.INFO, "Handling non-critical errors that occured in add image process");
            }
            
            // When everything happens without an error:
            if (!(cancelled || hasCritError)) {

                try {
                    // Tell the progress monitor we're done
                    progressMonitor.setProgress(100);

                    if (newContents.isEmpty()) {
                        if (addImageProcess != null) { // and if we're done configuring ingest
                            // commit anything
                            try {
                                commitImage();
                            } catch (Exception ex) {
                                errorList.add(ex.getMessage());
                                // Log error/display warning
                                logger.log(Level.SEVERE, "Error adding image to case.", ex);
                            }
                        } else {
                            logger.log(Level.SEVERE, "Missing image process object");
                        }
                    }

                    else {   //already commited?
                        logger.log(Level.INFO, "Assuming image already committed, will not commit.");
                    }

                } catch (Exception ex) {
                    //handle unchecked exceptions post image add

                    errorList.add(ex.getMessage());
                    
                    logger.log(Level.WARNING, "Unexpected errors occurred while running post add image cleanup. ", ex);
                   
                    logger.log(Level.SEVERE, "Error adding image to case", ex);
                } finally {


                }
            }
            
            // invoke the callBack, unless the caller cancelled 
            if (!cancelled)
                doCallBack();
            
            return;
        }

        /*
         * Call the callback with results, new content, and errors, if any
         */
        private void doCallBack()
        {     
              DSPCallback.DSP_Result result;
              
              if (hasCritError) {
                    result = DSPCallback.DSP_Result.CRITICAL_ERRORS;
              }
              else if (!errorList.isEmpty()) {
                  result = DSPCallback.DSP_Result.NONCRITICAL_ERRORS;       
              }      
              else {
                  result = DSPCallback.DSP_Result.NO_ERRORS;
              }

              // invoke the callcak, passing it the result, list of new contents, and list of errors
              callbackObj.done(result, errorList, newContents);
        }
        
        /*
         * cancel the image addition, if possible
         */
        public void cancelTask() {
            
             cancelled = true;
             
             if (!addImageDone) {
                try {
                  interrupt();
                }
                catch (Exception ex) {
                      logger.log(Level.SEVERE, "Failed to interrup the add image task...");    
                }
            }
            else {
                try {
                  revert();  
                }
                catch(Exception ex) {
                     logger.log(Level.SEVERE, "Failed to revert the add image task...");   
                }
            }
        }
        /*
         * Interrurp the add image process if it is still running
         */
        private void interrupt() throws Exception {
            
            try {
                logger.log(Level.INFO, "interrupt() add image process");
                addImageProcess.stop();  //it might take time to truly stop processing and writing to db
            } catch (TskException ex) {
                throw new Exception("Error stopping add-image process.", ex);
            }
        }

        /*
         * Revert - if image has already been added but not committed yet
         */
        void revert() {
            
             if (!reverted) {
                 
                try {
                    logger.log(Level.INFO, "Revert after add image process");
                    try {
                        addImageProcess.revert();
                    } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Error reverting add image process", ex);
                    }
                } finally {
                     //unlock db write within EWT thread
                        SleuthkitCase.dbWriteUnlock();
                }
                reverted = true;
            }
        }
    }
