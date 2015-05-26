/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponentinterfaces;


import javax.swing.JPanel;

/**
 * Interface used by the Add DataSource wizard to allow different 
 * types of data sources to be added to a case.  Examples of data 
 * sources include disk images, local files, etc. 
 * 
 * The interface provides a uniform mechanism for the Autopsy UI
 * to:
 *  - Collect details from the user about the data source to be processed.
 *  - Process the data source in the background and add data to the database
 *  - Provides progress feedback to the user / UI. 
 */
public interface DataSourceProcessor {
   
    /**
     * The DSP Panel may fire Property change events
     * The caller must enure to add itself as a listener and 
     * then react appropriately to the events
     */
    enum DSP_PANEL_EVENT {
        UPDATE_UI,  ///< the content of JPanel has changed that MAY warrant updates to the caller UI
        FOCUS_NEXT  ///< the caller UI may move focus the the next UI element, following the panel.
    };
    
    
   /**
    * Returns the type of Data Source it handles. 
    * This name gets displayed in the drop-down listbox
    */
    String getDataSourceType();
    
   /**
    * Returns the picker panel to be displayed along with any other
    * runtime options supported by the data source handler.  The
    * DSP is responsible for storing the settings so that a later 
    * call to run() will have the user-specified settings.  
    * 
    * Should be less than 544 pixels wide and 173 pixels high. 
    */
    JPanel getPanel();
    
   /**
    * Called to validate the input data in the panel.
    * Returns true if no errors, or 
    * Returns false if there is an error.  
    */
    boolean isPanelValid();
    
   /**
    * Called to invoke the handling of data source in the background.
    * Returns after starting the background thread.   
    * 
    * @param progressPanel progress panel to be updated while processing
    * @param dspCallback Contains the callback method DataSourceProcessorCallback.done() that the DSP must call when the background thread finishes with errors and status. 
    */
    void run(DataSourceProcessorProgressMonitor progressPanel, DataSourceProcessorCallback dspCallback);
    
    
   /**
    * Called to cancel the background processing.
    */
    void cancel();
    
   /**
    * Called to reset/reinitialize  the DSP.
    */
    void reset();
}
