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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.sleuthkit.datamodel.DataSource;

/**
 * Provides methods to query for datasource files by mime type.
 */
public class DataSourceMimeTypeSummary {

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
     */
    public static Long getCountOfFilesForMimeTypes(DataSource currentDataSource, Set<String> setOfMimeTypes) {
        return DataSourceInfoUtilities.getCountOfRegularFiles(currentDataSource,
                "mime_type IN " + getSqlSet(setOfMimeTypes),
                "Unable to get count of files for specified mime types");
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
     */
    public static Long getCountOfFilesNotInMimeTypes(DataSource currentDataSource, Set<String> setOfMimeTypes) {
        return DataSourceInfoUtilities.getCountOfRegularFiles(currentDataSource,
                "mime_type NOT IN " + getSqlSet(setOfMimeTypes)
                + " AND mime_type IS NOT NULL AND mime_type <> '' ",
                "Unable to get count of files without specified mime types");
    }

    /**
     * Gets the number of files in the data source with no assigned mime type.
     *
     * @param currentDataSource The data source.
     *
     * @return The number of files with no mime type or null if there is an
     *         issue searching the data source.
     *
     */
    public static Long getCountOfFilesWithNoMimeType(DataSource currentDataSource) {
        return DataSourceInfoUtilities.getCountOfRegularFiles(currentDataSource,
                "(mime_type IS NULL OR mime_type = '') ",
                "Unable to get count of files without a mime type");
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
    private static String getSqlSet(Set<String> setValues) {
        List<String> quotedValues = setValues
                .stream()
                .map(str -> String.format("'%s'", str.replace("'", "")))
                .collect(Collectors.toList());

        String commaSeparatedQuoted = String.join(", ", quotedValues);
        return String.format("(%s) ", commaSeparatedQuoted);
    }

    private DataSourceMimeTypeSummary() {
    }
}
