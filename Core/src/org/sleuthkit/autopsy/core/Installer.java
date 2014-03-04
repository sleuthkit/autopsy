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
package org.sleuthkit.autopsy.core;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.modules.ModuleInstall;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Wrapper over Installers in packages in Core module This is the main
 * registered installer in the MANIFEST.MF
 */
public class Installer extends ModuleInstall {

    private List<ModuleInstall> packageInstallers;
    private static final Logger logger = Logger.getLogger(Installer.class.getName());
    private static volatile boolean javaFxInit = false;

    static {
        loadDynLibraries();
    }
    
    private static void loadDynLibraries() {
        /* On Windows, we distribute dlls that libtsk_jni depend on.
         * If libtsk_jni tries to load them, they will not be found by
         * Windows because they are in special NetBeans folders. So, we
         * manually load them from within Autopsy so that they are found 
         * via the NetBeans loading setup.  These are copied by the build
         * script when making the ZIP file.  In a development environment
         * they will need to be loaded from standard places in your system.
         * 
         * On non-Windows platforms, we assume the dependncies are all installed
         * and loadable (i.e. a 'make install' was done). 
         */
        if (PlatformUtil.isWindowsOS()) {
            try {
                //Note: if shipping with a different CRT version, this will only print a warning
                //and try to use linker mechanism to find the correct versions of libs.
                //We should update this if we officially switch to a new version of CRT/compiler
                System.loadLibrary("msvcr100");
                System.loadLibrary("msvcp100");
                logger.log(Level.INFO, "MS CRT libraries loaded");
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading ms crt libraries, ", e);
            }

            try {
               System.loadLibrary("zlib");
               logger.log(Level.INFO, "ZLIB library loaded loaded");
            } catch (UnsatisfiedLinkError e) {
               logger.log(Level.SEVERE, "Error loading ZLIB library, ", e);
            }

            try {
               System.loadLibrary("libewf");
               logger.log(Level.INFO, "EWF library loaded");
            } catch (UnsatisfiedLinkError e) {
               logger.log(Level.SEVERE, "Error loading EWF library, ", e);
            }
         }
     }
    
    public Installer() {
        logger.log(Level.INFO, "core installer created");
        javaFxInit = false;
        packageInstallers = new ArrayList<ModuleInstall>();

        packageInstallers.add(org.sleuthkit.autopsy.coreutils.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.corecomponents.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.datamodel.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.ingest.Installer.getDefault());

    }

    /**
     * Check if JavaFx initialized
     * @return false if java fx not initialized (classes coult not load), true if initialized
     */
    public static boolean isJavaFxInited() {
        return javaFxInit;
    }
    
    private static void initJavaFx() {
        //initialize java fx if exists
        System.setProperty("javafx.macosx.embedded", "true");
        try {
            // Creating a JFXPanel initializes JavaFX
            new JFXPanel();
            Platform.setImplicitExit(false);
            javaFxInit = true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | Exception e) {
            //in case javafx not present
            final String msg = NbBundle.getMessage(Installer.class, "Installer.errorInitJavafx.msg");
            final String details = NbBundle.getMessage(Installer.class, "Installer.errorInitJavafx.details");
            logger.log(Level.SEVERE, msg
		       + details, e);

            WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
                @Override
                public void run() {
                    MessageNotifyUtil.Notify.error(msg, details);
                }
            });
        }
    }

    @Override
    public void restored() {
        super.restored();

        logger.log(Level.INFO, "restored()");

        initJavaFx();

        for (ModuleInstall mi : packageInstallers) {
            logger.log(Level.INFO, mi.getClass().getName() + " restored()");
            try {
                mi.restored();
            } catch (Exception e) {
                logger.log(Level.WARNING, "", e);
            }
        }

    }

    @Override
    public void validate() throws IllegalStateException {
        super.validate();

        logger.log(Level.INFO, "validate()");
        for (ModuleInstall mi : packageInstallers) {
            logger.log(Level.INFO, mi.getClass().getName() + " validate()");
            try {
                mi.validate();
            } catch (Exception e) {
                logger.log(Level.WARNING, "", e);
            }
        }
    }

    @Override
    public void uninstalled() {
        super.uninstalled();

        logger.log(Level.INFO, "uninstalled()");

        for (ModuleInstall mi : packageInstallers) {
            logger.log(Level.INFO, mi.getClass().getName() + " uninstalled()");
            try {
                mi.uninstalled();
            } catch (Exception e) {
                logger.log(Level.WARNING, "", e);
            }
        }



    }

    @Override
    public void close() {
        super.close();

        logger.log(Level.INFO, "close()");

        //exit JavaFx plat
        if (javaFxInit) {
            Platform.exit();
        }

        for (ModuleInstall mi : packageInstallers) {
            logger.log(Level.INFO, mi.getClass().getName() + " close()");
            try {
                mi.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "", e);
            }
        }
    }
}
