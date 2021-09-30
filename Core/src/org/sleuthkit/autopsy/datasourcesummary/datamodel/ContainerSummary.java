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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Provides methods to query for data source overview details.
 */
public class ContainerSummary {

    private final SleuthkitCaseProvider provider;

    /**
     * Main constructor.
     */
    public ContainerSummary() {
        this(SleuthkitCaseProvider.DEFAULT);
    }

    /**
     * Main constructor.
     *
     * @param provider The means of obtaining a sleuthkit case.
     */
    public ContainerSummary(SleuthkitCaseProvider provider) {
        this.provider = provider;
    }

    /**
     * Gets the size of unallocated files in a particular datasource.
     *
     * @param currentDataSource The data source.
     *
     * @return The size or null if the query could not be executed.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getSizeOfUnallocatedFiles(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        if (currentDataSource == null) {
            return null;
        }

        final String valueParam = "value";
        final String countParam = "count";
        String query = "SELECT SUM(size) AS " + valueParam + ", COUNT(*) AS " + countParam
                + " FROM tsk_files"
                + " WHERE " + DataSourceInfoUtilities.getMetaFlagsContainsStatement(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC)
                + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType()
                + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                + " AND name<>''"
                + " AND data_source_obj_id=" + currentDataSource.getId();

        DataSourceInfoUtilities.ResultSetHandler<Long> handler = (resultSet) -> {
            if (resultSet.next()) {
                // ensure that there is an unallocated count result that is attached to this data source
                long resultCount = resultSet.getLong(valueParam);
                return (resultCount > 0) ? resultSet.getLong(valueParam) : null;
            } else {
                return null;
            }
        };

        return DataSourceInfoUtilities.getBaseQueryResult(provider.get(), query, handler);
    }

    /**
     * Retrieves the concatenation of operating system attributes for a
     * particular data source.
     *
     * @param dataSource The data source.
     *
     * @return The concatenated value or null if the query could not be
     *         executed.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public String getOperatingSystems(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

        if (dataSource == null) {
            return null;
        }

        return getConcattedAttrValue(dataSource.getId(),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO.getTypeID(),
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID());
    }

    /**
     * Retrieves the concatenation of data source usage for a particular data
     * source.
     *
     * @param dataSource The data source.
     *
     * @return The concatenated value or null if the query could not be
     *         executed.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public String getDataSourceType(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

        if (dataSource == null) {
            return null;
        }

        return getConcattedAttrValue(dataSource.getId(),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE.getTypeID(),
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID());
    }

    /**
     * Generates a string which is a concatenation of the value received from
     * the result set.
     *
     * @param query      The query.
     * @param valueParam The parameter for the value in the result set.
     * @param separator  The string separator used in concatenation.
     *
     * @return The concatenated string or null if the query could not be
     *         executed.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    private String getConcattedStringsResult(String query, String valueParam, String separator)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

        DataSourceInfoUtilities.ResultSetHandler<String> handler = (resultSet) -> {
            String toRet = "";
            boolean first = true;
            while (resultSet.next()) {
                if (first) {
                    first = false;
                } else {
                    toRet += separator;
                }
                toRet += resultSet.getString(valueParam);
            }

            return toRet;
        };

        return DataSourceInfoUtilities.getBaseQueryResult(provider.get(), query, handler);
    }

    /**
     * Generates a concatenated string value based on the text value of a
     * particular attribute in a particular artifact for a specific data source.
     *
     * @param dataSourceId    The data source id.
     * @param artifactTypeId  The artifact type id.
     * @param attributeTypeId The attribute type id.
     *
     * @return The concatenated value or null if the query could not be
     *         executed.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    private String getConcattedAttrValue(long dataSourceId, int artifactTypeId, int attributeTypeId)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

        final String valueParam = "concatted_attribute_value";
        String query = "SELECT attr.value_text AS " + valueParam
                + " FROM blackboard_artifacts bba "
                + " INNER JOIN blackboard_attributes attr ON bba.artifact_id = attr.artifact_id "
                + " WHERE bba.data_source_obj_id = " + dataSourceId
                + " AND bba.artifact_type_id = " + artifactTypeId
                + " AND attr.attribute_type_id = " + attributeTypeId;

        String separator = ", ";
        return getConcattedStringsResult(query, valueParam, separator);
    }

    /**
     * Data model data for data source images.
     */
    public static class ImageDetails {

        private final Long unallocatedSize;
        private final long size;
        private final long sectorSize;

        private final String timeZone;
        private final String imageType;

        private final List<String> paths;
        private final String md5Hash;
        private final String sha1Hash;
        private final String sha256Hash;

        /**
         * Main constructor.
         *
         * @param unallocatedSize Size in bytes of unallocated space or null if
         *                        no unallocated space could be determined.
         * @param size            Total size in bytes.
         * @param sectorSize      Sector size in bytes.
         * @param timeZone        The time zone.
         * @param imageType       The type of image.
         * @param paths           The source paths for the image.
         * @param md5Hash         The md5 hash or null.
         * @param sha1Hash        The sha1 hash or null.
         * @param sha256Hash      The sha256 hash or null.
         */
        ImageDetails(Long unallocatedSize, long size, long sectorSize,
                String timeZone, String imageType, List<String> paths, String md5Hash,
                String sha1Hash, String sha256Hash) {
            this.unallocatedSize = unallocatedSize;
            this.size = size;
            this.sectorSize = sectorSize;
            this.timeZone = timeZone;
            this.imageType = imageType;
            this.paths = paths == null ? Collections.emptyList() : new ArrayList<>(paths);
            this.md5Hash = md5Hash;
            this.sha1Hash = sha1Hash;
            this.sha256Hash = sha256Hash;
        }

        /**
         * @return Size in bytes of unallocated space or null if no unallocated
         *         space could be determined.
         */
        public Long getUnallocatedSize() {
            return unallocatedSize;
        }

        /**
         * @return Total size in bytes.
         */
        public long getSize() {
            return size;
        }

        /**
         * @return Sector size in bytes.
         */
        public long getSectorSize() {
            return sectorSize;
        }

        /**
         * @return The time zone.
         */
        public String getTimeZone() {
            return timeZone;
        }

        /**
         * @return The type of image.
         */
        public String getImageType() {
            return imageType;
        }

        /**
         * @return The source paths for the image.
         */
        public List<String> getPaths() {
            return Collections.unmodifiableList(paths);
        }

        /**
         * @return The md5 hash or null.
         */
        public String getMd5Hash() {
            return md5Hash;
        }

        /**
         * @return The sha1 hash or null.
         */
        public String getSha1Hash() {
            return sha1Hash;
        }

        /**
         * @return The sha256 hash or null.
         */
        public String getSha256Hash() {
            return sha256Hash;
        }
    }

    /**
     * Data model for container data.
     */
    public static class ContainerDetails {

        private final String displayName;
        private final String originalName;
        private final String deviceIdValue;
        private final String acquisitionDetails;
        private final ImageDetails imageDetails;

        /**
         * Main constructor.
         *
         * @param displayName        The display name for this data source.
         * @param originalName       The original name for this data source.
         * @param deviceIdValue      The device id value for this data source.
         * @param acquisitionDetails The acquisition details for this data
         *                           source or null.
         * @param imageDetails       If the data source is an image, the image
         *                           data model for this data source or null if
         *                           non-image.
         */
        ContainerDetails(String displayName, String originalName, String deviceIdValue,
                String acquisitionDetails, ImageDetails imageDetails) {
            this.displayName = displayName;
            this.originalName = originalName;
            this.deviceIdValue = deviceIdValue;
            this.acquisitionDetails = acquisitionDetails;
            this.imageDetails = imageDetails;
        }

        /**
         * @return The display name for this data source.
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * @return The original name for this data source.
         */
        public String getOriginalName() {
            return originalName;
        }

        /**
         * @return The device id value for this data source.
         */
        public String getDeviceId() {
            return deviceIdValue;
        }

        /**
         * @return The acquisition details for this data source or null.
         */
        public String getAcquisitionDetails() {
            return acquisitionDetails;
        }

        /**
         * @return If the data source is an image, the image details for this
         *         data source or null if non-image.
         */
        public ImageDetails getImageDetails() {
            return imageDetails;
        }
    }

    /**
     * Generates a container data model object containing data about the data
     * source.
     *
     * @param ds The data source.
     *
     * @return The generated view model.
     */
    public ContainerDetails getContainerDetails(DataSource ds) throws TskCoreException, SQLException, SleuthkitCaseProvider.SleuthkitCaseProviderException {
        if (ds == null) {
            return null;
        }

        return new ContainerDetails(
                ds.getName(),
                ds.getName(),
                ds.getDeviceId(),
                ds.getAcquisitionDetails(),
                ds instanceof Image ? getImageDetails((Image) ds) : null
        );
    }

    /**
     * Generates an image data model object containing data about the image.
     *
     * @param image The image.
     *
     * @return The generated view model.
     */
    public ImageDetails getImageDetails(Image image) throws TskCoreException, SQLException, SleuthkitCaseProvider.SleuthkitCaseProviderException {
        if (image == null) {
            return null;
        }

        Long unallocSize = getSizeOfUnallocatedFiles(image);
        String imageType = image.getType().getName();
        long size = image.getSize();
        long sectorSize = image.getSsize();
        String timeZone = image.getTimeZone();
        List<String> paths = image.getPaths() == null ? Collections.emptyList() : Arrays.asList(image.getPaths());
        String md5 = image.getMd5();
        String sha1 = image.getSha1();
        String sha256 = image.getSha256();

        return new ImageDetails(unallocSize, size, sectorSize, timeZone, imageType, paths, md5, sha1, sha256);
    }
}
