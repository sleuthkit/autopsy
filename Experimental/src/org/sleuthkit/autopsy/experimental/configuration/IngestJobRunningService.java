/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.configuration;

import javax.swing.JPanel;

/**
 * Interface to run an ingest job in the background.
 */
public interface IngestJobRunningService {

    /**
     * Starts the service
     */
    void start();
    
    /**
     * Stops the service
     */
    void stop();
    
    /**
     * Returns a panel to be displayed while using this service
     * 
     * @return panel to be displayed while using this service
     */
    JPanel getStartupWindow();
}
