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

package org.sleuthkit.autopsy.coreutils;

import java.io.IOException;
import java.util.logging.FileHandler;
import org.openide.modules.ModuleInstall;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import org.openide.modules.Places;



/**
 * Manages a this module's lifecycle. Sets up logging to file.
 */
public class Installer extends ModuleInstall {

    static final Logger autopsyLogger = Logger.getLogger("");
    static final String LOG_FILENAME_PATTERN = Places.getUserDirectory().getAbsolutePath() + "/var/log/autopsy.log"; //%t is system temp dir, %g is log number
    static final int LOG_SIZE = 0; // in bytes, zero is unlimited
    static final int LOG_FILE_COUNT = 10;
    static Handler logs;

    @Override
    public void restored() {
        if (logs == null) {
            try {
                logs = new FileHandler(LOG_FILENAME_PATTERN, LOG_SIZE, LOG_FILE_COUNT);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            logs.setFormatter(new SimpleFormatter());
            autopsyLogger.addHandler(logs);
        }
        
        autopsyLogger.log(Level.INFO, "Java runtime version: " + Version.getJavaRuntimeVersion());
        
        autopsyLogger.log(Level.INFO, "Netbeans Platform build: " + Version.getNetbeansBuild());
        
        autopsyLogger.log(Level.INFO, "Application name: " + Version.getName() 
                + ", version: " + Version.getVersion() + ", build: " + Version.getBuildType());
    }
    
    @Override
    public void uninstalled() {
        autopsyLogger.removeHandler(logs);
        logs.close();
        logs = null;
    }


}
