/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.healthmonitor;

import java.util.logging.Level;
import org.openide.modules.ModuleInstall;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;

public class Installer extends ModuleInstall {

    private static final Logger logger = Logger.getLogger(Installer.class.getName());
    private static final long serialVersionUID = 1L;

    private static Installer instance;

    public synchronized static Installer getDefault() {
        if (instance == null) {
            instance = new Installer();
        }
        return instance;
    }

    private Installer() {
        super();
    }
    
    @Override
    public void restored() {
        
        try {
            HealthMonitor.startUpIfEnabled();
        } catch (HealthMonitorException ex) {
            logger.log(Level.SEVERE, "Error starting health services monitor", ex);
        }
    }
    
    @Override
    public void close() {
        try {
            HealthMonitor.shutdown();
        } catch (HealthMonitorException ex) {
            logger.log(Level.SEVERE, "Error stopping health services monitor", ex);
        }
    }
}