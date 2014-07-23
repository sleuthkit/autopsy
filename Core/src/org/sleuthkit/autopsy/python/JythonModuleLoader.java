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
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.python.util.PythonInterpreter;
import org.sleuthkit.autopsy.actions.OpenPythonModulesFolderAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;

/**
 * Finds and loads Autopsy modules written using the Jython variant of the
 * Python scripting language.
 */
public final class JythonModuleLoader {

    private static final Logger logger = Logger.getLogger(JythonModuleLoader.class.getName()); // RJCTODO: Need this?

    /**
     * Get the currently available set of ingest module factories. The factories
     * are not cached between calls since Python scripts defining classes
     * derived from IngestModuleFactory may be added or removed between
     * invocations.
     *
     * @return A list of objects that implement the IngestModuleFactory
     * interface.
     */
    public static List<IngestModuleFactory> getIngestModuleFactories() {
        List<IngestModuleFactory> factories = new ArrayList<>();
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
                            if (line.startsWith("class ") && (line.contains("IngestModuleFactoryAdapter") || line.contains("IngestModuleFactory"))) {
                                String className = line.substring(6, line.indexOf("("));
                                try {
                                    factories.add((IngestModuleFactory) createObjectFromScript(script, className, IngestModuleFactory.class));
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
        return factories;
    }

    private static Object createObjectFromScript(File script, String className, Class clazz) {
        PythonInterpreter interpreter = new PythonInterpreter(); // RJCTODO: Does a new one need to be created each time?
        interpreter.execfile(script.getAbsolutePath());
        interpreter.exec("obj = " + className + "()");
        return interpreter.get("obj", clazz);
    }

    private static class PythonScriptFileFilter implements FilenameFilter {

        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(".py");
        }
    }
}
