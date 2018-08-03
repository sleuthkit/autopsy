/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonfilessearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static junit.framework.Assert.assertTrue;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.commonfilesearch.AllIntraCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonfilesearch.CommonAttributeSearchResults;
import org.sleuthkit.autopsy.commonfilesearch.IntraCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonfilesearch.SingleIntraCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import static org.sleuthkit.autopsy.testutils.IngestUtils.getIngestModuleTemplate;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Ingested w/o mime type info added to DB.
 *
 * Setup:
 *
 * Add images set 1, set 2, set 3, and set 4 to case. Do not run mime type
 * module.
 */
public class IngestedWithNoFileTypesIntraCaseTests extends NbTestCase {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(IngestedWithNoFileTypesIntraCaseTests.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    private final IntraCaseUtils utils;
    
    public IngestedWithNoFileTypesIntraCaseTests(String name) {
        super(name);
        
        this.utils = new IntraCaseUtils(this, "IngestedWithNoFileTypes");
    }

    @Override
    public void setUp() {
        this.utils.setUp();

        IngestModuleTemplate hashLookupTemplate = getIngestModuleTemplate(new HashLookupModuleFactory());

        ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
        templates.add(hashLookupTemplate);

        IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestedWithNoFileTypesIntraCaseTests.class.getCanonicalName(), IngestJobSettings.IngestType.FILES_ONLY, templates);

        try {
            IngestUtils.runIngestJob(Case.getCurrentCaseThrows().getDataSources(), ingestJobSettings);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
    
    @Override
    public void tearDown(){
        this.utils.tearDown();
    }

    /**
     * Search using all data sources and filtering for media types. We should
     * find nothing and no errors should arise.
     */
    public void testOne() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();

            IntraCaseCommonAttributeSearcher allSourcesBuilder = new AllIntraCaseCommonAttributeSearcher(dataSources, true, false);
            CommonAttributeSearchResults metadata = allSourcesBuilder.findFiles();

            Map<Long, String> objectIdToDataSource = IntraCaseUtils.mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = IntraCaseUtils.getFiles(objectIdToDataSource.keySet());

            assertTrue(files.isEmpty());

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Search using single data source and filtering for doc types. Observe that
     * nothing is found and that nothing blows up.
     */
    public void testTwo() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            Long third = IntraCaseUtils.getDataSourceIdByName(IntraCaseUtils.SET3, dataSources);

            IntraCaseCommonAttributeSearcher singleSourceBuilder = new SingleIntraCaseCommonAttributeSearcher(third, dataSources, true, false);
            CommonAttributeSearchResults metadata = singleSourceBuilder.findFiles();

            Map<Long, String> objectIdToDataSource = IntraCaseUtils.mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = IntraCaseUtils.getFiles(objectIdToDataSource.keySet());

            assertTrue(files.isEmpty());

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
}
