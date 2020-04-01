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
 * This class updates CR schema to 1.4
 * 
 *  New correlation types for accounts are added, as well as some accounts related new tables are added in this version.
 * 
 */
public class CentralRepoDbUpgrader13To14 implements CentralRepoDbUpgrader {

    @Override
    public void upgradeSchema(CaseDbSchemaVersionNumber dbSchemaVersion, Connection connection) throws CentralRepoException, SQLException {

        if (dbSchemaVersion.compareTo(new CaseDbSchemaVersionNumber(1, 4)) < 0) {

            try (Statement statement = connection.createStatement();) {

                CentralRepoPlatforms selectedPlatform = CentralRepoDbManager.getSavedDbChoice().getDbPlatform();

                // Create account_types and accounts tables which are referred by X_instances tables
                statement.execute(RdbmsCentralRepoFactory.getCreateAccountTypesTableStatement(selectedPlatform));
                statement.execute(RdbmsCentralRepoFactory.getCreateAccountsTableStatement(selectedPlatform));

                for (CorrelationAttributeInstance.Type type : CorrelationAttributeInstance.getDefaultCorrelationTypes()) {
                    String instance_type_dbname = CentralRepoDbUtil.correlationTypeToInstanceTableName(type);

                    if (type.getId() >= CorrelationAttributeInstance.ADDITIONAL_TYPES_BASE_ID) {

                        // these are new Correlation types - new tables need to be created
                        statement.execute(String.format(RdbmsCentralRepoFactory.getCreateAccountInstancesTableTemplate(selectedPlatform), instance_type_dbname, instance_type_dbname));
                        statement.execute(String.format(RdbmsCentralRepoFactory.getAddCaseIdIndexTemplate(), instance_type_dbname, instance_type_dbname));
                        statement.execute(String.format(RdbmsCentralRepoFactory.getAddDataSourceIdIndexTemplate(), instance_type_dbname, instance_type_dbname));
                        statement.execute(String.format(RdbmsCentralRepoFactory.getAddValueIndexTemplate(), instance_type_dbname, instance_type_dbname));
                        statement.execute(String.format(RdbmsCentralRepoFactory.getAddKnownStatusIndexTemplate(), instance_type_dbname, instance_type_dbname));
                        statement.execute(String.format(RdbmsCentralRepoFactory.getAddObjectIdIndexTemplate(), instance_type_dbname, instance_type_dbname));

                        // add new correlation type
                        CentralRepoDbUtil.insertCorrelationType(connection, type);

                    } else if (type.getId() == CorrelationAttributeInstance.EMAIL_TYPE_ID || type.getId() == CorrelationAttributeInstance.PHONE_TYPE_ID) {
                        // Alter the existing _instance tables for Phone and Email attributes to add account_id column 
                        String sqlStr = String.format(getAlterArtifactInstancesAddAccountIdTemplate(selectedPlatform), instance_type_dbname);
                        statement.execute(sqlStr);

                        // SQLite does NOT allow adding a constraint with Alter Table statement.
                        // The alternative would be to create new tables, copy all data over, and delete old tables - potentially a time consuming process. 
                        // We decided to not add this constraint for SQLite, since there likely aren't many users using SQLite based Central Repo.
                        if (selectedPlatform == CentralRepoPlatforms.POSTGRESQL) {
                            sqlStr = String.format(getAlterArtifactInstancesAddAccountIdConstraintTemplate(), instance_type_dbname);
                            statement.execute(sqlStr);
                        }
                    }
                }

                // insert default accounts data
                RdbmsCentralRepoFactory.insertDefaultAccountsTablesContent(connection, selectedPlatform);
            }
        }

    }
    
    /**
     * Returns ALTER TABLE SQL string template to add an account_id column to a
     * TYPE_instances table.
     *
     * @param selectedPlatform
     *
     * @return SQL string template to alter the table.
     */
    static String getAlterArtifactInstancesAddAccountIdTemplate(CentralRepoPlatforms selectedPlatform) {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "ALTER TABLE %s"
                + " ADD account_id " + RdbmsCentralRepoFactory.getBigIntType(selectedPlatform) + " DEFAULT NULL";

    }

    /**
     * Returns ALTER TABLE SQL string template to add a Foreign Key constraint
     * to a TYPE_instances table.
     *
     * @return SQL string template to alter the table.
     */
    static String getAlterArtifactInstancesAddAccountIdConstraintTemplate() {
        // Each "%s" will be replaced with the relevant TYPE_instances table name.
        return "ALTER TABLE %s"
                + " ADD CONSTRAINT account_id_fk foreign key (account_id) references accounts(id)";
    }
    
    
}
