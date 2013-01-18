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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.modules.ModuleInstall;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Wrapper over Installers in packages in Core module This is the main
 * registered installer in the MANIFEST.MF
 */
public class Installer extends ModuleInstall {

    private List<ModuleInstall> packageInstallers;
    private static final Logger logger = Logger.getLogger(Installer.class.getName());

    static {
        loadDynLibraries();
    }

    private static void loadDynLibraries() {
        if (PlatformUtil.isWindowsOS()) {
            try {
                //on windows force loading ms crt dependencies first
                //in case linker can't find them on some systems
                //Note: if shipping with a different CRT version, this will only print a warning
                //and try to use linker mechanism to find the correct versions of libs.
                //We should update this if we officially switch to a new version of CRT/compiler
                System.loadLibrary("msvcr100");
                System.loadLibrary("msvcp100");
                logger.log(Level.INFO, "MS CRT libraries loaded");
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading ms crt libraries, ", e);
            }
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

        /* We should rename the Windows dll, to remove the lib prefix.
         */
        try {
            String tskLibName = null;
            if (PlatformUtil.isWindowsOS()) {
                tskLibName = "libtsk_jni";
            } else {
                tskLibName = "tsk_jni";
            }
            System.loadLibrary(tskLibName);
            logger.log(Level.INFO, "TSK_JNI library loaded");
        } catch (UnsatisfiedLinkError e) {
            logger.log(Level.SEVERE, "Error loading tsk_jni library", e);
        }
    }

    public Installer() {
        packageInstallers = new ArrayList<ModuleInstall>();

        packageInstallers.add(org.sleuthkit.autopsy.coreutils.Installer.getDefault() );
        packageInstallers.add(org.sleuthkit.autopsy.corecomponents.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.datamodel.Installer.getDefault() );
        packageInstallers.add(org.sleuthkit.autopsy.ingest.Installer.getDefault() );

    }

    @Override
    public void restored() {
        super.restored();

        logger.log(Level.INFO, "restored()");
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
