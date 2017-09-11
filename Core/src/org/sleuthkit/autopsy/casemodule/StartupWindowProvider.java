/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Provides the start up window to rest of the application. It may return the
 * main / default startup window, or a custom one if it has been discovered.
 *
 * All that is required to create a custom startup window in a module and active
 * it, is to implement StartupWindowInterface and register it with lookup as a
 * ServiceProvider. The custom startup window is automatically chosen over the
 * default one, given it is the only external module custom startup window.
 */
public class StartupWindowProvider implements StartupWindowInterface {

    private static volatile StartupWindowProvider instance;
    private static final Logger logger = Logger.getLogger(StartupWindowProvider.class.getName());
    private volatile StartupWindowInterface startupWindowToUse;

    public static StartupWindowProvider getInstance() {
        if (instance == null) {
            synchronized (StartupWindowProvider.class) {
                if (instance == null) {
                    instance = new StartupWindowProvider();
                    instance.init();
                }
            }
        }

        return instance;
    }

    private void init() {
        if (startupWindowToUse == null) {
            //discover the registered windows
            Collection<? extends StartupWindowInterface> startupWindows
                    = Lookup.getDefault().lookupAll(StartupWindowInterface.class);

            int windowsCount = startupWindows.size();
            if (windowsCount == 1) {
                startupWindowToUse = startupWindows.iterator().next();
                logger.log(Level.INFO, "Will use the default startup window: " + startupWindowToUse.toString()); //NON-NLS
            } else if (windowsCount == 2) {
                //pick the non default one
                Iterator<? extends StartupWindowInterface> it = startupWindows.iterator();
                while (it.hasNext()) {
                    StartupWindowInterface window = it.next();
                    if (!org.sleuthkit.autopsy.casemodule.StartupWindow.class.isInstance(window)) {
                        startupWindowToUse = window;
                        logger.log(Level.INFO, "Will use the custom startup window: " + startupWindowToUse.toString()); //NON-NLS
                        break;
                    }
                }
            } else {
                // select first non-Autopsy start up window
                Iterator<? extends StartupWindowInterface> it = startupWindows.iterator();
                while (it.hasNext()) {
                    StartupWindowInterface window = it.next();
                    if (!window.getClass().getCanonicalName().startsWith("org.sleuthkit.autopsy")) {
                        startupWindowToUse = window;
                        logger.log(Level.INFO, "Will use the custom startup window: " + startupWindowToUse.toString()); //NON-NLS
                        break;
                    }
                }
            }

            if (startupWindowToUse == null) {
                logger.log(Level.SEVERE, "Unexpected error, no startup window chosen, using the default"); //NON-NLS
                startupWindowToUse = new org.sleuthkit.autopsy.casemodule.StartupWindow();
            }
        }
    }

    @Override
    public void open() {
        if (startupWindowToUse != null) {
            startupWindowToUse.open();
        }
    }

    @Override
    public void close() {
        if (startupWindowToUse != null) {
            startupWindowToUse.close();
        }
    }
}
