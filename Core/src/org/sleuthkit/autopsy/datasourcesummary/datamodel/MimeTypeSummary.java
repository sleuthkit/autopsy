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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Class to export summary information used by TypesPanel tab on the known files
 * present in the specified DataSource.
 */
public class MimeTypeSummary {

    private final SleuthkitCaseProvider provider;

    /**
     * Main constructor.
     */
    public MimeTypeSummary() {
        this(SleuthkitCaseProvider.DEFAULT);
    }

    /**
     * Main constructor.
     *
     * @param provider The means of obtaining a sleuthkit case.
     */
    public MimeTypeSummary(SleuthkitCaseProvider provider) {
        this.provider = provider;
    }

    /**
     * Get the number of files in the case database for the current data source
     * which have the specified mimetypes.
     *
     * @param currentDataSource the data source which we are finding a file
     *                          count
     *
     * @param setOfMimeTypes    the set of mime types which we are finding the
     *                          number of occurences of
     *
     * @return a Long value which represents the number of occurrences of the
     *         specified mime types in the current case for the specified data
     *         source, null if no count was retrieved
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfFilesForMimeTypes(DataSource currentDataSource, Set<String> setOfMimeTypes)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return DataSourceInfoUtilities.getCountOfRegNonSlackFiles(provider.get(), currentDataSource, "mime_type IN " + getSqlSet(setOfMimeTypes));
    }

    /**
     * Get the number of files in the case database for the current data source
     * which do not have the specified mimetypes.
     *
     * @param currentDataSource the data source which we are finding a file
     *                          count
     *
     * @param setOfMimeTypes    the set of mime types that should be excluded.
     *
     * @return a Long value which represents the number of files that do not
     *         have the specific mime type, but do have a mime type.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfFilesNotInMimeTypes(DataSource currentDataSource, Set<String> setOfMimeTypes)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return DataSourceInfoUtilities.getCountOfRegNonSlackFiles(provider.get(), currentDataSource,
                "mime_type NOT IN " + getSqlSet(setOfMimeTypes)
                + " AND mime_type IS NOT NULL AND mime_type <> '' ");
    }

    /**
     * Get a count of all regular files in a datasource.
     *
     * @param dataSource The datasource.
     *
     * @return The count of regular files.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfAllRegularFiles(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return DataSourceInfoUtilities.getCountOfRegNonSlackFiles(provider.get(), dataSource, null);
    }

    /**
     * Gets the number of files in the data source with no assigned mime type.
     *
     * @param currentDataSource The data source.
     *
     * @return The number of files with no mime type or null if there is an
     *         issue searching the data source.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfFilesWithNoMimeType(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return DataSourceInfoUtilities.getCountOfRegNonSlackFiles(provider.get(), currentDataSource, "(mime_type IS NULL OR mime_type = '') ");
    }

    /**
     * Derives a sql set string (i.e. "('val1', 'val2', 'val3')"). A naive
     * attempt is made to sanitize the strings by removing single quotes from
     * values.
     *
     * @param setValues The values that should be present in the set. Single
     *                  quotes are removed.
     *
     * @return The sql set string.
     */
    private String getSqlSet(Set<String> setValues) {
        List<String> quotedValues = setValues
                .stream()
                .map(str -> String.format("'%s'", str.replace("'", "")))
                .collect(Collectors.toList());

        String commaSeparatedQuoted = String.join(", ", quotedValues);
        return String.format("(%s) ", commaSeparatedQuoted);
    }
}
