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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import static org.junit.Assert.fail;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopAccountResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopDeviceAttachedResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopDomainsResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopProgramsResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopWebSearchResult;
import org.sleuthkit.autopsy.testutils.TskMockUtils;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Tests for UserActivitySummary.
 */
public class UserActivitySummaryTest {

    private interface DataFunction<T> {

        List<T> retrieve(UserActivitySummary userActivitySummary, DataSource datasource, int count) throws
                SleuthkitCaseProviderException, TskCoreException;
    }

    private static final DataFunction<TopWebSearchResult> WEB_SEARCH_QUERY
            = (userActivity, dataSource, count) -> userActivity.getMostRecentWebSearches(dataSource, count);

    private static final DataFunction<TopAccountResult> ACCOUNT_QUERY
            = (userActivity, dataSource, count) -> userActivity.getRecentAccounts(dataSource, count);

    private static final DataFunction<TopDomainsResult> DOMAINS_QUERY
            = (userActivity, dataSource, count) -> userActivity.getRecentDomains(dataSource, count);

    private static final DataFunction<TopDeviceAttachedResult> DEVICE_QUERY
            = (userActivity, dataSource, count) -> userActivity.getRecentDevices(dataSource, count);

    private static final DataFunction<TopProgramsResult> PROGRAMS_QUERY
            = (userActivity, dataSource, count) -> userActivity.getTopPrograms(dataSource, count);

    private static final Map<String, DataFunction<?>> USER_ACTIVITY_METHODS = new HashMap<String, DataFunction<?>>() {
        {
            put("getMostRecentWebSearches", WEB_SEARCH_QUERY);
            put("getRecentAccounts", ACCOUNT_QUERY);
            put("getRecentDomains", DOMAINS_QUERY);
            put("getRecentDevices", DEVICE_QUERY);
            put("getTopPrograms", PROGRAMS_QUERY);
        }
    };

    private static final BlackboardArtifact.Type ACCOUNT_TYPE = new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_ACCOUNT);
    private static final BlackboardAttribute.Type TYPE_ACCOUNT_TYPE = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE);

    private static final long DAY_SECONDS = 24 * 60 * 60;

    private static Pair<SleuthkitCase, Blackboard> getArtifactsTSKMock(List<BlackboardArtifact> returnArr) throws TskCoreException {
        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        Blackboard mockBlackboard = mock(Blackboard.class);
        when(mockCase.getBlackboard()).thenReturn(mockBlackboard);
        when(mockBlackboard.getArtifacts(anyInt(), anyLong())).thenReturn(returnArr);
        return Pair.of(mockCase, mockBlackboard);
    }

    private static void verifyCalled(Blackboard mockBlackboard, int artifactType, long datasourceId, String failureMessage) throws TskCoreException {
        verify(mockBlackboard, times(1).description(failureMessage)).getArtifacts(artifactType, datasourceId);
    }

    private static UserActivitySummary getTestClass(SleuthkitCase tskCase, boolean hasTranslation, Function<String, String> translateFunction)
            throws NoServiceProviderException, TranslationException {

        return new UserActivitySummary(
                () -> tskCase,
                TskMockUtils.getTextTranslationService(translateFunction, hasTranslation),
                TskMockUtils.getJavaLogger("UNIT TEST LOGGER")
        );
    }

    private <T> void testMinCount(DataFunction<T> funct, String id)
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {

        for (int count : new int[]{0, -1}) {
            Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(null);
            UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

            try {
                funct.retrieve(summary, TskMockUtils.getDataSource(1), -1);
            } catch (IllegalArgumentException ignored) {
                // this exception is expected so continue if getArtifacts never called
                verify(tskPair.getRight(), never().description(
                        String.format("Expected %s would not call getArtifacts for count %d", id, count)))
                        .getArtifacts(anyInt(), anyLong());

                continue;
            }
            fail(String.format("Expected an Illegal argument exception to be thrown in method %s with count of %d", id, count));
        }
    }

    @Test
    public void testMinCountInvariant()
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {

        for (Entry<String, DataFunction<?>> query : USER_ACTIVITY_METHODS.entrySet()) {
            testMinCount(query.getValue(), query.getKey());
        }
    }

    private <T> void testNullDataSource(DataFunction<T> funct, String id)
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(null);
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);
        List<T> retArr = funct.retrieve(summary, null, 10);
        verify(tskPair.getRight(), never()
                .description(String.format("Expected method %s to return empty list for null data source and not call SleuthkitCase", id)))
                .getArtifacts(anyInt(), anyLong());

        String errorMessage = String.format("Expected %s would return empty list for null data source", id);
        Assert.assertTrue(errorMessage, retArr != null);
        Assert.assertTrue(errorMessage, retArr.isEmpty());
    }

    @Test
    public void testNullDataSource()
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {

        for (Entry<String, DataFunction<?>> query : USER_ACTIVITY_METHODS.entrySet()) {
            testNullDataSource(query.getValue(), query.getKey());
        }
    }

    private <T> void testNoResultsReturned(DataFunction<T> funct, String id)
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {
        long dataSourceId = 1;
        int count = 10;
        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(new ArrayList<>());
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);
        List<T> retArr = funct.retrieve(summary, TskMockUtils.getDataSource(dataSourceId), count);

        Assert.assertTrue(String.format("Expected non null empty list returned from %s", id), retArr != null);
        Assert.assertTrue(String.format("Expected non null empty list returned from %s", id), retArr.isEmpty());
    }

    @Test
    public void testNoResultsReturned()
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {

        for (Entry<String, DataFunction<?>> query : USER_ACTIVITY_METHODS.entrySet()) {
            testNoResultsReturned(query.getValue(), query.getKey());
        }
    }

    private static List<String> EXCLUDED_DEVICES = Arrays.asList("ROOT_HUB", "ROOT_HUB20");

    private static BlackboardArtifact getRecentDeviceArtifact(long artifactId, DataSource dataSource,
            String deviceId, String deviceMake, String deviceModel, Long date) {

        try {
            return TskMockUtils.getArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_DEVICE_ATTACHED), artifactId, dataSource,
                    TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_ID, deviceId),
                    TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, date),
                    TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE, deviceMake),
                    TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL, deviceModel)
            );
        } catch (TskCoreException e) {
            fail("Something went wrong while mocking");
            return null;
        }
    }

    @Test
    public void getRecentDevices_appropriateFiltering() throws TskCoreException, NoServiceProviderException,
            SleuthkitCaseProviderException, TranslationException {

        long dataSourceId = 1;
        int count = 10;
        long time = DAY_SECONDS * 42;
        String acceptedDevice = "ACCEPTED DEVICE";

        DataSource ds = TskMockUtils.getDataSource(dataSourceId);

        List<String> allKeys = new ArrayList<>(EXCLUDED_DEVICES);
        allKeys.add(acceptedDevice);

        List<BlackboardArtifact> artifacts = IntStream.range(0, allKeys.size())
                .mapToObj((idx) -> {
                    String key = allKeys.get(idx);
                    return getRecentDeviceArtifact(1000L + idx, ds, "ID " + key, "MAKE " + key, key, time);
                })
                .collect(Collectors.toList());

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(artifacts);
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

        List<TopDeviceAttachedResult> results = summary.getRecentDevices(ds, count);

        verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID(), dataSourceId,
                "Expected getRecentDevices to call getArtifacts with correct arguments.");
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(acceptedDevice, results.get(0).getDeviceModel());
        Assert.assertEquals("MAKE " + acceptedDevice, results.get(0).getDeviceMake());
        Assert.assertEquals("ID " + acceptedDevice, results.get(0).getDeviceId());
        Assert.assertEquals(time, results.get(0).getDateAccessed().getTime() / 1000);
    }

    @Test
    public void getRecentDevices_limitedToCount()
            throws TskCoreException, NoServiceProviderException, SleuthkitCaseProviderException, TskCoreException, TranslationException {

        int countRequested = 10;
        for (int returnedCount : new int[]{1, 9, 10, 11}) {
            long dataSourceId = 1L;
            DataSource dataSource = TskMockUtils.getDataSource(dataSourceId);

            List<BlackboardArtifact> returnedArtifacts = IntStream.range(0, returnedCount)
                    .mapToObj((idx) -> getRecentDeviceArtifact(1000 + idx, dataSource, "ID" + idx, "MAKE" + idx, "MODEL" + idx, DAY_SECONDS * idx))
                    .collect(Collectors.toList());

            Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(returnedArtifacts);
            UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

            List<TopDeviceAttachedResult> results = summary.getRecentDevices(dataSource, countRequested);
            verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID(), dataSourceId,
                    "Expected getRecentDevices to call getArtifacts with correct arguments.");

            Assert.assertEquals(Math.min(countRequested, returnedCount), results.size());
        }
    }

    private static BlackboardArtifact getWebSearchArtifact(long artifactId, DataSource dataSource, String query, Long date) {
        try {
            return TskMockUtils.getArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY), artifactId, dataSource,
                    TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_TEXT, query),
                    TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, date)
            );
        } catch (TskCoreException e) {
            fail("Something went wrong while mocking");
            return null;
        }
    }

    @Test
    public void getMostRecentWebSearches_grouping() throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {
        long dataSourceId = 1;
        DataSource ds = TskMockUtils.getDataSource(dataSourceId);

        String query1 = "This is Query 1";
        String query2 = "This is Query 2";
        BlackboardArtifact art1a = getWebSearchArtifact(1001, ds, query1, DAY_SECONDS * 1);
        BlackboardArtifact art2a = getWebSearchArtifact(1002, ds, query2, DAY_SECONDS * 2);
        BlackboardArtifact art2b = getWebSearchArtifact(1003, ds, query2.toUpperCase(), DAY_SECONDS * 3);
        BlackboardArtifact art1b = getWebSearchArtifact(1004, ds, query1.toUpperCase(), DAY_SECONDS * 4);
        BlackboardArtifact art1c = getWebSearchArtifact(1005, ds, query1.toLowerCase(), DAY_SECONDS * 5);

        List<BlackboardArtifact> artList = Arrays.asList(art1a, art2a, art2b, art1b, art1c);

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(artList);
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);
        List<TopWebSearchResult> results = summary.getMostRecentWebSearches(ds, 10);
        verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID(), dataSourceId,
                "Expected getRecentDevices to call getArtifacts with correct arguments.");

        Assert.assertEquals("Expected two different search queries", 2, results.size());
        Assert.assertTrue(query1.equalsIgnoreCase(results.get(0).getSearchString()));
        Assert.assertEquals(DAY_SECONDS * 5, results.get(0).getDateAccessed().getTime() / 1000);
        Assert.assertTrue(query2.equalsIgnoreCase(results.get(1).getSearchString()));
        Assert.assertEquals(DAY_SECONDS * 3, results.get(1).getDateAccessed().getTime() / 1000);
    }

    private void webSearchTranslationTest(List<String> queries, boolean hasProvider, String translationSuffix)
            throws SleuthkitCaseProviderException, TskCoreException, NoServiceProviderException, TranslationException {

        long dataSourceId = 1;
        DataSource ds = TskMockUtils.getDataSource(dataSourceId);

        List<BlackboardArtifact> artList = IntStream.range(0, queries.size())
                .mapToObj((idx) -> getWebSearchArtifact(1000 + idx, ds, queries.get(idx), DAY_SECONDS * (queries.size() - idx)))
                .collect(Collectors.toList());

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(artList);

        Function<String, String> translator = (orig) -> {
            if (orig == null || translationSuffix == null) {
                return null;
            } else {
                return orig + translationSuffix;
            }
        };

        TextTranslationService translationService = TskMockUtils.getTextTranslationService(translator, hasProvider);

        UserActivitySummary summary = new UserActivitySummary(
                () -> tskPair.getLeft(),
                translationService,
                TskMockUtils.getJavaLogger("UNIT TEST LOGGER")
        );

        List<TopWebSearchResult> results = summary.getMostRecentWebSearches(ds, queries.size());

        if (hasProvider) {
            verify(translationService,
                    times(queries.size()).description("Expected translation to be called for each query"))
                    .translate(anyString());
        } else {
            verify(translationService,
                    never().description("Expected translation not to be called because no provider"))
                    .translate(anyString());
        }

        Assert.assertEquals(queries.size(), results.size());

        for (int i = 0; i < queries.size(); i++) {
            String query = queries.get(i);
            TopWebSearchResult result = results.get(i);

            Assert.assertTrue(query.equalsIgnoreCase(result.getSearchString()));
            if (hasProvider) {
                if (StringUtils.isBlank(translationSuffix)) {
                    Assert.assertNull(result.getTranslatedResult());
                } else {
                    Assert.assertTrue((query + translationSuffix).equalsIgnoreCase(result.getTranslatedResult()));
                }
            } else {
                Assert.assertNull(result.getTranslatedResult());
            }
        }
    }

    @Test
    public void getMostRecentWebSearches_handlesTranslation()
            throws SleuthkitCaseProviderException, TskCoreException, NoServiceProviderException, TranslationException {

        List<String> queryList = Arrays.asList("query1", "query2", "query3");
        String translationSuffix = " [TRANSLATED]";
        webSearchTranslationTest(queryList, false, translationSuffix);
        webSearchTranslationTest(queryList, true, null);
        webSearchTranslationTest(queryList, true, "");
        webSearchTranslationTest(queryList, true, "    ");
        webSearchTranslationTest(queryList, true, translationSuffix);
    }

    @Test
    public void getMostRecentWebSearches_limitedToCount()
            throws TskCoreException, NoServiceProviderException, SleuthkitCaseProviderException, TskCoreException, TranslationException {

        int countRequested = 10;
        for (int returnedCount : new int[]{1, 9, 10, 11}) {
            long dataSourceId = 1L;
            DataSource dataSource = TskMockUtils.getDataSource(dataSourceId);

            List<BlackboardArtifact> returnedArtifacts = IntStream.range(0, returnedCount)
                    .mapToObj((idx) -> getWebSearchArtifact(1000 + idx, dataSource, "Query" + idx, DAY_SECONDS * idx + 1))
                    .collect(Collectors.toList());

            Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(returnedArtifacts);
            UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

            List<TopWebSearchResult> results = summary.getMostRecentWebSearches(dataSource, countRequested);
            verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID(), dataSourceId,
                    "Expected getRecentDevices to call getArtifacts with correct arguments.");

            Assert.assertEquals(Math.min(countRequested, returnedCount), results.size());
        }
    }

    private BlackboardArtifact getDomainsArtifact(DataSource dataSource, long id, String domain, Long time) {
        List<BlackboardAttribute> attributes = new ArrayList<>();
        if (domain != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, domain));
        }

        if (time != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, time));
        }

        try {
            return TskMockUtils.getArtifact(
                    new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_WEB_HISTORY), id, dataSource,
                    attributes);
        } catch (TskCoreException e) {
            fail("TskCoreException occurred while trying to mock a blackboard artifact");
            return null;
        }
    }

    private static final long DOMAIN_WINDOW_DAYS = 30;

    @Test
    public void getRecentDomains_withinTimeWIndow() throws TskCoreException, SleuthkitCaseProviderException, NoServiceProviderException, TranslationException {
        long dataSourceId = 1;
        DataSource dataSource = TskMockUtils.getDataSource(dataSourceId);
        String domain1 = "www.google.com";
        String domain2 = "www.basistech.com";
        String domain3 = "www.github.com";
        String domain4 = "www.stackoverflow.com";

        BlackboardArtifact artifact1 = getDomainsArtifact(dataSource, 1000, domain1, DAY_SECONDS * DOMAIN_WINDOW_DAYS * 2);
        BlackboardArtifact artifact1a = getDomainsArtifact(dataSource, 10001, domain1, DAY_SECONDS * DOMAIN_WINDOW_DAYS);

        BlackboardArtifact artifact2 = getDomainsArtifact(dataSource, 1001, domain2, DAY_SECONDS * DOMAIN_WINDOW_DAYS - 1);

        BlackboardArtifact artifact3 = getDomainsArtifact(dataSource, 1002, domain3, DAY_SECONDS * DOMAIN_WINDOW_DAYS);
        BlackboardArtifact artifact3a = getDomainsArtifact(dataSource, 10021, domain3, 1L);

        BlackboardArtifact artifact4 = getDomainsArtifact(dataSource, 1003, domain4, 1L);

        List<BlackboardArtifact> retArr = Arrays.asList(artifact1, artifact1a, artifact2, artifact3, artifact3a, artifact4);

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(retArr);

        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

        List<TopDomainsResult> domains = summary.getRecentDomains(dataSource, 10);

        verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID(), dataSourceId,
                "Expected getRecentDomains to call getArtifacts with correct arguments.");

        Assert.assertEquals(2, domains.size());

        Assert.assertTrue("Expected " + domain1 + " to be first domain", domain1.equalsIgnoreCase(domains.get(0).getDomain()));
        Assert.assertEquals(DAY_SECONDS * DOMAIN_WINDOW_DAYS * 2, domains.get(0).getLastVisit().getTime() / 1000);
        Assert.assertEquals((Long) 2L, domains.get(0).getVisitTimes());

        Assert.assertTrue("Expected " + domain3 + " to be second domain", domain3.equalsIgnoreCase(domains.get(1).getDomain()));
        Assert.assertEquals(DAY_SECONDS * DOMAIN_WINDOW_DAYS, domains.get(1).getLastVisit().getTime() / 1000);
        Assert.assertEquals((Long) 1L, domains.get(1).getVisitTimes());
    }

    @Test
    public void getRecentDomains_appropriatelyFiltered() throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {
        long dataSourceId = 1;
        DataSource dataSource = TskMockUtils.getDataSource(dataSourceId);
        String domain1 = "www.google.com";

        // excluded
        String domain2 = "localhost";
        String domain3 = "127.0.0.1";

        BlackboardArtifact artifact1 = getDomainsArtifact(dataSource, 1000, domain1, DAY_SECONDS);
        BlackboardArtifact artifact2 = getDomainsArtifact(dataSource, 1001, domain2, DAY_SECONDS * 2);
        BlackboardArtifact artifact3 = getDomainsArtifact(dataSource, 1002, domain3, DAY_SECONDS * 3);

        List<BlackboardArtifact> retArr = Arrays.asList(artifact1, artifact2, artifact3);

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(retArr);

        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

        List<TopDomainsResult> domains = summary.getRecentDomains(dataSource, 10);

        verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID(), dataSourceId,
                "Expected getRecentDomains to call getArtifacts with correct arguments.");

        Assert.assertEquals(1, domains.size());

        Assert.assertTrue("Expected " + domain1 + " to be most recent domain", domain1.equalsIgnoreCase(domains.get(0).getDomain()));
        Assert.assertEquals(DAY_SECONDS, domains.get(0).getLastVisit().getTime() / 1000);
    }

    @Test
    public void getRecentDomains_groupedAppropriately() throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {
        long dataSourceId = 1;
        DataSource dataSource = TskMockUtils.getDataSource(dataSourceId);
        String domain1 = "www.google.com";
        String domain2 = "www.basistech.com";

        BlackboardArtifact artifact1 = getDomainsArtifact(dataSource, 1000, domain1, 1L);
        BlackboardArtifact artifact1a = getDomainsArtifact(dataSource, 1001, domain1, 6L);
        BlackboardArtifact artifact2 = getDomainsArtifact(dataSource, 1002, domain2, 2L);
        BlackboardArtifact artifact2a = getDomainsArtifact(dataSource, 1003, domain2, 3L);
        BlackboardArtifact artifact2b = getDomainsArtifact(dataSource, 1004, domain2, 4L);
        
        List<BlackboardArtifact> retArr = Arrays.asList(artifact1, artifact1a, artifact2, artifact2a, artifact2b);

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(retArr);
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

        List<TopDomainsResult> domains = summary.getRecentDomains(dataSource, 10);

        verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID(), dataSourceId,
                "Expected getRecentDomains to call getArtifacts with correct arguments.");

        Assert.assertEquals(2, domains.size());

        Assert.assertTrue(domain1.equalsIgnoreCase(domains.get(1).getDomain()));
        Assert.assertEquals(6L, domains.get(1).getLastVisit().getTime() / 1000);
        Assert.assertEquals((Long) 2L, domains.get(1).getVisitTimes());

        Assert.assertTrue(domain2.equalsIgnoreCase(domains.get(0).getDomain()));
        Assert.assertEquals(4L, domains.get(0).getLastVisit().getTime() / 1000);
        Assert.assertEquals((Long) 3L, domains.get(0).getVisitTimes());
    }

    @Test
    public void getRecentDomains_limitedAppropriately() 
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {
        
        int countRequested = 10;
        for (int returnedCount : new int[]{1, 9, 10, 11}) {
            long dataSourceId = 1L;
            DataSource dataSource = TskMockUtils.getDataSource(dataSourceId);

            // create a list where there are 1 accesses for first, 2 for second, etc.
            List<BlackboardArtifact> returnedArtifacts = IntStream.range(0, returnedCount)
                    .mapToObj((idx) -> {
                        return IntStream.range(0, idx + 1)
                                .mapToObj((numIdx) -> {
                                    int hash = 100 * idx + numIdx;
                                    return getDomainsArtifact(dataSource, 1000 + hash, "Domain " + idx, 10L);
                                });
                    })
                    .flatMap((s) -> s)
                    .collect(Collectors.toList());
            
            
            Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(returnedArtifacts);
            UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

            List<TopDomainsResult> results = summary.getRecentDomains(dataSource, countRequested);
            verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID(), dataSourceId,
                    "Expected getRecentDevices to call getArtifacts with correct arguments.");

            Assert.assertEquals(Math.min(countRequested, returnedCount), results.size());
        }
    }
}
