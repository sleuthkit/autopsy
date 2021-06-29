/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.sleuthkit.datamodel.CaseDbSchemaVersionNumber;

/**
 * This class updates CR schema to 1.6
 * 
 */
public class CentralRepoDbUpgrader15To16 implements CentralRepoDbUpgrader {
    
    @Override
    public void upgradeSchema(CaseDbSchemaVersionNumber dbSchemaVersion, Connection connection) throws CentralRepoException, SQLException {

        if (dbSchemaVersion.compareTo(new CaseDbSchemaVersionNumber(1, 6)) < 0) {
          
            try (Statement statement = connection.createStatement();) {

                CentralRepoPlatforms selectedPlatform = CentralRepoDbManager.getSavedDbChoice().getDbPlatform();

                for (CorrelationAttributeInstance.Type type : CorrelationAttributeInstance.getDefaultCorrelationTypes()) {
                    String instance_type_dbname = CentralRepoDbUtil.correlationTypeToInstanceTableName(type);

                    if ((type.getId() == CorrelationAttributeInstance.INSTALLED_PROGS_TYPE_ID) ||
                        (type.getId() == CorrelationAttributeInstance.OSACCOUNT_TYPE_ID)){

                        // these are new Correlation types - new tables need to be created
                        statement.execute(String.format(RdbmsCentralRepoFactory.getCreateAccountInstancesTableTemplate(selectedPlatform), instance_type_dbname, instance_type_dbname));
                        statement.execute(String.format(RdbmsCentralRepoFactory.getAddCaseIdIndexTemplate(), instance_type_dbname, instance_type_dbname));
                        statement.execute(String.format(RdbmsCentralRepoFactory.getAddDataSourceIdIndexTemplate(), instance_type_dbname, instance_type_dbname));
                        statement.execute(String.format(RdbmsCentralRepoFactory.getAddValueIndexTemplate(), instance_type_dbname, instance_type_dbname));
                        statement.execute(String.format(RdbmsCentralRepoFactory.getAddKnownStatusIndexTemplate(), instance_type_dbname, instance_type_dbname));
                        statement.execute(String.format(RdbmsCentralRepoFactory.getAddObjectIdIndexTemplate(), instance_type_dbname, instance_type_dbname));

                        // add new correlation type
                        CentralRepoDbUtil.insertCorrelationType(connection, type);

                    }
                }
            }
        }
    }
}
