/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import org.sleuthkit.datamodel.Image;

/**
 * Lookup interface for ingest configuration dialog
 */
public interface IngestConfigurator {
    /**
     * get JPanel container with the configurator
     * @return 
     */
    JPanel getIngestConfigPanel();
    
    /**
     * set image for the ingest
     * @param image to enqueue to ingest
     */
    void setImage(Image image);
    
    /**
     * start ingest enqueing previously set image
     */
    void start();
    
    /**
     * save configuration of lastly selected service
     */
    void save();
    
    /**
     * reload the simple panel
     */
    void reload();
    
    /**
     * find out if ingest is currently running
     * 
     * @return  true if ingest process is running, false otherwise
     */
    boolean isIngestRunning();
    
}
