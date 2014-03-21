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
import java.util.Set;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.util.Lookup;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.corecomponents.DataContentTopComponent;

/**
 * Responsible for opening and closing the core windows when a case is opened and closed.
 *
 * @author jantonius
 */
public class CoreComponentControl {

    private static final Logger logger = Logger.getLogger(CoreComponentControl.class.getName());
    private static final String DIRECTORY_TREE = NbBundle.getMessage(CoreComponentControl.class,
                                                                     "CoreComponentControl.CTL_DirectoryTreeTopComponent");
    private static final String FAVORITES = NbBundle.getMessage(CoreComponentControl.class,
                                                                "CoreComponentControl.CTL_FavoritesTopComponent");

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
        TopComponent contentWin = DataContentTopComponent.findInstance();
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
     * {@link DataContent}).
     * 
     * Note: The DataContent Top Component must be closed before the Directory Tree
     * and Favorites Top Components. Otherwise a NullPointerException will be thrown
     * from JFXPanel.
     */
    public static void closeCoreWindows() {
        WindowManager wm = WindowManager.getDefault();
        Set<? extends Mode> modes = wm.getModes();
        Iterator<? extends Mode> iter = wm.getModes().iterator();


        TopComponent directoryTree = null;
        TopComponent favorites = null;
        String tcName = "";
        while (iter.hasNext()) {
            Mode mode = iter.next();
            for (TopComponent tc : mode.getTopComponents()) {
                tcName = tc.getName();
                if (tcName == null) {
                    logger.log(Level.INFO, "tcName was null");
                    tcName = "";
                }
                // switch requires constant strings, so converted to if/else.
                if (DIRECTORY_TREE.equals(tcName)) {
                    directoryTree = tc;
                } else if (FAVORITES.equals(tcName)) {
                    favorites = tc;
                } else {
                    tc.close();
                }
            }
        }
        
        if (directoryTree != null) {
            directoryTree.close();
        }
        if (favorites != null) {
            favorites.close();
        }
    }
}
