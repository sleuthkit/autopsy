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
import java.sql.Statement;
import org.sleuthkit.datamodel.CaseDbSchemaVersionNumber;

/**
 * This class updates CR schema to 1.5
 * 
 */
public class CentralRepoDbUpgrader14To15 implements CentralRepoDbUpgrader {
    
    @Override
    public void upgradeSchema(CaseDbSchemaVersionNumber dbSchemaVersion, Connection connection) throws CentralRepoException, SQLException {

        if (dbSchemaVersion.compareTo(new CaseDbSchemaVersionNumber(1, 5)) < 0) {
          
            try (Statement statement = connection.createStatement();) {
                CentralRepoPlatforms selectedPlatform = CentralRepoDbManager.getSavedDbChoice().getDbPlatform();

                //  create persona tables and insert default data
                RdbmsCentralRepoFactory.createPersonaTables(statement, selectedPlatform);
                RdbmsCentralRepoFactory.insertDefaultPersonaTablesContent(connection, selectedPlatform);
            }
        }

    }
}
