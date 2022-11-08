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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static junit.framework.Assert.assertTrue;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import junit.framework.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.commonpropertiessearch.AllIntraCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonpropertiessearch.CommonAttributeCountSearchResults;
import org.sleuthkit.autopsy.commonpropertiessearch.IntraCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonpropertiessearch.SingleIntraCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import static org.sleuthkit.autopsy.testutils.IngestUtils.getIngestModuleTemplate;
import org.sleuthkit.autopsy.testutils.TestUtilsException;
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
public class IngestedWithNoFileTypesIntraCaseTest extends NbTestCase {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(IngestedWithNoFileTypesIntraCaseTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    private final IntraCaseTestUtils utils;
    
    public IngestedWithNoFileTypesIntraCaseTest(String name) {
        super(name);
        
        this.utils = new IntraCaseTestUtils(this, "IngestedWithNoFileTypes");
    }

    @Override
    public void setUp() {
        this.utils.setUp();

        IngestModuleTemplate hashLookupTemplate = getIngestModuleTemplate(new HashLookupModuleFactory());

        ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
        templates.add(hashLookupTemplate);

        IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestedWithNoFileTypesIntraCaseTest.class.getCanonicalName(), IngestJobSettings.IngestType.FILES_ONLY, templates);

        try {
            IngestUtils.runIngestJob(Case.getCurrentCaseThrows().getDataSources(), ingestJobSettings);
        } catch (NoCurrentCaseException | TskCoreException | TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
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

            IntraCaseCommonAttributeSearcher allSourcesBuilder = new AllIntraCaseCommonAttributeSearcher(dataSources, true, false, 0);
            CommonAttributeCountSearchResults metadata = allSourcesBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = IntraCaseTestUtils.mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = IntraCaseTestUtils.getFiles(objectIdToDataSource.keySet());

            assertTrue(files.isEmpty());

        } catch (TskCoreException | NoCurrentCaseException | SQLException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Search using single data source and filtering for doc types. Observe that
     * nothing is found and that nothing blows up.
     */
    public void testTwo() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            Long third = IntraCaseTestUtils.getDataSourceIdByName(IntraCaseTestUtils.SET3, dataSources);

            IntraCaseCommonAttributeSearcher singleSourceBuilder = new SingleIntraCaseCommonAttributeSearcher(third, dataSources, true, false, 0);
            CommonAttributeCountSearchResults metadata = singleSourceBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = IntraCaseTestUtils.mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = IntraCaseTestUtils.getFiles(objectIdToDataSource.keySet());

            assertTrue(files.isEmpty());

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }
}
