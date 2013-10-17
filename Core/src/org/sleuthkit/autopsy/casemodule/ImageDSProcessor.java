/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.openide.WizardDescriptor;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskException;

/**
 *
 * @author raman
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class ImageDSProcessor implements DataSourceProcessor {
  
    static final Logger logger = Logger.getLogger(ImageDSProcessor.class.getName());
    
    private ImageFilePanel imageFilePanel;
    private AddImageTask addImageTask;
   
    private boolean cancelled = false;
    
    DSPCallback callbackObj = null;
    
    // set to TRUE if the image options have been set via API and config Jpanel should be ignored
    private boolean imageOptionsSet = false;
    private String imagePath;
    private String timeZone;
    private boolean noFatOrphans;
            
        
    
    public ImageDSProcessor() {
        logger.log(Level.INFO, "RAMAN ImageDSProcessor()...");
        
        // Create the config panel
        imageFilePanel =  ImageFilePanel.getDefault();
        
    }
    
    
    @Override
    public String getType() {
      
        logger.log(Level.INFO, "RAMAN getName()...");
        
        return imageFilePanel.getContentType();
        
    }
            
    
   @Override
    public JPanel getPanel() {
       
       logger.log(Level.INFO, "RAMAN getPanel()...");
       
       // RAMAN TBD: we should ask the panel to preload with any saved settings
        
       imageFilePanel.select();
       
       return imageFilePanel;
   }
    
   @Override
   public String validatePanel() {
       
       logger.log(Level.INFO, "RAMAN validatePanel()...");
       
        if (imageFilePanel.validatePanel() )
           return null;
       else 
           return "Error in panel";    
   }
    
  @Override
  public void run(DSPProgressMonitor progressMonitor, DSPCallback cbObj) {
      
      logger.log(Level.INFO, "RAMAN run()...");
      
      callbackObj = cbObj;
      cancelled = false;
      
      if (!imageOptionsSet)
      {
          // get the image options from the panel
          imagePath = imageFilePanel.getContentPaths();
          timeZone = imageFilePanel.getTimeZone();
          noFatOrphans = imageFilePanel.getNoFatOrphans(); 
      }
      
      addImageTask = new AddImageTask(progressMonitor, cbObj);
      
      // set the image options needed by AddImageTask - such as TZ and NoFatOrphans **/
      addImageTask.SetImageOptions(imagePath, timeZone, noFatOrphans);
          
      addImageTask.execute();
       
      return;
  }
    
  @Override
  public void cancel() {
      
      logger.log(Level.INFO, "RAMAN cancelProcessing()...");
      
      cancelled = true;
      addImageTask.cancelTask();
      
      return;
  }
  
   
  @Override
  public void reset() {
      
     logger.log(Level.INFO, "RAMAN reset()...");
     
     // reset the config panel
     imageFilePanel.reset();
    
     // reset state 
     imageOptionsSet = false;
     imagePath = null;
     timeZone = null;
     noFatOrphans = false;
    
      return;
  }
  
  public void SetDataSourceOptions(String imgPath, String tz, boolean noFat) {
      
    this.imagePath = imgPath;
    this.timeZone  = tz;
    this.noFatOrphans = noFat;
      
    imageOptionsSet = true;
      
  }
  
  
   private class AddImageTask extends SwingWorker<Integer, Integer> {

        private Logger logger = Logger.getLogger(AddImageTask.class.getName());
        
        private Case currentCase;
        // true if the process was requested to stop
        private boolean cancelled = false;
        //true if revert has been invoked.
        private boolean reverted = false;
        private boolean hasCritError = false;
        private boolean addImageDone = false;
        
        private List<String> errorList = new ArrayList<String>();
        
        private DSPProgressMonitor progressMonitor;
        private DSPCallback callbackObj;
        
        private final List<Content> newContents = Collections.synchronizedList(new ArrayList<Content>());
        
        private SleuthkitJNI.CaseDbHandle.AddImageProcess addImageProcess;
        private CurrentDirectoryFetcher fetcher;
   
        private String imagePath;
        private String dataSourcetype;
        String timeZone;
        boolean noFatOrphans;
            
        
        
        public void SetImageOptions(String imgPath, String tz, boolean noOrphans) {
            this.imagePath = imgPath;
            this.timeZone = tz;
            this.noFatOrphans = noOrphans;
        }
        
      
        private class CurrentDirectoryFetcher extends SwingWorker<Integer, Integer> {

            DSPProgressMonitor progressMonitor;
            SleuthkitJNI.CaseDbHandle.AddImageProcess process;

            CurrentDirectoryFetcher(DSPProgressMonitor aProgressMonitor, SleuthkitJNI.CaseDbHandle.AddImageProcess proc) {
                this.progressMonitor = aProgressMonitor;
                this.process = proc;
               // this.progressBar = aProgressBar;
            }

            /**
             * @return the currently processing directory
             */
            @Override
            protected Integer doInBackground() {
                try {
                    while (!(addImageDone)) { 
                        EventQueue.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                progressMonitor.setText(process.currentDirectory()); 
                            }
                        });

                        Thread.sleep(2 * 1000);
                    }
                    return 1;
                } catch (InterruptedException ie) {
                    return -1;
                }
            }
        }
   
         
        protected AddImageTask(DSPProgressMonitor aProgressMonitor, DSPCallback cbObj ) {
            this.progressMonitor = aProgressMonitor;
            currentCase = Case.getCurrentCase();
            
            this.callbackObj = cbObj;
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

            logger.log(Level.INFO, "RAMAN: doInBackground()");
             
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
            fetcher = new CurrentDirectoryFetcher(progressMonitor, addImageProcess);
          
            try {
                progressMonitor.setIndeterminate(true);
                progressMonitor.setProgress(0);
               
                fetcher.execute();
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

            logger.log(Level.INFO, "RAMAN: commitImage()...");

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
         * Must Not return without invoking the callBack.
         */
        @Override
        protected void done() {
            
            logger.log(Level.INFO, "RAMAN: done()...");
                   
            setProgress(100);
            
            // cancel
            fetcher.cancel(true);

            addImageDone = true;
            
            // attempt actions that might fail and force the process to stop
            if (cancelled || hasCritError) {
                logger.log(Level.INFO, "Handling errors or interruption that occured in add image process");
                revert();
                // Do not return yet.  Callback must be called
            }
            if (!errorList.isEmpty()) {
               
                logger.log(Level.INFO, "Handling non-critical errors that occured in add image process");
                
                // error are returned back to the caller
            }
            
            // When everything happens without an error:
            if (!(cancelled || hasCritError)) {

                try {
                    

                    // Tell the panel we're done
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
            
        }

        
        void doCallBack()
        {
              logger.log(Level.INFO, "RAMAN In doCallback()");
              
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
              
              callbackObj.done(result, errorList, newContents);
        }
        
        
        void cancelTask() {
            
             logger.log(Level.INFO, "RAMAN: cancelTask()...");
            
             cancelled = true;
             
             if (!addImageDone) {
                try {
                  addImageTask.interrupt();
                }
                catch (Exception ex) {
                      logger.log(Level.SEVERE, "Failed to interrup the add image task...");    
                }
            }
            else {
                try {
                  addImageTask.revert();  
                }
                catch(Exception ex) {
                     logger.log(Level.SEVERE, "Failed to revert the add image task...");   
                }
            }
        }
        void interrupt() throws Exception {
            
            logger.log(Level.INFO, "RAMAN: interrupt()...");
             
            //interrupted = true;
            try {
                logger.log(Level.INFO, "interrupt() add image process");
                addImageProcess.stop();  //it might take time to truly stop processing and writing to db
            } catch (TskException ex) {
                throw new Exception("Error stopping add-image process.", ex);
            }
        }

        //runs in EWT
        void revert() {
            
             logger.log(Level.INFO, "RAMAN: revert()...");
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
  
}
