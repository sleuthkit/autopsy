/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.io.IOException;
import java.util.logging.Level;
import org.openide.modules.ModuleInstall;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Installer for data source summary that caches geolocation data.
 */
public final class Installer extends ModuleInstall {

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
    }

    @Override
    public void restored() {
        GeolocationSummary summary = GeolocationSummary.getInstance();
        try {
            summary.load();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to load geolocation summary data.", ex);
        }
    }
}
