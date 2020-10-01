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
import org.sleuthkit.autopsy.testutils.TskMockUtils;
import static org.mockito.Mockito.*;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE;

/**
 * Unit tests for DataSourceInfoUtilities.getArtifacts
 */
public class GetArtifactsTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /**
     * Performs a test of DataSourceInfoUtilities.getArtifacts mocking return
     * results from SleuthkitCase.getBlackboard().getArtifacts(type id,
     * datasource id).
     *
     * @param artifactType      The artifact type to provide as an argument to
     *                          getArtifacts.
     * @param dataSource        The datasource to use as an argument.
     * @param attributeType     The attribute type to use as an argument.
     * @param sortOrder         The sort order to use as an argument.
     * @param count             The maximum count of records to return.
     * @param returnArr         The return result from the mocked
     *                          SleuthkitCase.getBlackboard().getArtifacts(type
     *                          id, datasource id).
     * @param blackboardEx      The TskCoreException thrown by the mocked
     *                          Blackboard.
     * @param expectedArr       The expected return result from
     *                          DataSourceInfoUtilities.getArtifacts.
     * @param expectedException The expected exception from
     *                          DataSourceInfoUtilities.getArtifacts.
     *
     * @throws TskCoreException
     */
    private void test(BlackboardArtifact.Type artifactType, DataSource dataSource, BlackboardAttribute.Type attributeType,
            SortOrder sortOrder, int count, List<BlackboardArtifact> returnArr, TskCoreException blackboardEx,
            List<BlackboardArtifact> expectedArr, Class<? extends Exception> expectedException) throws TskCoreException {

        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        Blackboard mockBlackboard = mock(Blackboard.class);
        when(mockCase.getBlackboard()).thenReturn(mockBlackboard);

        if (blackboardEx == null) {
            when(mockBlackboard.getArtifacts(anyInt(), anyLong())).thenReturn(returnArr);
        } else {
            when(mockBlackboard.getArtifacts(anyInt(), anyLong())).thenThrow(blackboardEx);
        }

        if (expectedException == null) {
            List<BlackboardArtifact> determinedArr = DataSourceInfoUtilities.getArtifacts(mockCase, artifactType,
                    dataSource, attributeType, sortOrder, count);

            verify(mockBlackboard, times(1)).getArtifacts(artifactType.getTypeID(), dataSource.getId());

            if (expectedArr == null && determinedArr == null) {
                return;
            }

            Assert.assertTrue(expectedArr != null && determinedArr != null);

            Assert.assertEquals(expectedArr.size(), determinedArr.size());

            for (int i = 0; i < expectedArr.size(); i++) {
                Assert.assertEquals(expectedArr.get(i), determinedArr.get(i));
            }
        } else {
            thrown.expect(expectedException);
            DataSourceInfoUtilities.getArtifacts(mockCase, artifactType, dataSource, attributeType, sortOrder, count);
        }
    }

    /**
     * Function that creates a blackboard attribute.
     */
    private interface AttrMaker<T> {

        /**
         * Makes a BlackboardAttribute.
         *
         * @param type   The type of attribute.
         * @param source The source for the attribute.
         * @param value  The value for the attribute.
         *
         * @return The created BlackboardAttribute.
         */
        BlackboardAttribute make(BlackboardAttribute.Type type, String source, T value);
    }

    /**
     * Generates a list of artifacts each with one attribute of the provided
     * value.
     *
     * @param artifactType The artifact type.
     * @param attrType     The attribute type.
     * @param dataSource   The data source.
     * @param values       The values to be converted to attributes and each
     *                     attribute will be attached to a different artifact.
     * @param attrMaker    Function for creating the attribute.
     *
     * @return A list of artifacts where each artifact has an attribute of
     *         attrType and one of the values provided.
     *
     * @throws TskCoreException
     */
    private <T> List<BlackboardArtifact> getArtifacts(ARTIFACT_TYPE artifactType, BlackboardAttribute.Type attrType,
            DataSource dataSource, List<T> values, AttrMaker<T> attrMaker) throws TskCoreException {

        List<BlackboardArtifact> toRet = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            toRet.add(TskMockUtils.mockArtifact(new BlackboardArtifact.Type(artifactType), 1000 + i, dataSource,
                    attrMaker.make(attrType, "TEST SOURCE", values.get(i))));
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

    /**
     * Does a basic test passing a list of generated artifacts in mixed up order
     * to DataSourceInfoUtilities.getArtifacts and expecting a sorted list to be
     * returned.
     *
     * @param artifactType The artifact type.
     * @param attrType     The attribute type to sort on.
     * @param values       The values for each artifact's attribute.
     * @param attrMaker    The means of making the attribute.
     * @param sortOrder    The sort order to provide as an argument.
     * @param count        The count to provide.
     *
     * @throws TskCoreException
     */
    private <T> void testSorted(ARTIFACT_TYPE artifactType, ATTRIBUTE_TYPE attrType, List<T> values,
            AttrMaker<T> attrMaker, SortOrder sortOrder, int count) throws TskCoreException {

        DataSource dataSource = TskMockUtils.mockDataSource(1);
        List<BlackboardArtifact> sortedArtifacts = getArtifacts(artifactType, new BlackboardAttribute.Type(attrType),
                dataSource, values, attrMaker);

        List<BlackboardArtifact> mixedUpArtifacts = getMixedUp(sortedArtifacts);

        List<BlackboardArtifact> expectedArtifacts = count == 0
                ? sortedArtifacts
                : sortedArtifacts.subList(0, Math.min(sortedArtifacts.size(), count));

        test(new BlackboardArtifact.Type(artifactType), dataSource, new BlackboardAttribute.Type(attrType),
                sortOrder, count, mixedUpArtifacts, null, expectedArtifacts, null);

    }

    /**
     * Performs a basic test of sort order on each sortable attribute type.
     *
     * @param sortOrder The sort order.
     *
     * @throws TskCoreException
     */
    private void testAscDesc(SortOrder sortOrder) throws TskCoreException {
        List<String> strings = Arrays.asList("aardvark", "Bear", "cat", "Dog", "elephant");
        List<Integer> integers = Arrays.asList(22, 31, 42, 50, 60);
        List<Long> longs = Arrays.asList(22L, 31L, 42L, 50L, 60L);

        long day = 24 * 60 * 60;
        List<Long> dateTimes = Arrays.asList(day, 2 * day, 3 * day, 4 * day, 5 * day);
        List<Double> doubles = Arrays.asList(68.5, 70.1, 71.5, 72.3, 73.5);

        if (sortOrder == SortOrder.DESCENDING) {
            Collections.reverse(strings);
            Collections.reverse(integers);
            Collections.reverse(longs);
            Collections.reverse(dateTimes);
            Collections.reverse(doubles);
        }

        // sort on string
        testSorted(ARTIFACT_TYPE.TSK_WEB_COOKIE, ATTRIBUTE_TYPE.TSK_NAME, strings, BlackboardAttribute::new, sortOrder, 0);

        // sort on int
        testSorted(ARTIFACT_TYPE.TSK_PROG_RUN, ATTRIBUTE_TYPE.TSK_COUNT, integers, BlackboardAttribute::new, sortOrder, 0);

        // sort on long
        testSorted(ARTIFACT_TYPE.TSK_PROG_RUN, ATTRIBUTE_TYPE.TSK_BYTES_SENT, longs, BlackboardAttribute::new, sortOrder, 0);

        // sort on date
        testSorted(ARTIFACT_TYPE.TSK_RECENT_OBJECT, ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, dateTimes, BlackboardAttribute::new, sortOrder, 0);

        // sort on double
        testSorted(ARTIFACT_TYPE.TSK_GPS_BOOKMARK, ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, doubles, BlackboardAttribute::new, sortOrder, 0);
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
    public void testLimits() throws TskCoreException {
        List<Integer> integers = Arrays.asList(22, 31, 42, 50, 60);
        testSorted(ARTIFACT_TYPE.TSK_PROG_RUN, ATTRIBUTE_TYPE.TSK_COUNT, integers, BlackboardAttribute::new, SortOrder.ASCENDING, 3);
        testSorted(ARTIFACT_TYPE.TSK_PROG_RUN, ATTRIBUTE_TYPE.TSK_COUNT, integers, BlackboardAttribute::new, SortOrder.ASCENDING, 5);
        testSorted(ARTIFACT_TYPE.TSK_PROG_RUN, ATTRIBUTE_TYPE.TSK_COUNT, integers, BlackboardAttribute::new, SortOrder.ASCENDING, 10);
    }

    /**
     * Performs a test to ensure that an IllegalArgumentException is thrown on
     * an invalid attribute type (i.e. JSON or BYTE)
     *
     * @param artifactType  The artifact type to provide as an argument.
     * @param attributeType The attribute type to provide as an argument.
     * @param val           A dummy value.
     * @param attrMaker     The means of creating the attribute.
     *
     * @throws TskCoreException
     */
    private <T> void testFailOnBadAttrType(BlackboardArtifact.Type artifactType, BlackboardAttribute.Type attributeType, T val,
            AttrMaker<T> attrMaker) throws TskCoreException {

        DataSource dataSource = TskMockUtils.mockDataSource(1);

        List<BlackboardArtifact> artifacts = Arrays.asList(
                TskMockUtils.mockArtifact(artifactType, 2, dataSource, attrMaker.make(attributeType, "TEST SOURCE", val)),
                TskMockUtils.mockArtifact(artifactType, 3, dataSource, attrMaker.make(attributeType, "TEST SOURCE", val))
        );
        test(artifactType,
                dataSource,
                attributeType,
                SortOrder.ASCENDING,
                0,
                artifacts,
                null,
                null,
                IllegalArgumentException.class);
    }

    @Test
    public void testFailOnJson() throws TskCoreException {
        testFailOnBadAttrType(
                new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_GPS_ROUTE),
                new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_WAYPOINTS),
                "{ \"data\": \"none\" }",
                BlackboardAttribute::new);
    }

    @Test
    public void testFailOnBytes() throws TskCoreException {
        testFailOnBadAttrType(
                new BlackboardArtifact.Type(999, "BYTE_ARRAY_TYPE", "Byte Array Type"),
                new BlackboardAttribute.Type(999, "BYTE_ARR_ATTR_TYPE", "Byte Array Attribute Type", TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.BYTE),
                new byte[]{0x0, 0x1, 0x2},
                BlackboardAttribute::new);
    }

    @Test
    public void testPurgeAttrNotPresent() throws TskCoreException {
        long day = 24 * 60 * 60;
        DataSource dataSource = TskMockUtils.mockDataSource(1);

        BlackboardArtifact.Type ART_TYPE = new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_PROG_RUN);

        BlackboardArtifact mock1 = TskMockUtils.mockArtifact(ART_TYPE, 10, dataSource,
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT, "TEST SOURCE", 5),
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, "TEST SOURCE", day));

        BlackboardArtifact mock2 = TskMockUtils.mockArtifact(ART_TYPE, 20, dataSource,
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT, "TEST SOURCE", 6));

        BlackboardArtifact mock3 = TskMockUtils.mockArtifact(ART_TYPE, 30, dataSource,
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT, "TEST SOURCE", 7),
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, "TEST SOURCE", 3 * day));

        test(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_PROG_RUN),
                dataSource,
                new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME),
                SortOrder.ASCENDING,
                0,
                Arrays.asList(mock2, mock3, mock1),
                null,
                Arrays.asList(mock1, mock3),
                null);
    }

    @Test
    public void testMultAttrsPresent() throws TskCoreException {
        long day = 24 * 60 * 60;
        DataSource dataSource = TskMockUtils.mockDataSource(1);

        BlackboardArtifact.Type ART_TYPE = new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_PROG_RUN);

        BlackboardArtifact mock1 = TskMockUtils.mockArtifact(ART_TYPE, 10, dataSource,
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT, "TEST SOURCE", 7),
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, "TEST SOURCE", day));

        BlackboardArtifact mock2 = TskMockUtils.mockArtifact(ART_TYPE, 20, dataSource,
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT, "TEST SOURCE", 6));

        BlackboardArtifact mock3 = TskMockUtils.mockArtifact(ART_TYPE, 30, dataSource,
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT, "TEST SOURCE", 5),
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, "TEST SOURCE", 3 * day));

        test(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_PROG_RUN),
                dataSource,
                new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_COUNT),
                SortOrder.ASCENDING,
                0,
                Arrays.asList(mock2, mock3, mock1),
                null,
                Arrays.asList(mock3, mock2, mock1),
                null);
    }

    @Test
    public void testTskCoreExceptionThrown() throws TskCoreException {
        test(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_ACCOUNT),
                TskMockUtils.mockDataSource(1),
                new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE),
                SortOrder.ASCENDING,
                0,
                null,
                new TskCoreException("Failure Test"),
                new ArrayList<>(),
                TskCoreException.class);
    }

    @Test
    public void testThrowOnLessThan0() throws TskCoreException {
        test(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_ACCOUNT),
                TskMockUtils.mockDataSource(1),
                new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE),
                SortOrder.ASCENDING,
                -1,
                null,
                null,
                null,
                IllegalArgumentException.class);
    }

    @Test
    public void testEmptyListReturned() throws TskCoreException {
        test(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_ACCOUNT),
                TskMockUtils.mockDataSource(1),
                new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE),
                SortOrder.ASCENDING,
                0,
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                null);
    }
}
