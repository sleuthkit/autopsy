/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 * @author gregd
 */
public class ThreePanelDAO {
    
    private final Cache<TableCacheKey, DataArtifactTableDTO> tableCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private DataArtifactTableDTO fetchDataArtifactsForTable(TableCacheKey cacheKey) {
        // GVDTODO
    }
    
    public DataArtifactTableDTO getDataArtifactsForTable(BlackboardArtifact.Type artType, Long dataSourceId) throws ExecutionException {
        TableCacheKey cacheKey = new TableCacheKey(artType, dataSourceId);
        return tableCache.get(cacheKey, () -> fetchDataArtifactsForTable(cacheKey));
    }
    

    
    public void dropTableCache() {
        tableCache.invalidateAll();
    }
    
    public void dropTableCache(BlackboardArtifact.Type artType) {
        tableCache.invalidate(artType);
    }
    
    private static class TableCacheKey {
        private final BlackboardArtifact.Type artifactType;
        private final Long dataSourceId;

        public TableCacheKey(BlackboardArtifact.Type artifactType, Long dataSourceId) {
            this.artifactType = artifactType;
            this.dataSourceId = dataSourceId;
        }

        public BlackboardArtifact.Type getArtifactType() {
            return artifactType;
        }

        public Long getDataSourceId() {
            return dataSourceId;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 47 * hash + Objects.hashCode(this.artifactType);
            hash = 47 * hash + Objects.hashCode(this.dataSourceId);
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
            final TableCacheKey other = (TableCacheKey) obj;
            if (!Objects.equals(this.artifactType, other.artifactType)) {
                return false;
            }
            if (!Objects.equals(this.dataSourceId, other.dataSourceId)) {
                return false;
            }
            return true;
        }

        
        
        
    }
    
    public static class ColumnKey {
        private final String key;
        private final String displayName;
        private final String description;

        public ColumnKey(String key, String displayName, String description) {
            this.key = key;
            this.displayName = displayName;
            this.description = description;
        }

        public String getKey() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }
    
    public static class DataArtifactIntrinsicData {
        // GVDTODO
    }
    
    public static class DataArtifactRow {
        private final List<Object> rowData;
        private final DataArtifactIntrinsicData intrinsicData;

        public DataArtifactRow(List<Object> rowData, DataArtifactIntrinsicData intrinsicData) {
            this.rowData = rowData;
            this.intrinsicData = intrinsicData;
        }

        public List<Object> getRowData() {
            return rowData;
        }

        public DataArtifactIntrinsicData getIntrinsicData() {
            return intrinsicData;
        }        
    }
    
    
    public static class DataArtifactTableDTO {
        private final List<ColumnKey> columnHeaders;
        private final List<DataArtifactRow> rows;

        public DataArtifactTableDTO(List<ColumnKey> columnHeaders, List<DataArtifactRow> rows) {
            this.columnHeaders = columnHeaders;
            this.rows = rows;
        }

        public List<ColumnKey> getColumnHeaders() {
            return columnHeaders;
        }

        public List<DataArtifactRow> getRows() {
            return rows;
        }
    }
}
