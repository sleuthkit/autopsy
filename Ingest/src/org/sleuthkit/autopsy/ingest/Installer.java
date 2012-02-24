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
package org.sleuthkit.autopsy.ingest;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.modules.ModuleInstall;
import org.openide.windows.WindowManager;

/**
 * Initializes ingest manager when the module is loaded
 */
public class Installer extends ModuleInstall {

    @Override
    public void restored() {

        Logger logger = Logger.getLogger(Installer.class.getName());
        logger.log(Level.INFO, "Initializing ingest manager");
        final IngestManager manager = IngestManager.getDefault();
        WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
            @Override
            public void run() {
                manager.initUI();
            }
        });

    }

    @Override
    public boolean closing() {
       
        return true;
    }
}
