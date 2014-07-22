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
package org.sleuthkit.autopsy.ingest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Lookup;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.examples.SampleExecutableIngestModuleFactory;
import org.sleuthkit.autopsy.examples.SampleIngestModuleFactory;
import org.sleuthkit.autopsy.modules.android.AndroidModuleFactory;
import org.sleuthkit.autopsy.modules.e01verify.E01VerifierModuleFactory;
import org.sleuthkit.autopsy.modules.exif.ExifParserModuleFactory;
import org.sleuthkit.autopsy.modules.fileextmismatch.FileExtMismatchDetectorModuleFactory;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeIdModuleFactory;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.modules.sevenzip.ArchiveFileExtractorModuleFactory;

/**
 * Looks up loaded ingest module factories using the NetBeans global lookup.
 */
final class IngestModuleFactoryLoader {

    private static final Logger logger = Logger.getLogger(IngestModuleFactoryLoader.class.getName());
    private static IngestModuleFactoryLoader instance;
    private final PythonInterpreter interpreter;
    private int instanceNumber;

    private IngestModuleFactoryLoader() {
        interpreter = new PythonInterpreter();
    }

    synchronized static IngestModuleFactoryLoader getInstance() {
        if (instance == null) {
            instance = new IngestModuleFactoryLoader();
        }
        return instance;
    }

    /**
     * Get the currently available set of ingest module factories. The factories
     * are not cached between calls since NetBeans modules with classes labeled
     * as IngestModuleFactory service providers and/or Python scripts defining
     * classes derived from IngestModuleFactory may be added or removed between
     * invocations.
     *
     * @return A list of objects that implement the IngestModuleFactory
     * interface.
     */
    synchronized List<IngestModuleFactory> getIngestModuleFactories() {
        // Discover the ingest module factories, making sure that there are no
        // duplicate module display names. The duplicates requirement could be
        // eliminated if the enabled/disabled modules setting was by factory 
        // class name instead of module display name. Also note that that we are 
        // temporarily  hard-coding ordering of module factories until the 
        // module configuration file is reworked, so the discovered factories 
        // are initially mapped by class name.
        
        // make map of factory name to factory
        HashSet<String> moduleDisplayNames = new HashSet<>();
        HashMap<String, IngestModuleFactory> moduleFactoriesByClass = new HashMap<>();
        Collection<? extends IngestModuleFactory> factories = Lookup.getDefault().lookupAll(IngestModuleFactory.class);
        for (IngestModuleFactory factory : factories) {
            if (!moduleDisplayNames.contains(factory.getModuleDisplayName())) {
                moduleDisplayNames.add(factory.getModuleDisplayName());
                moduleFactoriesByClass.put(factory.getClass().getCanonicalName(), factory);
                logger.log(Level.INFO, "Found ingest module factory: name = {0}, version = {1}", new Object[]{factory.getModuleDisplayName(), factory.getModuleVersionNumber()}); //NON-NLS
            } else {
                // Not popping up a message box to keep this class UI-indepdent.
                logger.log(Level.SEVERE, "Found duplicate ingest module display name (name = {0})", factory.getModuleDisplayName()); //NON-NLS
            }
        }
        
        // Kick out the sample module factories.
        moduleFactoriesByClass.remove(SampleIngestModuleFactory.class.getCanonicalName());
        moduleFactoriesByClass.remove(SampleExecutableIngestModuleFactory.class.getCanonicalName());

        // Do the core ingest module ordering hack described above.
        ArrayList<String> coreModuleOrdering = new ArrayList<String>() {
            {
                add("org.sleuthkit.autopsy.recentactivity.RecentActivityExtracterModuleFactory"); //NON-NLS
                add(HashLookupModuleFactory.class.getCanonicalName());
                add(FileTypeIdModuleFactory.class.getCanonicalName());
                add(ArchiveFileExtractorModuleFactory.class.getCanonicalName());
                add(ExifParserModuleFactory.class.getCanonicalName());
                add("org.sleuthkit.autopsy.keywordsearch.KeywordSearchModuleFactory"); //NON-NLS
                add("org.sleuthkit.autopsy.thunderbirdparser.EmailParserModuleFactory"); //NON-NLS
                add(FileExtMismatchDetectorModuleFactory.class.getCanonicalName());
                add(E01VerifierModuleFactory.class.getCanonicalName());
                add(AndroidModuleFactory.class.getCanonicalName());
            }
        };
        
        // make the ordered list of factories, starting with the core
        // modules. Remove the core factories from the map.
        List<IngestModuleFactory> orderedModuleFactories = new ArrayList<>();
        for (String className : coreModuleOrdering) {
            IngestModuleFactory coreFactory = moduleFactoriesByClass.remove(className);
            if (coreFactory != null) {
                orderedModuleFactories.add(coreFactory);
            }
            else {
                logger.log(Level.SEVERE, "Core factory " + coreFactory + " not loaded");
            }
        }

        // Add in any non-core factories discovered. Order is not guaranteed!
        for (IngestModuleFactory nonCoreFactory : moduleFactoriesByClass.values()) {
            orderedModuleFactories.add(nonCoreFactory);
        }

        // RJCTODO: Replace hard-coding with discovery
        try {
            String pathToPythonScript = "C:\\autopsy\\Core\\src\\org\\sleuthkit\\autopsy\\examples\\SampleJythonIngestModule.py";
            this.interpreter.execfile(pathToPythonScript);   
            String factoryClassName = pathToPythonScript.substring(pathToPythonScript.lastIndexOf("\\") + 1);
            factoryClassName = factoryClassName.substring(0, factoryClassName.indexOf("."));
            String instanceName = "ingestModuleFactory" + "_" + instanceNumber++;
            this.interpreter.exec(instanceName + " = " + factoryClassName + "Factory()");
            IngestModuleFactory factory = this.interpreter.get(instanceName, IngestModuleFactory.class);
            orderedModuleFactories.add(factory);
        } catch (Exception ex) {
            // RJCTODO: Do error different handling
            // Jython exceptions apparently don't support getMessage()
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.toString(), NotifyDescriptor.ERROR_MESSAGE));
        }

        return orderedModuleFactories;
    }
}
