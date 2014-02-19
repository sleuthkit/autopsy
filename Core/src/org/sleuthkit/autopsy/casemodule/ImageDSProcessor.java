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
import java.util.ArrayList;
import java.util.List;
import javax.swing.filechooser.FileFilter;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;

/**
 * Image data source processor.
 * Handles the addition of  "disk images" to Autopsy.
 * 
 * An instance of this class is created via the Netbeans Lookup() method.
 * 
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class ImageDSProcessor implements DataSourceProcessor {
  
    static final Logger logger = Logger.getLogger(ImageDSProcessor.class.getName());
    
    // Data source type handled by this processor
    private final static String dsType = "Image File";
    
    // The Config UI panel that plugins into the Choose Data Source Wizard
    private final ImageFilePanel imageFilePanel;
    
    // The Background task that does the actual work of adding the image 
    private AddImageTask addImageTask;
   
    // true of cancelled by the caller
    private boolean cancelled = false;
    
    DataSourceProcessorCallback callbackObj = null;
    
    // set to TRUE if the image options have been set via API and config Jpanel should be ignored
    private boolean imageOptionsSet = false;
    
    // image options
    private String imagePath;
    private String timeZone;
    private boolean noFatOrphans;
     
    static final GeneralFilter rawFilter = new GeneralFilter(GeneralFilter.RAW_IMAGE_EXTS, GeneralFilter.RAW_IMAGE_DESC);
    static final GeneralFilter encaseFilter = new GeneralFilter(GeneralFilter.ENCASE_IMAGE_EXTS, GeneralFilter.ENCASE_IMAGE_DESC);
    
    static final List<String> allExt = new ArrayList<>();
    static {
        allExt.addAll(GeneralFilter.RAW_IMAGE_EXTS);
        allExt.addAll(GeneralFilter.ENCASE_IMAGE_EXTS);
    }
    static final String allDesc = "All Supported Types";
    static final GeneralFilter allFilter = new GeneralFilter(allExt, allDesc);
    
    static final List<FileFilter> filtersList = new ArrayList<>();
    
    static {
        filtersList.add(allFilter);
        filtersList.add(rawFilter);
        filtersList.add(encaseFilter);
    }
    
    /*
     * A no argument constructor is required for the NM lookup() method to create an object
     */
    public ImageDSProcessor() {
        
        // Create the config panel
        imageFilePanel =  ImageFilePanel.createInstance(ImageDSProcessor.class.getName(), filtersList);
        
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

       imageFilePanel.readSettings();
       imageFilePanel.select();
       
       return imageFilePanel;
   }
    
    /**
     * Validates the data collected by the JPanel
     *
     * @return String returns NULL if success, error string if there is any errors  
     **/  
   @Override
   public boolean isPanelValid() {
       
        return imageFilePanel.validatePanel(); 
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
      
      if (!imageOptionsSet)
      {
          //tell the panel to save the current settings
          imageFilePanel.storeSettings();
          
          // get the image options from the panel
          imagePath = imageFilePanel.getContentPaths();
          timeZone = imageFilePanel.getTimeZone();
          noFatOrphans = imageFilePanel.getNoFatOrphans(); 
      }
      
      addImageTask = new AddImageTask(imagePath, timeZone, noFatOrphans,  progressMonitor, cbObj); 
      new Thread(addImageTask).start();
       
  }
  
    /**
     * Cancel the data source processing
     **/    
  @Override
  public void cancel() {
      
      cancelled = true;
      addImageTask.cancelTask();
  }
  
  /**
   * Reset the data source processor
   **/    
  @Override
  public void reset() {
      
     // reset the config panel
     imageFilePanel.reset();
    
     // reset state 
     imageOptionsSet = false;
     imagePath = null;
     timeZone = null;
     noFatOrphans = false;
  }
  
  /**
   * Sets the data source options externally.
   * To be used by a client that does not have a UI and does not use the JPanel to 
   * collect this information from a user.
   * 
   * @param imgPath path to thew image or first image
   * @param tz timeZone 
   * @param noFat whether to parse FAT orphans
   **/ 
  public void setDataSourceOptions(String imgPath, String tz, boolean noFat) {
      
    this.imagePath = imgPath;
    this.timeZone  = tz;
    this.noFatOrphans = noFat;
      
    imageOptionsSet = true;
      
  }
  
  
}
