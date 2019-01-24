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
package org.sleuthkit.autopsy.casemodule.datasourceSummary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.CaseDbAccessManager;

/**
 * Get the map of Data Source ID to counts of items found for a query which
 * selects data_source_obj_id and count(*) with a group by data_source_obj_id
 * clause.
 */
class DataSourceCountsCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

    private static final Logger logger = Logger.getLogger(DataSourceCountsCallback.class.getName());
    private Map<Long, Long> dataSourceObjIdCounts = new HashMap<>();

    @Override
    public void process(ResultSet rs) {
        try {
            while (rs.next()) {
                try {
                    dataSourceObjIdCounts.put(rs.getLong("data_source_obj_id"), rs.getLong("count"));
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Unable to get data_source_obj_id or count from result set", ex);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to get next result for counts by datasource", ex);
        }
    }

    /**
     * Get the processed results
     *
     * @return Collection which maps datasource id to a count for the number of
     *         items found with that datasource id, only contains entries for
     *         datasources with at least 1 item found.
     */
    Map<Long, Long> getMapOfCounts() {
        return Collections.unmodifiableMap(dataSourceObjIdCounts);
    }

}
