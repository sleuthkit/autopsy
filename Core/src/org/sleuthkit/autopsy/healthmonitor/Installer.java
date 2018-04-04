/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.healthmonitor;

import java.util.logging.Level;
import org.openide.modules.ModuleInstall;
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
            ServicesHealthMonitor.startUp();
        } catch (HealthMonitorException ex) {
            logger.log(Level.SEVERE, "Error starting health services monitor", ex);
        }
    }

    @Override
    public boolean closing() {
        //platform about to close
        ServicesHealthMonitor.close();

        return true;
    }

    @Override
    public void uninstalled() {
        //module is being unloaded
        ServicesHealthMonitor.close();

    }
}