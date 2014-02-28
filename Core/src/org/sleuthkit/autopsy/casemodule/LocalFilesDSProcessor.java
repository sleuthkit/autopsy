/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014  Basis Technology Corp.
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

import javax.swing.JPanel;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.Logger;

@ServiceProvider(service = DataSourceProcessor.class)
public class LocalFilesDSProcessor implements DataSourceProcessor {
    
    static final Logger logger = Logger.getLogger(LocalFilesDSProcessor.class.getName());
    
    // Data source type handled by this processor
    private static final String dsType = NbBundle.getMessage(LocalFilesDSProcessor.class, "LocalFilesDSProcessor.dsType");
    
    // The Config UI panel that plugins into the Choose Data Source Wizard
    private final LocalFilesPanel localFilesPanel;
    
    // The Background task that does the actual work of adding the files 
    private AddLocalFilesTask addFilesTask;
   
    // true if cancelled by the caller
    private boolean cancelled = false;
    
    DataSourceProcessorCallback callbackObj = null;
    
    // set to TRUE if the image options have been set via API and config Jpanel should be ignored
    private boolean localFilesOptionsSet = false;
    
    // data source options
    private String localFilesPath;
   
    /*
     * A no argument constructor is required for the NM lookup() method to create an object
     */
    public LocalFilesDSProcessor() {
        
        // Create the config panel
        localFilesPanel =  LocalFilesPanel.getDefault();    
    }
    
    // this static method is used by the wizard to determine dsp type for 'core' data source processors
    public static String getType() {
        return dsType;
    }
    
    /**
     * Returns the Data source type (string) handled by this DSP
     *
     * @return String the data source type
     **/ 
    @Override
    public String getDataSourceType() {
        return dsType;
    }
            
    /**
     * Returns the JPanel for collecting the Data source information
     *
     * @return JPanel the config panel 
     **/ 
   @Override
    public JPanel getPanel() {
       localFilesPanel.select();
       return localFilesPanel;
   }
    
    /**
     * Validates the data collected by the JPanel
     *
     * @return String returns NULL if success, error string if there is any errors  
     **/  
   @Override
   public boolean isPanelValid() {
        return localFilesPanel.validatePanel();
   }
    
   /**
     * Runs the data source processor.
     * This must kick off processing the data source in background
     *
     * @param progressMonitor Progress monitor to report progress during processing 
     * @param cbObj callback to call when processing is done.
     **/    
  @Override
  public void run(DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback cbObj) {
      
      callbackObj = cbObj;
      cancelled = false;
      
      if (!localFilesOptionsSet) {
          // get the selected file paths from the panel
          localFilesPath = localFilesPanel.getContentPaths();
      }
      
      addFilesTask = new AddLocalFilesTask(localFilesPath,  progressMonitor, cbObj); 
      new Thread(addFilesTask).start();
       
  }
   
   /**
     * Cancel the data source processing
     **/    
  @Override
  public void cancel() {
      
      cancelled = true;
      addFilesTask.cancelTask();
    
  }
  
  /**
   * Reset the data source processor
   **/ 
  @Override
  public void reset() {
      
     // reset the config panel
     localFilesPanel.reset();
    
     // reset state 
     localFilesOptionsSet = false;
     localFilesPath = null;

  }
  
  /**
   * Sets the data source options externally.
   * To be used by a client that does not have a UI and does not use the JPanel to 
   * collect this information from a user.
   * 
   * @param filesPath PATH_SEP list of paths to local files
   *
   **/ 
  public void setDataSourceOptions(String filesPath) {
      
    localFilesPath = filesPath;
    
    localFilesOptionsSet = true;
      
  }
    
    
}
