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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static junit.framework.Assert.assertTrue;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.commonfilesearch.AllDataSourcesCommonFilesAlgorithm;
import org.sleuthkit.autopsy.commonfilesearch.CommonFilesMetadata;
import org.sleuthkit.autopsy.commonfilesearch.CommonFilesMetadataBuilder;
import org.sleuthkit.autopsy.commonfilesearch.SingleDataSource;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.testutils.IngestUtils;
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
public class IngestedWithNoFileTypes extends AbstractIntraCaseCommonFilesSearchTest {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(IngestedWithNoFileTypes.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public IngestedWithNoFileTypes(String name) {
        super(name);
    }

    @Override
    public void setUp() {
        super.setUp();

        IngestModuleTemplate hashLookupTemplate = IngestUtils.getIngestModuleTemplate(new HashLookupModuleFactory());

        ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
        templates.add(hashLookupTemplate);

        IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestedWithHashAndFileType.class.getCanonicalName(), IngestJobSettings.IngestType.FILES_ONLY, templates);

        try {
            IngestUtils.runIngestJob(Case.getCurrentCaseThrows().getDataSources(), ingestJobSettings);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    @Override
    protected String getCaseName() {
        return "IngestedWithNoFileTypes";
    }

    /**
     * Search using all data sources and filtering for media types. We should
     * find nothing and no errors should arise.
     */
    public void testOne() {
        try {
            Map<Long, String> dataSources = this.dataSourceLoader.getDataSourceMap();

            CommonFilesMetadataBuilder allSourcesBuilder = new AllDataSourcesCommonFilesAlgorithm(dataSources, true, false);
            CommonFilesMetadata metadata = allSourcesBuilder.findFiles();

            Map<Long, String> objectIdToDataSource = mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = getFiles(objectIdToDataSource.keySet());

            assertTrue(files.isEmpty());

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /**
     * Search using single data source and filtering for doc types. Observe that
     * nothing is found and that nothing blows up.
     */
    public void testTwo() {
        try {
            Map<Long, String> dataSources = this.dataSourceLoader.getDataSourceMap();
            Long third = this.getDataSourceIdByIndex(2, dataSources);

            CommonFilesMetadataBuilder singleSourceBuilder = new SingleDataSource(third, dataSources, true, false);
            CommonFilesMetadata metadata = singleSourceBuilder.findFiles();

            Map<Long, String> objectIdToDataSource = mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = getFiles(objectIdToDataSource.keySet());

            assertTrue(files.isEmpty());

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
}
