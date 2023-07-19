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
import org.sleuthkit.datamodel.DataSource;

/**
 * Base implementation for a series of results to be displayed in the results viewer.
 */
public class BaseSearchResultsDTO implements SearchResultsDTO {

    private final String typeId;
    private final String displayName;
    private final List<ColumnKey> columns;
    private final List<RowDTO> items;
    private final long totalResultsCount;
    private final long startItem;
    private final String signature;
    private final DataSource parentDataSource;

    public BaseSearchResultsDTO(String typeId, String displayName, List<ColumnKey> columns, List<RowDTO> items, String signature) {
        this(typeId, displayName, columns, items, signature, 0, items == null ? 0 : items.size());
    }

    public BaseSearchResultsDTO(String typeId, String displayName, List<ColumnKey> columns, List<RowDTO> items, String signature, long startItem, long totalResultsCount) {
        this(typeId, displayName, columns, items, signature, startItem, totalResultsCount, null);
    }
    
    public BaseSearchResultsDTO(String typeId, String displayName, List<ColumnKey> columns, List<RowDTO> items, String signature, long startItem, long totalResultsCount, DataSource parentDataSource) {
        this.typeId = typeId;
        this.displayName = displayName;
        this.columns = columns;
        this.items = items;
        this.startItem = startItem;
        this.totalResultsCount = totalResultsCount;
        this.signature = signature;
        this.parentDataSource = parentDataSource;
    }

    @Override
    public String getTypeId() {
        return typeId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public List<ColumnKey> getColumns() {
        return columns;
    }

    @Override
    public List<RowDTO> getItems() {
        return items;
    }

    @Override
    public long getTotalResultsCount() {
        return totalResultsCount;
    }

    @Override
    public long getStartItem() {
        return startItem;
    }
    
    @Override
    public String getSignature() {
        return signature;
    }
    
    @Override
    public DataSource getDataSourceParent() {
        return parentDataSource;
    }
}
