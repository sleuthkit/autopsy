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
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import junit.framework.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import static org.sleuthkit.autopsy.commonpropertiessearch.IntraCaseTestUtils.*;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeIdModuleFactory;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.autopsy.testutils.TestUtilsException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Ensures that matches only are found for files which appear in at least two
 * data sources.
 *
 * The two datasources used here have no common files. One of the data sources
 * has two identical files within it. This should not count as a match.
 *
 * None of the test files should be found in the results of this test.
 */
public class MatchesInAtLeastTwoSourcesIntraCaseTest extends NbTestCase {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(MatchesInAtLeastTwoSourcesIntraCaseTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    private final IntraCaseTestUtils utils;

    public MatchesInAtLeastTwoSourcesIntraCaseTest(String name) {
        super(name);

        this.utils = new IntraCaseTestUtils(this, "MatchesInAtLeastTwoSources");
    }

    @Override
    public void setUp() {
        this.utils.createAsCurrentCase();

        final ImageDSProcessor imageDSProcessor = new ImageDSProcessor();

        this.utils.addImageOne(imageDSProcessor);
        this.utils.addImageFour(imageDSProcessor);

        IngestModuleTemplate hashLookupTemplate = IngestUtils.getIngestModuleTemplate(new HashLookupModuleFactory());
        IngestModuleTemplate mimeTypeLookupTemplate = IngestUtils.getIngestModuleTemplate(new FileTypeIdModuleFactory());

        ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
        templates.add(hashLookupTemplate);
        templates.add(mimeTypeLookupTemplate);

        IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestedWithHashAndFileTypeIntraCaseTest.class.getCanonicalName(), IngestJobSettings.IngestType.FILES_ONLY, templates);

        try {
            IngestUtils.runIngestJob(Case.getCurrentCaseThrows().getDataSources(), ingestJobSettings);
        } catch (NoCurrentCaseException | TskCoreException | TestUtilsException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    @Override
    public void tearDown() {
        this.utils.tearDown();
    }

    public void testOne() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();

            AbstractCommonAttributeSearcher allSourcesBuilder = new AllIntraCaseCommonAttributeSearcher(dataSources, false, false, 0);
            CommonAttributeCountSearchResults metadata = allSourcesBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = IntraCaseTestUtils.mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = IntraCaseTestUtils.getFiles(objectIdToDataSource.keySet());

            assertTrue(IntraCaseTestUtils.verifyInstanceExistanceAndCount(files, dataSources, IMG, SET1, 0));
            assertTrue(IntraCaseTestUtils.verifyInstanceExistanceAndCount(files, dataSources, IMG, SET4, 0));

            assertTrue(IntraCaseTestUtils.verifyInstanceExistanceAndCount(files, dataSources, DOC, SET1, 0));
            assertTrue(IntraCaseTestUtils.verifyInstanceExistanceAndCount(files, dataSources, DOC, SET4, 0));

            assertTrue(IntraCaseTestUtils.verifyInstanceExistanceAndCount(files, dataSources, EMPTY, SET1, 0));
            assertTrue(IntraCaseTestUtils.verifyInstanceExistanceAndCount(files, dataSources, EMPTY, SET4, 0));

        } catch (NoCurrentCaseException | TskCoreException | SQLException | CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }
}
