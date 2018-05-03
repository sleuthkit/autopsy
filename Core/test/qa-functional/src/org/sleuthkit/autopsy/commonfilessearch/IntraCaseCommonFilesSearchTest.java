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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Map;
import junit.framework.Assert;
import static junit.framework.Assert.*;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.commonfilesearch.AllDataSourcesCommonFilesAlgorithm;
import org.sleuthkit.autopsy.commonfilesearch.CommonFilesMetadata;
import org.sleuthkit.autopsy.commonfilesearch.CommonFilesMetadataBuilder;
import org.sleuthkit.autopsy.commonfilesearch.DataSourceLoader;
import org.sleuthkit.autopsy.commonfilesearch.SingleDataSource;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author bsweeney
 */
public abstract class IntraCaseCommonFilesSearchTest extends NbTestCase {

    private static final String CASE_NAME = "IntraCaseCommonFilesSearchTest";
    private static final Path CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), CASE_NAME);

    private final Path IMAGE_PATH_1 = Paths.get(this.getDataDir().toString(), "3776", "commonfiles_image1_v1.vhd");
    private final Path IMAGE_PATH_2 = Paths.get(this.getDataDir().toString(), "3776", "commonfiles_image2_v1.vhd");
    private final Path IMAGE_PATH_3 = Paths.get(this.getDataDir().toString(), "3776", "commonfiles_image3_v1.vhd");
    private final Path IMAGE_PATH_4 = Paths.get(this.getDataDir().toString(), "3776", "commonfiles_image4_v1.vhd");

    protected DataSourceLoader dataSourceLoader;

    public IntraCaseCommonFilesSearchTest(String name) {
        super(name);
    }

    @Override
    public void setUp() {

        CaseUtils.createCase(CASE_DIRECTORY_PATH);

        IngestUtils.addDataSource(new ImageDSProcessor(), IMAGE_PATH_1);
        IngestUtils.addDataSource(new ImageDSProcessor(), IMAGE_PATH_2);
        IngestUtils.addDataSource(new ImageDSProcessor(), IMAGE_PATH_3);
        IngestUtils.addDataSource(new ImageDSProcessor(), IMAGE_PATH_4);

        this.dataSourceLoader = new DataSourceLoader();
    }

    @Override
    public void tearDown() {
        CaseUtils.closeCase();
        CaseUtils.deleteCaseDir(CASE_DIRECTORY_PATH);
    }

    public class UningestedCases extends IntraCaseCommonFilesSearchTest {

        public UningestedCases(String name) {
            super(name);
        }

        /**
         * Add images #1, #2, #3, and #4 to case. Do not ingest. Find all
         * matches & all file types. Confirm no matches are found (since there
         * are no hashes to match). Find all matches on image #1 & all file
         * types. Confirm no matches.
         */
        public void testOne() {
            try {
                Map<Long, String> dataSources = this.dataSourceLoader.getDataSourceMap();

                CommonFilesMetadataBuilder allSourcesBuilder = new AllDataSourcesCommonFilesAlgorithm(dataSources, false, false);
                CommonFilesMetadata metadata = allSourcesBuilder.findCommonFiles();

                int resultCount = metadata.size();
                assertEquals(resultCount, 0);
                
            } catch (NoCurrentCaseException | TskCoreException | SQLException ex) {
                fail(ex.getMessage());
            }
        }

        public void testTwo() {
            try {
                Map<Long, String> dataSources = this.dataSourceLoader.getDataSourceMap();
                Long first = new Long(1);

                CommonFilesMetadataBuilder singleSourceBuilder = new SingleDataSource(first, dataSources, false, false);
                CommonFilesMetadata metadata = singleSourceBuilder.findCommonFiles();
                
                int resultCount = metadata.size();
                assertEquals(resultCount, 0);
                
            } catch (NoCurrentCaseException | TskCoreException | SQLException ex) {
                fail(ex.getMessage());
            }
        }
    }

    public class IngestedWithHashAlgOnly extends IntraCaseCommonFilesSearchTest {

        public IngestedWithHashAlgOnly(String name) {
            super(name);
        }

        /**
         * Add #1, #2, #3, and #4 to case and ingest with hash algorithm. Find
         * all matches & all file types. Confirm file.jpg is found on all three
         * and file.docx is found on two. Find matches on ‘#1’ & all file types.
         * Confirm same results. Find matches on ‘#2 & all file types: Confirm
         * file.jpg. Find matches on ‘#3’ & all file types: Confirm file.jpg and
         * file.docx. Find matches on #4 & all file types: Confirm nothing is
         * found
         */
        public void testTwo() {

        }

    }

    public class NoMatches extends IntraCaseCommonFilesSearchTest {

        public NoMatches(String name) {
            super(name);
        }

        @Override
        public void setUp() {

            CaseUtils.createCase(CASE_DIRECTORY_PATH);
            IngestUtils.addDataSource(new ImageDSProcessor(), IMAGE_PATH_1);
            IngestUtils.addDataSource(new ImageDSProcessor(), IMAGE_PATH_4);
        }

        /**
         * Add #1 and #4 to case and ingest. Find all matches & all file types.
         * Confirm nothing matches
         */
        public void testThree() {

        }
    }
}
