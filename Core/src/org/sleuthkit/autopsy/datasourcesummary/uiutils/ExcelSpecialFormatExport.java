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
    public static class ExcelItemResult {
        private final int rowStart;
        private final int rowEnd;
        private final int colStart;
        private final int colEnd;

        public ExcelItemResult(int rowStart, int rowEnd, int colStart, int colEnd) {
            this.rowStart = rowStart;
            this.rowEnd = rowEnd;
            this.colStart = colStart;
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
        int write(Sheet sheet, int rowStart, ExcelExport.WorksheetEnv env) throws ExcelExportException;
    }
    
    public static class KeyValueItemExportable implements ExcelItemExportable {
        private final ExcelCellModel key;
        private final ExcelCellModel value;
        private final int colStart;

        public KeyValueItemExportable(ExcelCellModel key, ExcelCellModel value) {
            this(key, value, 1);
        }
        
        public KeyValueItemExportable(ExcelCellModel key, ExcelCellModel value, int colStart) {
            this.key = key;
            this.value = value;
            this.colStart = colStart;
        }
        
        @Override
        public int write(Sheet sheet, int rowStart, ExcelExport.WorksheetEnv env) throws ExcelExportException {
            Row row = sheet.getRow(rowStart);
            ExcelExport.createCell(row, colStart, key, Optional.of(env.getHeaderStyle()));
            ExcelExport.createCell(row, colStart + 1, value, Optional.empty());
            return rowStart + 1;
        }
        
    }
    
    private final String sheetName;
    private final List<ExcelItemExportable> exports;

    public ExcelSpecialFormatExport(String sheetName, List<ExcelItemExportable> exports) {
        this.sheetName = sheetName;
        this.exports = exports == null ? Collections.emptyList() : exports;
    }
    
    
    
    @Override
    public String getSheetName() {
        return sheetName;
    }

    @Override
    public void renderSheet(Sheet sheet, ExcelExport.WorksheetEnv env) throws ExcelExportException {
        int rowStart = 1;
        for (ExcelItemExportable export : exports) {
            if (export == null) {
                continue;
            }
            
            int endRow = export.write(sheet, rowStart, env);
            rowStart = endRow + 1;
        }
    }
    
}
