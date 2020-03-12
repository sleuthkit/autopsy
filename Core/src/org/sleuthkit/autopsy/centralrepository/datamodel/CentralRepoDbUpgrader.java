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

import java.sql.Connection;
import java.sql.SQLException;
import org.sleuthkit.datamodel.CaseDbSchemaVersionNumber;

/**
 * Common interface to upgrade central repository database schema.
 */
public interface CentralRepoDbUpgrader {
    
    /**
     * Updates the Central Repository schema using the given open connection.
     * 
     * @param dbSchemaVersion Current schema version.
     * @param connection Connection to use for upgrade.
     * 
     * @throws CentralRepoException If there is an error in upgrade.
     * @throws SQLException If there is any SQL errors.
     */
    void upgradeSchema(CaseDbSchemaVersionNumber dbSchemaVersion, Connection connection) throws CentralRepoException, SQLException;
    
}
