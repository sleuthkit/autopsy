/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.python;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.python.util.PythonInterpreter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.report.GeneralReportModule;

/**
 * Finds and loads Autopsy modules written using the Jython variant of the
 * Python scripting language.
 */
public final class JythonModuleLoader {

    private static final Logger logger = Logger.getLogger(JythonModuleLoader.class.getName());
    private static final String PYTHON_MODULE_FOLDERS_LIST = Paths.get(PlatformUtil.getUserConfigDirectory(), "IngestModuleSettings", "listOfPythonModules.settings").toAbsolutePath().toString();
    // maintain a private list of loaded modules (folders) and their last 'loading' time.
    // Check this list before reloading the modules.
    private static Map<String, Long> pythonModuleFolderList;

    /**
     * Get ingest module factories implemented using Jython.
     *
     * @return A list of objects that implement the IngestModuleFactory
     *         interface.
     */
    public static List<IngestModuleFactory> getIngestModuleFactories() {
        return getInterfaceImplementations(new IngestModuleFactoryDefFilter(), IngestModuleFactory.class);
    }

    /**
     * Get general report modules implemented using Jython.
     *
     * @return A list of objects that implement the GeneralReportModule
     *         interface.
     */
    public static List<GeneralReportModule> getGeneralReportModules() {
        return getInterfaceImplementations(new GeneralReportModuleDefFilter(), GeneralReportModule.class);
    }

    private static <T> List<T> getInterfaceImplementations(LineFilter filter, Class<T> interfaceClass) {
        List<T> objects = new ArrayList<>();
        Set<File> pythonModuleDirs = new HashSet<>();
        PythonInterpreter interpreter = new PythonInterpreter();

        // deserialize the list of python modules from the disk.
        if (new File(PYTHON_MODULE_FOLDERS_LIST).exists()) {
            // try deserializing if PYTHON_MODULE_FOLDERS_LIST exists. Else,
            // instantiate a new pythonModuleFolderList.
            try (FileInputStream fis = new FileInputStream(PYTHON_MODULE_FOLDERS_LIST); ObjectInputStream ois = new ObjectInputStream(fis)) {
                pythonModuleFolderList = (HashMap) ois.readObject();
            } catch (IOException | ClassNotFoundException ex) {
                logger.log(Level.INFO, "Unable to deserialize pythonModuleList from existing " + PYTHON_MODULE_FOLDERS_LIST + ". New pythonModuleList instantiated", ex); // NON-NLS
                pythonModuleFolderList = new HashMap<>();
            }
        } else {
            pythonModuleFolderList = new HashMap<>();
            logger.log(Level.INFO, "{0} does not exist. New pythonModuleList instantiated", PYTHON_MODULE_FOLDERS_LIST); // NON-NLS
        }

        // add python modules from 'autospy/build/cluster/InternalPythonModules' folder
        // which are copied from 'autopsy/*/release/InternalPythonModules' folders.
        for (File f : InstalledFileLocator.getDefault().locateAll("InternalPythonModules", JythonModuleLoader.class.getPackage().getName(), false)) {
            Collections.addAll(pythonModuleDirs, f.listFiles());
        }
        // add python modules from 'testuserdir/python_modules' folder
        Collections.addAll(pythonModuleDirs, new File(PlatformUtil.getUserPythonModulesPath()).listFiles());

        for (File file : pythonModuleDirs) {
            if (file.isDirectory()) {
                File[] pythonScripts = file.listFiles(new PythonScriptFileFilter());
                for (File script : pythonScripts) {
                    try {
                        Scanner fileScanner = new Scanner(script);
                        while (fileScanner.hasNextLine()) {
                            String line = fileScanner.nextLine();
                            if (line.startsWith("class ") && filter.accept(line)) { //NON-NLS
                                String className = line.substring(6, line.indexOf("("));
                                try {
                                    // check if ANY file in the module folder has changed.
                                    // Not only .py files.
                                    boolean reloadModule = hasModuleFolderChanged(file.getAbsolutePath(), file.listFiles());
                                    objects.add(createObjectFromScript(interpreter, script, className, interfaceClass, reloadModule));
                                    if (reloadModule) {
                                        MessageNotifyUtil.Notify.info(NbBundle.getMessage(JythonModuleLoader.class, "JythonModuleLoader.createObjectFromScript.reloadScript.title"),
                                                NbBundle.getMessage(JythonModuleLoader.class, "JythonModuleLoader.createObjectFromScript.reloadScript.msg", script.getParent())); // NON-NLS
                                    }
                                } catch (Exception ex) {
                                    logger.log(Level.SEVERE, String.format("Failed to load %s from %s", className, script.getAbsolutePath()), ex); //NON-NLS
                                    // NOTE: using ex.toString() because the current version is always returning null for ex.getMessage().
                                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                                            NbBundle.getMessage(JythonModuleLoader.class, "JythonModuleLoader.errorMessages.failedToLoadModule", className, ex.toString()),
                                            NotifyDescriptor.ERROR_MESSAGE));
                                }
                            }
                        }
                    } catch (FileNotFoundException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to open %s", script.getAbsolutePath()), ex); //NON-NLS
                        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                                NbBundle.getMessage(JythonModuleLoader.class, "JythonModuleLoader.errorMessages.failedToOpenModule", script.getAbsolutePath()),
                                NotifyDescriptor.ERROR_MESSAGE));
                    }
                }
                pythonModuleFolderList.put(file.getAbsolutePath(), System.currentTimeMillis());
            }
        }

        // serialize the list of python modules to the disk.
        try (FileOutputStream fos = new FileOutputStream(PYTHON_MODULE_FOLDERS_LIST); ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(pythonModuleFolderList);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error serializing pythonModuleList to the disk.", ex); // NON-NLS
        }

        return objects;
    }

    private static <T> T createObjectFromScript(PythonInterpreter interpreter, File script, String className, Class<T> interfaceClass, boolean reloadModule) {
        // Add the directory where the Python script resides to the Python
        // module search path to allow the script to use other scripts bundled
        // with it.
        interpreter.exec("import sys"); //NON-NLS
        String path = Matcher.quoteReplacement(script.getParent());
        interpreter.exec("sys.path.append('" + path + "')"); //NON-NLS
        String moduleName = script.getName().replaceAll("\\.py$", ""); //NON-NLS

        // reload the module so that the changes made to it can be loaded.
        interpreter.exec("import " + moduleName); //NON-NLS
        if (reloadModule) {
            interpreter.exec("reload(" + moduleName + ")"); //NON-NLS
        }

        // Importing the appropriate class from the Py Script which contains multiple classes.
        interpreter.exec("from " + moduleName + " import " + className);
        interpreter.exec("obj = " + className + "()"); //NON-NLS

        T obj = interpreter.get("obj", interfaceClass); //NON-NLS

        // Remove the directory where the Python script resides from the Python
        // module search path.
        interpreter.exec("sys.path.remove('" + path + "')"); //NON-NLS

        return obj;
    }


    // returns true if any file inside the Python module folder has been
    // modified since last loading of ingest factories.
    private static boolean hasModuleFolderChanged(String moduleFolderName, File[] files) {
        if (pythonModuleFolderList.containsKey(moduleFolderName)) {
            for (File file : files) {
                if (file.lastModified() > pythonModuleFolderList.get(moduleFolderName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class PythonScriptFileFilter implements FilenameFilter {

        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(".py"); //NON-NLS
        } //NON-NLS
    }

    private static interface LineFilter {

        boolean accept(String line);
    }

    private static class IngestModuleFactoryDefFilter implements LineFilter {

        @Override
        public boolean accept(String line) {
            return (line.contains("IngestModuleFactoryAdapter") || line.contains("IngestModuleFactory")); //NON-NLS
        }
    }

    private static class GeneralReportModuleDefFilter implements LineFilter {

        @Override
        public boolean accept(String line) {
            return (line.contains("GeneralReportModuleAdapter") || line.contains("GeneralReportModule")); //NON-NLS
        }
    }
}
