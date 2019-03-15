/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.datasourcesummary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.CaseDbAccessManager;

/**
 * Get the map of Data Source ID to a value found related to that data source
 * query which selects data_source_obj_id and value which is grouped by that
 * data source object id.
 */
class DataSourceSingleValueCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

    private static final Logger logger = Logger.getLogger(DataSourceSingleValueCallback.class.getName());
    private final Map<Long, Long> dataSourceObjIdValues = new HashMap<>();

    @Override
    public void process(ResultSet rs) {
        try {
            while (rs.next()) {
                try {
                    dataSourceObjIdValues.put(rs.getLong("data_source_obj_id"), rs.getLong("value"));
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Unable to get data_source_obj_id or value from result set", ex);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to get next result for valuess by datasource", ex);
        }
    }

    /**
     * Get the processed results
     *
     * @return Collection which maps datasource id to a value related to the
     *         datasource id, only contains entries for datasources with at
     *         least 1 item found.
     */
    Map<Long, Long> getMapOfValues() {
        return Collections.unmodifiableMap(dataSourceObjIdValues);
    }

}
