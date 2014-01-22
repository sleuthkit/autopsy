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


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
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
 class AddImageTask implements Runnable {

        private Logger logger = Logger.getLogger(AddImageTask.class.getName());
        
        private Case currentCase;
        // true if the process was requested to stop
        private volatile boolean cancelled = false;
        //true if revert has been invoked.
        private boolean reverted = false;
        private boolean hasCritError = false;
        
        private volatile boolean  addImageDone = false;
        
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
                        String currDir = process.currentDirectory();
                        if (currDir != null) {
                            if (!currDir.isEmpty() ) {
                                progressMonitor.setProgressText("Adding: " + currDir);
                            }
                        }
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
        public void run() {
             
            
            errorList.clear();
           
            //lock DB for writes in this thread
            SleuthkitCase.dbWriteLock();
             
            addImageProcess = currentCase.makeAddImageProcess(timeZone, true, noFatOrphans);
            dirFetcher = new Thread( new CurrentDirectoryFetcher(progressMonitor, addImageProcess));
          
            try {
                progressMonitor.setIndeterminate(true);
                progressMonitor.setProgress(0);
               
                dirFetcher.start();
                
                addImageProcess.run(new String[]{this.imagePath});
                
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Core errors occurred while running add image. ", ex);
                //critical core/system error and process needs to be interrupted
                hasCritError = true;
                errorList.add(ex.getMessage());
            } catch (TskDataException ex) {
                logger.log(Level.WARNING, "Data errors occurred while running add image. ", ex);
                errorList.add(ex.getMessage());
            } 
            finally {
             
            }

            // handle addImage done
            postProcess();
            
            // unclock the DB 
            SleuthkitCase.dbWriteUnlock();
             
            return;
        }

        /**
         * Commit the newly added image to DB
         *
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
               
                if (imageId != 0) {
                    // get the newly added Image so we can return to caller
                    Image newImage = currentCase.getSleuthkitCase().getImageById(imageId);
                     
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
         * Post processing after the addImageProcess is done.
         * 
         */
        private void postProcess() {
        
            
            // cancel the directory fetcher
            dirFetcher.interrupt();
            
            addImageDone = true; 
            // attempt actions that might fail and force the process to stop
            
            if (cancelled || hasCritError) {
                logger.log(Level.WARNING, "Critical errors or interruption in add image process. Image will not be comitted.");
                revert();
            }
            
            if (!errorList.isEmpty()) {
                logger.log(Level.INFO, "There were errors that occured in add image process");
            }
            
            
            // When everything happens without an error:
            if (!(cancelled || hasCritError)) {

                try {
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
                    // Tell the progress monitor we're done
                    progressMonitor.setProgress(100);

                } catch (Exception ex) {
                    //handle unchecked exceptions post image add
                    errorList.add(ex.getMessage());
                    
                    logger.log(Level.WARNING, "Unexpected errors occurred while running post add image cleanup. ", ex);
                    logger.log(Level.SEVERE, "Error adding image to case", ex);
                } finally {


                }
            }
            
            // invoke the callBack, unless the caller cancelled 
            if (!cancelled) {
                doCallBack();
            }
            
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
                    
                }
                reverted = true;
            }
        }
    }
