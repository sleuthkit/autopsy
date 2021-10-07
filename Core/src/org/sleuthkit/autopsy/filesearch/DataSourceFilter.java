/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filesearch;

import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.util.EnumSet;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Filter by mime type used in filter areas of file search by attribute.
 */
class DataSourceFilter extends AbstractFileSearchFilter<DataSourcePanel> {

    /**
     * Construct DataSourceFilter with the DataSourcePanel
     *
     * @param component A DataSourcePanel
     */
    DataSourceFilter(DataSourcePanel component) {
        super(component);
    }

    /**
     * Default constructor to construct a new DataSourceFilter with a new
     * DataSourcePanel
     */
    DataSourceFilter() {
        this(new DataSourcePanel());
    }

    @Override
    public boolean isEnabled() {
        return this.getComponent().isSelected();
    }

    /**
     * Set the data source filter to select the specified data source initially.
     *
     * @param dataSourceId - The data source to select.
     */
    void setSelectedDataSource(long dataSourceId) {
        this.getComponent().setDataSourceSelected(dataSourceId);
    }

    /**
     * Reset the data source filter to be up to date with the current case.
     */
    void resetDataSourceFilter() {
        this.getComponent().resetDataSourcePanel();
    }

    @Override
    public String getPredicate() throws FilterValidationException {
        String predicate = "";
        for (Long dataSourceObjId : this.getComponent().getDataSourcesSelected()) {
            if (!predicate.isEmpty()) {
                predicate += " OR ";
            }
            predicate += "data_source_obj_id = '" + dataSourceObjId + "'";
        }
        return predicate;
    }

    @Override
    public void addActionListener(ActionListener lis) {
        //Unused by now
    }

    @Override
    @Messages({
        "DataSourceFilter.errorMessage.emptyDataSource=At least one data source must be selected."
    })
    public boolean isValid() {
        if (this.getComponent().getDataSourcesSelected().isEmpty()) {
            setLastError(Bundle.DataSourceFilter_errorMessage_emptyDataSource());
            return false;
        }
        return true;
    }
}
