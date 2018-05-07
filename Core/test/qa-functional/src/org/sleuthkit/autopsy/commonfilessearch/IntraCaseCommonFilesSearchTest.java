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
import org.netbeans.junit.NbTestCase;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.commonfilesearch.DataSourceLoader;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;

/**
 *
 * @author bsweeney
 */
public abstract class IntraCaseCommonFilesSearchTest extends NbTestCase {

    private static final String CASE_NAME = "IntraCaseCommonFilesSearchTest";
    static final Path CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), CASE_NAME);

    protected final Path IMAGE_PATH_1 = Paths.get(this.getDataDir().toString(), "3776", "commonfiles_image1_v1.vhd");
    private final Path IMAGE_PATH_2 = Paths.get(this.getDataDir().toString(), "3776", "commonfiles_image2_v1.vhd");
    private final Path IMAGE_PATH_3 = Paths.get(this.getDataDir().toString(), "3776", "commonfiles_image3_v1.vhd");
    protected final Path IMAGE_PATH_4 = Paths.get(this.getDataDir().toString(), "3776", "commonfiles_image4_v1.vhd");

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
}
