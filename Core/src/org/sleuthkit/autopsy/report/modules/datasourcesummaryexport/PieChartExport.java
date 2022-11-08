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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTPieChart;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.PieChartItem;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelExportException;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelSheetExport;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.ExcelItemExportable;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.ItemDimensions;

/**
 *
 * Class that creates an excel pie chart along with data table.
 */
class PieChartExport implements ExcelItemExportable, ExcelSheetExport {

    private static final int DEFAULT_ROW_SIZE = 20;
    private static final int DEFAULT_COL_SIZE = 10;
    private static final int DEFAULT_ROW_PADDING = 1;
    private static final int DEFAULT_COL_OFFSET = 1;

    private final ExcelTableExport<PieChartItem, ? extends CellModel> tableExport;
    private final int colOffset;
    private final int rowPadding;
    private final int colSize;
    private final int rowSize;
    private final String chartTitle;
    private final String sheetName;

    /**
     * Main constructor assuming defaults.
     *
     * @param keyColumnHeader The header column name for the table descriptions
     * (i.e. file types).
     * @param valueColumnHeader The header column name for the values.
     * @param valueFormatString The excel format string to use for values.
     * @param chartTitle The title for the chart.
     * @param slices The values for the pie slices.
     */
    PieChartExport(String keyColumnHeader,
            String valueColumnHeader, String valueFormatString,
            String chartTitle,
            List<PieChartItem> slices) {
        this(keyColumnHeader, valueColumnHeader, valueFormatString, chartTitle, chartTitle, slices,
                DEFAULT_COL_OFFSET, DEFAULT_ROW_PADDING, DEFAULT_COL_SIZE, DEFAULT_ROW_SIZE);
    }

    /**
     * Main constructor.
     *
     * @param keyColumnHeader The header column name for the table descriptions
     * (i.e. file types).
     * @param valueColumnHeader The header column name for the values.
     * @param valueFormatString The excel format string to use for values.
     * @param chartTitle The title for the chart.
     * @param sheetName The sheet name if used as a sheet export.
     * @param slices The values for the pie slices.
     * @param colOffset The column spacing between the table and the chart.
     * @param rowPadding The padding between this and data above or below (if
     * used as an ExcelItemExportable).
     * @param colSize The column size of the chart.
     * @param rowSize The row size of the chart.
     */
    PieChartExport(String keyColumnHeader,
            String valueColumnHeader, String valueFormatString,
            String chartTitle, String sheetName,
            List<PieChartItem> slices,
            int colOffset, int rowPadding, int colSize, int rowSize) {

        this.tableExport = new ExcelTableExport<>(chartTitle,
                Arrays.asList(
                        new ColumnModel<>(keyColumnHeader, (slice) -> new DefaultCellModel<>(slice.getLabel())),
                        new ColumnModel<>(valueColumnHeader, (slice) -> new DefaultCellModel<>(slice.getValue(), null, valueFormatString))
                ),
                slices);
        this.colOffset = colOffset;
        this.rowPadding = rowPadding;
        this.colSize = colSize;
        this.rowSize = rowSize;
        this.chartTitle = chartTitle;
        this.sheetName = sheetName;
    }

    @Override
    public String getSheetName() {
        return sheetName;
    }

    @Override
    public void renderSheet(Sheet sheet, ExcelExport.WorksheetEnv env) throws ExcelExport.ExcelExportException {
        write(sheet, 0, 0, env);
    }

    @Override
    public ItemDimensions write(Sheet sheet, int rowStart, int colStart, ExcelExport.WorksheetEnv env) throws ExcelExportException {
        if (!(sheet instanceof XSSFSheet)) {
            throw new ExcelExportException("Sheet must be an XSSFSheet in order to write.");
        }

        XSSFSheet xssfSheet = (XSSFSheet) sheet;

        // write pie chart table data
        ItemDimensions tableDimensions = tableExport.write(xssfSheet, rowStart + rowPadding, colStart, env);

        XSSFDrawing drawing = xssfSheet.createDrawingPatriarch();

        int chartColStart = colStart + 2 + colOffset;

        //createAnchor has arguments of (int dx1, int dy1, int dx2, int dy2, int col1, int row1, int col2, int row2);
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, chartColStart, rowStart + rowPadding, chartColStart + colSize + 1, rowStart + rowSize + 1);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.RIGHT);

        // CellRangeAddress has arguments of (int firstRow, int lastRow, int firstCol, int lastCol)
        XDDFDataSource<String> cat = XDDFDataSourcesFactory.fromStringCellRange(xssfSheet,
                new CellRangeAddress(tableDimensions.getRowStart() + 1, tableDimensions.getRowEnd(),
                        tableDimensions.getColStart(), tableDimensions.getColStart()));

        XDDFNumericalDataSource<Double> val = XDDFDataSourcesFactory.fromNumericCellRange(xssfSheet,
                new CellRangeAddress(tableDimensions.getRowStart() + 1, tableDimensions.getRowEnd(),
                        tableDimensions.getColStart() + 1, tableDimensions.getColStart() + 1));

        XDDFPieChartData data = (XDDFPieChartData) chart.createData(ChartTypes.PIE, null, null);
        data.setVaryColors(true);
        data.addSeries(cat, val);

        // Add data labels
        if (!chart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).isSetDLbls()) {
            chart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).addNewDLbls();
        }

        chart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).getDLbls().addNewShowVal().setVal(true);
        chart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).getDLbls().addNewShowSerName().setVal(false);
        chart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).getDLbls().addNewShowCatName().setVal(true);
        chart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).getDLbls().addNewShowPercent().setVal(true);
        chart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).getDLbls().addNewShowLegendKey().setVal(false);

        chart.plot(data);

        return new ItemDimensions(rowStart, colStart, Math.max(tableDimensions.getRowEnd(), rowStart + rowSize) + rowPadding, chartColStart + colSize);
    }

}
