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
package org.sleuthkit.autopsy.ingest;

import org.openide.modules.ModuleInstall;
import org.openide.windows.WindowManager;

/**
 * Initializes ingest manager when the module is loaded
 */
public class Installer extends ModuleInstall {

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
        final IngestManager manager = IngestManager.getInstance();
        WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
            @Override
            public void run() {
                //at this point UI top component is present for sure, ensure manager has it
                manager.initIngestMessageInbox();
            }
        });

    }

    @Override
    public boolean closing() {
        //force ingest inbox closed on exit and save state as such
        IngestMessageTopComponent.findInstance().close();
        return true;
    }
}
