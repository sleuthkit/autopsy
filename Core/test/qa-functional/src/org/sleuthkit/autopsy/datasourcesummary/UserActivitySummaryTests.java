/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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

package org.sleuthkit.autopsy.datasourcesummary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopDomainsResult;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.integrationtesting.IntegrationTest;
import org.sleuthkit.autopsy.integrationtesting.IntegrationTestGroup;

/**
 * Tests for the UserActivitySummary class.
 */
@ServiceProvider(service = IntegrationTestGroup.class)
public class UserActivitySummaryTests implements IntegrationTestGroup {

    /**
     * Runs UserActivitySummary.getRecentDomains for all data sources found in
     * the current case.
     *
     * @return A map where the key is the data source name and the value are the
     *         results of that method.
     */
    @IntegrationTest
    public Map<String, List<Map<String, Object>>> getRecentDomainsTest()
            throws NoCurrentCaseException, TskCoreException, SleuthkitCaseProviderException {

        UserActivitySummary userActivitySummary = new UserActivitySummary();
        Map<String, List<Map<String, Object>>> toRet = new HashMap<>();
        for (Content c : Case.getCurrentCaseThrows().getDataSources()) {
            if (c instanceof DataSource) {
                DataSource ds = (DataSource) c;
                List<Map<String, Object>> thisResult = userActivitySummary.getRecentDomains(ds, 10).stream()
                        .map((TopDomainsResult tdr) -> {
                            return new HashMap<String, Object>() {{
                                put("lastAccessed", tdr.getLastAccessed());
                                put("visitTimes", tdr.getVisitTimes());
                                put("domain", tdr.getDomain());
                            }}; 
                        })
                        .collect(Collectors.toList());
                toRet.put(ds.getName(), thisResult);
            }
        }
        return toRet;

    }

    //...other tests for other methods in the class...
}
