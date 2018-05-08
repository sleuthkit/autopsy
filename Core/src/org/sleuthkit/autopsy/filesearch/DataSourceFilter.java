/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
import org.openide.util.NbBundle.Messages;

/**
 * Filter by mime type used in filter areas of file search by attribute.
 */
class DataSourceFilter extends AbstractFileSearchFilter<DataSourcePanel> {

    public DataSourceFilter(DataSourcePanel component) {
        super(component);
    }

    public DataSourceFilter() {
        this(new DataSourcePanel());
    }

    @Override
    public boolean isEnabled() {
        return this.getComponent().isSelected();
    }

    @Override
    public String getPredicate() throws FilterValidationException {
        String predicate = "";
        for (Long dataSourceObjId : this.getComponent().getDataSourcesSelected()) {
            predicate += "data_source_obj_id = '" + dataSourceObjId + "' OR ";
        }
        if (predicate.length() > 3) {
            predicate = predicate.substring(0, predicate.length() - 3);
        }
        return predicate;
    }

    @Override
    public void addActionListener(ActionListener l) {
    }

    @Override
    @Messages ({
        "DataSourceFilter.errorMessage.emptyDataSource=At least one data source must be selected."
    })
    public boolean isValid() {
        if(this.getComponent().getDataSourcesSelected().isEmpty()){
            setLastError(Bundle.DataSourceFilter_errorMessage_emptyDataSource());
            return false;
        }
        return true;
    }
}
