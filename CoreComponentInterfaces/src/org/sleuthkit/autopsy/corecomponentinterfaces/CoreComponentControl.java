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
package org.sleuthkit.autopsy.corecomponentinterfaces;

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.util.Lookup;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Responsible for opening and closing the core windows when a case is opened and closed.
 *
 * @author jantonius
 */
public class CoreComponentControl {

    private static final Logger logger = Logger.getLogger(CoreComponentControl.class.getName());

    /**
     * Opens all TopComponent windows that are needed ({@link DataExplorer}, {@link DataResult}, and
     * {@link DataContent})
     */
    public static void openCoreWindows() {
        // TODO: there has to be a better way to do this.

        // find the data explorer top components
        Collection<? extends DataExplorer> dataExplorers = Lookup.getDefault().lookupAll(DataExplorer.class);
        for (DataExplorer de : dataExplorers) {
            TopComponent explorerWin = de.getTopComponent();
            Mode m = WindowManager.getDefault().findMode("explorer");
            if (m != null) {
                m.dockInto(explorerWin); // redock into the explorer mode
            } else {
                logger.log(Level.WARNING, "Could not find explorer mode and dock explorer window");
            }
            explorerWin.open(); // open that top component
        }

        // find the data content top component
        DataContent dc = Lookup.getDefault().lookup(DataContent.class);
        TopComponent contentWin = dc.getTopComponent();
        Mode m = WindowManager.getDefault().findMode("output");
        if (m != null) {
            m.dockInto(contentWin); // redock into the output mode
        } else {
            logger.log(Level.WARNING, "Could not find output mode and dock content window");
        }

        contentWin.open(); // open that top component
    }

    /**
     * Closes all TopComponent windows that needed ({@link DataExplorer}, {@link DataResult}, and
     * {@link DataContent})
     */
    public static void closeCoreWindows() {
        WindowManager wm = WindowManager.getDefault();
        Iterator<? extends Mode> iter = wm.getModes().iterator();

        while (iter.hasNext()) {
            Mode mode = iter.next();
            for (TopComponent tc : mode.getTopComponents()) {
                tc.close();
            }
        }
    }
}
