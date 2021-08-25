/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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

import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * Helper class for getting Recent Activity data.
 */
public class RecentFilesSummary {

    private final static BlackboardAttribute.Type DATETIME_ACCESSED_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED);
    private final static BlackboardAttribute.Type DOMAIN_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN);
    private final static BlackboardAttribute.Type PATH_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH);
    private final static BlackboardAttribute.Type ASSOCATED_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT);
    private final static BlackboardAttribute.Type EMAIL_FROM_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM);
    private final static BlackboardAttribute.Type MSG_DATEIME_SENT_ATT = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_SENT);
    private final static BlackboardArtifact.Type ASSOCATED_OBJ_ART = new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT);

    private static final DateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());

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

    /**
     * Removes fileDetails entries with redundant paths, sorts by date
     * descending and limits to the limit provided.
     *
     * @param fileDetails The file details list.
     * @param limit The maximum number of entries to return.
     * @return The sorted limited list with unique paths.
     */
    private static <T extends RecentFileDetails> List<T> getSortedLimited(List<T> fileDetails, int limit) {
        Map<String, T> fileDetailsMap = fileDetails.stream()
                .filter(details -> details != null)
                .collect(Collectors.toMap(
                        d -> d.getPath().toUpperCase(),
                        d -> d,
                        (d1, d2) -> Long.compare(d1.getDateAsLong(), d2.getDateAsLong()) > 0 ? d1 : d2));

        return fileDetailsMap.values().stream()
                .sorted((a, b) -> -Long.compare(a.getDateAsLong(), b.getDateAsLong()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Returns a RecentFileDetails object as derived from the recent document
     * artifact or null if no appropriate object can be made.
     *
     * @param artifact The artifact.
     * @return The derived object or null if artifact is invalid.
     */
    private static RecentFileDetails getRecentlyOpenedDocument(BlackboardArtifact artifact) {
        String path = DataSourceInfoUtilities.getStringOrNull(artifact, PATH_ATT);
        Long lastOpened = DataSourceInfoUtilities.getLongOrNull(artifact, DATETIME_ACCESSED_ATT);

        if (StringUtils.isBlank(path) || lastOpened == null || lastOpened == 0) {
            return null;
        } else {
            return new RecentFileDetails(artifact, path, lastOpened);
        }
    }

    /**
     * Return a list of the most recently opened documents based on the
     * TSK_RECENT_OBJECT artifact.
     *
     * @param dataSource The data source to query.
     * @param maxCount The maximum number of results to return, pass 0 to get a
     * list of all results.
     *
     * @return A list RecentFileDetails representing the most recently opened
     * documents or an empty list if none were found.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<RecentFileDetails> getRecentlyOpenedDocuments(DataSource dataSource, int maxCount) throws SleuthkitCaseProviderException, TskCoreException {
        if (dataSource == null) {
            return Collections.emptyList();
        }

        throwOnNonPositiveCount(maxCount);

        List<RecentFileDetails> details = provider.get().getBlackboard()
                .getArtifacts(ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID(), dataSource.getId()).stream()
                .map(art -> getRecentlyOpenedDocument(art))
                .filter(d -> d != null)
                .collect(Collectors.toList());

        return getSortedLimited(details, maxCount);
    }

    /**
     * Returns a RecentDownloadDetails object as derived from the recent
     * download artifact or null if no appropriate object can be made.
     *
     * @param artifact The artifact.
     * @return The derived object or null if artifact is invalid.
     */
    private static RecentDownloadDetails getRecentDownload(BlackboardArtifact artifact) {
        Long accessedTime = DataSourceInfoUtilities.getLongOrNull(artifact, DATETIME_ACCESSED_ATT);
        String domain = DataSourceInfoUtilities.getStringOrNull(artifact, DOMAIN_ATT);
        String path = DataSourceInfoUtilities.getStringOrNull(artifact, PATH_ATT);

        if (StringUtils.isBlank(path) || accessedTime == null || accessedTime == 0) {
            return null;
        } else {
            return new RecentDownloadDetails(artifact, path, accessedTime, domain);
        }
    }

    /**
     * Throws an IllegalArgumentException if count is less than 1.
     *
     * @param count The count.
     */
    private static void throwOnNonPositiveCount(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Invalid count: value must be greater than 0.");
        }
    }

    /**
     * Return a list of the most recent downloads based on the value of the the
     * artifact TSK_DATETIME_ACCESSED attribute.
     *
     * @param dataSource Data source to query.
     * @param maxCount Maximum number of results to return, passing 0 will
     * return all results.
     *
     * @return A list of RecentFileDetails objects or empty list if none were
     * found.
     *
     * @throws TskCoreException
     * @throws SleuthkitCaseProviderException
     */
    public List<RecentDownloadDetails> getRecentDownloads(DataSource dataSource, int maxCount) throws TskCoreException, SleuthkitCaseProviderException {
        if (dataSource == null) {
            return Collections.emptyList();
        }

        throwOnNonPositiveCount(maxCount);

        List<RecentDownloadDetails> details = provider.get().getBlackboard()
                .getArtifacts(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID(), dataSource.getId()).stream()
                .map(art -> getRecentDownload(art))
                .filter(d -> d != null)
                .collect(Collectors.toList());

        return getSortedLimited(details, maxCount);
    }

    /**
     * Returns a list of the most recent message attachments.
     *
     * @param dataSource Data source to query.
     * @param maxCount Maximum number of results to return, passing 0 will
     * return all results.
     *
     * @return A list of RecentFileDetails of the most recent attachments.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<RecentAttachmentDetails> getRecentAttachments(DataSource dataSource, int maxCount) throws SleuthkitCaseProviderException, TskCoreException {
        if (dataSource == null) {
            return Collections.emptyList();
        }

        throwOnNonPositiveCount(maxCount);

        SleuthkitCase skCase = provider.get();

        List<BlackboardArtifact> associatedArtifacts = skCase.getBlackboard()
                .getArtifacts(ASSOCATED_OBJ_ART.getTypeID(), dataSource.getId());

        List<RecentAttachmentDetails> details = new ArrayList<>();
        for (BlackboardArtifact artifact : associatedArtifacts) {
            RecentAttachmentDetails thisDetails = getRecentAttachment(artifact, skCase);

            if (thisDetails != null) {
                details.add(thisDetails);
            }
        }

        return getSortedLimited(details, maxCount);
    }

    /**
     * Creates a RecentAttachmentDetails object from the associated object
     * artifact or null if no RecentAttachmentDetails object can be derived.
     *
     * @param artifact The associated object artifact.
     * @param skCase The current case.
     * @return The derived object or null.
     * @throws TskCoreException
     */
    private static RecentAttachmentDetails getRecentAttachment(BlackboardArtifact artifact, SleuthkitCase skCase) throws TskCoreException {
        // get associated artifact or return no result
        BlackboardAttribute attribute = artifact.getAttribute(ASSOCATED_ATT);
        if (attribute == null) {
            return null;
        }

        // get associated message artifact if exists or return no result
        BlackboardArtifact messageArtifact = skCase.getBlackboardArtifact(attribute.getValueLong());
        if (messageArtifact == null || !isMessageArtifact(messageArtifact)) {
            return null;
        }

        // get abstract file if exists or return no result
        Content content = artifact.getParent();
        if (!(content instanceof AbstractFile)) {
            return null;
        }

        AbstractFile abstractFile = (AbstractFile) content;

        // get the path, sender, and date
        String path = Paths.get(abstractFile.getParentPath(), abstractFile.getName()).toString();
        String sender = DataSourceInfoUtilities.getStringOrNull(messageArtifact, EMAIL_FROM_ATT);
        Long date = DataSourceInfoUtilities.getLongOrNull(messageArtifact, MSG_DATEIME_SENT_ATT);

        if (date == null || date == 0 || StringUtils.isBlank(path)) {
            return null;
        } else {
            return new RecentAttachmentDetails(messageArtifact, path, date, sender);
        }
    }

    /**
     * Is the given artifact a message.
     *
     * @param nodeArtifact An artifact that might be a message. Must not be
     * null.
     *
     * @return True if the given artifact is a message artifact
     */
    private static boolean isMessageArtifact(BlackboardArtifact nodeArtifact) {
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
        private final BlackboardArtifact artifact;

        /**
         * Constructor for files with just a path and date.
         *
         * @param artifact The relevant artifact.
         * @param path File path.
         * @param date File access date\time in seconds with java epoch
         */
        RecentFileDetails(BlackboardArtifact artifact, String path, long date) {
            this.artifact = artifact;
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
        public Long getDateAsLong() {
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

        /**
         * @return The pertinent artifact for this recent file hit.
         */
        public BlackboardArtifact getArtifact() {
            return artifact;
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
         * @param artifact The relevant artifact.
         * @param path File path.
         * @param date File access date\time in seconds with java epoch.
         * @param webDomain The webdomain from which the file was downloaded.
         */
        RecentDownloadDetails(BlackboardArtifact artifact, String path, long date, String webDomain) {
            super(artifact, path, date);
            this.webDomain = webDomain;
        }

        /**
         * Returns the web domain.
         *
         * @return The web domain or empty string if not available or
         * applicable.
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
         * @param artifact The relevant artifact.
         * @param path File path.
         * @param date File crtime.
         * @param sender The sender of the message from which the file was
         * attached.
         */
        RecentAttachmentDetails(BlackboardArtifact artifact, String path, long date, String sender) {
            super(artifact, path, date);
            this.sender = sender;
        }

        /**
         * Return the sender of the attached file.
         *
         * @return The sender of the attached file or empty string if not
         * available.
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
