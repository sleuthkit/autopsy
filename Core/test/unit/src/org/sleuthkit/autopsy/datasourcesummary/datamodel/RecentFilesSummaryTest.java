/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp. Contact: carrier <at> sleuthkit <dot>
 * org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentAttachmentDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentDownloadDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentFileDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.testutils.RandomizationUtils;
import org.sleuthkit.autopsy.testutils.TskMockUtils;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Tests for RecentFilesSummaryTest
 */
public class RecentFilesSummaryTest {

    /**
     * An interface for calling methods in RecentFilesSummary in a uniform
     * manner.
     */
    private interface RecentFilesMethod<T> {

        /**
         * Means of acquiring data from a method in RecentFilesSummary.
         *
         * @param recentFilesSummary The RecentFilesSummary object.
         * @param dataSource The datasource.
         * @param count The number of items to retrieve.
         *
         * @return The method's return data.
         *
         * @throws SleuthkitCaseProviderException
         * @throws TskCoreException
         */
        List<T> fetch(RecentFilesSummary recentFilesSummary, DataSource dataSource, int count)
                throws SleuthkitCaseProviderException, TskCoreException;
    }

    private static final RecentFilesMethod<RecentFileDetails> RECENT_DOCS_FUNCT
            = (summary, dataSource, count) -> summary.getRecentlyOpenedDocuments(dataSource, count);

    private static final RecentFilesMethod<RecentDownloadDetails> RECENT_DOWNLOAD_FUNCT
            = (summary, dataSource, count) -> summary.getRecentDownloads(dataSource, count);

    private static final RecentFilesMethod<RecentAttachmentDetails> RECENT_ATTACHMENT_FUNCT
            = (summary, dataSource, count) -> summary.getRecentAttachments(dataSource, count);

    /**
     * If -1 count passed to method, should throw IllegalArgumentException.
     *
     * @param method The method to call.
     * @param methodName The name of the metho
     *
     * @throws TskCoreException
     * @throws SleuthkitCaseProviderException
     */
    private <T> void testNonPositiveCount_ThrowsError(RecentFilesMethod<T> method, String methodName)
            throws TskCoreException, SleuthkitCaseProviderException {
        Pair<SleuthkitCase, Blackboard> casePair = DataSourceSummaryMockUtils.getArtifactsTSKMock(null);
        DataSource dataSource = TskMockUtils.getDataSource(1);
        RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());

        try {
            method.fetch(summary, dataSource, -1);
            fail("Expected method " + methodName + " to fail on negative count.");
        } catch (IllegalArgumentException ignored) {
            verify(casePair.getRight(),
                    never().description("Expected negative count for " + methodName + " to not call any methods in SleuthkitCase."))
                    .getArtifacts(anyInt(), anyLong());
        }
    }

    @Test
    public void getRecentlyOpenedDocuments_nonPositiveCount_ThrowsError() throws TskCoreException, SleuthkitCaseProviderException {
        testNonPositiveCount_ThrowsError(RECENT_DOCS_FUNCT, "getRecentlyOpenedDocuments");
    }

    @Test
    public void getRecentDownloads_nonPositiveCount_ThrowsError() throws TskCoreException, SleuthkitCaseProviderException {
        testNonPositiveCount_ThrowsError(RECENT_DOWNLOAD_FUNCT, "getRecentDownloads");
    }

    @Test
    public void getRecentAttachments_nonPositiveCount_ThrowsError() throws TskCoreException, SleuthkitCaseProviderException {
        testNonPositiveCount_ThrowsError(RECENT_ATTACHMENT_FUNCT, "getRecentAttachments");
    }

    /**
     * Tests that if no data source provided, an empty list is returned and
     * SleuthkitCase isn't called.
     *
     * @param recentFilesMethod The method to call.
     * @param methodName The name of the method
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    private <T> void testNoDataSource_ReturnsEmptyList(RecentFilesMethod<T> recentFilesMethod, String methodName)
            throws SleuthkitCaseProviderException, TskCoreException {

        Pair<SleuthkitCase, Blackboard> casePair = DataSourceSummaryMockUtils.getArtifactsTSKMock(null);
        RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());

        List<? extends T> items = recentFilesMethod.fetch(summary, null, 10);
        Assert.assertNotNull("Expected method " + methodName + " to return an empty list.", items);
        Assert.assertEquals("Expected method " + methodName + " to return an empty list.", 0, items.size());
        verify(casePair.getRight(),
                never().description("Expected null datasource for " + methodName + " to not call any methods in SleuthkitCase."))
                .getArtifacts(anyInt(), anyLong());
    }

    @Test
    public void getRecentlyOpenedDocuments_noDataSource_ReturnsEmptyList() throws TskCoreException, SleuthkitCaseProviderException {
        testNoDataSource_ReturnsEmptyList(RECENT_DOCS_FUNCT, "getRecentlyOpenedDocuments");
    }

    @Test
    public void getRecentDownloads_noDataSource_ReturnsEmptyList() throws TskCoreException, SleuthkitCaseProviderException {
        testNoDataSource_ReturnsEmptyList(RECENT_DOWNLOAD_FUNCT, "getRecentDownloads");
    }

    @Test
    public void getRecentAttachments_noDataSource_ReturnsEmptyList() throws TskCoreException, SleuthkitCaseProviderException {
        testNonPositiveCount_ThrowsError(RECENT_ATTACHMENT_FUNCT, "getRecentAttachments");
    }

    /**
     * If SleuthkitCase returns no results, an empty list is returned.
     *
     * @param recentFilesMethod The method to call.
     * @param methodName The name of the method.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    private <T> void testNoReturnedResults_ReturnsEmptyList(RecentFilesMethod<T> recentFilesMethod, String methodName)
            throws SleuthkitCaseProviderException, TskCoreException {

        Pair<SleuthkitCase, Blackboard> casePair = DataSourceSummaryMockUtils.getArtifactsTSKMock(Collections.emptyList());
        RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());
        DataSource dataSource = TskMockUtils.getDataSource(1);
        List<? extends T> items = recentFilesMethod.fetch(summary, dataSource, 10);
        Assert.assertNotNull("Expected method " + methodName + " to return an empty list.", items);
        Assert.assertEquals("Expected method " + methodName + " to return an empty list.", 0, items.size());
        verify(casePair.getRight(),
                times(1).description("Expected " + methodName + " to call Blackboard once."))
                .getArtifacts(anyInt(), anyLong());
    }

    @Test
    public void getRecentlyOpenedDocuments_noReturnedResults_ReturnsEmptyList() throws TskCoreException, SleuthkitCaseProviderException {
        testNoReturnedResults_ReturnsEmptyList(RECENT_DOCS_FUNCT, "getRecentlyOpenedDocuments");
    }

    @Test
    public void getRecentDownloads_noReturnedResults_ReturnsEmptyList() throws TskCoreException, SleuthkitCaseProviderException {
        testNoReturnedResults_ReturnsEmptyList(RECENT_DOWNLOAD_FUNCT, "getRecentDownloads");
    }

    @Test
    public void getRecentAttachments_testNoDataSource_ReturnsEmptyList() throws TskCoreException, SleuthkitCaseProviderException {
        testNoReturnedResults_ReturnsEmptyList(RECENT_ATTACHMENT_FUNCT, "getRecentAttachments");
    }

    private static final long DAY_SECONDS = 24 * 60 * 60;

    /**
     * A means of creating a number representing seconds from epoch where the
     * lower the idx, the more recent the time.
     */
    private static final Function<Integer, Long> dateTimeRetriever = (idx) -> (365 - idx) * DAY_SECONDS + 1;

    /**
     * Gets a mock BlackboardArtifact.
     *
     * @param ds The data source to which the artifact belongs.
     * @param artifactId The artifact id.
     * @param artType The artifact type.
     * @param attributeArgs The mapping of attribute type to value for each
     * attribute in the artifact.
     *
     * @return The mock artifact.
     */
    private BlackboardArtifact getArtifact(DataSource ds, long artifactId, ARTIFACT_TYPE artType, List<Pair<ATTRIBUTE_TYPE, Object>> attributeArgs) {
        try {
            List<BlackboardAttribute> attributes = attributeArgs.stream()
                    .filter((arg) -> arg != null && arg.getLeft() != null && arg.getRight() != null)
                    .map((arg) -> {
                        return TskMockUtils.getAttribute(arg.getLeft(), arg.getRight());
                    })
                    .collect(Collectors.toList());

            return TskMockUtils.getArtifact(new BlackboardArtifact.Type(artType), artifactId, ds, attributes);
        } catch (TskCoreException ex) {
            fail("There was an error mocking an artifact.");
            return null;
        }
    }

    /**
     * Returns a mock artifact for getRecentlyOpenedDocuments.
     *
     * @param ds The datasource for the artifact.
     * @param artifactId The artifact id.
     * @param dateTime The time in seconds from epoch.
     * @param path The path for the document.
     *
     * @return The mock artifact with pertinent attributes.
     */
    private BlackboardArtifact getRecentDocumentArtifact(DataSource ds, long artifactId, Long dateTime, String path) {
        return getArtifact(ds, artifactId, ARTIFACT_TYPE.TSK_RECENT_OBJECT, Arrays.asList(
                Pair.of(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED , dateTime),
                Pair.of(ATTRIBUTE_TYPE.TSK_PATH, path)
        ));
    }

    @Test
    public void getRecentlyOpenedDocuments_sortedByDateTimeAndLimited() throws SleuthkitCaseProviderException, TskCoreException {
        Function<Integer, String> pathRetriever = (idx) -> "/path/to/downloads/" + idx;
        DataSource dataSource = TskMockUtils.getDataSource(1);

        int countRequest = 10;
        for (int countToGenerate : new int[]{1, 9, 10, 11}) {
            // generate artifacts for each artifact
            List<BlackboardArtifact> artifacts = new ArrayList<>();
            for (int idx = 0; idx < countToGenerate; idx++) {
                BlackboardArtifact artifact = getRecentDocumentArtifact(dataSource,
                        1000 + idx, dateTimeRetriever.apply(idx), pathRetriever.apply(idx));
                artifacts.add(artifact);
            }

            // run through method
            Pair<SleuthkitCase, Blackboard> casePair = DataSourceSummaryMockUtils.getArtifactsTSKMock(RandomizationUtils.getMixedUp(artifacts));
            RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());
            List<RecentFileDetails> results = summary.getRecentlyOpenedDocuments(dataSource, countRequest);

            // verify results
            int expectedCount = Math.min(countRequest, countToGenerate);
            Assert.assertNotNull(results);
            Assert.assertEquals(expectedCount, results.size());
            for (int i = 0; i < expectedCount; i++) {
                Assert.assertEquals(dateTimeRetriever.apply(i), results.get(i).getDateAsLong());
                Assert.assertEquals(pathRetriever.apply(i), results.get(i).getPath());
            }
        }
    }

    @Test
    public void getRecentlyOpenedDocuments_uniquePaths() throws SleuthkitCaseProviderException, TskCoreException {
        DataSource dataSource = TskMockUtils.getDataSource(1);

        BlackboardArtifact item1 = getRecentDocumentArtifact(dataSource, 1001, DAY_SECONDS, "/a/path");
        BlackboardArtifact item2 = getRecentDocumentArtifact(dataSource, 1002, DAY_SECONDS + 1, "/a/path");
        BlackboardArtifact item3 = getRecentDocumentArtifact(dataSource, 1003, DAY_SECONDS + 2, "/a/path");
        List<BlackboardArtifact> artifacts = Arrays.asList(item2, item3, item1);

        Pair<SleuthkitCase, Blackboard> casePair = DataSourceSummaryMockUtils.getArtifactsTSKMock(RandomizationUtils.getMixedUp(artifacts));
        RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());
        List<RecentFileDetails> results = summary.getRecentlyOpenedDocuments(dataSource, 10);

        // verify results (only successItem)
        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals((Long) (DAY_SECONDS + 2), results.get(0).getDateAsLong());
        Assert.assertTrue("/a/path".equalsIgnoreCase(results.get(0).getPath()));
    }

    @Test
    public void getRecentlyOpenedDocuments_filtersMissingData() throws SleuthkitCaseProviderException, TskCoreException {
        DataSource dataSource = TskMockUtils.getDataSource(1);

        BlackboardArtifact successItem = getRecentDocumentArtifact(dataSource, 1001, DAY_SECONDS, "/a/path");
        BlackboardArtifact nullTime = getRecentDocumentArtifact(dataSource, 1002, null, "/a/path2");
        BlackboardArtifact zeroTime = getRecentDocumentArtifact(dataSource, 10021, 0L, "/a/path2a");
        List<BlackboardArtifact> artifacts = Arrays.asList(nullTime, zeroTime, successItem);

        Pair<SleuthkitCase, Blackboard> casePair = DataSourceSummaryMockUtils.getArtifactsTSKMock(RandomizationUtils.getMixedUp(artifacts));
        RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());
        List<RecentFileDetails> results = summary.getRecentlyOpenedDocuments(dataSource, 10);

        // verify results (only successItem)
        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals((Long) DAY_SECONDS, results.get(0).getDateAsLong());
        Assert.assertTrue("/a/path".equalsIgnoreCase(results.get(0).getPath()));
    }

    /**
     * Creates a mock blackboard artifact for getRecentDownloads.
     *
     * @param ds The datasource.
     * @param artifactId The artifact id.
     * @param dateTime The time in seconds from epoch.
     * @param domain The domain.
     * @param path The path for the download.
     *
     * @return The mock artifact.
     */
    private BlackboardArtifact getRecentDownloadArtifact(DataSource ds, long artifactId, Long dateTime, String domain, String path) {
        return getArtifact(ds, artifactId, ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, Arrays.asList(
                Pair.of(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, dateTime),
                Pair.of(ATTRIBUTE_TYPE.TSK_DOMAIN, domain),
                Pair.of(ATTRIBUTE_TYPE.TSK_PATH, path)
        ));
    }

    @Test
    public void getRecentDownloads_sortedByDateTimeAndLimited() throws SleuthkitCaseProviderException, TskCoreException {
        Function<Integer, String> domainRetriever = (idx) -> String.format("www.domain%d.com", idx);
        Function<Integer, String> pathRetriever = (idx) -> "/path/to/downloads/doc" + idx + ".pdf";

        // run through method
        DataSource dataSource = TskMockUtils.getDataSource(1);

        int countRequest = 10;
        for (int countToGenerate : new int[]{1, 9, 10, 11}) {
            // generate artifacts for each artifact
            List<BlackboardArtifact> artifacts = new ArrayList<>();
            for (int idx = 0; idx < countToGenerate; idx++) {
                BlackboardArtifact artifact = getRecentDownloadArtifact(dataSource,
                        1000 + idx, dateTimeRetriever.apply(idx), domainRetriever.apply(idx),
                        pathRetriever.apply(idx));

                artifacts.add(artifact);
            }

            // call method
            Pair<SleuthkitCase, Blackboard> casePair = DataSourceSummaryMockUtils.getArtifactsTSKMock(RandomizationUtils.getMixedUp(artifacts));
            RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());
            List<RecentDownloadDetails> results = summary.getRecentDownloads(dataSource, countRequest);

            // verify results
            int expectedCount = Math.min(countRequest, countToGenerate);
            Assert.assertNotNull(results);
            Assert.assertEquals(expectedCount, results.size());
            for (int i = 0; i < expectedCount; i++) {
                Assert.assertEquals(dateTimeRetriever.apply(i), results.get(i).getDateAsLong());
                Assert.assertEquals(pathRetriever.apply(i), results.get(i).getPath());
                Assert.assertEquals(domainRetriever.apply(i), results.get(i).getWebDomain());
            }
        }
    }

    @Test
    public void getRecentDownloads_uniquePaths() throws SleuthkitCaseProviderException, TskCoreException {
        DataSource dataSource = TskMockUtils.getDataSource(1);

        BlackboardArtifact item1 = getRecentDownloadArtifact(dataSource, 1001, DAY_SECONDS, "domain1.com", "/a/path1");
        BlackboardArtifact item1a = getRecentDownloadArtifact(dataSource, 10011, DAY_SECONDS + 1, "domain1.com", "/a/path1");
        BlackboardArtifact item2 = getRecentDownloadArtifact(dataSource, 1002, DAY_SECONDS + 2, "domain2.com", "/a/path1");
        BlackboardArtifact item3 = getRecentDownloadArtifact(dataSource, 1003, DAY_SECONDS + 3, "domain2a.com", "/a/path1");
        List<BlackboardArtifact> artifacts = Arrays.asList(item2, item3, item1);

        Pair<SleuthkitCase, Blackboard> casePair = DataSourceSummaryMockUtils.getArtifactsTSKMock(RandomizationUtils.getMixedUp(artifacts));
        RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());

        // call method
        List<RecentDownloadDetails> results = summary.getRecentDownloads(dataSource, 10);

        // verify results
        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals((Long) (DAY_SECONDS + 3), results.get(0).getDateAsLong());
        Assert.assertTrue("/a/path1".equalsIgnoreCase(results.get(0).getPath()));
        Assert.assertTrue("domain2a.com".equalsIgnoreCase(results.get(0).getWebDomain()));
    }

    @Test
    public void getRecentDownloads_filtersMissingData() throws SleuthkitCaseProviderException, TskCoreException {
        DataSource dataSource = TskMockUtils.getDataSource(1);

        BlackboardArtifact successItem = getRecentDownloadArtifact(dataSource, 1001, DAY_SECONDS, "domain1.com", "/a/path1");
        BlackboardArtifact nullTime = getRecentDownloadArtifact(dataSource, 1002, null, "domain2.com", "/a/path2");
        BlackboardArtifact zeroTime = getRecentDownloadArtifact(dataSource, 10021, 0L, "domain2a.com", "/a/path2a");
        List<BlackboardArtifact> artifacts = Arrays.asList(nullTime, zeroTime, successItem);

        Pair<SleuthkitCase, Blackboard> casePair = DataSourceSummaryMockUtils.getArtifactsTSKMock(RandomizationUtils.getMixedUp(artifacts));
        RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());

        // call method
        List<RecentDownloadDetails> results = summary.getRecentDownloads(dataSource, 10);

        // verify results
        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals((Long) DAY_SECONDS, results.get(0).getDateAsLong());
        Assert.assertTrue("/a/path1".equalsIgnoreCase(results.get(0).getPath()));
    }

    /**
     * getRecentAttachments method has special setup conditions. This class
     * encapsulates all the SleuthkitCase/BlackboardArtifact setup for on
     * possible return item.
     */
    private class AttachmentArtifactItem {

        private final Integer messageArtifactTypeId;
        private final boolean associatedAttrFormed;
        private final String emailFrom;
        private final Long messageTime;
        private final boolean isParent;
        private final String fileParentPath;
        private final String fileName;

        /**
         * Constructor with all parameters.
         *
         * @param messageArtifactTypeId The type id for the artifact or null if
         * no message artifact to be created.
         * @param emailFrom Who the message is from or null not to include
         * attribute.
         * @param messageTime Time in seconds from epoch or null not to include
         * attribute.
         * @param fileParentPath The parent AbstractFile's path value.
         * @param fileName The parent AbstractFile's filename value.
         * @param associatedAttrFormed If false, the TSK_ASSOCIATED_OBJECT
         * artifact has no attribute (even though it is required).
         * @param hasParent Whether or not the artifact has a parent
         * AbstractFile.
         */
        AttachmentArtifactItem(Integer messageArtifactTypeId, String emailFrom, Long messageTime,
                String fileParentPath, String fileName,
                boolean associatedAttrFormed, boolean hasParent) {

            this.messageArtifactTypeId = messageArtifactTypeId;
            this.associatedAttrFormed = associatedAttrFormed;
            this.emailFrom = emailFrom;
            this.messageTime = messageTime;
            this.isParent = hasParent;
            this.fileParentPath = fileParentPath;
            this.fileName = fileName;
        }

        /**
         * Convenience constructor where defaults of required attributes and
         * SleuthkitCase assumed.
         *
         * @param messageArtifactTypeId The type id for the artifact or null if
         * no message artifact to be created.
         * @param emailFrom Who the message is from or null not to include
         * attribute.
         * @param messageTime Time in seconds from epoch or null not to include
         * attribute.
         * @param fileParentPath The parent AbstractFile's path value.
         * @param fileName The parent AbstractFile's filename value.
         */
        AttachmentArtifactItem(Integer messageArtifactTypeId, String emailFrom, Long messageTime, String fileParentPath, String fileName) {
            this(messageArtifactTypeId, emailFrom, messageTime, fileParentPath, fileName, true, true);
        }

        boolean isAssociatedAttrFormed() {
            return associatedAttrFormed;
        }

        String getEmailFrom() {
            return emailFrom;
        }

        Long getMessageTime() {
            return messageTime;
        }

        boolean hasParent() {
            return isParent;
        }

        String getFileParentPath() {
            return fileParentPath;
        }

        String getFileName() {
            return fileName;
        }

        Integer getMessageArtifactTypeId() {
            return messageArtifactTypeId;
        }
    }

    /**
     * Sets up the associated artifact message for the TSK_ASSOCIATED_OBJECT.
     *
     * @param artifacts The mapping of artifact id to artifact.
     * @param item The record to setup.
     * @param dataSource The datasource.
     * @param associatedId The associated attribute id.
     * @param artifactId The artifact id.
     *
     * @return The associated Artifact blackboard attribute.
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute setupAssociatedMessage(Map<Long, BlackboardArtifact> artifacts, AttachmentArtifactItem item,
            DataSource dataSource, Long associatedId, Long artifactId) throws TskCoreException {

        BlackboardAttribute associatedAttr = TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, associatedId);

        if (item.getMessageArtifactTypeId() == null) {
            return associatedAttr;
        }

        // find the artifact type or null if not found
        ARTIFACT_TYPE messageType = Stream.of(ARTIFACT_TYPE.values())
                .filter((artType) -> artType.getTypeID() == item.getMessageArtifactTypeId())
                .findFirst()
                .orElse(null);

        // if there is a message type, create the artifact
        if (messageType != null) {
            List<BlackboardAttribute> attributes = new ArrayList<>();
            if (item.getEmailFrom() != null) {
                attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_EMAIL_FROM, item.getEmailFrom()));
            }

            if (item.getMessageTime() != null) {
                attributes.add(TskMockUtils.getAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_SENT, item.getMessageTime()));
            }

            artifacts.put(associatedId, TskMockUtils.getArtifact(
                    new BlackboardArtifact.Type(messageType), artifactId, dataSource, attributes));
        }
        return associatedAttr;
    }

    /**
     * Since getRecentAttachments does not simply query one type of artifact and
     * return results, this method sets up a mock SleuthkitCase and Blackboard
     * to return pertinent data.
     *
     * @param items Each attachment item where each item could represent a
     * return result if fully formed.
     *
     * @return The mock SleuthkitCase and Blackboard.
     */
    private Pair<SleuthkitCase, Blackboard> getRecentAttachmentArtifactCase(List<AttachmentArtifactItem> items) {
        SleuthkitCase skCase = mock(SleuthkitCase.class);
        Blackboard blackboard = mock(Blackboard.class);
        when(skCase.getBlackboard()).thenReturn(blackboard);

        DataSource dataSource = TskMockUtils.getDataSource(1);

        long objIdCounter = 100;
        Map<Long, BlackboardArtifact> artifacts = new HashMap<>();
        try {
            for (AttachmentArtifactItem item : items) {
                BlackboardAttribute associatedAttr = null;
                // if the associated attribute is fully formed, 
                // create the associated attribute and related artifact
                if (item.isAssociatedAttrFormed()) {
                    associatedAttr = setupAssociatedMessage(artifacts, item, dataSource, ++objIdCounter, ++objIdCounter);
                }

                // create the content parent for the associated object if one should be present
                Content parent = (item.hasParent())
                        ? TskMockUtils.getAbstractFile(++objIdCounter, item.getFileParentPath(), item.getFileName())
                        : null;

                Long associatedId = ++objIdCounter;
                artifacts.put(associatedId, TskMockUtils.getArtifact(
                        new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT),
                        parent, associatedId, dataSource, associatedAttr));
            }

            // set up the blackboard to return artifacts that match the type id.
            when(blackboard.getArtifacts(anyInt(), anyLong())).thenAnswer((inv) -> {
                Object[] args = inv.getArguments();
                int artifactType = (Integer) args[0];
                return artifacts.values().stream()
                        .filter(art -> art.getArtifactTypeID() == artifactType)
                        .collect(Collectors.toList());
            });

            // also set up the sleuthkitcase to return the artifact with the matching id or null.
            when(skCase.getBlackboardArtifact(anyLong())).thenAnswer((inv2) -> {
                Object[] args2 = inv2.getArguments();
                long id = (Long) args2[0];
                return artifacts.get(id);
            });

            return Pair.of(skCase, blackboard);
        } catch (TskCoreException ex) {
            fail("There was an error while creating SleuthkitCase for getRecentAttachments");
            return null;
        }
    }

    @Test
    public void getRecentAttachments_sortedByDateTimeAndLimited() throws SleuthkitCaseProviderException, TskCoreException {
        DataSource dataSource = TskMockUtils.getDataSource(1);
        // a deterministic means of transforming an index into a particular attribute type so that they can be created 
        // and compared on return
        Function<Integer, String> emailFromRetriever = (idx) -> String.format("person%d@basistech.com", idx);
        Function<Integer, String> pathRetriever = (idx) -> "/path/to/attachment/" + idx;
        Function<Integer, String> fileNameRetriever = (idx) -> String.format("%d-filename.png", idx);

        int countRequest = 10;
        for (int countToGenerate : new int[]{1, 9, 10, 11}) {
            // set up the items in the sleuthkit case
            List<AttachmentArtifactItem> items = IntStream.range(0, countToGenerate)
                    .mapToObj((idx) -> new AttachmentArtifactItem(ARTIFACT_TYPE.TSK_MESSAGE.getTypeID(),
                    emailFromRetriever.apply(idx), dateTimeRetriever.apply(idx),
                    pathRetriever.apply(idx), fileNameRetriever.apply(idx)))
                    .collect(Collectors.toList());

            List<AttachmentArtifactItem> mixedUpItems = RandomizationUtils.getMixedUp(items);
            Pair<SleuthkitCase, Blackboard> casePair = getRecentAttachmentArtifactCase(mixedUpItems);
            RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());

            // retrieve results
            List<RecentAttachmentDetails> results = summary.getRecentAttachments(dataSource, countRequest);

            // verify results
            int expectedCount = Math.min(countRequest, countToGenerate);
            Assert.assertNotNull(results);
            Assert.assertEquals(expectedCount, results.size());

            for (int i = 0; i < expectedCount; i++) {
                RecentAttachmentDetails result = results.get(i);
                Assert.assertEquals(dateTimeRetriever.apply(i), result.getDateAsLong());
                Assert.assertTrue(emailFromRetriever.apply(i).equalsIgnoreCase(result.getSender()));
                Assert.assertTrue(Paths.get(pathRetriever.apply(i), fileNameRetriever.apply(i)).toString()
                        .equalsIgnoreCase(result.getPath()));
            }
        }
    }

    @Test
    public void getRecentAttachments_filterData() throws SleuthkitCaseProviderException, TskCoreException {
        // setup data
        DataSource dataSource = TskMockUtils.getDataSource(1);

        AttachmentArtifactItem successItem = new AttachmentArtifactItem(ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(),
                "person@sleuthkit.com", DAY_SECONDS, "/parent/path", "msg.pdf");
        AttachmentArtifactItem successItem2 = new AttachmentArtifactItem(ARTIFACT_TYPE.TSK_MESSAGE.getTypeID(),
                "person_on_skype", DAY_SECONDS + 1, "/parent/path/to/skype", "skype.png");
        AttachmentArtifactItem wrongArtType = new AttachmentArtifactItem(ARTIFACT_TYPE.TSK_CALLLOG.getTypeID(),
                "5555675309", DAY_SECONDS + 2, "/path/to/callog/info", "callog.dat");
        AttachmentArtifactItem missingTimeStamp = new AttachmentArtifactItem(ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(),
                "person2@sleuthkit.com", null, "/parent/path", "msg2.pdf");
        AttachmentArtifactItem zeroTimeStamp = new AttachmentArtifactItem(ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(),
                "person2a@sleuthkit.com", 0L, "/parent/path", "msg2a.png");
        AttachmentArtifactItem noParentFile = new AttachmentArtifactItem(ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(),
                "person4@sleuthkit.com", DAY_SECONDS + 4, "/parent/path", "msg4.jpg", true, false);
        AttachmentArtifactItem noAssocAttr = new AttachmentArtifactItem(ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(),
                "person3@sleuthkit.com", DAY_SECONDS + 5, "/parent/path", "msg5.gif", false, true);
        AttachmentArtifactItem missingAssocArt = new AttachmentArtifactItem(null,
                "person3@sleuthkit.com", DAY_SECONDS + 6, "/parent/path", "msg6.pdf");

        List<AttachmentArtifactItem> items = Arrays.asList(successItem, successItem2,
                wrongArtType, missingTimeStamp, zeroTimeStamp,
                noParentFile, noAssocAttr, missingAssocArt);

        Pair<SleuthkitCase, Blackboard> casePair = getRecentAttachmentArtifactCase(items);
        RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());

        // get data
        List<RecentAttachmentDetails> results = summary.getRecentAttachments(dataSource, 10);

        // verify results
        Assert.assertNotNull(results);
        Assert.assertEquals(2, results.size());
        RecentAttachmentDetails successItem2Details = results.get(0);
        RecentAttachmentDetails successItemDetails = results.get(1);

        Assert.assertEquals(successItemDetails.getDateAsLong(), (Long) DAY_SECONDS);
        Assert.assertTrue(Paths.get(successItem.getFileParentPath(), successItem.getFileName())
                .toString().equalsIgnoreCase(successItemDetails.getPath()));
        Assert.assertTrue(successItem.getEmailFrom().equalsIgnoreCase(successItemDetails.getSender()));

        Assert.assertEquals(successItem2Details.getDateAsLong(), (Long) (DAY_SECONDS + 1));
        Assert.assertTrue(Paths.get(successItem2.getFileParentPath(), successItem2.getFileName())
                .toString().equalsIgnoreCase(successItem2Details.getPath()));
        Assert.assertTrue(successItem2.getEmailFrom().equalsIgnoreCase(successItem2Details.getSender()));
    }

    @Test
    public void getRecentAttachments_uniquePath() throws SleuthkitCaseProviderException, TskCoreException {
        // setup data
        DataSource dataSource = TskMockUtils.getDataSource(1);

        AttachmentArtifactItem item1 = new AttachmentArtifactItem(ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(),
                "person@sleuthkit.com", DAY_SECONDS, "/parent/path", "msg.pdf");
        AttachmentArtifactItem item2 = new AttachmentArtifactItem(ARTIFACT_TYPE.TSK_MESSAGE.getTypeID(),
                "person_on_skype", DAY_SECONDS + 1, "/parent/path", "msg.pdf");
        AttachmentArtifactItem item3 = new AttachmentArtifactItem(ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(),
                "person2@sleuthkit.com", DAY_SECONDS + 2, "/parent/path", "msg.pdf");

        List<AttachmentArtifactItem> items = Arrays.asList(item1, item2, item3);

        Pair<SleuthkitCase, Blackboard> casePair = getRecentAttachmentArtifactCase(items);
        RecentFilesSummary summary = new RecentFilesSummary(() -> casePair.getLeft());

        // get data
        List<RecentAttachmentDetails> results = summary.getRecentAttachments(dataSource, 10);

        // verify results
        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.size());

        Assert.assertEquals(results.get(0).getDateAsLong(), (Long) (DAY_SECONDS + 2));
        Assert.assertTrue(Paths.get(item3.getFileParentPath(), item3.getFileName())
                .toString().equalsIgnoreCase(results.get(0).getPath()));
        Assert.assertTrue(results.get(0).getSender().equalsIgnoreCase(item3.getEmailFrom()));
    }
}
