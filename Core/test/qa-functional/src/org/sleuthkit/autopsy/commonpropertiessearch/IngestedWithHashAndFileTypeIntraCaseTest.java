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
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.commonpropertiessearch.AbstractCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonpropertiessearch.AllIntraCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonpropertiessearch.CommonAttributeCountSearchResults;
import org.sleuthkit.autopsy.commonpropertiessearch.SingleIntraCaseCommonAttributeSearcher;
import static org.sleuthkit.autopsy.commonpropertiessearch.IntraCaseTestUtils.*;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeIdModuleFactory;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Add set 1, set 2, set 3, and set 4 to case and ingest with hash algorithm.
 */
public class IngestedWithHashAndFileTypeIntraCaseTest extends NbTestCase {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(IngestedWithHashAndFileTypeIntraCaseTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    private final IntraCaseTestUtils utils;

    public IngestedWithHashAndFileTypeIntraCaseTest(String name) {
        super(name);

        this.utils = new IntraCaseTestUtils(this, "IngestedWithHashAndFileTypeTests");
    }

    @Override
    public void setUp() {
        this.utils.setUp();

        IngestModuleTemplate hashLookupTemplate = IngestUtils.getIngestModuleTemplate(new HashLookupModuleFactory());
        IngestModuleTemplate mimeTypeLookupTemplate = IngestUtils.getIngestModuleTemplate(new FileTypeIdModuleFactory());

        ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
        templates.add(hashLookupTemplate);
        templates.add(mimeTypeLookupTemplate);

        IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestedWithHashAndFileTypeIntraCaseTest.class.getCanonicalName(), IngestType.FILES_ONLY, templates);

        try {
            IngestUtils.runIngestJob(Case.getCurrentCaseThrows().getDataSources(), ingestJobSettings);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    @Override
    public void tearDown() {
        this.utils.tearDown();
    }

    /**
     * Find all matches & all file types. Confirm file.jpg is found on all three
     * and file.docx is found on two.
     */
    public void testOneA() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();

            AbstractCommonAttributeSearcher allSourcesBuilder = new AllIntraCaseCommonAttributeSearcher(dataSources, false, false, 0);
            CommonAttributeCountSearchResults metadata = allSourcesBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = IntraCaseTestUtils.mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = IntraCaseTestUtils.getFiles(objectIdToDataSource.keySet());

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET1, 2));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET2, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET3, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET1, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET3, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET4, 0));

        } catch (NoCurrentCaseException | TskCoreException | SQLException | CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Find all matches & only image types. Confirm file.jpg is found on all
     * three.
     */
    public void testOneB() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();

            AbstractCommonAttributeSearcher allSourcesBuilder = new AllIntraCaseCommonAttributeSearcher(dataSources, true, false, 0);
            CommonAttributeCountSearchResults metadata = allSourcesBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = getFiles(objectIdToDataSource.keySet());

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET1, 2));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET2, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET3, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET4, 0));

        } catch (NoCurrentCaseException | TskCoreException | SQLException | CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Find all matches & only image types. Confirm file.jpg is found on all
     * three.
     */
    public void testOneC() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();

            AbstractCommonAttributeSearcher allSourcesBuilder = new AllIntraCaseCommonAttributeSearcher(dataSources, false, true, 0);
            CommonAttributeCountSearchResults metadata = allSourcesBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = getFiles(objectIdToDataSource.keySet());

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET1, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET3, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET4, 0));

        } catch (NoCurrentCaseException | TskCoreException | SQLException | CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Find matches on set 1 & all file types. Confirm same results.
     *
     */
    public void testTwoA() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            Long first = getDataSourceIdByName(SET1, dataSources);

            AbstractCommonAttributeSearcher singleSourceBuilder = new SingleIntraCaseCommonAttributeSearcher(first, dataSources, false, false, 0);
            CommonAttributeCountSearchResults metadata = singleSourceBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = getFiles(objectIdToDataSource.keySet());

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET1, 2));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET2, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET3, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET1, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET3, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET4, 0));

        } catch (NoCurrentCaseException | TskCoreException | SQLException | CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Find matches on set 1 & only media types. Confirm same results.
     *
     */
    public void testTwoB() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            Long first = getDataSourceIdByName(SET1, dataSources);

            AbstractCommonAttributeSearcher singleSourceBuilder = new SingleIntraCaseCommonAttributeSearcher(first, dataSources, true, false, 0);
            CommonAttributeCountSearchResults metadata = singleSourceBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = getFiles(objectIdToDataSource.keySet());

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET1, 2));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET2, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET3, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET4, 0));

        } catch (NoCurrentCaseException | TskCoreException | SQLException | CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Find matches on set 1 & all file types. Confirm same results.
     *
     */
    public void testTwoC() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            Long first = getDataSourceIdByName(SET1, dataSources);

            AbstractCommonAttributeSearcher singleSourceBuilder = new SingleIntraCaseCommonAttributeSearcher(first, dataSources, false, true, 0);
            CommonAttributeCountSearchResults metadata = singleSourceBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = getFiles(objectIdToDataSource.keySet());

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET1, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET3, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET4, 0));

        } catch (NoCurrentCaseException | TskCoreException | SQLException | CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Find matches on set 2 & all file types: Confirm file.jpg.
     *
     */
    public void testThree() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            Long second = getDataSourceIdByName(SET2, dataSources);

            AbstractCommonAttributeSearcher singleSourceBuilder = new SingleIntraCaseCommonAttributeSearcher(second, dataSources, false, false, 0);
            CommonAttributeCountSearchResults metadata = singleSourceBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = getFiles(objectIdToDataSource.keySet());

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET1, 2));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET2, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET3, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET4, 0));

        } catch (NoCurrentCaseException | TskCoreException | SQLException | CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Find matches on set 4 & all file types: Confirm nothing is found.
     */
    public void testFour() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            Long last = getDataSourceIdByName(SET4, dataSources);

            AbstractCommonAttributeSearcher singleSourceBuilder = new SingleIntraCaseCommonAttributeSearcher(last, dataSources, false, false, 0);
            CommonAttributeCountSearchResults metadata = singleSourceBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = getFiles(objectIdToDataSource.keySet());

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET4, 0));

        } catch (NoCurrentCaseException | TskCoreException | SQLException | CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Find matches on set 3 & all file types: Confirm file.jpg and file.docx.
     */
    public void testFive() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            Long third = getDataSourceIdByName(SET3, dataSources);

            AbstractCommonAttributeSearcher singleSourceBuilder = new SingleIntraCaseCommonAttributeSearcher(third, dataSources, false, false, 0);
            CommonAttributeCountSearchResults metadata = singleSourceBuilder.findMatchesByCount();

            Map<Long, String> objectIdToDataSource = mapFileInstancesToDataSources(metadata);

            List<AbstractFile> files = getFiles(objectIdToDataSource.keySet());

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET1, 2));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET2, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET3, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, IMG, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET1, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET3, 1));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, DOC, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, PDF, SET4, 0));

            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET1, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET2, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET3, 0));
            assertTrue(verifyInstanceExistanceAndCount(files, objectIdToDataSource, EMPTY, SET4, 0));

        } catch (NoCurrentCaseException | TskCoreException | SQLException | CentralRepoException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }
}
