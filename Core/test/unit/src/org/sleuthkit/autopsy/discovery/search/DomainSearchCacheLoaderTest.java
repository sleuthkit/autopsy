/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.search;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
import org.junit.Test;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.SearchKey;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CATEGORIZATION;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

public class DomainSearchCacheLoaderTest {

    @Test
    public void load_GroupByDataSourceSortByGroupNameAndDomain() throws DiscoveryException, TskCoreException, SQLException, InterruptedException {
        List<Result> domains = Arrays.asList(
                DomainSearchTestUtils.mockDomainResult("google.com", 10, 100, 3, 5, 4, 110),
                DomainSearchTestUtils.mockDomainResult("yahoo.com", 1, 5, 3, 7, 20, 100),
                DomainSearchTestUtils.mockDomainResult("google.com", 5, 20, 3, 1, 4, 105),
                DomainSearchTestUtils.mockDomainResult("facebook.com", 2, 2, 3, 1, 3, 110),
                DomainSearchTestUtils.mockDomainResult("abc.com", 1, 2, 3, 3, 4, 100),
                DomainSearchTestUtils.mockDomainResult("xyz.com", 1, 2, 3, 3, 4, 20)
        );

        SleuthkitCase caseDb = mock(SleuthkitCase.class);
        when(caseDb.getBlackboardArtifacts(TSK_WEB_CATEGORIZATION)).thenReturn(new ArrayList<>());

        SearchKey key = new SearchKey(null, new ArrayList<>(),
                new DiscoveryAttributes.DataSourceAttribute(),
                Group.GroupSortingAlgorithm.BY_GROUP_NAME,
                ResultsSorter.SortingMethod.BY_DOMAIN_NAME,
                caseDb, null, new TestSearchContextImpl(false));

        DomainSearchCacheLoader loader = mock(DomainSearchCacheLoader.class);
        when(loader.getResultDomainsFromDatabase(key)).thenReturn(domains);
        when(loader.load(key)).thenCallRealMethod();
        Map<GroupKey, List<Result>> results = loader.load(key);
        assertEquals(4, results.size());
        for (List<Result> group : results.values()) {
            ResultDomain previous = null;
            for (Result result : group) {
                ResultDomain current = (ResultDomain) result;
                if (previous != null) {
                    assertTrue(previous.getDomain().compareTo(current.getDomain()) < 0);
                }
                previous = current;
            }
        }
    }

    @Test
    public void load_GroupByNothingByGroupNameAndDomain() throws DiscoveryException, TskCoreException, SQLException, InterruptedException {
        List<Result> domains = Arrays.asList(
                DomainSearchTestUtils.mockDomainResult("google.com", 10, 100, 1, 5, 4, 110),
                DomainSearchTestUtils.mockDomainResult("yahoo.com", 1, 5, 1, 7, 20, 100),
                DomainSearchTestUtils.mockDomainResult("facebook.com", 2, 2, 1, 1, 3, 110),
                DomainSearchTestUtils.mockDomainResult("abc.com", 1, 2, 1, 3, 4, 100),
                DomainSearchTestUtils.mockDomainResult("xyz.com", 1, 2, 1, 3, 4, 20)
        );

        SleuthkitCase caseDb = mock(SleuthkitCase.class);
        when(caseDb.getBlackboardArtifacts(TSK_WEB_CATEGORIZATION)).thenReturn(new ArrayList<>());

        SearchKey key = new SearchKey(null, new ArrayList<>(),
                new DiscoveryAttributes.NoGroupingAttribute(),
                Group.GroupSortingAlgorithm.BY_GROUP_NAME,
                ResultsSorter.SortingMethod.BY_DOMAIN_NAME,
                caseDb, null, new TestSearchContextImpl(false));

        DomainSearchCacheLoader loader = mock(DomainSearchCacheLoader.class);
        when(loader.getResultDomainsFromDatabase(key)).thenReturn(domains);
        when(loader.load(key)).thenCallRealMethod();
        Map<GroupKey, List<Result>> results = loader.load(key);
        assertEquals(1, results.size());
        for (List<Result> group : results.values()) {
            ResultDomain previous = null;
            for (Result result : group) {
                ResultDomain current = (ResultDomain) result;
                if (previous != null) {
                    assertTrue(previous.getDomain().compareTo(current.getDomain()) < 0);
                }
                previous = current;
            }
        }
    }

    @Test
    public void load_GroupByNothingSortByNameAndDataSource() throws DiscoveryException, TskCoreException, SQLException, InterruptedException {
        List<Result> domains = Arrays.asList(
                DomainSearchTestUtils.mockDomainResult("google.com", 10, 100, 7, 5, 4, 110),
                DomainSearchTestUtils.mockDomainResult("yahoo.com", 1, 5, 7, 7, 20, 100)
        );

        SleuthkitCase caseDb = mock(SleuthkitCase.class);
        when(caseDb.getBlackboardArtifacts(TSK_WEB_CATEGORIZATION)).thenReturn(new ArrayList<>());

        SearchKey key = new SearchKey(null, new ArrayList<>(),
                new DiscoveryAttributes.NoGroupingAttribute(),
                Group.GroupSortingAlgorithm.BY_GROUP_NAME,
                ResultsSorter.SortingMethod.BY_DATA_SOURCE,
                caseDb, null, new TestSearchContextImpl(false));

        DomainSearchCacheLoader loader = mock(DomainSearchCacheLoader.class);
        when(loader.getResultDomainsFromDatabase(key)).thenReturn(domains);
        when(loader.load(key)).thenCallRealMethod();

        Map<GroupKey, List<Result>> results = loader.load(key);
        assertEquals(1, results.size());
        for (List<Result> group : results.values()) {
            ResultDomain previous = null;
            for (Result result : group) {
                ResultDomain current = (ResultDomain) result;
                if (previous != null) {
                    assertTrue(Long.compare(previous.getDataSource().getId(), current.getDataSource().getId()) < 0);
                }
                previous = current;
            }
        }
    }

    @Test
    public void load_GroupByDataSourceBySizeAndName() throws DiscoveryException, TskCoreException, SQLException, InterruptedException {
        List<Result> domains = Arrays.asList(
                DomainSearchTestUtils.mockDomainResult("google.com", 10, 100, 7, 5, 4, 110),
                DomainSearchTestUtils.mockDomainResult("yahoo.com", 1, 5, 7, 7, 20, 100)
        );

        SleuthkitCase caseDb = mock(SleuthkitCase.class);
        when(caseDb.getBlackboardArtifacts(TSK_WEB_CATEGORIZATION)).thenReturn(new ArrayList<>());

        SearchKey key = new SearchKey(null, new ArrayList<>(),
                new DiscoveryAttributes.DataSourceAttribute(),
                Group.GroupSortingAlgorithm.BY_GROUP_SIZE,
                ResultsSorter.SortingMethod.BY_DOMAIN_NAME,
                caseDb, null, new TestSearchContextImpl(false));

        DomainSearchCacheLoader loader = mock(DomainSearchCacheLoader.class);
        when(loader.getResultDomainsFromDatabase(key)).thenReturn(domains);
        when(loader.load(key)).thenCallRealMethod();
        Map<GroupKey, List<Result>> results = loader.load(key);
        assertEquals(2, results.size());
        for (List<Result> group : results.values()) {
            ResultDomain previous = null;
            for (Result result : group) {
                ResultDomain current = (ResultDomain) result;
                if (previous != null) {
                    assertTrue(previous.getDomain().compareTo(current.getDomain()) < 0);
                }
                previous = current;
            }
        }
    }

}
