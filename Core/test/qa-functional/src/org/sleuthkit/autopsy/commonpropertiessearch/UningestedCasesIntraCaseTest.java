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
import java.util.Map;
import static junit.framework.Assert.assertEquals;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import junit.framework.Assert;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.commonpropertiessearch.AllIntraCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonpropertiessearch.CommonAttributeCountSearchResults;
import org.sleuthkit.autopsy.commonpropertiessearch.IntraCaseCommonAttributeSearcher;
import org.sleuthkit.autopsy.commonpropertiessearch.SingleIntraCaseCommonAttributeSearcher;
import static org.sleuthkit.autopsy.commonpropertiessearch.IntraCaseTestUtils.SET1;
import static org.sleuthkit.autopsy.commonpropertiessearch.IntraCaseTestUtils.getDataSourceIdByName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Test that cases which are created but have not run any ingest modules turn up
 * no results.
 *
 * Setup:
 *
 * Add images set 1, set 2, set 3, and set 4 to case. Do not ingest.
 *
 */
public class UningestedCasesIntraCaseTest extends NbTestCase {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(UningestedCasesIntraCaseTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    private final IntraCaseTestUtils utils;
    
    public UningestedCasesIntraCaseTest(String name) {
        super(name);
        
        this.utils = new IntraCaseTestUtils(this, "UningestedCasesTests");
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

            IntraCaseCommonAttributeSearcher allSourcesBuilder = new AllIntraCaseCommonAttributeSearcher(dataSources, false, false, 0);
            CommonAttributeCountSearchResults metadata = allSourcesBuilder.findMatchesByCount();

            int resultCount = metadata.size();
            assertEquals(resultCount, 0);

        } catch (TskCoreException | NoCurrentCaseException | SQLException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Find all matches on image #1 & all file types. Confirm no matches.
     */
    public void testTwo() {
        try {
            Map<Long, String> dataSources = this.utils.getDataSourceMap();
            Long first = getDataSourceIdByName(SET1, dataSources);

            IntraCaseCommonAttributeSearcher singleSourceBuilder = new SingleIntraCaseCommonAttributeSearcher(first, dataSources, false, false, 0);
            CommonAttributeCountSearchResults metadata = singleSourceBuilder.findMatchesByCount();

            int resultCount = metadata.size();
            assertEquals(resultCount, 0);

        } catch (TskCoreException | NoCurrentCaseException | SQLException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex.getMessage());
        }
    }
}
