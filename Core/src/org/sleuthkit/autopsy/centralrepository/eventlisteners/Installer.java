/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.centralrepository.eventlisteners;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * Install event listeners during module initialization
 */
public class Installer extends ModuleInstall {

    private static final Logger LOGGER = Logger.getLogger(Installer.class.getName());
    private static final long serialVersionUID = 1L;
    private final CaseEventListener pcl = new CaseEventListener();
    private final IngestEventsListener ieListener = new IngestEventsListener();

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

    @NbBundle.Messages({
        "Installer.initialCreateSqlite.title=Enable Central Repository?",
        "Installer.initialCreateSqlite.messageHeader=The Central Repository is not enabled. Would you like to enable it?",
        "Installer.initialCreateSqlite.messageDesc=It will store information about all hashes and identifiers that you process. " +
            "You can use this to ignore previously seen files and make connections between cases."
    })
    @Override
    public void restored() {
        Case.addPropertyChangeListener(pcl);
        ieListener.installListeners();

        
        Map<String, String> centralRepoSettings = ModuleSettings.getConfigSettings("CentralRepository");
        String initializedStr = centralRepoSettings.get("initialized");
        
        // check to see if the repo has been initialized asking to setup cr
        boolean initialized = Boolean.parseBoolean(initializedStr);
        
        // if it hasn't received that flag, check for a previous install where cr is already setup
        if (!initialized) {
            boolean prevRepo = Boolean.parseBoolean(centralRepoSettings.get("db.useCentralRepo"));
            // if it has been previously set up and is in use, mark as previously initialized and save the settings
            if (prevRepo) {
                initialized = true;
                ModuleSettings.setConfigSetting("CentralRepository", "initialized", "true");
            }
        }

        // if central repository hasn't been previously initialized, initialize it
        if (!initialized) {
            // if running with a GUI, prompt the user
            if (RuntimeProperties.runningWithGUI()) {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            String dialogText = 
                                "<html><body>" + 
                                    "<div style='width: 400px;'>" +
                                        "<p>" + NbBundle.getMessage(this.getClass(), "Installer.initialCreateSqlite.messageHeader") + "</p>" +
                                        "<p style='margin-top: 10px'>" + NbBundle.getMessage(this.getClass(), "Installer.initialCreateSqlite.messageDesc") + "</p>" +
                                    "</div>" +
                                "</body></html>";

                            if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(),
                                    dialogText,
                                    NbBundle.getMessage(this.getClass(), "Installer.initialCreateSqlite.title"),
                                    JOptionPane.YES_NO_OPTION)) {

                                setupDefaultSqlite();
                            }
                        } catch (CentralRepoException ex) {
                            LOGGER.log(Level.SEVERE, "There was an error while initializing the central repository database", ex);

                            reportUpgradeError(ex);
                        }
                    });
                } catch (InterruptedException | InvocationTargetException ex) {
                    LOGGER.log(Level.SEVERE, "There was an error while running the swing utility invoke later while creating the central repository database", ex);
                }
            } // if no GUI, just initialize
            else {
                try {
                    setupDefaultSqlite();
                } catch (CentralRepoException ex) {
                     LOGGER.log(Level.SEVERE, "There was an error while initializing the central repository database", ex);

                    reportUpgradeError(ex);
                }
            }

            ModuleSettings.setConfigSetting("CentralRepository", "initialized", "true");
        } 
        
        // now run regular module startup code
        try {
            CentralRepoDbManager.upgradeDatabase();
        } catch (CentralRepoException ex) {
            LOGGER.log(Level.SEVERE, "There was an error while upgrading the central repository database", ex);
            if (RuntimeProperties.runningWithGUI()) {
                reportUpgradeError(ex);
            }
        }
    }

    private void setupDefaultSqlite() throws CentralRepoException {
        CentralRepoDbManager manager = new CentralRepoDbManager();
        manager.setupDefaultSqliteDb();
    }

    @NbBundle.Messages({ "Installer.centralRepoUpgradeFailed.title=Central repository disabled" })
    private void reportUpgradeError(CentralRepoException ex) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                JOptionPane.showMessageDialog(null,
                    ex.getUserMessage(),
                    NbBundle.getMessage(this.getClass(),
                        "Installer.centralRepoUpgradeFailed.title"),
                    JOptionPane.ERROR_MESSAGE);
            });
        } catch (InterruptedException | InvocationTargetException e) {
            LOGGER.log(Level.WARNING, e.getMessage(), e);
        }

    }

    @Override
    public boolean closing() {
        //platform about to close

        return true;
    }

    @Override
    public void uninstalled() {
        //module is being unloaded

        Case.removePropertyChangeListener(pcl);
        pcl.shutdown();
        ieListener.shutdown();
        ieListener.uninstallListeners();

        // TODO: remove thread pool
    }
}
