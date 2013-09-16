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
package org.sleuthkit.autopsy.ingest;

import java.util.List;
import javax.swing.JPanel;
import org.sleuthkit.datamodel.Content;

/**
 * Instances of this class provide the following services:
 *    1. A way to save and load the ingest process configuration settings for a 
 *       given ingest process context.
 *    2. A UI component for configuring ingest process settings.
 *    3. A way to specify input content and start the ingest process for a 
 *       given ingest process context.
 */
// @@@ This interface needs to be re-designed. An interface for allowing the 
// authors of ingest modules to expose context sensitive module configuration 
// settings needs to be provided; there also needs to be a way for users to 
// configure the ingest process that uses those modules. These are separate 
// concerns; likewise, kicking off an ingest process for particular content in
// a particular context is a separate concern.
public interface IngestConfigurator {
    /**
     * Specifies the ingest process context for the purpose of choosing, saving, 
     * and loading ingest process configuration settings; also determines what
     * configuration settings will be in effect if the setContent() and start()
     * methods are called to start the ingest process for some content specified
     * using the setContent() method.
     * @return A list, possibly empty, of messages describing errors that 
     * occurred when loading the configuration settings.
     */
    public List<String> setContext(String contextName);
    
    /**
     * Provides a UI component for choosing ingest process configuration 
     * settings for the ingest process context specified using the setContext() 
     * method.
     */
    JPanel getIngestConfigPanel();

    /**
     * Saves the ingest process configuration settings for the ingest process 
     * context specified using the setContext() method.
     */
    void save();
        
    /**
     * Sets the input content for an ingest process prior to calling start() to
     * run the process using the process configuration settings for the context 
     * specified using setContext(). 
     */
    void setContent(List<Content> inputContent);
    
    /**
     * Starts (queues) the ingest process for the content specified using the 
     * setContent() method, using the configuration settings corresponding to 
     * the ingest process context specified using the setContext() method.
     */
    void start();
        
    /**
     * Returns true if any ingest process is running, false otherwise.
     * Note that the running process may or may not be the process started 
     * (queued) by an invocation of the start() method.
     */
    boolean isIngestRunning(); 
}
