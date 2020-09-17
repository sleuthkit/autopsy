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

import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * Helper class for getting data for the Recent Files Data Summary tab.
 */
public class RecentFilesSummary implements DefaultArtifactUpdateGovernor {

    private final static BlackboardAttribute.Type DATETIME_ACCESSED_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED);
    private final static BlackboardAttribute.Type DOMAIN_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN);
    private final static BlackboardAttribute.Type PATH_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH);
    private final static BlackboardAttribute.Type DATETIME_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
    private final static BlackboardAttribute.Type ASSOCATED_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT);
    private final static BlackboardAttribute.Type EMAIL_FROM_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM);
    private final static BlackboardAttribute.Type MSG_DATEIME_SENT_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_SENT);
    private final static BlackboardArtifact.Type ASSOCATED_OBJ_ART = new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT);

    private static final DateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());

    private static final Set<Integer> ARTIFACT_UPDATE_TYPE_IDS = new HashSet<>(Arrays.asList(
            ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID(),
            ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID(),
            ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT.getTypeID(),
            ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(),
            ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()
    ));

    private final SleuthkitCaseProvider provider;

    /**
     * Default constructor.
     */
    public RecentFilesSummary() {
        this(SleuthkitCaseProvider.DEFAULT);
    }

    /**
     * Construct object with given SleuthkitCaseProvider
     *
     * @param provider SleuthkitCaseProvider provider, cannot be null.
     */
    public RecentFilesSummary(SleuthkitCaseProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Unable to construct RecentFileSummary object. SleuthkitCaseProvider cannot be null");
        }

        this.provider = provider;
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return ARTIFACT_UPDATE_TYPE_IDS;
    }

    /**
     * Return a list of the most recently opened documents based on the
     * TSK_RECENT_OBJECT artifact.
     *
     * @param dataSource The data source to query.
     * @param maxCount   The maximum number of results to return, pass 0 to get
     *                   a list of all results.
     *
     * @return A list RecentFileDetails representing the most recently opened
     *         documents or an empty list if none were found.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<RecentFileDetails> getRecentlyOpenedDocuments(DataSource dataSource, int maxCount) throws SleuthkitCaseProviderException, TskCoreException {
        if (dataSource == null) {
            throw new IllegalArgumentException("Failed to get recently opened documents given data source was null");
        }

        List<BlackboardArtifact> artifactList
                = DataSourceInfoUtilities.getArtifacts(provider.get(),
                        new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_RECENT_OBJECT),
                        dataSource,
                        DATETIME_ATT,
                        DataSourceInfoUtilities.SortOrder.DESCENDING,
                        10);

        List<RecentFileDetails> fileDetails = new ArrayList<>();
        for (BlackboardArtifact artifact : artifactList) {
            Long accessedTime = null;
            String path = "";

            // Get all the attributes in one call.
            List<BlackboardAttribute> attributeList = artifact.getAttributes();
            for (BlackboardAttribute attribute : attributeList) {

                if (attribute.getAttributeType().equals(DATETIME_ATT)) {
                    accessedTime = attribute.getValueLong();
                } else if (attribute.getAttributeType().equals(PATH_ATT)) {
                    path = attribute.getValueString();
                }

                if (accessedTime != null) {
                    fileDetails.add(new RecentFileDetails(path, accessedTime));
                }
            }

        }

        return fileDetails;
    }

    /**
     * Return a list of the most recent downloads based on the value of the the
     * artifact TSK_DATETIME_ACCESSED attribute.
     *
     * @param dataSource Data source to query.
     * @param maxCount   Maximum number of results to return, passing 0 will
     *                   return all results.
     *
     * @return A list of RecentFileDetails objects or empty list if none were
     *         found.
     *
     * @throws TskCoreException
     * @throws SleuthkitCaseProviderException
     */
    public List<RecentDownloadDetails> getRecentDownloads(DataSource dataSource, int maxCount) throws TskCoreException, SleuthkitCaseProviderException {
        List<BlackboardArtifact> artifactList
                = DataSourceInfoUtilities.getArtifacts(provider.get(),
                        new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD),
                        dataSource,
                        DATETIME_ACCESSED_ATT,
                        DataSourceInfoUtilities.SortOrder.DESCENDING,
                        maxCount);

        List<RecentDownloadDetails> fileDetails = new ArrayList<>();
        for (BlackboardArtifact artifact : artifactList) {
            // Get all the attributes in one call.
            Long accessedTime = null;
            String domain = "";
            String path = "";

            List<BlackboardAttribute> attributeList = artifact.getAttributes();
            for (BlackboardAttribute attribute : attributeList) {

                if (attribute.getAttributeType().equals(DATETIME_ACCESSED_ATT)) {
                    accessedTime = attribute.getValueLong();
                } else if (attribute.getAttributeType().equals(DOMAIN_ATT)) {
                    domain = attribute.getValueString();
                } else if (attribute.getAttributeType().equals(PATH_ATT)) {
                    path = attribute.getValueString();
                }
            }
            if (accessedTime != null) {
                fileDetails.add(new RecentDownloadDetails(path, accessedTime, domain));
            }
        }

        return fileDetails;
    }

    /**
     * Returns a list of the most recent message attachments.
     *
     * @param dataSource Data source to query.
     * @param maxCount   Maximum number of results to return, passing 0 will
     *                   return all results.
     *
     * @return A list of RecentFileDetails of the most recent attachments.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<RecentAttachmentDetails> getRecentAttachments(DataSource dataSource, int maxCount) throws SleuthkitCaseProviderException, TskCoreException {
        return createListFromMap(buildAttachmentMap(dataSource), maxCount);
    }

    /**
     * Build a map of all of the message attachment sorted in date order.
     *
     * @param dataSource Data source to query.
     *
     * @return Returns a SortedMap of details objects returned in descending
     *         order.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    private SortedMap<Long, List<RecentAttachmentDetails>> buildAttachmentMap(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        SleuthkitCase skCase = provider.get();
        TreeMap<Long, List<RecentAttachmentDetails>> sortedMap = new TreeMap<>();

        List<BlackboardArtifact> associatedArtifacts = skCase.getBlackboard().getArtifacts(ASSOCATED_OBJ_ART.getTypeID(), dataSource.getId());
        for (BlackboardArtifact artifact : associatedArtifacts) {
            BlackboardAttribute attribute = artifact.getAttribute(ASSOCATED_ATT);
            if (attribute == null) {
                continue;
            }

            BlackboardArtifact messageArtifact = skCase.getBlackboardArtifact(attribute.getValueLong());
            if (isMessageArtifact(messageArtifact)) {
                Content content = artifact.getParent();
                if (content instanceof AbstractFile) {
                    String sender;
                    Long date = null;
                    String path;

                    BlackboardAttribute senderAttribute = messageArtifact.getAttribute(EMAIL_FROM_ATT);
                    if (senderAttribute != null) {
                        sender = senderAttribute.getValueString();
                    } else {
                        sender = "";
                    }
                    senderAttribute = messageArtifact.getAttribute(MSG_DATEIME_SENT_ATT);
                    if (senderAttribute != null) {
                        date = senderAttribute.getValueLong();
                    }

                    AbstractFile abstractFile = (AbstractFile) content;

                    path = Paths.get(abstractFile.getParentPath(), abstractFile.getName()).toString();

                    if (date != null && date != 0) {
                        List<RecentAttachmentDetails> list = sortedMap.get(date);
                        if (list == null) {
                            list = new ArrayList<>();
                            sortedMap.put(date, list);
                        }
                        RecentAttachmentDetails details = new RecentAttachmentDetails(path, date, sender);
                        if (!list.contains(details)) {
                            list.add(details);
                        }
                    }
                }
            }
        }
        return sortedMap.descendingMap();
    }

    /**
     * Create a list of detail objects from the given sorted map of the max
     * size.
     *
     * @param sortedMap A Map of attachment details sorted by date.
     * @param maxCount  Maximum number of values to return.
     *
     * @return A list of the details of the most recent attachments or empty
     *         list if none where found.
     */
    private List<RecentAttachmentDetails> createListFromMap(SortedMap<Long, List<RecentAttachmentDetails>> sortedMap, int maxCount) {
        List<RecentAttachmentDetails> fileList = new ArrayList<>();

        for (List<RecentAttachmentDetails> mapList : sortedMap.values()) {
            if (maxCount == 0 || fileList.size() + mapList.size() <= maxCount) {
                fileList.addAll(mapList);
                continue;
            }

            if (maxCount == fileList.size()) {
                break;
            }

            for (RecentAttachmentDetails details : mapList) {
                if (fileList.size() < maxCount) {
                    fileList.add(details);
                } else {
                    break;
                }
            }
        }

        return fileList;
    }

    /**
     * Is the given artifact a message.
     *
     * @param nodeArtifact An artifact that might be a message. Must not be
     *                     null.
     *
     * @return True if the given artifact is a message artifact
     */
    private boolean isMessageArtifact(BlackboardArtifact nodeArtifact) {
        final int artifactTypeID = nodeArtifact.getArtifactTypeID();
        return artifactTypeID == ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()
                || artifactTypeID == ARTIFACT_TYPE.TSK_MESSAGE.getTypeID();
    }

    /**
     * General data model object for files object.
     */
    public static class RecentFileDetails {

        private final String path;
        private final long date;

        /**
         * Constructor for files with just a path and date.
         *
         * @param path File path.
         * @param date File access date\time in seconds with java epoch
         */
        RecentFileDetails(String path, long date) {
            this.path = path;
            this.date = date;
        }

        /**
         * Returns the formatted date because the JTablePanel render assumes
         * only string data.
         *
         * @return Formatted date time.
         */
        public String getDateAsString() {
            return DATETIME_FORMAT.format(date * 1000);
        }

        /**
         * Returns the date as the seconds from java epoch.
         *
         * @return Seconds from java epoch.
         */
        Long getDateAsLong() {
            return date;
        }

        /**
         * Returns the file path.
         *
         * @return The file path.
         */
        public String getPath() {
            return path;
        }

    }

    /**
     * Data model for recently downloaded files.
     */
    public static class RecentDownloadDetails extends RecentFileDetails {

        private final String webDomain;

        /**
         * Constructor for files with just a path and date.
         *
         * @param path      File path.
         * @param date      File access date\time in seconds with java epoch.
         * @param webDomain The webdomain from which the file was downloaded.
         */
        RecentDownloadDetails(String path, long date, String webDomain) {
            super(path, date);
            this.webDomain = webDomain;
        }

        /**
         * Returns the web domain.
         *
         * @return The web domain or empty string if not available or
         *         applicable.
         */
        public String getWebDomain() {
            return webDomain;
        }
    }

    /**
     * Data model for recently attachments.
     */
    public static class RecentAttachmentDetails extends RecentFileDetails {

        private final String sender;

        /**
         * Constructor for recent download files which have a path, date and
         * domain value.
         *
         * @param path   File path.
         * @param date   File crtime.
         * @param sender The sender of the message from which the file was
         *               attached.
         */
        RecentAttachmentDetails(String path, long date, String sender) {
            super(path, date);
            this.sender = sender;
        }

        /**
         * Return the sender of the attached file.
         *
         * @return The sender of the attached file or empty string if not
         *         available.
         */
        public String getSender() {
            return sender;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof RecentAttachmentDetails)) {
                return false;
            }
            RecentAttachmentDetails compareObj = (RecentAttachmentDetails) obj;

            return compareObj.getSender().equals(this.sender)
                    && compareObj.getPath().equals(this.getPath())
                    && compareObj.getDateAsLong().equals(this.getDateAsLong());
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 73 * hash + Objects.hashCode(this.sender);
            return hash;
        }
    }
}
