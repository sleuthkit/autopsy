/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.discovery;

import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Base class for the filters.
 */
abstract class AbstractFilter {

    /**
     * Returns part of a query on the tsk_files table that can be AND-ed with
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
     * Run a secondary filter that does not operate on tsk_files.
     *
     * @param currentResults The current list of matching files; empty if no
     *                       filters have yet been run.
     * @param caseDb         The case database
     * @param centralRepoDb  The central repo database. Can be null if the
     *                       filter does not require it.
     *
     * @return The list of files that match this filter (and any that came
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
