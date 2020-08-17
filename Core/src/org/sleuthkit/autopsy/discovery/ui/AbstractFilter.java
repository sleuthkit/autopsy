/*
 * Autopsy
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
package org.sleuthkit.autopsy.discovery.ui;

import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.discovery.FileSearchException;
import org.sleuthkit.autopsy.discovery.ResultFile;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Base class for the filters.
 */
abstract class AbstractFilter {

    /**
     * Returns part of a query on the table that can be AND-ed with
     * other pieces
     *
     * @return the SQL query or an empty string if there is no SQL query for
     *         this filter.
     */
    abstract String getWhereClause();

    /**
     * Indicates whether this filter needs to use the secondary, non-SQL method
     * applyAlternateFilter().
     *
     * @return false by default
     */
    boolean useAlternateFilter() {
        return false;
    }

    /**
     * Run a secondary filter that does not operate on table.
     *
     * @param currentResults The current list of matching results; empty if no
     *                       filters have yet been run.
     * @param caseDb         The case database
     * @param centralRepoDb  The central repo database. Can be null if the
     *                       filter does not require it.
     *
     * @return The list of results that match this filter (and any that came
     *         before it)
     *
     * @throws FileSearchException
     */
    List<ResultFile> applyAlternateFilter(List<ResultFile> currentResults, SleuthkitCase caseDb,
            CentralRepository centralRepoDb) throws FileSearchException {
        return new ArrayList<>();
    }

    /**
     * Get a description of the selected filter.
     *
     * @return A description of the filter
     */
    abstract String getDesc();
}
