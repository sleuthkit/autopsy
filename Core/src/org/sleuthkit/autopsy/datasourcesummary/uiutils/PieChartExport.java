/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xssf.usermodel.Chart;
import org.apache.poi.xssf.usermodel.ClientAnchor;
import org.apache.poi.xssf.usermodel.Drawing;
import org.apache.poi.xssf.usermodel.Row;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport.ExcelSheetExport;

/**
 *
 * @author gregd
 */
public class PieChartExport implements ExcelSheetExport {
    private final String sheetName;
    
    
    @Override
    public String getSheetName() {
        return sheetName;
    }

    @Override
    public void renderSheet(Sheet sheet, ExcelExport.WorksheetEnv env) throws ExcelExport.ExcelExportException {
        
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue(keyHeader);
        headerRow.createCell(1).setCellValue(valHeader);

        for (int v = 0; v < slices.size(); v++) {
            Double val = slices.get(v).getAmount();
            Row row = sheet.createRow(v + 1);
            row.createCell(0).setCellValue(slices.get(v).getLabel());
            row.createCell(1).setCellValue(val);
        }

        Drawing drawing = sheet.createDrawingPatriarch();
        ClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 3, 1, 3 + colSize, 1 + rowSize);

        Chart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);
//        XDDFChartLegend legend = chart.getOrAddLegend();
//        legend.setPosition(LegendPosition.BOTTOM);

        XDDFDataSource<String> cat = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(1, slices.size(), 0, 0));
        XDDFNumericalDataSource<Double> val = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, slices.size(), 1, 1));

        XDDFPieChartData data = (XDDFPieChartData) chart.createData(ChartTypes.PIE, null, null);

        data.setVaryColors(true);
        XDDFChartData.Series series = data.addSeries(cat, val);

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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
