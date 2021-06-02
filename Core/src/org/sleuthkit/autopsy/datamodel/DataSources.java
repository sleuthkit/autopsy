/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2021 Basis Technology Corp.
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

/**
 * An "Autopsy visitable item" that supplies a 
 */
public class DataSources implements AutopsyVisitableItem {

    private final long datasourceObjId;
     
    public DataSources() {
        this(0);
    }

    public DataSources(long datasourceObjId) {
        this.datasourceObjId = datasourceObjId;
    }
    
    long filteringDataSourceObjId() {
        return this.datasourceObjId;
    }
    
    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
