/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 - 2021 Basis Technology Corp.
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

import java.awt.Color;
import java.sql.SQLException;
import java.util.Set;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Helper class for getting summary information on the known files present in the
 * specified DataSource..
 */
public class TypesSummary {

    private final SleuthkitCaseProvider provider;

    /**
     * Main constructor.
     */
    public TypesSummary() {
        this(SleuthkitCaseProvider.DEFAULT);
    }

    /**
     * Main constructor.
     *
     * @param provider The means of obtaining a sleuthkit case.
     */
    public TypesSummary(SleuthkitCaseProvider provider) {
        this.provider = provider;
    }


    /**
     * Get count of regular files (not directories) in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfFiles(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return DataSourceInfoUtilities.getCountOfRegularFiles(
                provider.get(),
                currentDataSource,
                null
        );
    }

    /**
     * Get count of allocated files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfAllocatedFiles(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

        return DataSourceInfoUtilities.getCountOfRegNonSlackFiles(provider.get(), currentDataSource,
                DataSourceInfoUtilities.getMetaFlagsContainsStatement(TskData.TSK_FS_META_FLAG_ENUM.ALLOC));
    }

    /**
     * Get count of unallocated files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfUnallocatedFiles(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

        return DataSourceInfoUtilities.getCountOfRegNonSlackFiles(provider.get(), currentDataSource,
                DataSourceInfoUtilities.getMetaFlagsContainsStatement(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC));
    }

    /**
     * Get count of directories in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfDirectories(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

        return DataSourceInfoUtilities.getCountOfTskFiles(provider.get(), currentDataSource,
                "meta_type=" + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue()
                + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType());
    }

    /**
     * Get count of slack files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfSlackFiles(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {

        return DataSourceInfoUtilities.getCountOfRegularFiles(provider.get(), currentDataSource,
                "type=" + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType());
    }

    /**
     * Information concerning a particular file type category.
     */
    public static class FileTypeCategoryData {

        private final String label;
        private final Set<String> mimeTypes;
        private final Color color;

        /**
         * Main constructor.
         *
         * @param label The label for this slice.
         * @param mimeTypes The mime types associated with this slice.
         * @param color The color associated with this slice.
         */
        public FileTypeCategoryData(String label, Set<String> mimeTypes, Color color) {
            this.label = label;
            this.mimeTypes = mimeTypes;
            this.color = color;
        }

        /**
         * Constructor that accepts FileTypeCategory.
         *
         * @param label The label for this slice.
         * @param fileCategory The category associated with this slice.
         * @param color The color associated with this slice.
         */
        public FileTypeCategoryData(String label, FileTypeUtils.FileTypeCategory fileCategory, Color color) {
            this(label, fileCategory.getMediaTypes(), color);
        }

        /**
         * @return The label for this category.
         */
        public String getLabel() {
            return label;
        }

        /**
         * @return The mime types associated with this category.
         */
        public Set<String> getMimeTypes() {
            return mimeTypes;
        }

        /**
         * @return The color associated with this category.
         */
        public Color getColor() {
            return color;
        }
    }
}
