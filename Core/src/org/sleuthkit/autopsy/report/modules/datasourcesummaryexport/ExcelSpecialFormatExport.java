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
package org.sleuthkit.autopsy.report.modules.datasourcesummaryexport;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelExportException;

/**
 * An excel export that has special row-by-row formatting.
 */
class ExcelSpecialFormatExport implements ExcelExport.ExcelSheetExport {

    /**
     * The dimensions consumed by an item in an ExcelSpecialFormatExport list of
     * items to be rendered.
     */
    static class ItemDimensions {

        private final int rowStart;
        private final int rowEnd;
        private final int colStart;
        private final int colEnd;

        /**
         * Main constructor.
         *
         * @param rowStart The starting excel row of the item.
         * @param colStart The starting excel column of the item.
         * @param rowEnd The last excel row of the the item.
         * @param colEnd The last excel column of the item.
         */
        ItemDimensions(int rowStart, int colStart, int rowEnd, int colEnd) {
            this.rowStart = rowStart;
            this.colStart = colStart;
            this.rowEnd = rowEnd;
            this.colEnd = colEnd;
        }

        /**
         * @return The starting excel row of the item.
         */
        int getRowStart() {
            return rowStart;
        }

        /**
         * @return The last excel row of the the item.
         */
        int getRowEnd() {
            return rowEnd;
        }

        /**
         * @return The starting excel column of the item.
         */
        int getColStart() {
            return colStart;
        }

        /**
         * @return The last excel column of the item.
         */
        int getColEnd() {
            return colEnd;
        }
    }

    /**
     * An item to be exported in a specially formatted excel export.
     */
    interface ExcelItemExportable {

        /**
         * Writes the item to the sheet in the special format export sheet.
         *
         * @param sheet The sheet.
         * @param rowStart The starting row to start writing.
         * @param colStart The starting column to start writing.
         * @param env The excel export context.
         * @return The dimensions of what has been written.
         * @throws ExcelExportException
         */
        ItemDimensions write(Sheet sheet, int rowStart, int colStart, ExcelExport.WorksheetEnv env) throws ExcelExportException;
    }

    /**
     * Writes a string to a single cell in a specially formatted excel export.
     */
    static class SingleCellExportable implements ExcelItemExportable {

        private final CellModel item;

        /**
         * Main constructor.
         *
         * @param key The text to be written.
         */
        SingleCellExportable(String key) {
            this(new DefaultCellModel<>(key));
        }

        /**
         * Main constructor.
         *
         * @param item The cell model to be written.
         */
        SingleCellExportable(CellModel item) {
            this.item = item;
        }

        @Override
        public ItemDimensions write(Sheet sheet, int rowStart, int colStart, ExcelExport.WorksheetEnv env) throws ExcelExportException {
            Row row = sheet.createRow(rowStart);
            ExcelExport.createCell(env, row, colStart, item, Optional.empty());
            return new ItemDimensions(rowStart, colStart, rowStart, colStart);
        }
    }

    /**
     * Writes a row consisting of first column as a key and second column as a
     * value.
     */
    static class KeyValueItemExportable implements ExcelItemExportable {

        private final CellModel key;
        private final CellModel value;

        /**
         * Main constructor.
         *
         * @param key The string key to be exported.
         * @param value The cell model to be exported.
         */
        KeyValueItemExportable(String key, CellModel value) {
            this(new DefaultCellModel<>(key), value);
        }

        /**
         * Main constructor.
         *
         * @param key The cell key to be exported.
         * @param value The cell model to be exported.
         */
        KeyValueItemExportable(CellModel key, CellModel value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public ItemDimensions write(Sheet sheet, int rowStart, int colStart, ExcelExport.WorksheetEnv env) throws ExcelExportException {
            Row row = sheet.createRow(rowStart);
            ExcelExport.createCell(env, row, colStart, key, Optional.of(env.getHeaderStyle()));
            ExcelExport.createCell(env, row, colStart + 1, value, Optional.empty());
            return new ItemDimensions(rowStart, colStart, rowStart, colStart + 1);
        }
    }

    /**
     * A special format excel export item that shows a title and a list of items
     * indented one column.
     *
     * i.e.
     * <pre>
     * title
     *      item 1
     *      item 2
     * </pre>
     */
    static class TitledExportable implements ExcelItemExportable {

        private static final int DEFAULT_INDENT = 1;

        private final String title;
        private final List<? extends ExcelItemExportable> children;

        /**
         * Main constructor.
         *
         * @param title The title for the export.
         * @param children The children to be indented and enumerated.
         */
        TitledExportable(String title, List<? extends ExcelItemExportable> children) {
            this.title = title;
            this.children = children;
        }

        @Override
        public ItemDimensions write(Sheet sheet, int rowStart, int colStart, ExcelExport.WorksheetEnv env) throws ExcelExportException {
            ExcelExport.createCell(env, sheet.createRow(rowStart), colStart, new DefaultCellModel<>(title), Optional.of(env.getHeaderStyle()));
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

    private final String sheetName;
    private final List<ExcelItemExportable> exports;

    /**
     * Main constructor.
     *
     * @param sheetName The name of the sheet.
     * @param exports The row-by-row items to be exported.
     */
    ExcelSpecialFormatExport(String sheetName, List<ExcelItemExportable> exports) {
        this.sheetName = sheetName;
        this.exports = exports == null ? Collections.emptyList() : exports;
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
