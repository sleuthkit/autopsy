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
package org.sleuthkit.autopsy.mainui.nodes;

import java.beans.PropertyChangeEvent;
import java.util.concurrent.ExecutionException;
import org.sleuthkit.autopsy.mainui.datamodel.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * Provides a generic interface to perform searches and determine if refreshes
 * are needed without needing to know which DAO to use.
 */
public abstract class DAOFetcher<P> {

    private final P parameters;

    /**
     * Main constructor.
     *
     * @param parameters The search parameters.
     */
    public DAOFetcher(P parameters) {
        this.parameters = parameters;
    }

    /**
     * Returns the provided search params.
     *
     * @return The provided search params.
     */
    protected P getParameters() {
        return parameters;
    }

    /**
     * Fetches search results data based on paging settings.
     *
     *
     * @param pageSize    The number of items per page.
     * @param pageIdx     The page index.
     * @param hardRefresh Whether or not to perform a hard refresh.
     *
     * @return The retrieved data.
     *
     * @throws ExecutionException
     */
    public abstract SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException;

    /**
     * Returns true if the ingest module event will require a refresh in the
     * data.
     *
     * @param evt The event.
     *
     * @return True if the
     */
    public abstract boolean isRefreshRequired(DAOEvent evt);
}
