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
package org.sleuthkit.autopsy.datamodel;

import java.util.Objects;
import org.sleuthkit.datamodel.DataSource;

/**
 * A top level UI grouping of Files, Views, Results, Tags 
 * for 'Group by Data Source' view of the tree.
 * 
 */
public class DataSourceGrouping  implements AutopsyVisitableItem {
    
    private final DataSource dataSource;

    public DataSourceGrouping(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    DataSource getDataSource() {
        return this.dataSource;
    }
    
    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DataSourceGrouping other = (DataSourceGrouping) obj;
        return this.dataSource.getId() == other.getDataSource().getId();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + Objects.hashCode(this.dataSource);
        return hash;
    }

}
