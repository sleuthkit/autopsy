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
import java.util.Collections;
import java.util.Date;
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
import static org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceSummaryMockUtils.getArtifactsTSKMock;
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

    /**
     * Function to retrieve data from UserActivitySummary with the provided
     * arguments.
     */
    private interface DataFunction<T> {

        /**
         * A UserActivitySummary method encapsulated in a uniform manner.
         *
         * @param userActivitySummary The UserActivitySummary class to use.
         * @param datasource The data source.
         * @param count The count.
         * @return The list of objects to return.
         * @throws SleuthkitCaseProviderException
         * @throws TskCoreException
         */
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

    private static final long DAY_SECONDS = 24 * 60 * 60;

    private static void verifyCalled(Blackboard mockBlackboard, int artifactType, long datasourceId, String failureMessage) throws TskCoreException {
        verify(mockBlackboard, times(1).description(failureMessage)).getArtifacts(artifactType, datasourceId);
    }

    /**
     * Gets a UserActivitySummary class to test.
     *
     * @param tskCase The SleuthkitCase.
     * @param hasTranslation Whether the translation service is functional.
     * @param translateFunction Function for translation.
     *
     * @return The UserActivitySummary class to use for testing.
     *
     * @throws NoServiceProviderException
     * @throws TranslationException
     */
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

    /**
     * Ensures that passing a non-positive count causes
     * IllegalArgumentException.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
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

    /**
     * If datasource is null, all methods return an empty list.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
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

    /**
     * If no artifacts in SleuthkitCase, all data returning methods return an
     * empty list.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
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

    /**
     * Tests that UserActivitySummary.getRecentDevices removes things like
     * ROOT_HUB. See EXCLUDED_DEVICES for excluded items.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws SleuthkitCaseProviderException
     * @throws TranslationException
     */
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
        Assert.assertEquals(time, results.get(0).getLastAccessed().getTime() / 1000);
    }

    /**
     * Ensures that UserActivitySummary.getRecentDevices limits returned entries
     * to count provided.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws TranslationException
     */
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

    @Test
    public void getRecentDevices_uniqueByDeviceId()
            throws TskCoreException, NoServiceProviderException, SleuthkitCaseProviderException, TskCoreException, TranslationException {

        long dataSourceId = 1L;
        DataSource dataSource = TskMockUtils.getDataSource(dataSourceId);
        BlackboardArtifact item1 = getRecentDeviceArtifact(1001, dataSource, "ID1", "MAKE1", "MODEL1", DAY_SECONDS);
        BlackboardArtifact item2 = getRecentDeviceArtifact(1002, dataSource, "ID1", "MAKE1", "MODEL1", DAY_SECONDS + 1);
        BlackboardArtifact item3 = getRecentDeviceArtifact(1003, dataSource, "ID1", "MAKE1", "MODEL1", DAY_SECONDS + 2);

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(Arrays.asList(item1, item2, item3));
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

        List<TopDeviceAttachedResult> results = summary.getRecentDevices(dataSource, 10);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals((DAY_SECONDS + 2), results.get(0).getLastAccessed().getTime() / 1000);
        Assert.assertTrue("ID1".equalsIgnoreCase(results.get(0).getDeviceId()));
        Assert.assertTrue("MAKE1".equalsIgnoreCase(results.get(0).getDeviceMake()));
        Assert.assertTrue("MODEL1".equalsIgnoreCase(results.get(0).getDeviceModel()));
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

    /**
     * Ensures that UserActivitySummary.getMostRecentWebSearches groups
     * artifacts appropriately (i.e. queries with the same name).
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
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
        Assert.assertEquals(DAY_SECONDS * 5, results.get(0).getLastAccessed().getTime() / 1000);
        Assert.assertTrue(query2.equalsIgnoreCase(results.get(1).getSearchString()));
        Assert.assertEquals(DAY_SECONDS * 3, results.get(1).getLastAccessed().getTime() / 1000);
    }

    private void webSearchTranslationTest(List<String> queries, boolean hasProvider, String translationSuffix)
            throws SleuthkitCaseProviderException, TskCoreException, NoServiceProviderException, TranslationException {

        long dataSourceId = 1;
        DataSource ds = TskMockUtils.getDataSource(dataSourceId);

        // create artifacts for each query where first query in the list will have most recent time.
        List<BlackboardArtifact> artList = IntStream.range(0, queries.size())
                .mapToObj((idx) -> getWebSearchArtifact(1000 + idx, ds, queries.get(idx), DAY_SECONDS * (queries.size() - idx)))
                .collect(Collectors.toList());

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(artList);

        // return name with suffix if original exists and suffix is not null.
        Function<String, String> translator = (orig) -> {
            if (orig == null || translationSuffix == null) {
                return null;
            } else {
                return orig + translationSuffix;
            }
        };

        // set up a mock TextTranslationService returning a translation
        TextTranslationService translationService = TskMockUtils.getTextTranslationService(translator, hasProvider);

        UserActivitySummary summary = new UserActivitySummary(
                () -> tskPair.getLeft(),
                translationService,
                TskMockUtils.getJavaLogger("UNIT TEST LOGGER")
        );

        List<TopWebSearchResult> results = summary.getMostRecentWebSearches(ds, queries.size());

        // verify translation service only called if hasProvider
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

        // verify the translation if there should be one
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

    /**
     * Verify that UserActivitySummary.getMostRecentWebSearches handles
     * translation appropriately.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     */
    @Test
    public void getMostRecentWebSearches_handlesTranslation()
            throws SleuthkitCaseProviderException, TskCoreException, NoServiceProviderException, TranslationException {

        List<String> queryList = Arrays.asList("query1", "query2", "query3");
        String translationSuffix = " [TRANSLATED]";
        // if no provider.
        webSearchTranslationTest(queryList, false, translationSuffix);

        // if no translation.
        webSearchTranslationTest(queryList, true, null);

        // if translation is the same (translation suffix doesn't change the trimmed string value)
        webSearchTranslationTest(queryList, true, "");
        webSearchTranslationTest(queryList, true, "    ");

        // if there is an actual translation
        webSearchTranslationTest(queryList, true, translationSuffix);
    }

    /**
     * Ensure that UserActivitySummary.getMostRecentWebSearches results limited
     * to count.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws TranslationException
     */
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

    /**
     * UserActivitySummary.getRecentDomains should return results within 30 days
     * of the most recent access.
     *
     * @throws TskCoreException
     * @throws SleuthkitCaseProviderException
     * @throws NoServiceProviderException
     * @throws TranslationException
     */
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
        Assert.assertEquals(DAY_SECONDS * DOMAIN_WINDOW_DAYS * 2, domains.get(0).getLastAccessed().getTime() / 1000);
        Assert.assertEquals((Long) 2L, domains.get(0).getVisitTimes());

        Assert.assertTrue("Expected " + domain3 + " to be second domain", domain3.equalsIgnoreCase(domains.get(1).getDomain()));
        Assert.assertEquals(DAY_SECONDS * DOMAIN_WINDOW_DAYS, domains.get(1).getLastAccessed().getTime() / 1000);
        Assert.assertEquals((Long) 1L, domains.get(1).getVisitTimes());
    }

    /**
     * Ensure that items like localhost and 127.0.0.1 are removed from results.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
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
        Assert.assertEquals(DAY_SECONDS, domains.get(0).getLastAccessed().getTime() / 1000);
    }

    /**
     * Ensure domains are grouped by name.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
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
        Assert.assertEquals(6L, domains.get(1).getLastAccessed().getTime() / 1000);
        Assert.assertEquals((Long) 2L, domains.get(1).getVisitTimes());

        Assert.assertTrue(domain2.equalsIgnoreCase(domains.get(0).getDomain()));
        Assert.assertEquals(4L, domains.get(0).getLastAccessed().getTime() / 1000);
        Assert.assertEquals((Long) 3L, domains.get(0).getVisitTimes());
    }

    /**
     * Ensure that UserActivitySummary.getRecentDomains limits to count
     * appropriately.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
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

    /**
     * Get email artifact to be used with getRecentAccounts
     *
     * @param artifactId The artifact id.
     * @param dataSource The datasource.
     * @param dateRcvd The date received in seconds or null to exclude.
     * @param dateSent The date sent in seconds or null to exclude.
     *
     * @return The mock artifact.
     */
    private static BlackboardArtifact getEmailArtifact(long artifactId, DataSource dataSource, Long dateRcvd, Long dateSent) {
        List<BlackboardAttribute> attributes = new ArrayList<>();

        if (dateRcvd != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_RCVD, dateRcvd));
        }

        if (dateSent != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_SENT, dateSent));
        }

        try {
            return TskMockUtils.getArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_EMAIL_MSG),
                    artifactId, dataSource, attributes);
        } catch (TskCoreException ignored) {
            fail("Something went wrong while mocking");
            return null;
        }
    }

    /**
     * Get calllog artifact to be used with getRecentAccounts
     *
     * @param artifactId The artifact id.
     * @param dataSource The datasource.
     * @param dateStart The date start in seconds or null to exclude.
     * @param dateEnd The date end in seconds or null to exclude.
     *
     * @return The mock artifact.
     */
    private static BlackboardArtifact getCallogArtifact(long artifactId, DataSource dataSource, Long dateStart, Long dateEnd) {
        List<BlackboardAttribute> attributes = new ArrayList<>();

        if (dateStart != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_START, dateStart));
        }

        if (dateEnd != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_END, dateEnd));
        }

        try {
            return TskMockUtils.getArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_CALLLOG),
                    artifactId, dataSource, attributes);
        } catch (TskCoreException ignored) {
            fail("Something went wrong while mocking");
            return null;
        }
    }

    /**
     * Get message artifact to be used with getRecentAccounts
     *
     * @param artifactId The artifact id.
     * @param dataSource The datasource.
     * @param type The account type.
     * @param dateSent The date of the message in seconds.
     */
    private static BlackboardArtifact getMessageArtifact(long artifactId, DataSource dataSource, String type, Long dateTime) {
        List<BlackboardAttribute> attributes = new ArrayList<>();

        if (type != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, type));
        }

        if (dateTime != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, dateTime));
        }

        try {
            return TskMockUtils.getArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_MESSAGE),
                    artifactId, dataSource, attributes);
        } catch (TskCoreException ignored) {
            fail("Something went wrong while mocking");
            return null;
        }
    }

    /**
     * Performs a test on UserActivitySummary.getRecentAccounts.
     *
     * @param dataSource The datasource to use as parameter.
     * @param count The count to use as a parameter.
     * @param retArtifacts The artifacts to return from
     * SleuthkitCase.getArtifacts. This method filters based on artifact type
     * from the call.
     * @param expectedResults The expected results.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws
     * org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException
     */
    private void getRecentAccountsTest(DataSource dataSource, int count,
            List<BlackboardArtifact> retArtifacts, List<TopAccountResult> expectedResults)
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {

        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        Blackboard mockBlackboard = mock(Blackboard.class);
        when(mockCase.getBlackboard()).thenReturn(mockBlackboard);

        when(mockBlackboard.getArtifacts(anyInt(), anyLong())).thenAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            int artifactType = (Integer) args[0];
            return retArtifacts.stream()
                    .filter((art) -> art.getArtifactTypeID() == artifactType)
                    .collect(Collectors.toList());
        });

        UserActivitySummary summary = getTestClass(mockCase, false, null);

        List<TopAccountResult> receivedResults = summary.getRecentAccounts(dataSource, count);

        verifyCalled(mockBlackboard, ARTIFACT_TYPE.TSK_MESSAGE.getTypeID(), dataSource.getId(),
                "Expected getRecentAccounts to call getArtifacts requesting TSK_MESSAGE.");

        verifyCalled(mockBlackboard, ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(), dataSource.getId(),
                "Expected getRecentAccounts to call getArtifacts requesting TSK_EMAIL_MSG.");

        verifyCalled(mockBlackboard, ARTIFACT_TYPE.TSK_CALLLOG.getTypeID(), dataSource.getId(),
                "Expected getRecentAccounts to call getArtifacts requesting TSK_CALLLOG.");

        Assert.assertEquals(expectedResults.size(), receivedResults.size());
        for (int i = 0; i < expectedResults.size(); i++) {
            TopAccountResult expectedItem = expectedResults.get(i);
            TopAccountResult receivedItem = receivedResults.get(i);

            // since this may be somewhat variable
            Assert.assertTrue(expectedItem.getAccountType().equalsIgnoreCase(receivedItem.getAccountType()));
            Assert.assertEquals(expectedItem.getLastAccessed().getTime(), receivedItem.getLastAccessed().getTime());
            Assert.assertEquals(expectedItem.getArtifact(), receivedItem.getArtifact());
        }
    }

    private void getRecentAccountsOneArtTest(DataSource dataSource, BlackboardArtifact retArtifact, TopAccountResult expectedResult)
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {
        getRecentAccountsTest(dataSource, 10, Arrays.asList(retArtifact), expectedResult != null ? Arrays.asList(expectedResult) : Collections.emptyList());
    }

    /**
     * Verify that UserActivitySummary.getRecentAccounts attempts to find a date
     * but if none present, the artifact is excluded.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
    @Test
    public void getRecentAccounts_filtersNoDate()
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {

        DataSource ds1 = TskMockUtils.getDataSource(1);
        BlackboardArtifact email1 = getEmailArtifact(31, ds1, DAY_SECONDS, null);
        getRecentAccountsOneArtTest(ds1, email1,
                new TopAccountResult(
                        Bundle.DataSourceUserActivitySummary_getRecentAccounts_emailMessage(),
                        new Date(DAY_SECONDS * 1000), email1));

        BlackboardArtifact email2 = getEmailArtifact(2, ds1, null, DAY_SECONDS);
        getRecentAccountsOneArtTest(ds1, email2,
                new TopAccountResult(
                        Bundle.DataSourceUserActivitySummary_getRecentAccounts_emailMessage(),
                        new Date(DAY_SECONDS * 1000), email2));

        BlackboardArtifact email3 = getEmailArtifact(3, ds1, null, null);
        getRecentAccountsOneArtTest(ds1, email3, null);

        BlackboardArtifact email4 = getEmailArtifact(4, ds1, DAY_SECONDS, DAY_SECONDS * 2);
        getRecentAccountsOneArtTest(ds1, email4,
                new TopAccountResult(
                        Bundle.DataSourceUserActivitySummary_getRecentAccounts_emailMessage(),
                        new Date(DAY_SECONDS * 2 * 1000), email4));

        BlackboardArtifact callog1 = getCallogArtifact(11, ds1, DAY_SECONDS, null);
        getRecentAccountsOneArtTest(ds1, callog1,
                new TopAccountResult(
                        Bundle.DataSourceUserActivitySummary_getRecentAccounts_calllogMessage(),
                        new Date(DAY_SECONDS * 1000), callog1));

        BlackboardArtifact callog2 = getCallogArtifact(12, ds1, null, DAY_SECONDS);
        getRecentAccountsOneArtTest(ds1, callog2,
                new TopAccountResult(
                        Bundle.DataSourceUserActivitySummary_getRecentAccounts_calllogMessage(),
                        new Date(DAY_SECONDS * 1000), callog2));

        BlackboardArtifact callog3 = getCallogArtifact(13, ds1, null, null);
        getRecentAccountsOneArtTest(ds1, callog3, null);

        BlackboardArtifact callog4 = getCallogArtifact(14, ds1, DAY_SECONDS, DAY_SECONDS * 2);
        getRecentAccountsOneArtTest(ds1, callog4,
                new TopAccountResult(
                        Bundle.DataSourceUserActivitySummary_getRecentAccounts_calllogMessage(),
                        new Date(DAY_SECONDS * 2 * 1000), callog4));

        BlackboardArtifact message1 = getMessageArtifact(21, ds1, "Skype", null);
        getRecentAccountsOneArtTest(ds1, message1, null);

        BlackboardArtifact message2 = getMessageArtifact(22, ds1, null, DAY_SECONDS);
        getRecentAccountsOneArtTest(ds1, message2, null);

        BlackboardArtifact message3 = getMessageArtifact(23, ds1, null, null);
        getRecentAccountsOneArtTest(ds1, message3, null);

        BlackboardArtifact message4 = getMessageArtifact(24, ds1, "Skype", DAY_SECONDS);
        getRecentAccountsOneArtTest(ds1, message4, new TopAccountResult("Skype", new Date(DAY_SECONDS * 1000), message4));

    }

    /**
     * Verifies that UserActivitySummary.getRecentAccounts groups appropriately
     * by account type.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
    @Test
    public void getRecentAccounts_rightGrouping()
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {
        DataSource ds1 = TskMockUtils.getDataSource(1);
        BlackboardArtifact email1 = getEmailArtifact(11, ds1, DAY_SECONDS - 11, null);
        BlackboardArtifact email2 = getEmailArtifact(12, ds1, DAY_SECONDS - 12, null);
        BlackboardArtifact email3 = getEmailArtifact(13, ds1, DAY_SECONDS + 13, null);

        BlackboardArtifact callog1 = getCallogArtifact(21, ds1, DAY_SECONDS - 21, null);
        BlackboardArtifact callog2 = getCallogArtifact(22, ds1, DAY_SECONDS + 22, null);

        BlackboardArtifact message1a = getMessageArtifact(31, ds1, "Skype", DAY_SECONDS - 31);
        BlackboardArtifact message1b = getMessageArtifact(32, ds1, "Skype", DAY_SECONDS + 32);

        BlackboardArtifact message2a = getMessageArtifact(41, ds1, "Facebook", DAY_SECONDS - 41);
        BlackboardArtifact message2b = getMessageArtifact(41, ds1, "Facebook", DAY_SECONDS + 42);

        getRecentAccountsTest(ds1, 10,
                Arrays.asList(email1, email2, email3, callog1, callog2, message1a, message1b, message2a, message2b),
                Arrays.asList(
                        new TopAccountResult("Facebook", new Date((DAY_SECONDS + 42) * 1000), message2b),
                        new TopAccountResult("Skype", new Date((DAY_SECONDS + 32) * 1000), message1b),
                        new TopAccountResult(Bundle.DataSourceUserActivitySummary_getRecentAccounts_calllogMessage(), new Date((DAY_SECONDS + 22) * 1000), callog2),
                        new TopAccountResult(Bundle.DataSourceUserActivitySummary_getRecentAccounts_emailMessage(), new Date((DAY_SECONDS + 13) * 1000), email3)
                ));
    }

    /**
     * Verifies that UserActivitySummary.getRecentAccounts properly limits
     * results returned.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
    @Test
    public void getRecentAccounts_rightLimit()
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {
        int countRequested = 10;
        for (int returnedCount : new int[]{1, 9, 10, 11}) {
            long dataSourceId = 1L;
            DataSource dataSource = TskMockUtils.getDataSource(dataSourceId);

            List<BlackboardArtifact> returnedArtifacts = IntStream.range(0, returnedCount)
                    .mapToObj((idx) -> getMessageArtifact(1000 + idx, dataSource, "Message Type " + idx, DAY_SECONDS * idx + 1))
                    .collect(Collectors.toList());

            Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(returnedArtifacts);
            UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

            List<TopAccountResult> results = summary.getRecentAccounts(dataSource, countRequested);
            verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_MESSAGE.getTypeID(), dataSource.getId(),
                    "Expected getRecentAccounts to call getArtifacts requesting TSK_MESSAGE.");

            verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(), dataSource.getId(),
                    "Expected getRecentAccounts to call getArtifacts requesting TSK_EMAIL_MSG.");

            verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_CALLLOG.getTypeID(), dataSource.getId(),
                    "Expected getRecentAccounts to call getArtifacts requesting TSK_CALLLOG.");

            Assert.assertEquals(Math.min(countRequested, returnedCount), results.size());
        }
    }

    /**
     * Ensures that UserActivity.getShortFolderName handles paths appropriately
     * including Program Files and AppData folders.
     *
     * @throws NoServiceProviderException
     * @throws TskCoreException
     * @throws TranslationException
     */
    @Test
    public void getShortFolderName_rightConversions() throws NoServiceProviderException, TskCoreException, TranslationException {
        Map<String, String> expected = new HashMap<>();
        expected.put("/Program Files/Item/Item.exe", "Item");
        expected.put("/Program Files (x86)/Item/Item.exe", "Item");
        expected.put("/Program_Files/Item/Item.exe", "");

        expected.put("/User/test_user/item/AppData/Item/Item.exe", "AppData");
        expected.put("/User/test_user/item/Application Data/Item/Item.exe", "AppData");

        expected.put("/Other Path/Item/Item.exe", "");

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(null);
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

        for (Entry<String, String> path : expected.entrySet()) {
            Assert.assertTrue(path.getValue().equalsIgnoreCase(UserActivitySummary.getShortFolderName(path.getKey(), "Item.exe")));
            Assert.assertTrue(path.getValue().equalsIgnoreCase(UserActivitySummary.getShortFolderName(path.getKey().toUpperCase(), "Item.exe".toUpperCase())));
            Assert.assertTrue(path.getValue().equalsIgnoreCase(UserActivitySummary.getShortFolderName(path.getKey().toLowerCase(), "Item.exe".toLowerCase())));
        }
    }

    private static BlackboardArtifact getProgramArtifact(long artifactId, DataSource dataSource, String programName, String path, Integer count, Long dateTime) {
        List<BlackboardAttribute> attributes = new ArrayList<>();

        if (programName != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, programName));
        }

        if (path != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_PATH, path));
        }

        if (dateTime != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, dateTime));
        }

        if (count != null) {
            attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_COUNT, count));
        }

        try {
            return TskMockUtils.getArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_PROG_RUN),
                    artifactId, dataSource, attributes);
        } catch (TskCoreException ignored) {
            fail("Something went wrong while mocking");
            return null;
        }
    }

    /**
     * Ensures that getTopPrograms filters results like ntosboot programs or
     * /Windows folders.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
    @Test
    public void getTopPrograms_filtered()
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {

        DataSource ds1 = TskMockUtils.getDataSource(1);
        BlackboardArtifact ntosToRemove = getProgramArtifact(1, ds1, "ntosboot", "/Program Files/etc/", 21, 21L);
        BlackboardArtifact windowsToRemove = getProgramArtifact(2, ds1, "Program.exe", "/Windows/", 21, 21L);
        BlackboardArtifact windowsToRemove2 = getProgramArtifact(3, ds1, "Program.exe", "/Windows/Nested/", 21, 21L);
        BlackboardArtifact noProgramNameToRemove = getProgramArtifact(4, ds1, null, "/Program Files/", 21, 21L);
        BlackboardArtifact noProgramNameToRemove2 = getProgramArtifact(5, ds1, "   ", "/Program Files/", 21, 21L);
        BlackboardArtifact successful = getProgramArtifact(6, ds1, "ProgramSuccess.exe", "/AppData/Success/", null, null);
        BlackboardArtifact successful2 = getProgramArtifact(7, ds1, "ProgramSuccess2.exe", "/AppData/Success/", 22, 22L);

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(Arrays.asList(
                ntosToRemove,
                windowsToRemove,
                windowsToRemove2,
                noProgramNameToRemove,
                noProgramNameToRemove2,
                successful,
                successful2
        ));
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);
        List<TopProgramsResult> results = summary.getTopPrograms(ds1, 10);

        Assert.assertEquals(2, results.size());
        Assert.assertTrue("ProgramSuccess2.exe".equalsIgnoreCase(results.get(0).getProgramName()));
        Assert.assertTrue("ProgramSuccess.exe".equalsIgnoreCase(results.get(1).getProgramName()));
    }

    /**
     * Ensures proper grouping of programs with index of program name and path.
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
    @Test
    public void getTopPrograms_correctGrouping()
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {

        DataSource ds1 = TskMockUtils.getDataSource(1);
        BlackboardArtifact prog1 = getProgramArtifact(1, ds1, "program1.exe", "/Program Files/etc/", 21, 21L);
        BlackboardArtifact prog1a = getProgramArtifact(1, ds1, "program1.exe", "/Program Files/etc/", 1, 31L);
        BlackboardArtifact prog1b = getProgramArtifact(1, ds1, "program1.exe", "/Program Files/etc/", 2, 11L);

        BlackboardArtifact prog2 = getProgramArtifact(1, ds1, "program1.exe", "/Program Files/another/", 31, 21L);
        BlackboardArtifact prog2a = getProgramArtifact(1, ds1, "program1.exe", "/Program Files/another/", 1, 31L);
        BlackboardArtifact prog2b = getProgramArtifact(1, ds1, "program1.exe", "/Program Files/another/", 2, 11L);

        BlackboardArtifact prog3 = getProgramArtifact(1, ds1, "program2.exe", "/Program Files/another/", 10, 21L);
        BlackboardArtifact prog3a = getProgramArtifact(1, ds1, "program2.exe", "/Program Files/another/", 1, 22L);
        BlackboardArtifact prog3b = getProgramArtifact(1, ds1, "program2.exe", "/Program Files/another/", 2, 11L);

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(Arrays.asList(
                prog1, prog1a, prog1b,
                prog2, prog2a, prog2b,
                prog3, prog3a, prog3b
        ));
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);
        List<TopProgramsResult> results = summary.getTopPrograms(ds1, 10);

        Assert.assertEquals(3, results.size());
        Assert.assertTrue("program1.exe".equalsIgnoreCase(results.get(0).getProgramName()));
        Assert.assertTrue("/Program Files/another/".equalsIgnoreCase(results.get(0).getProgramPath()));
        Assert.assertEquals((Long) 31L, results.get(0).getRunTimes());
        Assert.assertEquals((Long) 31L, (Long) (results.get(0).getLastAccessed().getTime() / 1000));

        Assert.assertTrue("program1.exe".equalsIgnoreCase(results.get(1).getProgramName()));
        Assert.assertTrue("/Program Files/etc/".equalsIgnoreCase(results.get(1).getProgramPath()));
        Assert.assertEquals((Long) 21L, results.get(1).getRunTimes());
        Assert.assertEquals((Long) 31L, (Long) (results.get(1).getLastAccessed().getTime() / 1000));

        Assert.assertTrue("program2.exe".equalsIgnoreCase(results.get(2).getProgramName()));
        Assert.assertTrue("/Program Files/another/".equalsIgnoreCase(results.get(2).getProgramPath()));
        Assert.assertEquals((Long) 10L, results.get(2).getRunTimes());
        Assert.assertEquals((Long) 22L, (Long) (results.get(2).getLastAccessed().getTime() / 1000));
    }

    private void assertProgramOrder(DataSource ds1, List<BlackboardArtifact> artifacts, List<String> programNamesReturned)
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {

        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(artifacts);
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);
        List<TopProgramsResult> results = summary.getTopPrograms(ds1, 10);

        Assert.assertEquals(programNamesReturned.size(), results.size());
        for (int i = 0; i < programNamesReturned.size(); i++) {
            Assert.assertTrue(programNamesReturned.get(i).equalsIgnoreCase(results.get(i).getProgramName()));
        }
    }

    /**
     * Ensure that UserActivitySummary.getTopPrograms properly orders results
     * (first by run count, then date, then program name).
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
    @Test
    public void getTopPrograms_correctOrdering()
            throws TskCoreException, NoServiceProviderException, TranslationException, SleuthkitCaseProviderException {

        DataSource ds1 = TskMockUtils.getDataSource(1);
        BlackboardArtifact sortByRunsCount1 = getProgramArtifact(1001, ds1, "Program1.exe", "/Program Files/Folder/", 8, 1L);
        BlackboardArtifact sortByRunsCount2 = getProgramArtifact(1002, ds1, "Program2.exe", "/Program Files/Folder/", 9, 2L);
        BlackboardArtifact sortByRunsCount3 = getProgramArtifact(1003, ds1, "Program3.exe", "/Program Files/Folder/", 10, 3L);
        assertProgramOrder(ds1, Arrays.asList(sortByRunsCount1, sortByRunsCount2, sortByRunsCount3), Arrays.asList("Program3.exe", "Program2.exe", "Program1.exe"));

        BlackboardArtifact sortByRunDate1 = getProgramArtifact(1011, ds1, "Program1.exe", "/Program Files/Folder/", null, 1L);
        BlackboardArtifact sortByRunDate2 = getProgramArtifact(1012, ds1, "Program2.exe", "/Program Files/Folder/", null, 3L);
        BlackboardArtifact sortByRunDate3 = getProgramArtifact(1013, ds1, "Program3.exe", "/Program Files/Folder/", null, 2L);
        assertProgramOrder(ds1, Arrays.asList(sortByRunDate1, sortByRunDate2, sortByRunDate3), Arrays.asList("Program2.exe", "Program3.exe", "Program1.exe"));

        BlackboardArtifact sortByProgName1 = getProgramArtifact(1021, ds1, "cProgram.exe", "/Program Files/Folder/", null, null);
        BlackboardArtifact sortByProgName2 = getProgramArtifact(1022, ds1, "BProgram.exe", "/Program Files/Folder/", null, null);
        BlackboardArtifact sortByProgName3 = getProgramArtifact(1023, ds1, "aProgram.exe", "/Program Files/Folder/", null, null);
        assertProgramOrder(ds1, Arrays.asList(sortByProgName1, sortByProgName2, sortByProgName3), Arrays.asList("aProgram.exe", "BProgram.exe", "cProgram.exe"));
    }

    /**
     * Ensure that UserActivitySummary.getTopPrograms properly limits results
     * (if no run count and no run date, then no limit).
     *
     * @throws TskCoreException
     * @throws NoServiceProviderException
     * @throws TranslationException
     * @throws SleuthkitCaseProviderException
     */
    @Test
    public void getTopPrograms_limited()
            throws TskCoreException, NoServiceProviderException,
            TranslationException, SleuthkitCaseProviderException {

        int countRequested = 10;
        for (int returnedCount : new int[]{1, 9, 10, 11}) {
            long dataSourceId = 1L;
            DataSource dataSource = TskMockUtils.getDataSource(dataSourceId);

            // if data is present for counts and dates, the results are limited
            List<BlackboardArtifact> returnedArtifacts = IntStream.range(0, returnedCount)
                    .mapToObj((idx) -> getProgramArtifact(1000 + idx, dataSource, "Program" + idx,
                    "/Program Files/Folder/", idx + 1, DAY_SECONDS * idx + 1))
                    .collect(Collectors.toList());

            Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(returnedArtifacts);
            UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);

            List<TopProgramsResult> results = summary.getTopPrograms(dataSource, countRequested);
            verifyCalled(tskPair.getRight(), ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID(), dataSourceId,
                    "Expected getRecentDevices to call getArtifacts with correct arguments.");

            Assert.assertEquals(Math.min(countRequested, returnedCount), results.size());

            // if that data is not present, it is not limited
            List<BlackboardArtifact> returnedArtifactsAlphabetical = IntStream.range(0, returnedCount)
                    .mapToObj((idx) -> getProgramArtifact(1000 + idx, dataSource, "Program" + idx, null, null, null))
                    .collect(Collectors.toList());

            Pair<SleuthkitCase, Blackboard> tskPairAlphabetical = getArtifactsTSKMock(returnedArtifactsAlphabetical);
            UserActivitySummary summaryAlphabetical = getTestClass(tskPairAlphabetical.getLeft(), false, null);

            List<TopProgramsResult> resultsAlphabetical = summaryAlphabetical.getTopPrograms(dataSource, countRequested);
            verifyCalled(tskPairAlphabetical.getRight(), ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID(), dataSourceId,
                    "Expected getRecentDevices to call getArtifacts with correct arguments.");

            // ensure alphabetical by name
            for (int i = 0; i < resultsAlphabetical.size() - 1; i++) {
                Assert.assertTrue(resultsAlphabetical.get(i).getProgramName().compareToIgnoreCase(resultsAlphabetical.get(i + 1).getProgramName()) < 0);
            }

            Assert.assertEquals(returnedArtifacts.size(), resultsAlphabetical.size());
        }
    }
}
