/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2013 Basis Technology Corp.
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

import java.util.List;
import javax.swing.JPanel;
import org.openide.WizardDescriptor;
import org.sleuthkit.datamodel.Content;

public interface DataSourceProcessor {
    
    
               
    
   // public DataSourceProcessor createInstance();
            
    
   /**
    * Returns the type of Data Source it handles. 
    * This name gets displayed in the drop-down listbox
    **/
    String getType();
    
   /**
    * Returns the picker panel to be displayed along with any other
    * runtime options supported by the data source handler. 
    **/
    JPanel getPanel();
    
   /**
    * Called to validate the input data in the panel.
    * Returns null if no errors, or 
    * Returns a string describing the error if there are errors.  
    **/
    String validatePanel();
    
   /**
    * Called to invoke the handling of Data source in the background.
    * Returns after starting the background thread 
    * @param settings wizard settings to read/store properties
    * @param progressPanel progress panel to be updated while processing
    * 
    **/
    void run(DSPProgressMonitor progressPanel, DSPCallback dspCallback);
    
    /**
    * Called after run() is done to get the new content added by the handler.
    * Returns a list of content added by the data source handler  
    **/
   // List<Content> getNewContents();
            
    
   /**
    * Called to get the list of errors.
    **/
   // String[] getErrors();
    
   /**
    * Called to cancel the background processing.
    * 
    * TODO look into current use cases to see if this should wait until it has stopped or not. 
    **/
    void cancel();
    
    /**
    * Called to reset/reinit  the DSP.
    * 
    **/
    void reset();

    
}
