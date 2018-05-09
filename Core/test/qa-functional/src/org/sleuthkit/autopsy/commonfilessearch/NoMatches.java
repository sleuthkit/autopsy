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

import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;

public class NoMatches extends IntraCaseCommonFilesSearchTest {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(NoMatches.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public NoMatches(String name) {
        super(name);
    }

    @Override
    public void setUp() {

        CaseUtils.createCase(CASE_DIRECTORY_PATH, this.getCaseName());
        IngestUtils.addDataSource(new ImageDSProcessor(), IMAGE_PATH_1);
        IngestUtils.addDataSource(new ImageDSProcessor(), IMAGE_PATH_4);
    }

    /**
     * Add #1 and #4 to case and ingest. Find all matches & all file types.
     * Confirm nothing matches
     */
    public void testThree() {

    }

    @Override
    protected String getCaseName() {
        return "NoMatchesTest";
    }
}
