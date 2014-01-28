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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.datamodel.TskCoreException;


/**
     * Thread that will add logical files to database, and then kick-off ingest
     * modules. Note: the add logical files task cannot currently be reverted as
     * the add image task can. This is a separate task from AddImgTask because
     * it is much simpler and does not require locks, since the underlying file
     * manager methods acquire the locks for each transaction when adding
     * logical files.
     */
 class AddLocalFilesTask implements Runnable {

    private Logger logger = Logger.getLogger(AddLocalFilesTask.class.getName());
     
    private String dataSourcePath;
    private DSPProgressMonitor progressMonitor;
    private DSPCallback callbackObj;
        
    private Case currentCase;
    // true if the process was requested to stop
    private volatile boolean cancelled = false;
    private boolean hasCritError = false;
    
   private List<String> errorList = new ArrayList<String>();
   private final List<Content> newContents = Collections.synchronizedList(new ArrayList<Content>()); 
   

    protected AddLocalFilesTask(String dataSourcePath, DSPProgressMonitor aProgressMonitor, DSPCallback cbObj) {
       
        currentCase = Case.getCurrentCase();
       
        this.dataSourcePath = dataSourcePath;
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
        
        final LocalFilesAddProgressUpdater progUpdater = new LocalFilesAddProgressUpdater(progressMonitor);
        try {
            
            progressMonitor.setIndeterminate(true);
            progressMonitor.setProgress(0);
            
            final FileManager fileManager = currentCase.getServices().getFileManager();
            String[] paths = dataSourcePath.split(LocalFilesPanel.FILES_SEP);
            List<String> absLocalPaths = new ArrayList<String>();
            for (String path : paths) {
                absLocalPaths.add(path);
            }
            newContents.add(fileManager.addLocalFilesDirs(absLocalPaths, progUpdater));
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Errors occurred while running add logical files. ", ex);
            hasCritError = true;
            errorList.add(ex.getMessage());
        } finally {
           
        }
        
         // handle  done
        postProcess();
        
        return;
    }

    /**
     *
     * (called by EventDispatch Thread after doInBackground finishes)
     */
    protected void postProcess() {
        
        if (cancelled || hasCritError) {
            logger.log(Level.WARNING, "Handling errors or interruption that occured in logical files process");
            
        }
        if (!errorList.isEmpty()) {
                //data error (non-critical)
                logger.log(Level.WARNING, "Handling non-critical errors that occured in logical files process");
        }
   
        if (!(cancelled || hasCritError)) {
            progressMonitor.setProgress(100);
            progressMonitor.setIndeterminate(false);      
        }
          
        // invoke the callBack, unless the caller cancelled 
        if (!cancelled) {
            doCallBack();
        }
           
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

         // invoke the callback, passing it the result, list of new contents, and list of errors
         callbackObj.done(result, errorList, newContents);
   }
        
   /*
    * cancel the files addition, if possible
    */
   public void cancelTask() {
       cancelled = true;
  
   }
        
    /**
     * Updates the wizard status with logical file/folder
     */
    private class LocalFilesAddProgressUpdater implements FileManager.FileAddProgressUpdater {

        private int count = 0;
        private DSPProgressMonitor progressMonitor;
       

        LocalFilesAddProgressUpdater(DSPProgressMonitor progressMonitor) {
           
            this.progressMonitor = progressMonitor;
        }

        @Override
        public void fileAdded(final AbstractFile newFile) {
            if (count++ % 10 == 0) {  
                progressMonitor.setProgressText("Adding: " + newFile.getParentPath() + "/" + newFile.getName()); 
            }
        }
    }
}
