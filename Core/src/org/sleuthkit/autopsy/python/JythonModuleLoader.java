/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2020 Basis Technology Corp.
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
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.python.util.PythonInterpreter;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Comparator;

/**
 * Finds and loads Autopsy modules written using the Jython variant of the
 * Python scripting language.
 */
public final class JythonModuleLoader {

    private static final Logger logger = Logger.getLogger(JythonModuleLoader.class.getName());

    /**
     * Get ingest module factories implemented using Jython.
     *
     * @return A list of objects that implement the IngestModuleFactory
     *         interface.
     */
    public static synchronized List<IngestModuleFactory> getIngestModuleFactories() {
        return getInterfaceImplementations(new IngestModuleFactoryDefFilter(), IngestModuleFactory.class);
    }

    /**
     * Get general report modules implemented using Jython.
     *
     * @return A list of objects that implement the GeneralReportModule
     *         interface.
     */
    public static synchronized List<GeneralReportModule> getGeneralReportModules() {
        return getInterfaceImplementations(new GeneralReportModuleDefFilter(), GeneralReportModule.class);
    }

     /**
     * Get data source processors modules implemented using Jython.
     *
     * @return A list of objects that implement the DataSourceProcessor
     *         interface.
     */
    public static synchronized List<DataSourceProcessor> getDataSourceProcessorModules() {
        return getInterfaceImplementations(new DataSourceProcessorDefFilter(), DataSourceProcessor.class);
    }

    @Messages({"JythonModuleLoader.pythonInterpreterError.title=Python Modules",
                "JythonModuleLoader.pythonInterpreterError.msg=Failed to load python modules, See log for more details"})
    private static <T> List<T> getInterfaceImplementations(LineFilter filter, Class<T> interfaceClass) {
        List<T> objects = new ArrayList<>();
        Set<File> pythonModuleDirs = new HashSet<>();
        PythonInterpreter interpreter = null;
        // This method has previously thrown unchecked exceptions when it could not load because of non-latin characters.
        try {
            interpreter = new PythonInterpreter();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Failed to load python Intepreter. Cannot load python modules", ex);
            if(RuntimeProperties.runningWithGUI()){
                MessageNotifyUtil.Notify.show(Bundle.JythonModuleLoader_pythonInterpreterError_title(),Bundle.JythonModuleLoader_pythonInterpreterError_msg(), MessageNotifyUtil.MessageType.ERROR);
            }
            return objects;
        }
        // add python modules from 'autospy/build/cluster/InternalPythonModules' folder
        // which are copied from 'autopsy/*/release/InternalPythonModules' folders.
        for (File f : InstalledFileLocator.getDefault().locateAll("InternalPythonModules", "org.sleuthkit.autopsy.core", false)) { //NON-NLS
            Collections.addAll(pythonModuleDirs, f.listFiles());
        }
        // add python modules from 'testuserdir/python_modules' folder
        Collections.addAll(pythonModuleDirs, new File(PlatformUtil.getUserPythonModulesPath()).listFiles());

        for (File file : pythonModuleDirs) {
            if (file.isDirectory()) {
                File[] pythonScripts = file.listFiles(new PythonScriptFileFilter());
                for (File script : pythonScripts) {
                        try (Scanner fileScanner = new Scanner(new BufferedReader(new FileReader(script)))) {
                        while (fileScanner.hasNextLine()) {
                            String line = fileScanner.nextLine();
                            if (line.startsWith("class ") && filter.accept(line)) { //NON-NLS
                                String className = line.substring(6, line.indexOf("("));
                                try {
                                    objects.add(createObjectFromScript(interpreter, script, className, interfaceClass));
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
            }
        }
        
        Collections.sort(objects, Comparator.comparing((T obj) -> obj.getClass().getSimpleName(), (s1, s2) -> s1.compareToIgnoreCase(s2)));
        return objects;
    }

    private static <T> T createObjectFromScript(PythonInterpreter interpreter, File script, String className, Class<T> interfaceClass) {
        // Add the directory where the Python script resides to the Python
        // module search path to allow the script to use other scripts bundled
        // with it.
        interpreter.exec("import sys"); //NON-NLS
        String path = Matcher.quoteReplacement(script.getParent());
        interpreter.exec("sys.path.append('" + path + "')"); //NON-NLS
        String moduleName = script.getName().replaceAll("\\.py$", ""); //NON-NLS

        // reload the module so that the changes made to it can be loaded.
        interpreter.exec("import " + moduleName); //NON-NLS
        interpreter.exec("reload(" + moduleName + ")"); //NON-NLS

        // Importing the appropriate class from the Py Script which contains multiple classes.
        interpreter.exec("from " + moduleName + " import " + className); //NON-NLS
        interpreter.exec("obj = " + className + "()"); //NON-NLS

        T obj = interpreter.get("obj", interfaceClass); //NON-NLS

        // Remove the directory where the Python script resides from the Python
        // module search path.
        interpreter.exec("sys.path.remove('" + path + "')"); //NON-NLS

        return obj;
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

    /**
     * Filter IngestModule interface implementations
     */
    private static class IngestModuleFactoryDefFilter implements LineFilter {

        @Override
        public boolean accept(String line) {
            return (line.contains("IngestModuleFactoryAdapter") || line.contains("IngestModuleFactory")); //NON-NLS
        }
    }

    /**
     * Filter GeneralReportModule interface implementations
     */
    private static class GeneralReportModuleDefFilter implements LineFilter {

        @Override
        public boolean accept(String line) {
            return (line.contains("GeneralReportModuleAdapter") || line.contains("GeneralReportModule")); //NON-NLS
        }
    }

    /**
     * Filter DataSourceProcessor interface implementations
     */
    private static class DataSourceProcessorDefFilter implements LineFilter {

        @Override
        public boolean accept(String line) {
            return (line.contains("DataSourceProcessorAdapter") || line.contains("DataSourceProcessor")); //NON-NLS
        }
    }
}
