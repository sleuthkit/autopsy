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

package org.sleuthkit.autopsy.corecomponentinterfaces;

import java.awt.EventQueue;
import java.util.List;
import org.sleuthkit.datamodel.Content;

/**
 * Abstract class for a callback for a DataSourceProcessor.
 * 
 * Ensures that DSP invokes the caller overridden method, doneEDT(), 
 * in the EDT thread. 
 * 
 */
public abstract class DataSourceProcessorCallback {
    
    public enum DataSourceProcessorResult 
            {
                NO_ERRORS, 
                CRITICAL_ERRORS,
                NONCRITICAL_ERRORS,
    };
    
    /*
     * Invoke the caller supplied callback function on the EDT thread
     */
    public void done(DataSourceProcessorResult result, List<String> errList,  List<Content> newContents)
    {
        
        final DataSourceProcessorResult resultf = result;
        final List<String> errListf = errList;
        final List<Content> newContentsf = newContents;
                
            // Invoke doneEDT() that runs on the EDT .
            EventQueue.invokeLater(new Runnable() {
               @Override
               public void run() {
                   doneEDT(resultf, errListf, newContentsf );
                   
               }
           }); 
    }
    
    /*
     * calling code overrides to provide its own calllback 
     */
    public abstract void doneEDT(DataSourceProcessorResult result, List<String> errList,  List<Content> newContents);
};
