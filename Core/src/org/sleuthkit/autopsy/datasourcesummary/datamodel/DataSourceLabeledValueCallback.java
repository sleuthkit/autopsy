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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.CaseDbAccessManager;

/**
 * Callback which gets a map of datasource object IDs to maps of String labels
 * to the values associated with them, given a query which should select a
 * 'data_source_obj_id', a 'label', and a 'value'.
 */
class DataSourceLabeledValueCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

    private static final Logger logger = Logger.getLogger(DataSourceSingleValueCallback.class.getName());
    private final Map<Long, Map<String, Long>> dataSourceObjIdLabeledValues = new HashMap<>();

    @Override
    public void process(ResultSet rs) {
        try {
            while (rs.next()) {
                try {
                    Long dataSourceObjId = rs.getLong("data_source_obj_id");
                    String labelForValue = rs.getString("label");
                    Long value = rs.getLong("value");
                    Map<String, Long> labelsMap = dataSourceObjIdLabeledValues.get(dataSourceObjId) == null ? new HashMap<>() : dataSourceObjIdLabeledValues.get(dataSourceObjId);
                    labelsMap.put(labelForValue, value);
                    dataSourceObjIdLabeledValues.put(dataSourceObjId, labelsMap);
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
     * @return Collection which maps datasource object IDs to maps of String
     *         labels to the values associated with them only contains entries
     *         for data sources with at least 1 item found for any label.
     */
    Map<Long, Map<String, Long>> getMapOfLabeledValues() {
        return Collections.unmodifiableMap(dataSourceObjIdLabeledValues);
    }

}
