/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.List;

/**
 * Base implementation for a row result.
 */
public class BaseRowResultDTO implements RowResultDTO {
    
    private final List<Object> cellValues;
    private final long id;
    private final String typeId;

    public BaseRowResultDTO(List<Object> cellValues, String typeId, long id) {
        this.cellValues = cellValues;
        this.id = id;
        this.typeId = typeId;
    }

    @Override
    public List<Object> getCellValues() {
        return cellValues;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getTypeId() {
        return typeId;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 23 * hash + (int) (this.id ^ (this.id >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BaseRowResultDTO other = (BaseRowResultDTO) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }
    
}
