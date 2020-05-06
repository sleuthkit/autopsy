/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * An interface to process the resultset from a Central Repository DB query.
 * This enables clients of Central Repository to run custom queries and process
 * the results themselves.
 *
 */
interface CentralRepositoryDbQueryCallback {

    /**
     * Process the resultset from a query.
     * @param rs ResultSet.
     * 
     * @throws CentralRepoException In case of an error processing the result set.
     * @throws SQLException In case of a SQL error in processing the result set.
     */
    void process(ResultSet rs) throws CentralRepoException, SQLException;
}
