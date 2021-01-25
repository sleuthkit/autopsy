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
package org.sleuthkit.autopsy.ingest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.examples.SampleIngestModuleFactory;
import org.sleuthkit.autopsy.modules.dataSourceIntegrity.DataSourceIntegrityModuleFactory;
import org.sleuthkit.autopsy.modules.fileextmismatch.FileExtMismatchDetectorModuleFactory;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeIdModuleFactory;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.modules.interestingitems.InterestingItemsIngestModuleFactory;
import org.sleuthkit.autopsy.modules.photoreccarver.PhotoRecCarverIngestModuleFactory;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.EmbeddedFileExtractorModuleFactory;
import org.sleuthkit.autopsy.modules.encryptiondetection.EncryptionDetectionModuleFactory;
import org.sleuthkit.autopsy.centralrepository.ingestmodule.CentralRepoIngestModuleFactory;
import org.sleuthkit.autopsy.modules.pictureanalyzer.PictureAnalyzerIngestModuleFactory;
import org.sleuthkit.autopsy.modules.vmextractor.VMExtractorIngestModuleFactory;
import org.sleuthkit.autopsy.python.JythonModuleLoader;

/**
 * Discovers and instantiates ingest module factories.
 */
final class IngestModuleFactoryLoader {

    private static final Logger logger = Logger.getLogger(IngestModuleFactoryLoader.class.getName());
    private static final String SAMPLE_MODULE_FACTORY_CLASS_NAME = SampleIngestModuleFactory.class.getCanonicalName();
    private static final ArrayList<String> coreModuleOrdering = new ArrayList<String>() {
        private static final long serialVersionUID = 1L;
        {
            // The ordering of the core ingest module factories implemented
            // using Java is hard-coded. 
            add("org.sleuthkit.autopsy.recentactivity.RecentActivityExtracterModuleFactory"); //NON-NLS
            add(HashLookupModuleFactory.class.getCanonicalName());
            add(FileTypeIdModuleFactory.class.getCanonicalName());
            add(FileExtMismatchDetectorModuleFactory.class.getCanonicalName());
            add(EmbeddedFileExtractorModuleFactory.class.getCanonicalName());
            add(PictureAnalyzerIngestModuleFactory.class.getCanonicalName());
            add("org.sleuthkit.autopsy.keywordsearch.KeywordSearchModuleFactory"); //NON-NLS
            add("org.sleuthkit.autopsy.thunderbirdparser.EmailParserModuleFactory"); //NON-NLS
            add(EncryptionDetectionModuleFactory.class.getCanonicalName());
            add(InterestingItemsIngestModuleFactory.class.getCanonicalName());
            add(CentralRepoIngestModuleFactory.class.getCanonicalName());
            add(PhotoRecCarverIngestModuleFactory.class.getCanonicalName());
            add(VMExtractorIngestModuleFactory.class.getCanonicalName());
            add(DataSourceIntegrityModuleFactory.class.getCanonicalName());
        }
    };

    /**
     * Gets the currently available set of ingest module factories. The
     * factories are not cached between calls since NetBeans modules with
     * classes annotated as IngestModuleFactory service providers and/or Python
     * scripts defining classes derived from IngestModuleFactory may be added or
     * removed between invocations.
     *
     * @return A list of objects that implement the IngestModuleFactory
     *         interface.
     */
    static List<IngestModuleFactory> getIngestModuleFactories() {
        // A hash set of display names and a hash map of class names to 
        // discovered factories are used to de-duplicate and order the 
        // factories.
        HashSet<String> moduleDisplayNames = new HashSet<>();
        HashMap<String, IngestModuleFactory> javaFactoriesByClass = new HashMap<>();

        // Discover the ingest module factories implemented using Java with a
        // service provider annotation for the IngestModuleFactory interface.
        for (IngestModuleFactory factory : Lookup.getDefault().lookupAll(IngestModuleFactory.class)) {
            IngestModuleFactoryLoader.addFactory(factory, moduleDisplayNames, javaFactoriesByClass);
        }

        // Discover the ingest module factories implemented using Java with a
        // service provider annotation for the IngestModuleFactoryAdapter 
        // abstract base class.
        for (IngestModuleFactory factory : Lookup.getDefault().lookupAll(IngestModuleFactoryAdapter.class)) {
            if (!javaFactoriesByClass.containsValue(factory)) {
                IngestModuleFactoryLoader.addFactory(factory, moduleDisplayNames, javaFactoriesByClass);
            }
        }

        // Add the core ingest module factories in the desired order, removing
        // the core factories from the map so that the map will only contain 
        // non-core modules after this loop.
        List<IngestModuleFactory> factories = new ArrayList<>();
        for (String className : coreModuleOrdering) {
            IngestModuleFactory coreFactory = javaFactoriesByClass.remove(className);
            if (coreFactory != null) {
                factories.add(coreFactory);
            } else {
                logger.log(Level.SEVERE, "Core factory {0} not loaded", className); //NON-NLS
            }
        }

        // Add any remaining non-core factories discovered. Order with an 
        // alphabetical sort by module display name.
        TreeMap<String, IngestModuleFactory> javaFactoriesSortedByName = new TreeMap<>();
        for (IngestModuleFactory factory : javaFactoriesByClass.values()) {
            javaFactoriesSortedByName.put(factory.getModuleDisplayName(), factory);
        }
        factories.addAll(javaFactoriesSortedByName.values());

        // Add any ingest module factories implemented using Jython. Order is 
        // not guaranteed! 
        for (IngestModuleFactory factory : JythonModuleLoader.getIngestModuleFactories()) {
            if (!moduleDisplayNames.contains(factory.getModuleDisplayName())) {
                moduleDisplayNames.add(factory.getModuleDisplayName());
                factories.add(factory);
                logger.log(Level.INFO, "Found ingest module factory: name = {0}, version = {1}", new Object[]{factory.getModuleDisplayName(), factory.getModuleVersionNumber()}); //NON-NLS
            } else {
                logger.log(Level.WARNING, "Found duplicate ingest module display name (name = {0})", factory.getModuleDisplayName()); //NON-NLS
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                        NbBundle.getMessage(IngestModuleFactoryLoader.class, "IngestModuleFactoryLoader.errorMessages.duplicateDisplayName", factory.getModuleDisplayName()),
                        NotifyDescriptor.ERROR_MESSAGE));
            }
        }

        return factories;
    }

    private static void addFactory(IngestModuleFactory factory, HashSet<String> moduleDisplayNames, HashMap<String, IngestModuleFactory> javaFactoriesByClass) {
        // Ignore the sample ingest module factories implemented in Java.        
        String className = factory.getClass().getCanonicalName();
        if (className.equals(IngestModuleFactoryLoader.SAMPLE_MODULE_FACTORY_CLASS_NAME)) {
            return;
        }

        if (!moduleDisplayNames.contains(factory.getModuleDisplayName())) {
            moduleDisplayNames.add(factory.getModuleDisplayName());
            javaFactoriesByClass.put(factory.getClass().getCanonicalName(), factory);
            logger.log(Level.INFO, "Found ingest module factory: name = {0}, version = {1}", new Object[]{factory.getModuleDisplayName(), factory.getModuleVersionNumber()}); //NON-NLS
        } else {
            logger.log(Level.WARNING, "Found duplicate ingest module display name (name = {0})", factory.getModuleDisplayName()); //NON-NLS
        }
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private IngestModuleFactoryLoader() {
    }

}
