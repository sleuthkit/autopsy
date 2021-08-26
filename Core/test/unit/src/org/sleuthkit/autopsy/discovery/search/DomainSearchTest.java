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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;

public class DomainSearchTest {

    @Test
    public void groupSizes_SingleGroup_ShouldHaveSizeFour() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        DummyKey groupOne = new DummyKey("1");
        Map<GroupKey, List<Result>> dummyData = new HashMap<GroupKey, List<Result>>() {
            {
                put(groupOne, Arrays.asList(
                        DomainSearchTestUtils.mockDomainResult("google.com"),
                        DomainSearchTestUtils.mockDomainResult("yahoo.com"),
                        DomainSearchTestUtils.mockDomainResult("bing.com"),
                        DomainSearchTestUtils.mockDomainResult("amazon.com"))
                );
            }
        };
        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(dummyData);

        DomainSearch domainSearch = new DomainSearch(cache, null, null);
        Map<GroupKey, Integer> sizes = domainSearch.getGroupSizes(null,
                new ArrayList<>(), null, null, null, null, null, new TestSearchContextImpl(false));
        assertEquals(4, sizes.get(groupOne).longValue());
    }

    @Test
    public void groupSizes_MultipleGroups_ShouldHaveCorrectGroupSizes() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        DummyKey groupOne = new DummyKey("1");
        DummyKey groupTwo = new DummyKey("2");
        DummyKey groupThree = new DummyKey("3");

        Map<GroupKey, List<Result>> dummyData = new HashMap<GroupKey, List<Result>>() {
            {
                put(groupOne, Arrays.asList(
                        DomainSearchTestUtils.mockDomainResult("google.com"),
                        DomainSearchTestUtils.mockDomainResult("yahoo.com"),
                        DomainSearchTestUtils.mockDomainResult("bing.com"),
                        DomainSearchTestUtils.mockDomainResult("amazon.com"))
                );
                put(groupTwo, Arrays.asList(
                        DomainSearchTestUtils.mockDomainResult("facebook.com"),
                        DomainSearchTestUtils.mockDomainResult("spotify.com"),
                        DomainSearchTestUtils.mockDomainResult("netbeans.com"))
                );
                put(groupThree, Arrays.asList(
                        DomainSearchTestUtils.mockDomainResult("youtube.com"))
                );
            }
        };

        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(dummyData);

        DomainSearch domainSearch = new DomainSearch(cache, null, null);
        Map<GroupKey, Integer> sizes = domainSearch.getGroupSizes(null,
                new ArrayList<>(), null, null, null, null, null, new TestSearchContextImpl(false));
        assertEquals(4, sizes.get(groupOne).longValue());
        assertEquals(3, sizes.get(groupTwo).longValue());
        assertEquals(1, sizes.get(groupThree).longValue());
    }

    @Test
    public void groupSizes_EmptyGroup_ShouldBeSizeZero() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(new HashMap<>());

        DomainSearch domainSearch = new DomainSearch(cache, null, null);
        Map<GroupKey, Integer> sizes = domainSearch.getGroupSizes(null,
                new ArrayList<>(), null, null, null, null, null, new TestSearchContextImpl(false));
        assertEquals(0, sizes.size());
    }

    @Test
    public void getDomains_SingleGroupFullPage_ShouldContainAllDomains() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        DummyKey groupOne = new DummyKey("1");
        List<Result> domains = Arrays.asList(
                DomainSearchTestUtils.mockDomainResult("google.com"),
                DomainSearchTestUtils.mockDomainResult("yahoo.com"),
                DomainSearchTestUtils.mockDomainResult("bing.com"),
                DomainSearchTestUtils.mockDomainResult("amazon.com"));

        Map<GroupKey, List<Result>> dummyData = new HashMap<GroupKey, List<Result>>() {
            {
                put(groupOne, domains);
            }
        };

        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(dummyData);

        DomainSearch domainSearch = new DomainSearch(cache, null, null);
        List<Result> firstPage = domainSearch.getDomainsInGroup(null,
                new ArrayList<>(), null, null, null, groupOne, 0, 3, null, null, new TestSearchContextImpl(false));
        assertEquals(3, firstPage.size());
        for (int i = 0; i < firstPage.size(); i++) {
            assertEquals(domains.get(i), firstPage.get(i));
        }
    }

    @Test
    public void getDomains_SingleGroupOverSizedPage_ShouldContainAllDomains() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        DummyKey groupOne = new DummyKey("1");
        List<Result> domains = Arrays.asList(
                DomainSearchTestUtils.mockDomainResult("google.com"),
                DomainSearchTestUtils.mockDomainResult("yahoo.com"),
                DomainSearchTestUtils.mockDomainResult("bing.com"),
                DomainSearchTestUtils.mockDomainResult("amazon.com"));

        Map<GroupKey, List<Result>> dummyData = new HashMap<GroupKey, List<Result>>() {
            {
                put(groupOne, domains);
            }
        };

        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(dummyData);

        DomainSearch domainSearch = new DomainSearch(cache, null, null);
        List<Result> firstPage = domainSearch.getDomainsInGroup(null,
                new ArrayList<>(), null, null, null, groupOne, 0, 100, null, null, new TestSearchContextImpl(false));
        assertEquals(4, firstPage.size());
        for (int i = 0; i < firstPage.size(); i++) {
            assertEquals(domains.get(i), firstPage.get(i));
        }
    }

    @Test
    public void getDomains_SingleGroupHalfPage_ShouldContainHalfDomains() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        DummyKey groupOne = new DummyKey("1");
        List<Result> domains = Arrays.asList(
                DomainSearchTestUtils.mockDomainResult("google.com"),
                DomainSearchTestUtils.mockDomainResult("yahoo.com"),
                DomainSearchTestUtils.mockDomainResult("bing.com"),
                DomainSearchTestUtils.mockDomainResult("amazon.com"));

        Map<GroupKey, List<Result>> dummyData = new HashMap<GroupKey, List<Result>>() {
            {
                put(groupOne, domains);
            }
        };

        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(dummyData);

        DomainSearch domainSearch = new DomainSearch(cache, null, null);
        List<Result> firstPage = domainSearch.getDomainsInGroup(null,
                new ArrayList<>(), null, null, null, groupOne, 0, 2, null, null, new TestSearchContextImpl(false));
        assertEquals(2, firstPage.size());
        for (int i = 0; i < firstPage.size(); i++) {
            assertEquals(domains.get(i), firstPage.get(i));
        }
    }

    @Test
    public void getDomains_SingleGroupLastPageLastDomain_ShouldContainLastDomain() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        DummyKey groupOne = new DummyKey("1");
        List<Result> domains = Arrays.asList(
                DomainSearchTestUtils.mockDomainResult("google.com"),
                DomainSearchTestUtils.mockDomainResult("yahoo.com"),
                DomainSearchTestUtils.mockDomainResult("bing.com"),
                DomainSearchTestUtils.mockDomainResult("amazon.com"));

        Map<GroupKey, List<Result>> dummyData = new HashMap<GroupKey, List<Result>>() {
            {
                put(groupOne, domains);
            }
        };

        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(dummyData);

        DomainSearch domainSearch = new DomainSearch(cache, null, null);
        List<Result> firstPage = domainSearch.getDomainsInGroup(null,
                new ArrayList<>(), null, null, null, groupOne, 3, 1, null, null, new TestSearchContextImpl(false));
        assertEquals(1, firstPage.size());
        assertEquals(domains.get(domains.size() - 1), firstPage.get(0));
    }

    @Test
    public void getDomains_SingleGroupOversizedOffset_ShouldContainNoDomains() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        DummyKey groupOne = new DummyKey("1");
        List<Result> domains = Arrays.asList(
                DomainSearchTestUtils.mockDomainResult("google.com"),
                DomainSearchTestUtils.mockDomainResult("yahoo.com"),
                DomainSearchTestUtils.mockDomainResult("bing.com"),
                DomainSearchTestUtils.mockDomainResult("amazon.com"));

        Map<GroupKey, List<Result>> dummyData = new HashMap<GroupKey, List<Result>>() {
            {
                put(groupOne, domains);
            }
        };

        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(dummyData);

        DomainSearch domainSearch = new DomainSearch(cache, null, null);
        List<Result> firstPage = domainSearch.getDomainsInGroup(null,
                new ArrayList<>(), null, null, null, groupOne, 20, 5, null, null, new TestSearchContextImpl(false));
        assertEquals(0, firstPage.size());
    }

    @Test
    public void getDomains_SingleGroupZeroSizedPage_ShouldContainNoDomains() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        DummyKey groupOne = new DummyKey("1");
        List<Result> domains = Arrays.asList(
                DomainSearchTestUtils.mockDomainResult("google.com"),
                DomainSearchTestUtils.mockDomainResult("yahoo.com"),
                DomainSearchTestUtils.mockDomainResult("bing.com"),
                DomainSearchTestUtils.mockDomainResult("amazon.com"));

        Map<GroupKey, List<Result>> dummyData = new HashMap<GroupKey, List<Result>>() {
            {
                put(groupOne, domains);
            }
        };

        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(dummyData);

        DomainSearch domainSearch = new DomainSearch(cache, null, null);
        List<Result> firstPage = domainSearch.getDomainsInGroup(null,
                new ArrayList<>(), null, null, null, groupOne, 0, 0, null, null, new TestSearchContextImpl(false));
        assertEquals(0, firstPage.size());
    }

    @Test
    public void getDomains_MultipleGroupsFullPage_ShouldContainAllDomainsInGroup() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        DummyKey groupOne = new DummyKey("1");
        DummyKey groupTwo = new DummyKey("2");
        DummyKey groupThree = new DummyKey("3");

        Map<GroupKey, List<Result>> dummyData = new HashMap<GroupKey, List<Result>>() {
            {
                put(groupOne, Arrays.asList(
                        DomainSearchTestUtils.mockDomainResult("google.com"),
                        DomainSearchTestUtils.mockDomainResult("yahoo.com"),
                        DomainSearchTestUtils.mockDomainResult("bing.com"),
                        DomainSearchTestUtils.mockDomainResult("amazon.com"))
                );
                put(groupTwo, Arrays.asList(
                        DomainSearchTestUtils.mockDomainResult("facebook.com"),
                        DomainSearchTestUtils.mockDomainResult("spotify.com"),
                        DomainSearchTestUtils.mockDomainResult("netbeans.com"))
                );
                put(groupThree, Arrays.asList(
                        DomainSearchTestUtils.mockDomainResult("youtube.com"))
                );
            }
        };

        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(dummyData);

        DomainSearch domainSearch = new DomainSearch(cache, null, null);
        List<Result> firstPage = domainSearch.getDomainsInGroup(null,
                new ArrayList<>(), null, null, null, groupOne, 0, 3, null, null, new TestSearchContextImpl(false));
        assertEquals(3, firstPage.size());
    }

    @Test
    public void getDomains_MultipleGroupsHalfPage_ShouldContainHalfDomainsInGroup() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        DummyKey groupOne = new DummyKey("1");
        DummyKey groupTwo = new DummyKey("2");
        DummyKey groupThree = new DummyKey("3");

        Map<GroupKey, List<Result>> dummyData = new HashMap<GroupKey, List<Result>>() {
            {
                put(groupOne, Arrays.asList(
                        DomainSearchTestUtils.mockDomainResult("google.com"),
                        DomainSearchTestUtils.mockDomainResult("yahoo.com"),
                        DomainSearchTestUtils.mockDomainResult("bing.com"),
                        DomainSearchTestUtils.mockDomainResult("amazon.com"))
                );
                put(groupTwo, Arrays.asList(
                        DomainSearchTestUtils.mockDomainResult("facebook.com"),
                        DomainSearchTestUtils.mockDomainResult("spotify.com"),
                        DomainSearchTestUtils.mockDomainResult("netbeans.com"))
                );
                put(groupThree, Arrays.asList(
                        DomainSearchTestUtils.mockDomainResult("youtube.com"))
                );
            }
        };

        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(dummyData);

        DomainSearch domainSearch = new DomainSearch(cache, null, null);
        List<Result> firstPage = domainSearch.getDomainsInGroup(null,
                new ArrayList<>(), null, null, null, groupTwo, 1, 2, null, null, new TestSearchContextImpl(false));
        assertEquals(2, firstPage.size());
        for (int i = 0; i < firstPage.size(); i++) {
            assertEquals(dummyData.get(groupTwo).get(i + 1), firstPage.get(i));
        }
    }

    @Test
    public void getDomains_SingleGroupSimulatedPaging_ShouldPageThroughAllDomains() throws DiscoveryException {
        DomainSearchCache cache = mock(DomainSearchCache.class);

        DummyKey groupOne = new DummyKey("1");
        List<Result> domains = Arrays.asList(
                DomainSearchTestUtils.mockDomainResult("google.com"),
                DomainSearchTestUtils.mockDomainResult("yahoo.com"),
                DomainSearchTestUtils.mockDomainResult("bing.com"),
                DomainSearchTestUtils.mockDomainResult("amazon.com"),
                DomainSearchTestUtils.mockDomainResult("facebook.com"),
                DomainSearchTestUtils.mockDomainResult("capitalone.com"),
                DomainSearchTestUtils.mockDomainResult("spotify.com"),
                DomainSearchTestUtils.mockDomainResult("netsuite.com"));

        Map<GroupKey, List<Result>> dummyData = new HashMap<GroupKey, List<Result>>() {
            {
                put(groupOne, domains);
            }
        };

        when(cache.get(isNull(), eq(new ArrayList<>()), isNull(), isNull(), isNull(), isNull(), isNull(), any(SearchContext.class))).thenReturn(dummyData);

        DomainSearch domainSearch = new DomainSearch(cache, null, null);

        int start = 0;
        int size = 2;
        while (start + size <= domains.size()) {
            List<Result> page = domainSearch.getDomainsInGroup(null,
                    new ArrayList<>(), null, null, null, groupOne, start, size, null, null, new TestSearchContextImpl(false));
            assertEquals(2, page.size());
            for (int i = 0; i < page.size(); i++) {
                assertEquals(domains.get(start + i), page.get(i));
            }

            start += size;
        }
    }

    private class DummyKey extends GroupKey {

        private final String name;

        DummyKey(String name) {
            this.name = name;
        }

        @Override
        String getDisplayName() {
            return name;
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey instanceof GroupKey) {
                return this.getDisplayName().equals(((GroupKey) otherKey).getDisplayName());
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }

        @Override
        public int compareTo(GroupKey o) {
            return this.getDisplayName().compareTo(o.getDisplayName());
        }
    }
}
