/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.sleuthkit.autopsy.centralrepository.eventlisteners;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.OnShowing;
import org.sleuthkit.autopsy.centralrepository.CentralRepoSettings;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbChoice;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 *
 * @author gregd
 */
@OnShowing
public class OnUIShowing implements Runnable {
    private static final Logger logger = Logger.getLogger(OnUIShowing.class.getName());
    
    @Override
    public void run() {
        setupDefaultCentralRepository();
    }
    
    /**
     * Checks if the central repository has been set up and configured. If not,
     * does the set up unconditionally. If the application is running with a
     * GUI, a notification will be displayed to the user if the mode is RELEASE
     * (in other words, developers are exempt from seeing the notification).
     */
    private void setupDefaultCentralRepository() {
        Map<String, String> centralRepoSettings = ModuleSettings.getConfigSettings(CentralRepoSettings.getInstance().getModuleSettingsKey());
        String initializedStr = centralRepoSettings.get("initialized");

        // check to see if the repo has been initialized asking to setup cr
        boolean initialized = Boolean.parseBoolean(initializedStr);

        // if it hasn't received that flag, check for a previous install where cr is already setup
        if (!initialized) {
            boolean prevRepo = Boolean.parseBoolean(centralRepoSettings.get("db.useCentralRepo"));
            // if it has been previously set up and is in use, mark as previously initialized and save the settings
            if (prevRepo) {
                initialized = true;
                ModuleSettings.setConfigSetting(CentralRepoSettings.getInstance().getModuleSettingsKey(), "initialized", "true");
            }
        }
        
        if(initialized) {
            return; // Nothing to do
        }

        if (CentralRepositoryNotificationDialog.shouldDisplay()) {
            CentralRepositoryNotificationDialog.display();
        }

        try {
            CentralRepoDbManager manager = new CentralRepoDbManager();
            if (UserPreferences.getIsMultiUserModeEnabled()) {
                // Set up using existing multi-user settings.
                manager.setupPostgresDb(CentralRepoDbChoice.POSTGRESQL_MULTIUSER);
            } else {
                manager.setupDefaultSqliteDb();
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "There was an error while initializing the central repository database", ex);

            doMessageBoxIfRunningInGUI(ex);
        }

        ModuleSettings.setConfigSetting(CentralRepoSettings.getInstance().getModuleSettingsKey(), "initialized", "true");
    }
    
    /**
     * Display a central repository exception in a message box if running with a
     * GUI.
     *
     * @param ex The exception.
     */
    @NbBundle.Messages({"Installer.centralRepoUpgradeFailed.title=Central repository disabled"})
    private void doMessageBoxIfRunningInGUI(CentralRepoException ex) {
        if (RuntimeProperties.runningWithGUI()) {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    JOptionPane.showMessageDialog(null,
                            ex.getUserMessage(),
                            NbBundle.getMessage(this.getClass(), "Installer.centralRepoUpgradeFailed.title"),
                            JOptionPane.ERROR_MESSAGE);
                });
            } catch (InterruptedException | InvocationTargetException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
    }


}
