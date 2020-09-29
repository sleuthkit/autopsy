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

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.formula.functions.T;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
public class UserActivitySummaryTests {
    
    private interface DataFunction<T> {
        List<T> retrieve(UserActivitySummary userActivitySummary, DataSource datasource, int count);
    }
    
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    
    private static BlackboardArtifact.Type ACCOUNT_TYPE = new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_ACCOUNT);
    
    private static BlackboardAttribute.Type TYPE_ACCOUNT_TYPE = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE);
    
    
    
    private static Pair<SleuthkitCase, Blackboard> getArtifactsTSKMock(List<BlackboardArtifact> returnArr) throws TskCoreException {
        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        Blackboard mockBlackboard = mock(Blackboard.class);
        when(mockCase.getBlackboard()).thenReturn(mockBlackboard);
        when(mockBlackboard.getArtifacts(anyInt(), anyLong())).thenReturn(returnArr);
        return Pair.of(mockCase, mockBlackboard);
    }

    
    
    private static void verifyCalled(Blackboard mockBlackboard, int artifactType, long datasourceId) throws TskCoreException {
        verify(mockBlackboard, times(1)).getArtifacts(artifactType, datasourceId);
    }
    
    private static UserActivitySummary getTestClass(SleuthkitCase tskCase, boolean hasTranslation, Function<String, String> translateFunction) 
            throws NoServiceProviderException, TranslationException {   
        
        return new UserActivitySummary(
                () -> tskCase,
                TskMockUtils.getTextTranslationService(translateFunction, hasTranslation),
                TskMockUtils.getTSKLogger()
        );
    }
    
    private <T> void testMinCount(DataFunction<T> funct) throws TskCoreException, NoServiceProviderException, TranslationException {
        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(null);
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);
        thrown.expect(IllegalArgumentException.class);
        funct.retrieve(summary, TskMockUtils.mockDataSource(1), -1);
        
        Pair<SleuthkitCase, Blackboard> tskPair2 = getArtifactsTSKMock(null);
        UserActivitySummary summary2 = getTestClass(tskPair.getLeft(), false, null);
        thrown.expect(IllegalArgumentException.class);
        funct.retrieve(summary2, TskMockUtils.mockDataSource(1), -1);
    }
    
    
    private <T> void testNullDataSource(DataFunction<T> funct) throws TskCoreException, NoServiceProviderException, TranslationException {
        Pair<SleuthkitCase, Blackboard> tskPair = getArtifactsTSKMock(RET_ARR);
        UserActivitySummary summary = getTestClass(tskPair.getLeft(), false, null);
        List<T> retArr = funct.retrieve(summary, TskMockUtils.mockDataSource(1), -1);        
        Assert.assertTrue(retArr != null);
    }

    
    
    
    // what happens on count <= 0
    // datasource = null
    // no results returned
    // does not contain excluded
        // ROOT HUB
    // queries correct data sources (i.e. the 3 messages)
    // sorted and limited appropriately
    
    
    // public List<UserActivitySummary.TopDomainsResult> getRecentDomains(DataSource dataSource, int count) throws TskCoreException, SleuthkitCaseProvider.SleuthkitCaseProviderException
    // public List<UserActivitySummary.TopWebSearchResult> getMostRecentWebSearches(DataSource dataSource, int count) throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException
    // public List<TopDeviceAttachedResult> getRecentDevices(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException
}
