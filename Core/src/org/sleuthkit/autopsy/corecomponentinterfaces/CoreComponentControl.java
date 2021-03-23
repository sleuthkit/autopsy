/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-18 Basis Technology Corp.
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
import java.util.logging.Level;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.corecomponents.DataContentTopComponent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;

/**
 * Responsible for opening and closing the core windows when a case is opened
 * and closed.
 *
 */
final public class CoreComponentControl {

    private static final Logger logger = Logger.getLogger(CoreComponentControl.class.getName());
    @NbBundle.Messages("CoreComponentControl.CTL_DirectoryTreeTopComponent=Directory Tree")
    private static final String DIRECTORY_TREE = Bundle.CoreComponentControl_CTL_DirectoryTreeTopComponent();
    @NbBundle.Messages("CoreComponentControl.CTL_FavoritesTopComponent=Favorites")
    private static final String FAVORITES = Bundle.CoreComponentControl_CTL_FavoritesTopComponent();

    private CoreComponentControl() {
    }

    /**
     * Opens all TopComponent windows that are needed
     * ({@link DataExplorer}, {@link DataResult}, and {@link DataContent})
     */
    public static void openCoreWindows() {
        // preload UI components (JIRA-7345). This only takes place the first time Autopsy opens a case. 
        DirectoryTreeTopComponent dtc = DirectoryTreeTopComponent.findInstance();

        // find the data explorer top components
        Collection<? extends DataExplorer> dataExplorers = Lookup.getDefault().lookupAll(DataExplorer.class);
        for (DataExplorer de : dataExplorers) {
            TopComponent explorerWin = de.getTopComponent();
            Mode explorerMode = WindowManager.getDefault().findMode("explorer"); //NON-NLS
            if (explorerMode == null) {
                logger.log(Level.WARNING, "Could not find explorer mode and dock explorer window"); //NON-NLS
            } else {
                explorerMode.dockInto(explorerWin); // redock into the explorer mode
            }
            explorerWin.open(); // open that top component
        }

        // find the data content top component
        TopComponent contentWin = DataContentTopComponent.findInstance();
        Mode outputMode = WindowManager.getDefault().findMode("output"); //NON-NLS
        if (outputMode == null) {
            logger.log(Level.WARNING, "Could not find output mode and dock content window"); //NON-NLS
        } else {
            outputMode.dockInto(contentWin); // redock into the output mode
        }

        contentWin.open(); // open that top component
    }

    /**
     * Closes all TopComponent windows that needed
     * ({@link DataExplorer}, {@link DataResult}, and {@link DataContent}).
     *
     * Note: The DataContent Top Component must be closed before the Directory
     * Tree and Favorites Top Components. Otherwise a NullPointerException will
     * be thrown from JFXPanel.
     */
    public static void closeCoreWindows() {
        TopComponent directoryTree = null;
        TopComponent favorites = null;
        final WindowManager windowManager = WindowManager.getDefault();
        
        // Set the UI selections to null before closing the top components.
        // Otherwise it may experience errors trying to load data for the closed case.
        for (Mode mode : windowManager.getModes()) {
            for (TopComponent tc : windowManager.getOpenedTopComponents(mode)) {
                if(tc instanceof DataContent) {
                    ((DataContent) tc).setNode(null);
                } else if(tc instanceof DataResult) {
                    ((DataResult) tc).setNode(null);
                }
            }
        }
        
        for (Mode mode : windowManager.getModes()) {
            
            for (TopComponent tc : windowManager.getOpenedTopComponents(mode)) {
                String tcName = tc.getName();

                if (tcName == null) {
                    logger.log(Level.INFO, "tcName was null"); //NON-NLS
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
