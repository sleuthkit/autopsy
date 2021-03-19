/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport.ExcelExportException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport.ExcelSheetExport;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelSpecialFormatExport.ExcelItemExportable;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelSpecialFormatExport.ItemDimensions;

/**
 *
 * @author gregd
 */
public class PieChartExport implements ExcelItemExportable, ExcelSheetExport {
    private final ExcelTableExport<PieChartItem, ? extends ExcelCellModel> tableExport;
    private final int colOffset;
    private final int rowPadding;
    private final int colSize;
    private final int rowSize;
    private final String chartTitle;
    private final String sheetName;
    private final List<PieChartItem> slices;
    
    public PieChartExport(String keyColumnHeader, 
            String valueColumnHeader, String valueFormatString,
            String chartTitle,
            List<PieChartItem> slices) {
        this(keyColumnHeader, valueColumnHeader, valueFormatString, chartTitle, chartTitle, slices, 1, 1, 8, 10);
    }

    public PieChartExport(String keyColumnHeader, 
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
        this.slices = slices;
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
        
        //createAnchor(int dx1, int dy1, int dx2, int dy2, int col1, int row1, int col2, int row2);
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, chartColStart, rowStart + rowPadding, chartColStart + colSize, rowStart + rowSize);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);
//        XDDFChartLegend legend = chart.getOrAddLegend();
//        legend.setPosition(LegendPosition.BOTTOM);

        // (int firstRow, int lastRow, int firstCol, int lastCol)
        XDDFDataSource<String> cat = XDDFDataSourcesFactory.fromStringCellRange(xssfSheet,
                new CellRangeAddress(tableDimensions.getRowStart(), tableDimensions.getRowEnd(), 
                        tableDimensions.getColStart(), tableDimensions.getColStart()));
        
        XDDFNumericalDataSource<Double> val = XDDFDataSourcesFactory.fromNumericCellRange(xssfSheet,
                new CellRangeAddress(tableDimensions.getRowStart(), tableDimensions.getRowEnd(), 
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
        //chart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).getDLbls().addNewShowLegendKey().setVal(false);

        chart.plot(data);
        
        return new ItemDimensions(rowStart, colStart, Math.max(tableDimensions.getRowEnd(), rowStart + rowSize) + rowPadding, chartColStart + colSize);
    }


    
}
