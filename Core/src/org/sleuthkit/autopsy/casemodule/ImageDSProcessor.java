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
    private CurrentDirectoryFetcher fetcher;
  
    private boolean addImageDone = false;
    private boolean cancelled = false;
    
    DSPCallback callbackObj = null;
    
    public ImageDSProcessor() {
        logger.log(Level.INFO, "RAMAN ImageDSHandler()...");
        
        // Create the config panel
        imageFilePanel =  ImageFilePanel.getDefault();
        
    }
    
    /****
    @Override
    public ImageDSProcessor createInstance() {
        return new ImageDSProcessor();
    }
    *****/
    
    
    @Override
    public String getType() {
      
        logger.log(Level.INFO, "RAMAN getName()...");
        
        return imageFilePanel.getContentType();
        
    }
            
    
   @Override
    public ContentTypePanel getPanel() {
       
       logger.log(Level.INFO, "RAMAN getPanel()...");
        
       return imageFilePanel;
   }
    
   @Override
   public String validatePanel() {
       
       logger.log(Level.INFO, "RAMAN validatePanel()...");
               
       return null;
        
   }
    
  @Override
  public void run(WizardDescriptor settings, AddImageWizardAddingProgressPanel progressPanel, DSPCallback cbObj) {
      
      logger.log(Level.INFO, "RAMAN run()...");
      
      callbackObj = cbObj;
      addImageDone = false;
      cancelled = false;
      
      addImageTask = new AddImageTask(settings, progressPanel, cbObj);
      addImageTask.execute();
       
      return;
  }
    
 /***   
 @Override
 public String[] getErrors() {
     
     logger.log(Level.INFO, "RAMAN getErrors()...");
     
     // RAMAN TBD
     return null;
 }
 *****/
    
  @Override
  public void cancel() {
      
      logger.log(Level.INFO, "RAMAN cancelProcessing()...");
      
      cancelled = true;
      addImageTask.cancelTask();
      
      return;
  }
  
  /*****
   @Override
   public List<Content> getNewContents() {
       return addImageTask.getNewContents();
   }
   * *****/
    
  
  private static class CurrentDirectoryFetcher extends SwingWorker<Integer, Integer> {

        //AddImageWizardIngestConfigPanel.AddImageTask task;
        JProgressBar progressBar;
        AddImageWizardAddingProgressVisual progressVisual;
        SleuthkitJNI.CaseDbHandle.AddImageProcess process;

        CurrentDirectoryFetcher(JProgressBar aProgressBar, AddImageWizardAddingProgressVisual wiz, SleuthkitJNI.CaseDbHandle.AddImageProcess proc) {
            this.progressVisual = wiz;
            this.process = proc;
            this.progressBar = aProgressBar;
        }

        /**
         * @return the currently processing directory
         */
        @Override
        protected Integer doInBackground() {
            try {
                while (progressBar.getValue() < 100 || progressBar.isIndeterminate()) { //TODO Rely on state variable in AddImgTask class

                    EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            progressVisual.setCurrentDirText(process.currentDirectory());
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
  
  
   private class AddImageTask extends SwingWorker<Integer, Integer> {

        private JProgressBar progressBar;
        private Case currentCase;
        // true if the process was requested to stop
        private boolean cancelled = false;
        //true if revert has been invoked.
        private boolean reverted = false;
        private boolean hasCritError = false;
        private boolean addImagedone = false;
        
        
        //private String errorString = null;
        private List<String> errorList = new ArrayList<String>();
        
        private WizardDescriptor wizDescriptor;
        
        private Logger logger = Logger.getLogger(AddImageTask.class.getName());
        private AddImageWizardAddingProgressPanel progressPanel;
        private DSPCallback callbackObj;
        
        private final List<Content> newContents = Collections.synchronizedList(new ArrayList<Content>());
        
        private SleuthkitJNI.CaseDbHandle.AddImageProcess addImageProcess;
        
        protected AddImageTask(WizardDescriptor settings, AddImageWizardAddingProgressPanel aProgressPanel, DSPCallback cbObj ) {
            this.progressPanel = aProgressPanel;
            this.progressBar = progressPanel.getComponent().getProgressBar();
            currentCase = Case.getCurrentCase();
            
            this.callbackObj = cbObj;
            this.wizDescriptor = settings;
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

            /**** RAMAN TBD: a higher level caller should set up the cleanup task and then call DataSourceHandler.cancelProcessing()
             *
            // Add a cleanup task to interrupt the backgroud process if the
            // wizard exits while the background process is running.
            AddImageAction.CleanupTask cancelledWhileRunning = action.new CleanupTask() {
                @Override
                void cleanup() throws Exception {
                    logger.log(Level.INFO, "Add image process interrupted.");
                    addImageTask.interrupt(); //it might take time to truly interrupt
                }
            };
            * *************************/


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

    
            String dataSourcePath = (String) wizDescriptor.getProperty(AddImageAction.DATASOURCEPATH_PROP);
            String dataSourceType = (String) wizDescriptor.getProperty(AddImageAction.DATASOURCETYPE_PROP);
            String timeZone = wizDescriptor.getProperty(AddImageAction.TIMEZONE_PROP).toString();
            boolean noFatOrphans = ((Boolean) wizDescriptor.getProperty(AddImageAction.NOFATORPHANS_PROP)).booleanValue();
        
            
            addImageProcess = currentCase.makeAddImageProcess(timeZone, true, noFatOrphans);
            fetcher = new CurrentDirectoryFetcher(this.progressBar, progressPanel.getComponent(), addImageProcess);
            //RAMAN TBD: handle the cleanup task
            //cancelledWhileRunning.enable();
            try {
                progressPanel.setStateStarted();
                fetcher.execute();
                addImageProcess.run(new String[]{dataSourcePath});
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Core errors occurred while running add image. ", ex);
                //critical core/system error and process needs to be interrupted
                hasCritError = true;
                //errorString = ex.getMessage();
                errorList.add(ex.getMessage());
            } catch (TskDataException ex) {
                logger.log(Level.WARNING, "Data errors occurred while running add image. ", ex);
                //errorString = ex.getMessage();
                 errorList.add(ex.getMessage());
            } finally {
                // process is over, doesn't need to be dealt with if cancel happens
                 //RAMAN TBD: handle the cleanup task
                //cancelledWhileRunning.disable();

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
        private void commitImage(WizardDescriptor settings) throws Exception {

            logger.log(Level.INFO, "RAMAN: commitImage()...");
            
            String contentPath = (String) settings.getProperty(AddImageAction.DATASOURCEPATH_PROP);

            String timezone = settings.getProperty(AddImageAction.TIMEZONE_PROP).toString();
            settings.putProperty(AddImageAction.IMAGEID_PROP, "");

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
                    Image newImage = Case.getCurrentCase().addImage(contentPath, imageId, timezone);

                    //while we have the image, verify the size of its contents
                    String verificationErrors = newImage.verifyImageSize();
                    if (verificationErrors.equals("") == false) {
                        //data error (non-critical)
                        errorList.add(verificationErrors);
                        //progressPanel.addErrors(verificationErrors, false);
                    }

                    /*** RAMAN TBD: how to handle the newContent notification back to IngestConfigPanel **/
                    newContents.add(newImage);
                   
                    settings.putProperty(AddImageAction.IMAGEID_PROP, imageId);
                }

                // Can't bail and revert image add after commit, so disable image cleanup
                // task
                
                // RAMAN TBD: cleanup task should be handled by the caller
               // cleanupImage.disable();
                
                settings.putProperty(AddImageAction.IMAGECLEANUPTASK_PROP, null);

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
                    
            //these are required to stop the CurrentDirectoryFetcher
            progressBar.setIndeterminate(false);
            setProgress(100);

            addImageDone = true;
            
            // attempt actions that might fail and force the process to stop

            if (cancelled || hasCritError) {
                logger.log(Level.INFO, "Handling errors or interruption that occured in add image process");
                revert();
                if (hasCritError) {
                    //core error
                    // RAMAN TBD: Error reporting needs to be removed from here. All errors are returned to caller directly.
                    //progressPanel.addErrors(errorString, true);
                }
                // Do not return yet.  Callback must be called
            }
            if (!errorList.isEmpty()) {
                //data error (non-critical)
                logger.log(Level.INFO, "Handling non-critical errors that occured in add image process");
                
                 // RAMAN TBD: Error reporting needs to be removed from here. All errors are returned to caller directly.
                //progressPanel.addErrors(errorString, false);
            }
            
            // When everything happens without an error:
            if (!(cancelled || hasCritError)) {

                try {
                    

                    /* *****************************
                     * RAMAN TBD: the caller needs to handle the cleanup ???
                    // the add-image process needs to be reverted if the wizard doesn't finish
                    cleanupImage = action.new CleanupTask() {
                        //note, CleanupTask runs inside EWT thread
                        @Override
                        void cleanup() throws Exception {
                            logger.log(Level.INFO, "Running cleanup task after add image process");
                            revert();
                        }
                    };
                    cleanupImage.enable();
                    * ************************/

                    //if (errorString == null) { // complete progress bar
                    if (errorList.isEmpty() ) { // complete progress bar
                        progressPanel.getComponent().setProgressBarTextAndColor("*Data Source added.", 100, Color.black);
                    }

                    // RAMAN TBD: this should not be happening in here - caller should do this

                    // Get attention for the process finish
                    java.awt.Toolkit.getDefaultToolkit().beep(); //BEEP!
                    AddImageWizardAddingProgressVisual panel = progressPanel.getComponent();
                    if (panel != null) {
                        Window w = SwingUtilities.getWindowAncestor(panel);
                        if (w != null) {
                            w.toFront();
                        }
                    }

                    // Tell the panel we're done
                    progressPanel.setStateFinished();


                    if (newContents.isEmpty()) {
                        if (addImageProcess != null) { // and if we're done configuring ingest
                            // commit anything
                            try {
                                commitImage(wizDescriptor);
                            } catch (Exception ex) {
                                // Log error/display warning
                                logger.log(Level.SEVERE, "Error adding image to case.", ex);
                            }
                        } else {
                            logger.log(Level.SEVERE, "Missing image process object");
                        }
                    }

                    else    //already commited?
                         {
                        logger.log(Level.INFO, "Assuming image already committed, will not commit.");

                    }




                    // Start ingest if we can
                    // RAMAN TBD - remove this from here
                    //startIngest();  

                } catch (Exception ex) {
                    //handle unchecked exceptions post image add

                    logger.log(Level.WARNING, "Unexpected errors occurred while running post add image cleanup. ", ex);

                    progressPanel.getComponent().setProgressBarTextAndColor("*Failed to add image.", 0, Color.black); // set error message

                    // Log error/display warning

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
        
        /*****
        public List<Content> getNewContents() {
            
            return newContents;
            
        }
        * ********/
        
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
