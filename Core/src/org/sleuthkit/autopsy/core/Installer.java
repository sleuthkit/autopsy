/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
import javax.imageio.ImageIO;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.IngestRunningCheck;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.core.UserPreferences.SETTINGS_PROPERTIES;
import org.sleuthkit.autopsy.corelibs.OpenCvLoader;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.autopsy.python.JythonModuleLoader;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;

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
        
        // This call was moved from MediaViewImagePanel so that it is 
        // not called during top level component construction.
        ImageIO.scanForPlugins();
        
        // This will cause OpenCvLoader to load its library instead of 
        OpenCvLoader.openCvIsLoaded();
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
            
            // Only attempt to load OpenSSL if we're in 64 bit mode
            if(System.getProperty("sun.arch.data.model").contains("64")) {
                // libcrypto must be loaded before libssl to make sure it's the correct version
                try {
                    System.loadLibrary("libcrypto-1_1-x64"); //NON-NLS
                    logger.log(Level.INFO, "Crypto library loaded"); //NON-NLS
                } catch (UnsatisfiedLinkError e) {
                    logger.log(Level.SEVERE, "Error loading Crypto library, ", e); //NON-NLS
                }  

                try {
                    System.loadLibrary("libssl-1_1-x64"); //NON-NLS
                    logger.log(Level.INFO, "OpenSSL library loaded"); //NON-NLS
                } catch (UnsatisfiedLinkError e) {
                    logger.log(Level.SEVERE, "Error loading OpenSSL library, ", e); //NON-NLS
                }
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
        UserPreferences.updateConfig();
        updateConfig();

        packageInstallers = new ArrayList<>();
        packageInstallers.add(org.sleuthkit.autopsy.coreutils.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.corecomponents.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.datamodel.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.ingest.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.centralrepository.eventlisteners.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.healthmonitor.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.casemodule.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.modules.hashdatabase.infrastructure.Installer.getDefault());
        packageInstallers.add(org.sleuthkit.autopsy.report.infrastructure.Installer.getDefault());

        /**
         * This is a temporary workaround for the following bug in Tika that
         * results in a null pointer exception when used from the Image Gallery.
         * The current hypothesis is that the Image Gallery is cancelling the
         * thumbnail task that Tika initialization is happening on. Once the
         * Tika issue has been fixed we should no longer need this workaround.
         *
         * https://issues.apache.org/jira/browse/TIKA-2896
         */
        try {
            FileTypeDetector fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            logger.log(Level.SEVERE, "Failed to load file type detector.", ex);
        }
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
            
            // Due to a lingering issue https://bugs.openjdk.org/browse/JDK-8223377 where glass.dll from java 8 gets loaded instead of the java 17 one.
            String javaLibraryPath = "java.library.path";
            String jvmBinPathStr = Paths.get(System.getProperty("java.home"), "bin").toAbsolutePath().toString();
            String path = System.getProperty(javaLibraryPath);
            System.setProperty(javaLibraryPath, StringUtils.isBlank(path) ? jvmBinPathStr : jvmBinPathStr + File.pathSeparator + path);
            
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

    /**
     * Add the Gstreamer bin and lib paths to the PATH environment variable so
     * that the correct plugins and libraries are found when Gstreamer is
     * initialized later.
     */
    private static void addGstreamerPathsToEnv() {
        if (System.getProperty("jna.nosys") == null) {
            System.setProperty("jna.nosys", "true");
        }

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
     * Make a folder in the config directory for object detection classifiers if
     * one does not exist.
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
        initializeSevenZip();
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
        preloadJython();
        preloadTranslationServices();
    }

    /**
     * Initializes 7zip-java bindings. We are performing initialization once
     * because we encountered issues related to file locking when initialization
     * was performed closer to where the bindings are used. See JIRA-6528.
     */
    private static void initializeSevenZip() {
        try {
            SevenZip.initSevenZipFromPlatformJAR();
            logger.log(Level.INFO, "7zip-java bindings loaded"); //NON-NLS
        } catch (SevenZipNativeInitializationException e) {
            logger.log(Level.SEVERE, "Error loading 7zip-java bindings", e); //NON-NLS
        }
    }

    /**
     * Runs an initial load of the Jython modules to speed up subsequent loads.
     */
    private static void preloadJython() {
        Runnable loader = () -> {
            try {
                JythonModuleLoader.getIngestModuleFactories();
                JythonModuleLoader.getGeneralReportModules();
                JythonModuleLoader.getDataSourceProcessorModules();
            } catch (Exception ex) {
                // This is a firewall exception to ensure that any possible exception caused
                // by this initial load of the Jython modules are caught and logged.
                logger.log(Level.SEVERE, "There was an error while doing an initial load of python plugins.", ex);
            }

        };
        new Thread(loader).start();
    }
    
    /**
     * Runs an initial load of the translation services to speed up subsequent loads.
     */
    private static void preloadTranslationServices() {
        Runnable loader = () -> {
            try {
                TextTranslationService.getInstance();
            } catch (Exception ex) {
                // This is a firewall exception to ensure that any possible exception caused
                // by this initial load of the translation modules are caught and logged.
                logger.log(Level.SEVERE, "There was an error while doing an initial load of translation services.", ex);
            }
        };
        new Thread(loader).start();
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
