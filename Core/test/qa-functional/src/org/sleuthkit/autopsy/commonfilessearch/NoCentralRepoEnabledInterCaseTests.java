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

import java.io.IOException;
import java.util.Map;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.sleuthkit.datamodel.TskCoreException;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.commonfilesearch.AllCasesEamDbCommonFilesAlgorithm;
import org.sleuthkit.autopsy.commonfilesearch.CommonFilesMetadata;
import org.sleuthkit.autopsy.commonfilesearch.CommonFilesMetadataBuilder;

/**
 *
 * Just make sure nothing explodes when we run the feature in the absence of 
 * the Central Repo.  This should be considered 'defensive' as it should not be 
 * possible to even run the feature if the CR is not available.
 *
 */
public class NoCentralRepoEnabledInterCaseTests extends NbTestCase {

    private final InterCaseUtils utils;
    private Case currentCase;       //TODO do we need this???

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(NoCentralRepoEnabledInterCaseTests.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public NoCentralRepoEnabledInterCaseTests(String name) {
        super(name);
        this.utils = new InterCaseUtils(this);
    }

    @Override
    public void setUp() {
        try {
            this.currentCase = this.utils.createCases(this.utils.getIngestSettingsForHashAndFileType(), InterCaseUtils.CASE1);
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    @Override
    public void tearDown() {
        this.utils.tearDown();
    }

    public void testOne() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();

            CommonFilesMetadataBuilder builder = new AllCasesEamDbCommonFilesAlgorithm(dataSources, false, false);

            CommonFilesMetadata metadata = builder.findFiles();

            assertTrue("Should be no results.", metadata.size() == 0);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
}
