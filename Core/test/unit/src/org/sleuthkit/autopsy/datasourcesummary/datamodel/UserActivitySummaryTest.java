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
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import static org.junit.Assert.fail;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
    
    private static final DataFunction<TopWebSearchResult> WEB_SEARCH_QUERY = 
            (userActivity, dataSource, count) -> userActivity.getMostRecentWebSearches(dataSource, count);
    
    private static final DataFunction<TopAccountResult> ACCOUNT_QUERY = 
            (userActivity, dataSource, count) -> userActivity.getRecentAccounts(dataSource, count);
    
    private static final DataFunction<TopDomainsResult> DOMAINS_QUERY = 
            (userActivity, dataSource, count) -> userActivity.getRecentDomains(dataSource, count);
    
    private static final DataFunction<TopDeviceAttachedResult> DEVICE_QUERY = 
            (userActivity, dataSource, count) -> userActivity.getRecentDevices(dataSource, count);
    
    private static final DataFunction<TopProgramsResult> PROGRAMS_QUERY = 
            (userActivity, dataSource, count) -> userActivity.getTopPrograms(dataSource, count);
    
    private static final Map<String, DataFunction<?>> USER_ACTIVITY_METHODS = new HashMap<String, DataFunction<?>>() {{
        put("getMostRecentWebSearches", WEB_SEARCH_QUERY);
        put("getRecentAccounts", ACCOUNT_QUERY);
        put("getRecentDomains", DOMAINS_QUERY);
        put("getRecentDevices", DEVICE_QUERY);
        put("getTopPrograms", PROGRAMS_QUERY);
    }};
    
    
    private static BlackboardArtifact.Type ACCOUNT_TYPE = new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_ACCOUNT);
    private static BlackboardAttribute.Type TYPE_ACCOUNT_TYPE = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE);
    
    
    
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
            String deviceId, String deviceMake, String deviceModel, Long date) throws TskCoreException {
        
        return TskMockUtils.getArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_DEVICE_ATTACHED), artifactId, dataSource, 
                    TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_ID, deviceId),
                    TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, date),
                    TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE, deviceMake),
                    TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL, deviceModel)
                );
    }
    
    @Test
    public void testRecentDevicesFiltering() throws TskCoreException, NoServiceProviderException, 
            SleuthkitCaseProviderException, TranslationException {
        
        long dataSourceId = 1;
        int count = 10;
        long time = 24 * 60 * 60 * 42;
        String acceptedDevice = "ACCEPTED DEVICE";
        
        DataSource ds = TskMockUtils.getDataSource(dataSourceId);
                
        List<String> allKeys = new ArrayList<>(EXCLUDED_DEVICES);
        allKeys.add(acceptedDevice);
        
        List<BlackboardArtifact> artifacts = IntStream.range(0, allKeys.size())
                .mapToObj((idx) -> {
                    String key = allKeys.get(idx);
                    try {
                        return getRecentDeviceArtifact(1000L + idx, ds, "ID " + key, "MAKE " + key, key, time);    
                    } catch (TskCoreException ex) {
                        fail("Unable to create artifacts correctly");    
                        return null;
                    }
                })
                .collect(Collectors.toList());
        
        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(artifacts);
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);
        List<TopDeviceAttachedResult> results = summary.getRecentDevices(ds, count);
        
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(acceptedDevice, results.get(0).getDeviceModel());
        Assert.assertEquals("MAKE " + acceptedDevice, results.get(0).getDeviceMake());
        Assert.assertEquals("ID " + acceptedDevice, results.get(0).getDeviceId());
        Assert.assertEquals(time, results.get(0).getDateAccessed().getTime() / 1000);
    }
    
    @Test
    public void testRecentDevicesLimiting() {
        
    }

}
