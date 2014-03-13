/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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
public class LocalDiskDSProcessor  implements DataSourceProcessor {
    
    static final Logger logger = Logger.getLogger(ImageDSProcessor.class.getName());
    
    // Data source type handled by this processor
    private static  final String dsType = NbBundle.getMessage(LocalDiskDSProcessor.class, "LocalDiskDSProcessor.dsType.text");
    
    // The Config UI panel that plugins into the Choose Data Source Wizard
    private final LocalDiskPanel localDiskPanel;
    
    // The Background task that does the actual work of adding the local Disk 
    // Adding a local disk is exactly same as adding an Image.
    private AddImageTask addDiskTask;
   
    // true if cancelled by the caller
    private boolean cancelled = false;
    
    DataSourceProcessorCallback callbackObj = null;
    
    // set to TRUE if the image options have been set via API and config Jpanel should be ignored
    private boolean localDiskOptionsSet = false;
    
    // data source options
    private String localDiskPath;
    private String timeZone;
    private boolean noFatOrphans;
    
     /*
     * A no argument constructor is required for the NM lookup() method to create an object
     */
    public LocalDiskDSProcessor() {
        
        // Create the config panel
        localDiskPanel =  LocalDiskPanel.getDefault();
        
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
       
       localDiskPanel.select();
       return localDiskPanel;
   }
    /**
     * Validates the data collected by the JPanel
     *
     * @return String returns NULL if success, error string if there is any errors  
     **/  
   @Override
   public boolean isPanelValid() {
           return localDiskPanel.validatePanel();
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
      
      if (!localDiskOptionsSet) {
          // get the image options from the panel
          localDiskPath = localDiskPanel.getContentPaths();
          timeZone = localDiskPanel.getTimeZone();
          noFatOrphans = localDiskPanel.getNoFatOrphans(); 
      }
      
      addDiskTask = new AddImageTask(localDiskPath, timeZone, noFatOrphans,  progressMonitor, cbObj); 
      new Thread(addDiskTask).start();
       
  }
  
   /**
     * Cancel the data source processing
     **/    
  @Override
  public void cancel() {
      
      cancelled = true;
      
      addDiskTask.cancelTask();
  }
  
  /**
   * Reset the data source processor
   **/  
  @Override
  public void reset() {
      
     // reset the config panel
     localDiskPanel.reset();
    
     // reset state 
     localDiskOptionsSet = false;
     localDiskPath = null;
     timeZone = null;
     noFatOrphans = false;
    
  }
  
  /**
   * Sets the data source options externally.
   * To be used by a client that does not have a UI and does not use the JPanel to 
   * collect this information from a user.
   * 
   * @param diskPath path to the local disk
   * @param tz time zone
   * @param noFat whether to parse FAT orphans
   **/ 
  public void setDataSourceOptions(String diskPath, String tz, boolean noFat) {
      
    this.localDiskPath = diskPath;
    this.timeZone  = tz;
    this.noFatOrphans = noFat;
      
    localDiskOptionsSet = true;
      
  }
}
