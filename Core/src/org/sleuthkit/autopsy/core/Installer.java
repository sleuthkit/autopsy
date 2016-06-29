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
package org.sleuthkit.autopsy.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Wrapper over Installers in packages in Core module. This is the main
 * registered installer in the MANIFEST.MF.
 */
public class Installer extends ModuleInstall {

    private final List<ModuleInstall> packageInstallers;
    private static final Logger logger = Logger.getLogger(Installer.class.getName());
    private static volatile boolean javaFxInit = false;

    static {
        loadDynLibraries();
    }

    private static void loadDynLibraries() {
        /*
         * On Windows, we distribute dlls that libtsk_jni depend on. If
         * libtsk_jni tries to load them, they will not be found by Windows
         * because they are in special NetBeans folders. So, we manually load
         * them from within Autopsy so that they are found via the NetBeans
         * loading setup. These are copied by the build script when making the
         * ZIP file. In a development environment they will need to be loaded
         * from standard places in your system.
         *
         * On non-Windows platforms, we assume the dependncies are all installed
         * and loadable (i.e. a 'make install' was done).
         */
        if (PlatformUtil.isWindowsOS()) {
            try {
                //Note: if shipping with a different CRT version, this will only print a warning
                //and try to use linker mechanism to find the correct versions of libs.
                //We should update this if we officially switch to a new version of CRT/compiler
                System.loadLibrary("msvcr100"); //NON-NLS
                System.loadLibrary("msvcp100"); //NON-NLS

                logger.log(Level.INFO, "MSVCR100 and MSVCP100 libraries loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading MSVCR100 and MSVCP100 libraries, ", e); //NON-NLS
            }

            try {
                System.loadLibrary("zlib"); //NON-NLS
                logger.log(Level.INFO, "ZLIB library loaded loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading ZLIB library, ", e); //NON-NLS
            }

            try {
                System.loadLibrary("libewf"); //NON-NLS
                logger.log(Level.INFO, "EWF library loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading EWF library, ", e); //NON-NLS
            }

            try {
                System.loadLibrary("libvmdk"); //NON-NLS
                logger.log(Level.INFO, "VMDK library loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading VMDK library, ", e); //NON-NLS
            }

            try {
                System.loadLibrary("libvhdi"); //NON-NLS
                logger.log(Level.INFO, "VHDI library loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading VHDI library, ", e); //NON-NLS
            }

            /* PostgreSQL */
            try {
                System.loadLibrary("msvcr120"); //NON-NLS
                logger.log(Level.INFO, "MSVCR 120 library loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading MSVCR120 library, ", e); //NON-NLS
            }

            try {
                System.loadLibrary("libeay32"); //NON-NLS
                logger.log(Level.INFO, "LIBEAY32 library loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading LIBEAY32 library, ", e); //NON-NLS
            }

            try {
                System.loadLibrary("ssleay32"); //NON-NLS
                logger.log(Level.INFO, "SSLEAY32 library loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading SSLEAY32 library, ", e); //NON-NLS
            }

            try {
                System.loadLibrary("libiconv-2"); //NON-NLS
                logger.log(Level.INFO, "libiconv-2 library loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading libiconv-2 library, ", e); //NON-NLS
            }

            try {
                System.loadLibrary("libintl-8"); //NON-NLS
                logger.log(Level.INFO, "libintl-8 library loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading libintl-8 library, ", e); //NON-NLS
            }
            
            try {
                System.loadLibrary("libpq"); //NON-NLS
                logger.log(Level.INFO, "LIBPQ library loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading LIBPQ library, ", e); //NON-NLS
            }
        }
    }

    public Installer() {
        logger.log(Level.INFO, "core installer created"); //NON-NLS
        javaFxInit = false;
        packageInstallers = new ArrayList<>();
        packageInstallers.add(org.sleuthkit.autopsy.coreutils.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.corecomponents.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.datamodel.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.ingest.Installer.getDefault());
    }

    /**
     * Check if JavaFx initialized
     *
     * @return false if java fx not initialized (classes could not load), true
     *         if initialized
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

    private static void ensurePythonModulesFolderExists() {
        File pythonModulesDir = new File(PlatformUtil.getUserPythonModulesPath());
        pythonModulesDir.mkdir();
    }

    @Override
    public void restored() {
        super.restored();
        ensurePythonModulesFolderExists();
        initJavaFx();
        for (ModuleInstall mi : packageInstallers) {
            try {
                mi.restored();
                logger.log(Level.INFO, "{0} restore succeeded", mi.getClass().getName()); //NON-NLS
            } catch (Exception e) {
                String msg = mi.getClass().getName() + " restore failed"; //NON-NLS
                logger.log(Level.WARNING, msg, e);
            }
        }
        logger.log(Level.INFO, "Autopsy Core restore completed"); //NON-NLS        
    }

    @Override
    public void validate() throws IllegalStateException {
        super.validate();

        logger.log(Level.INFO, "validate()"); //NON-NLS
        for (ModuleInstall mi : packageInstallers) {
            logger.log(Level.INFO, "{0} validate()", mi.getClass().getName()); //NON-NLS
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

        logger.log(Level.INFO, "uninstalled()"); //NON-NLS

        for (ModuleInstall mi : packageInstallers) {
            logger.log(Level.INFO, "{0} uninstalled()", mi.getClass().getName()); //NON-NLS
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

        logger.log(Level.INFO, "close()"); //NON-NLS

        //exit JavaFx plat
        if (javaFxInit) {
            Platform.exit();
        }

        for (ModuleInstall mi : packageInstallers) {
            logger.log(Level.INFO, "{0} close()", mi.getClass().getName()); //NON-NLS
            try {
                mi.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "", e);
            }
        }
        for (Handler h : logger.getHandlers()) {
            h.close();   //must call h.close or a .LCK file will remain.
        }
    }
}
