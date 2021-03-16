/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport.ExcelExportException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelTableExport.ExcelCellModel;

/**
 *
 * @author gregd
 */
public class ExcelSpecialFormatExport implements ExcelExport.ExcelSheetExport {

    public static class ItemDimensions {

        private final int rowStart;
        private final int rowEnd;
        private final int colStart;
        private final int colEnd;

        public ItemDimensions(int rowStart, int colStart, int rowEnd, int colEnd) {
            this.rowStart = rowStart;
            this.colStart = colStart;
            this.rowEnd = rowEnd;
            this.colEnd = colEnd;
        }

        public int getRowStart() {
            return rowStart;
        }

        public int getRowEnd() {
            return rowEnd;
        }

        public int getColStart() {
            return colStart;
        }

        public int getColEnd() {
            return colEnd;
        }
    }

    public interface ExcelItemExportable {

        ItemDimensions write(Sheet sheet, int rowStart, int colStart, ExcelExport.WorksheetEnv env) throws ExcelExportException;
    }

    public static class SingleCellExportable implements ExcelItemExportable {

        private final ExcelCellModel item;

        public SingleCellExportable(String key) {
            this(new DefaultCellModel<>(key));
        }

        public SingleCellExportable(ExcelCellModel item) {
            this.item = item;
        }

        @Override
        public ItemDimensions write(Sheet sheet, int rowStart, int colStart, ExcelExport.WorksheetEnv env) throws ExcelExportException {
            Row row = sheet.createRow(rowStart);
            ExcelExport.createCell(row, colStart, item,
                    item.getExcelFormatString() == null
                    ? Optional.empty()
                    : Optional.of(ExcelExport.createCellStyle(env.getParentWorkbook(), item.getExcelFormatString())));
            return new ItemDimensions(rowStart, colStart, rowStart, colStart);
        }
    }

    public static class KeyValueItemExportable implements ExcelItemExportable {

        private final ExcelCellModel key;
        private final ExcelCellModel value;

        public KeyValueItemExportable(String key, ExcelCellModel value) {
            this(new DefaultCellModel<>(key), value);
        }

        public KeyValueItemExportable(ExcelCellModel key, ExcelCellModel value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public ItemDimensions write(Sheet sheet, int rowStart, int colStart, ExcelExport.WorksheetEnv env) throws ExcelExportException {
            Row row = sheet.createRow(rowStart);
            ExcelExport.createCell(row, colStart, key, Optional.of(env.getHeaderStyle()));
            ExcelExport.createCell(row, colStart + 1, value,
                    value.getExcelFormatString() == null
                    ? Optional.empty()
                    : Optional.of(ExcelExport.createCellStyle(env.getParentWorkbook(), value.getExcelFormatString())));
            return new ItemDimensions(rowStart, colStart, rowStart, colStart + 1);
        }
    }

    private final String sheetName;
    private final List<ExcelItemExportable> exports;

    public ExcelSpecialFormatExport(String sheetName, List<ExcelItemExportable> exports) {
        this.sheetName = sheetName;
        this.exports = exports == null ? Collections.emptyList() : exports;
    }

    public static class TitledExportable implements ExcelItemExportable {

        private static final int DEFAULT_INDENT = 1;

        private final String title;
        private final List<? extends ExcelItemExportable> children;

        public TitledExportable(String title, List<? extends ExcelItemExportable> children) {
            this.title = title;
            this.children = children;
        }

        @Override
        public ItemDimensions write(Sheet sheet, int rowStart, int colStart, ExcelExport.WorksheetEnv env) throws ExcelExportException {
            ExcelExport.createCell(sheet.createRow(rowStart), colStart, new DefaultCellModel<>(title), Optional.of(env.getHeaderStyle()));
            int curRow = rowStart + 1;
            int maxCol = colStart;
            for (ExcelItemExportable export : children) {
                if (export == null) {
                    continue;
                }

                ItemDimensions thisItemDim = export.write(sheet, curRow, colStart + DEFAULT_INDENT, env);
                curRow = thisItemDim.getRowEnd() + 1;
                maxCol = Math.max(thisItemDim.getColEnd(), maxCol);
            }

            return new ItemDimensions(rowStart, colStart, curRow - 1, maxCol);
        }

    }

    @Override
    public String getSheetName() {
        return sheetName;
    }

    @Override
    public void renderSheet(Sheet sheet, ExcelExport.WorksheetEnv env) throws ExcelExportException {
        int rowStart = 0;
        int maxCol = 0;
        for (ExcelItemExportable export : exports) {
            if (export == null) {
                continue;
            }

            ItemDimensions dimensions = export.write(sheet, rowStart, 0, env);
            rowStart = dimensions.getRowEnd() + 1;
            maxCol = Math.max(maxCol, dimensions.getColEnd());
        }

        // Resize all columns to fit the content size
        for (int i = 0; i <= maxCol; i++) {
            sheet.autoSizeColumn(i);
        }
    }

}
