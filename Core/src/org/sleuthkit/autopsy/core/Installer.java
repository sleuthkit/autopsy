/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import com.sun.jna.platform.win32.Kernel32;
import java.awt.Cursor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Handler;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.IngestRunningCheck;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.core.UserPreferences.SETTINGS_PROPERTIES;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Wrapper over Installers in packages in Core module. This is the main
 * registered installer in the MANIFEST.MF.
 */
public class Installer extends ModuleInstall {

    private static final long serialVersionUID = 1L;

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
                addGstreamerPathsToEnv();

                //Note: if shipping with a different CRT version, this will only print a warning
                //and try to use linker mechanism to find the correct versions of libs.
                //We should update this if we officially switch to a new version of CRT/compiler
                System.loadLibrary("api-ms-win-core-console-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-datetime-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-debug-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-errorhandling-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-file-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-file-l1-2-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-file-l2-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-handle-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-heap-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-interlocked-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-libraryloader-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-localization-l1-2-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-memory-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-namedpipe-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-processenvironment-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-processthreads-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-processthreads-l1-1-1"); //NON-NLS
                System.loadLibrary("api-ms-win-core-profile-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-rtlsupport-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-string-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-synch-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-synch-l1-2-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-sysinfo-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-timezone-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-core-util-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-conio-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-convert-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-environment-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-filesystem-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-heap-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-locale-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-math-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-multibyte-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-private-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-process-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-runtime-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-stdio-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-string-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-time-l1-1-0"); //NON-NLS
                System.loadLibrary("api-ms-win-crt-utility-l1-1-0"); //NON-NLS

                System.loadLibrary("ucrtbase"); //NON-NLS
                System.loadLibrary("vcruntime140"); //NON-NLS
                System.loadLibrary("msvcp140"); //NON-NLS

                logger.log(Level.INFO, "Visual C Runtime libraries loaded"); //NON-NLS
            } catch (UnsatisfiedLinkError e) {
                logger.log(Level.SEVERE, "Error loading Visual C Runtime libraries, ", e); //NON-NLS
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

            /*
             * PostgreSQL
             */
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

        // Prevent the Autopsy UI from shrinking on high DPI displays
        System.setProperty("sun.java2d.dpiaware", "false");
        System.setProperty("prism.allowhidpi", "false");

        // Update existing configuration in case of unsupported settings
        updateConfig();

        packageInstallers = new ArrayList<>();
        packageInstallers.add(org.sleuthkit.autopsy.coreutils.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.corecomponents.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.datamodel.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.ingest.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.centralrepository.eventlisteners.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.healthmonitor.Installer.getDefault());
    }

    /**
     * If the mode in the configuration file is 'REVIEW' (2, now invalid), this
     * method will set it to 'STANDALONE' (0) and disable auto ingest.
     */
    private void updateConfig() {
        String mode = ModuleSettings.getConfigSetting(SETTINGS_PROPERTIES, "AutopsyMode");
        if (mode != null) {
            int ordinal = Integer.parseInt(mode);
            if (ordinal > 1) {
                UserPreferences.setMode(UserPreferences.SelectedMode.STANDALONE);
                ModuleSettings.setConfigSetting(UserPreferences.SETTINGS_PROPERTIES, "JoinAutoModeCluster", Boolean.toString(false));
            }
        }
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
            JFXPanel panel = new JFXPanel();
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

    /**
     * Add the Gstreamer bin and lib paths to the PATH environment variable so
     * that the correct plugins and libraries are found when Gstreamer is
     * initialized later.
     */
    private static void addGstreamerPathsToEnv() {
        Path gstreamerPath = InstalledFileLocator.getDefault().locate("gstreamer", Installer.class.getPackage().getName(), false).toPath();

        if (gstreamerPath == null) {
            logger.log(Level.SEVERE, "Failed to find GStreamer.");
        } else {
            String arch = "x86_64";
            if (!PlatformUtil.is64BitJVM()) {
                arch = "x86";
            }

            Path gstreamerBasePath = Paths.get(gstreamerPath.toString(), "1.0", arch);
            Path gstreamerBinPath = Paths.get(gstreamerBasePath.toString(), "bin");
            Path gstreamerLibPath = Paths.get(gstreamerBasePath.toString(), "lib", "gstreamer-1.0");

            // Update the PATH environment variable to contain the GStreamer
            // lib and bin paths.
            Kernel32 k32 = Kernel32.INSTANCE;
            String path = System.getenv("PATH");
            if (StringUtils.isBlank(path)) {
                k32.SetEnvironmentVariable("PATH", gstreamerLibPath.toString());
            } else {
                /*
                 * Note that we *prepend* the paths so that the Gstreamer
                 * binaries associated with the current release are found rather
                 * than binaries associated with an earlier version of Autopsy.
                 */
                k32.SetEnvironmentVariable("PATH", gstreamerBinPath.toString() + File.pathSeparator + gstreamerLibPath.toString() + path);
            }
        }
    }

    /**
     * Make a folder in the config directory for object detection classifiers if one does not
     * exist.
     */
    private static void ensureClassifierFolderExists() {
        File objectDetectionClassifierDir = new File(PlatformUtil.getObjectDetectionClassifierPath());
        objectDetectionClassifierDir.mkdir();
    }

    /**
     * Make a folder in the config directory for Python Modules if one does not
     * exist.
     */
    private static void ensurePythonModulesFolderExists() {
        File pythonModulesDir = new File(PlatformUtil.getUserPythonModulesPath());
        pythonModulesDir.mkdir();
    }

    /**
     * Make a folder in the config directory for Ocr Language Packs if one does
     * not exist.
     */
    private static void ensureOcrLanguagePacksFolderExists() {
        File ocrLanguagePacksDir = new File(PlatformUtil.getOcrLanguagePacksPath());
        boolean createDirectory = ocrLanguagePacksDir.mkdir();

        //If the directory did not exist, copy the tessdata folder over so we 
        //support english.
        if (createDirectory) {
            File tessdataDir = InstalledFileLocator.getDefault().locate(
                    "Tesseract-OCR/tessdata", Installer.class.getPackage().getName(), false);
            try {
                FileUtils.copyDirectory(tessdataDir, ocrLanguagePacksDir);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Copying over default language packs for Tesseract failed.", ex);
            }
        }
    }

    @Override
    public void restored() {
        super.restored();
        ensurePythonModulesFolderExists();
        ensureClassifierFolderExists();
        ensureOcrLanguagePacksFolderExists();
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
            } catch (IllegalStateException e) {
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

    @NbBundle.Messages({
        "Installer.closing.confirmationDialog.title=Ingest is Running",
        "Installer.closing.confirmationDialog.message=Ingest is running, are you sure you want to exit?",
        "# {0} - exception message", "Installer.closing.messageBox.caseCloseExceptionMessage=Error closing case: {0}"
    })
    @Override
    public boolean closing() {
        if (IngestRunningCheck.checkAndConfirmProceed(Bundle.Installer_closing_confirmationDialog_title(), Bundle.Installer_closing_confirmationDialog_message())) {
            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            FutureTask<Void> future = new FutureTask<>(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    Case.closeCurrentCase();
                    return null;
                }
            });
            Thread thread = new Thread(future);
            thread.start();
            try {
                future.get();
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "Unexpected interrupt closing the current case", ex);
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, "Error closing the current case", ex);
                MessageNotifyUtil.Message.error(Bundle.Installer_closing_messageBox_caseCloseExceptionMessage(ex.getMessage()));
            } finally {
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
            return true;
        } else {
            return false;
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
