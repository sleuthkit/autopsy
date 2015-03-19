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
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.regex.Matcher;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.python.util.PythonInterpreter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.report.GeneralReportModule;

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
     * interface.
     */
    public static List<IngestModuleFactory> getIngestModuleFactories() {
        return getInterfaceImplementations(new IngestModuleFactoryDefFilter(), IngestModuleFactory.class);
    }

    /**
     * Get general report modules implemented using Jython.
     *
     * @return A list of objects that implement the GeneralReportModule
     * interface.
     */
    public static List<GeneralReportModule> getGeneralReportModules() {
        return getInterfaceImplementations(new GeneralReportModuleDefFilter(), GeneralReportModule.class);
    }

    private static <T> List<T> getInterfaceImplementations(LineFilter filter, Class<T> interfaceClass) {
        List<T> objects = new ArrayList<>();
        File pythonModulesDir = new File(PlatformUtil.getUserPythonModulesPath());
        File[] files = pythonModulesDir.listFiles();
        for (File file : files) {
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
                                    objects.add( createObjectFromScript(script, className, interfaceClass));
                                } catch (Exception ex) {
                                    logger.log(Level.SEVERE, String.format("Failed to load %s from %s", className, script.getAbsolutePath()), ex); //NON-NLS
                                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                                            NbBundle.getMessage(JythonModuleLoader.class, "JythonModuleLoader.errorMessages.failedToLoadModule", className, script.getAbsolutePath()),
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
        return objects;
    }
        
    private static <T> T createObjectFromScript(File script, String className, Class<T> interfaceClass) {
        // Make a "fresh" interpreter every time to avoid name collisions, etc.
        PythonInterpreter interpreter = new PythonInterpreter();

        // Add the directory where the Python script resides to the Python
        // module search path to allow the script to use other scripts bundled
        // with it.
        interpreter.exec("import sys"); //NON-NLS
        String path = Matcher.quoteReplacement(script.getParent());
        interpreter.exec("sys.path.append('" + path + "')"); //NON-NLS

        // Execute the script and create an instance of the desired class.
        interpreter.execfile(script.getAbsolutePath());
        // Importing the appropriate class from the Py Script which contains multiple classes.
        interpreter.exec("from " + script.getName().replaceAll(".py", "") + " import " + className);
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