/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import org.openide.util.NbBundle.Messages;

public class ThreePanelDAO {
    private static ThreePanelDAO instance = null;

    public synchronized static ThreePanelDAO getInstance() {
        if (instance == null) {
            instance = new ThreePanelDAO();
        }

        return instance;
    }

    private final ThreePanelDataArtifactDAO dataArtifactDAO = ThreePanelDataArtifactDAO.getInstance();
    private final ThreePanelViewsDAO viewsDAO = ThreePanelViewsDAO.getInstance();
    
    public ThreePanelDataArtifactDAO getDataArtifactsDAO() {
        return dataArtifactDAO;
    }
    
    public ThreePanelViewsDAO getViewsDAO() {
        return viewsDAO;
    }
    
    
    public static class ColumnKey {

        private final String fieldName;
        private final String displayName;
        private final String description;

        public ColumnKey(String fieldName, String displayName, String description) {
            this.fieldName = fieldName;
            this.displayName = displayName;
            this.description = description;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    public interface RowResultDTO {

        List<Object> getCellValues();

        long getId();
    }

    public static class BaseRowResultDTO implements RowResultDTO {

        private final List<Object> cellValues;
        private final long id;

        public BaseRowResultDTO(List<Object> cellValues, long id) {
            this.cellValues = cellValues;
            this.id = id;
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

    @Messages({
        "CountsRowResultDTO_columns_displayName_name=displayName",
        "CountsRowResultDTO_columns_displayName_displayName=Name",
        "CountsRowResultDTO_columns_displayName_description=Name",
        "CountsRowResultDTO_columns_count_name=displayName",
        "CountsRowResultDTO_columns_count_displayName=Name",
        "CountsRowResultDTO_columns_count_description=Name",})
    public static class CountsRowResultDTO implements RowResultDTO {

        public static ColumnKey DISPLAY_NAME_COL = new ColumnKey(
                Bundle.CountsRowResultDTO_columns_displayName_name(),
                Bundle.CountsRowResultDTO_columns_displayName_displayName(),
                Bundle.CountsRowResultDTO_columns_displayName_description()
        );

        public static ColumnKey COUNT_COL = new ColumnKey(
                Bundle.CountsRowResultDTO_columns_count_name(),
                Bundle.CountsRowResultDTO_columns_count_displayName(),
                Bundle.CountsRowResultDTO_columns_count_description()
        );

        private final long id;
        private final String displayName;
        private final long count;
        private final List<Object> cellValues;

        public CountsRowResultDTO(long id, String displayName, long count) {
            this.id = id;
            this.displayName = displayName;
            this.count = count;
            this.cellValues = ImmutableList.of(Arrays.asList(displayName, count));
        }

        @Override
        public long getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public long getCount() {
            return count;
        }

        @Override
        public List<Object> getCellValues() {
            return cellValues;
        }
    }

    public static interface SearchResultsDTO<R extends RowResultDTO> {

        String getTypeId();

        String getDisplayName();

        List<ColumnKey> getColumns();

        List<R> getItems();

        long getTotalResultsCount();
    }

    public static class BaseSearchResultsDTO<R extends RowResultDTO> implements SearchResultsDTO<R> {

        private final String typeId;
        private final String displayName;
        private final List<ColumnKey> columns;
        private final List<R> items;
        private final long totalResultsCount;

        public BaseSearchResultsDTO(String typeId, String displayName, List<ColumnKey> columns, List<R> items) {
            this(typeId, displayName, columns, items, items == null ? 0 : items.size());
        }

        public BaseSearchResultsDTO(String typeId, String displayName, List<ColumnKey> columns, List<R> items, long totalResultsCount) {
            this.typeId = typeId;
            this.displayName = displayName;
            this.columns = columns;
            this.items = items;
            this.totalResultsCount = totalResultsCount;
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
        public List<R> getItems() {
            return items;
        }

        @Override
        public long getTotalResultsCount() {
            return totalResultsCount;
        }

    }
}
