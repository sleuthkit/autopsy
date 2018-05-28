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

import java.util.Map;
import static junit.framework.Assert.assertEquals;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.commonfilesearch.AllDataSourcesCommonFilesAlgorithm;
import org.sleuthkit.autopsy.commonfilesearch.CommonFilesMetadata;
import org.sleuthkit.autopsy.commonfilesearch.CommonFilesMetadataBuilder;
import org.sleuthkit.autopsy.commonfilesearch.SingleDataSource;
import static org.sleuthkit.autopsy.commonfilessearch.IntraCaseUtils.SET1;
import static org.sleuthkit.autopsy.commonfilessearch.IntraCaseUtils.getDataSourceIdByName;

/**
 * Test that cases which are created but have not run any ingest modules turn up
 * no results.
 *
 * Setup:
 *
 * Add images set 1, set 2, set 3, and set 4 to case. Do not ingest.
 *
 */
public class UningestedCases extends NbTestCase {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(UningestedCases.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    private final IntraCaseUtils utils;
    
    public UningestedCases(String name) {
        super(name);
        
        this.utils = new IntraCaseUtils(this, "UningestedCasesTests");
    }
    
    @Override
    public void setUp(){
        this.utils.setUp();
    }
    
    @Override
    public void tearDown(){
        this.utils.tearDown();
    }

    /**
     * Find all matches & all file types. Confirm no matches are found (since
     * there are no hashes to match).
     */
    public void testOne() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();

            CommonFilesMetadataBuilder allSourcesBuilder = new AllDataSourcesCommonFilesAlgorithm(dataSources, false, false);
            CommonFilesMetadata metadata = allSourcesBuilder.findFiles();

            int resultCount = metadata.size();
            assertEquals(resultCount, 0);

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Find all matches on image #1 & all file types. Confirm no matches.
     */
    public void testTwo() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            Long first = getDataSourceIdByName(SET1, dataSources);

            CommonFilesMetadataBuilder singleSourceBuilder = new SingleDataSource(first, dataSources, false, false);
            CommonFilesMetadata metadata = singleSourceBuilder.findFiles();

            int resultCount = metadata.size();
            assertEquals(resultCount, 0);

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }
}
