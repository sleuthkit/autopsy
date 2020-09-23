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
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceInfoUtilities.SortOrder;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import static org.mockito.Mockito.*;
import org.sleuthkit.autopsy.testutils.TskMockUtils;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;

/**
 * Unit tests for DataSourceInfoUtilities.getArtifacts
 */
public class GetArtifactsTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final void test(BlackboardArtifact.Type artifactType, DataSource dataSource, BlackboardAttribute.Type attributeType,
            SortOrder sortOrder, List<BlackboardArtifact> returnArr, TskCoreException blackboardEx,
            List<BlackboardArtifact> expectedArr, Class<? extends Exception> expectedException) throws TskCoreException {

        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        Blackboard mockBlackboard = mock(Blackboard.class);
        when(mockCase.getBlackboard()).thenReturn(mockBlackboard);

        if (blackboardEx == null) {
            when(mockBlackboard.getArtifacts(anyInt(), anyInt())).thenReturn(returnArr);
            verify(mockBlackboard, times(1)).getArtifacts(artifactType.getTypeID(), dataSource.getId());
        } else {
            when(mockBlackboard.getArtifacts(anyInt(), anyInt())).thenThrow(blackboardEx);
        }

        if (expectedException == null) {
            List<BlackboardArtifact> determinedArr = DataSourceInfoUtilities.getArtifacts(mockCase, artifactType, dataSource, attributeType, sortOrder);
            if (expectedArr == null && returnArr == null) {
                return;
            }

            Assert.assertTrue(expectedArr != null && determinedArr != null);

            Assert.assertEquals(expectedArr.size(), determinedArr.size());

            for (int i = 0; i < expectedArr.size(); i++) {
                Assert.assertEquals(expectedArr.get(i), determinedArr.get(i));
            }
        } else {
            thrown.expect(expectedException);
            DataSourceInfoUtilities.getArtifacts(mockCase, artifactType, dataSource, attributeType, sortOrder);
        }
    }

    private interface AttrMaker<T> {

        BlackboardAttribute make(BlackboardAttribute.Type type, String source, T value);
    }

    private <T> List<BlackboardArtifact> getArtifacts(ARTIFACT_TYPE artifactType, BlackboardAttribute.Type attrType,
            DataSource dataSource, List<T> values, AttrMaker<T> attrMaker) throws TskCoreException {

        List<BlackboardArtifact> toRet = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            toRet.add(TskMockUtils.mockArtifact(new BlackboardArtifact.Type(artifactType), 1000 + i, dataSource, attrMaker.make(attrType, "TEST SOURCE", values.get(i))));
        }

        return toRet;
    }

    /**
     * Returns list in 0, n-1, 1, n-2 ... order. Deterministic so same results
     * each time, but not in original order.
     *
     * @return Mixed up list.
     */
    private <T> List<T> getMixedUp(List<T> list) {
        int forward = 0;
        int backward = list.size() - 1;

        List<T> newList = new ArrayList<>();
        while (forward <= backward) {
            newList.add(list.get(forward));

            if (forward < backward) {
                newList.add(list.get(backward));
            }

            forward++;
            backward--;
        }

        return newList;
    }

    private <T> void testSorted(ARTIFACT_TYPE artifactType, ATTRIBUTE_TYPE attrType, List<T> values, AttrMaker<T> attrMaker, SortOrder sortOrder) throws TskCoreException {
        DataSource dataSource = TskMockUtils.mockDataSource(1);
        List<BlackboardArtifact> sortedArtifacts = getArtifacts(artifactType, new BlackboardAttribute.Type(attrType), dataSource, values, attrMaker);

        List<BlackboardArtifact> mixedUpArtifacts = getMixedUp(sortedArtifacts);

        test(new BlackboardArtifact.Type(artifactType), dataSource, new BlackboardAttribute.Type(attrType),
                sortOrder, mixedUpArtifacts, null, sortedArtifacts, null);
    
    }
    
//    
//    		STRING(0, "String"), //NON-NLS
//		/**
//		 * The value type of the attribute is an int.
//		 */
//		INTEGER(1, "Integer"), //NON-NLS
//		/**
//		 * The value type of the attribute is a long.
//		 */
//		LONG(2, "Long"), //NON-NLS
//		/**
//		 * The value type of the attribute is a double.
//		 */
//		DOUBLE(3, "Double"), //NON-NLS
//		/**
//		 * The value type of the attribute is a byte array.
//		 */
//		BYTE(4, "Byte"), //NON-NLS
//		/**
//		 * The value type of the attribute is a long representing seconds from
//		 * January 1, 1970.
//		 */
//		DATETIME(5, "DateTime"),
//		/**
//		 * The value type of the attribute is a JSON string.
//		 */
//		JSON(6, "Json" );
//    

    
    private void testAscDesc(SortOrder sortOrder) throws TskCoreException {
        List<String> strings = Arrays.asList("aardvark", "Bear", "cat", "Dog", "elephant");
        List<Integer> integers = Arrays.asList(22, 31, 42, 50, 60);
        List<Long> longs = Arrays.asList(22L, 31L, 42L, 50L, 60L);
        
        long day = 24 * 60 * 60;
        List<Long> dateTimes = Arrays.asList(day, 2*day, 3*day, 4*day, 5*day);
        List<Double> doubles = Arrays.asList(68.5, 70.1, 71.5, 72.3, 73.5);
        
        if (sortOrder == SortOrder.DESCENDING) {
            Collections.reverse(strings);
            Collections.reverse(integers);
            Collections.reverse(longs);
            Collections.reverse(dateTimes);
            Collections.reverse(doubles);
        }
        
        // sort on string
        //testSorted(ARTIFACT_TYPE.TSK_WEB_COOKIE, ATTRIBUTE_TYPE.TSK_NAME, strings, BlackboardAttribute::new, sortOrder);

        // sort on int
        testSorted(ARTIFACT_TYPE.TSK_PROG_RUN, ATTRIBUTE_TYPE.TSK_COUNT, integers, BlackboardAttribute::new, sortOrder);
        
        // sort on long
        testSorted(ARTIFACT_TYPE.TSK_PROG_RUN, ATTRIBUTE_TYPE.TSK_BYTES_SENT, longs, BlackboardAttribute::new, sortOrder);
         
        // sort on date

        testSorted(ARTIFACT_TYPE.TSK_RECENT_OBJECT, ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, dateTimes, BlackboardAttribute::new, sortOrder);
        
        // sort on double
        testSorted(ARTIFACT_TYPE.TSK_GPS_BOOKMARK, ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, doubles, BlackboardAttribute::new, sortOrder);
    }
    
    private <T> void testFailOnBadAttrType(BlackboardArtifact.Type artifactType, BlackboardAttribute.Type attributeType, T val) {
        DataSource dataSource = TskMockUtils.mockDataSource(1);
        
        List<BlackboardArtifact> artifacts = Arrays.asList(
                TskMockUtils.mockArtifact(artifactType, 2, dataSource, attributes)
        );
        test(artifactType, dataSource, attributeType, SortOrder.ASCENDING, List<BlackboardArtifact> returnArr, TskCoreException blackboardEx,
        List<BlackboardArtifact> expectedArr, Class<? extends Exception> expectedException)
    }
        
    @Test
    public void testSortAscending() throws TskCoreException {
        testAscDesc(SortOrder.ASCENDING);
    }

    @Test
    public void testSortDescending() throws TskCoreException {
        testAscDesc(SortOrder.DESCENDING);
    }

    @Test
    public void testFailOnJson() throws TskCoreException {

    }

    @Test
    public void testFailOnBytes() throws TskCoreException {

    }

    @Test
    public void testPurgeAttrNotPresent() throws TskCoreException {

    }

    @Test
    public void testPurgeAttributeNullVal() throws TskCoreException {

    }

    @Test
    public void testSortWhereTwoAttrsPresent() throws TskCoreException {

    }

    @Test
    public void testMultAttrsPresent() throws TskCoreException {

    }

    @Test
    public void testTskCoreExceptionThrown() throws TskCoreException {

    }

    @Test
    public void testEmptyListReturned() throws TskCoreException {

    }

    @Test
    public void testNullListReturned() throws TskCoreException {

    }

}
