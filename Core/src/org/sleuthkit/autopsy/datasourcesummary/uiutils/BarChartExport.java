/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import com.google.cloud.Tuple;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFShapeProperties;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.BarGrouping;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
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
public class BarChartExport implements ExcelItemExportable, ExcelSheetExport {
    private final ExcelTableExport<BarChartSeries, ? extends ExcelCellModel> tableExport;
    private final int colOffset;
    private final int rowPadding;
    private final int colSize;
    private final int rowSize;
    private final String chartTitle;
    private final String sheetName;
    private final List<BarChartSeries> categories;
    private final String keyColumnHeader;
    
    public BarChartExport(String keyColumnHeader, 
            String valueFormatString,
            String chartTitle,
            List<BarChartSeries> categories) {
        this(keyColumnHeader, valueFormatString, chartTitle, chartTitle, categories, 1, 1, 8, 10);
    }

    public BarChartExport(String keyColumnHeader, String valueFormatString,
            String chartTitle, String sheetName, 
            List<BarChartSeries> categories, 
            int colOffset, int rowPadding, int colSize, int rowSize) {
        
        this.keyColumnHeader = keyColumnHeader;
        
        List<BarChartSeries> categoryKeys = categories.stream()
                .filter(cat -> cat != null && cat.getKey() != null)
                .sorted((c1, c2) -> c1.getKey().compareTo(c2.getKey()))
                .collect(Collectors.toList());
        
        List<Comparable<?>> rowKeys = categories.stream()
                .filter(cat -> cat != null && cat.getItems() != null)
                .flatMap(cat -> cat.getItems().stream().map(item -> item.getKey()))
                .filter(i -> i != null)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        Map<Tuple<Comparable<?>, Comparable<?>>, Double> valueMap = categories.stream()
                .flatMap((cat) -> cat.getItems().stream().map((item) -> Tuple.of(Tuple.of(cat.getKey(), item.getKey()), item.getValue())))
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue(), (v1, v2) -> v1));
        
        List<Pair<Comparable<?>, List<Double>>> values = rowKeys.stream()
                .map((rowValue))
                        
        this.tableExport = new ExcelTableExport<>(chartTitle,
                Stream.concat(Stream.of(new ColumnModel<>(keyColumnHeader, (category) -> new DefaultCellModel<>(category.getLabel()))), )
                Arrays.asList(
                        ,
                        new ColumnModel<>(valueColumnHeader, (category) -> new DefaultCellModel<>(category.getValue(), null, valueFormatString))
                        ), 
                categories);
        this.colOffset = colOffset;
        this.rowPadding = rowPadding;
        this.colSize = colSize;
        this.rowSize = rowSize;
        this.chartTitle = chartTitle;
        this.sheetName = sheetName;
        this.categories = categories;
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
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);
        

        // Use a category axis for the bottom axis.
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle(keyColumnHeader);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        // leftAxis.setTitle(valHeader);
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        leftAxis.setVisible(false);

        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        data.setBarGrouping(BarGrouping.STACKED);

        XDDFDataSource<String> headerSource = XDDFDataSourcesFactory.fromStringCellRange(sheet, new CellRangeAddress(1, keyVals.size(), 0, 0));
        data.setBarDirection(BarDirection.COL);

        for (int i = 0; i < categories.size(); i++) {
            XDDFChartData.Series series = data.addSeries(headerSource,
                    XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(1, keyVals.size(), i + 1, i + 1)));
            series.setTitle(categories.size() > i && categories.get(i).getIdentifier() != null ? categories.get(i).getIdentifier() : "", null);
            if (categories.get(i).getColor() != null) {
                Color color = categories.get(i).getColor();
                byte[] colorArrARGB = ByteBuffer.allocate(4).putInt(color.getRGB()).array();
                byte[] colorArrRGB = new byte[]{colorArrARGB[1], colorArrARGB[2], colorArrARGB[3]};
                XDDFSolidFillProperties fill = new XDDFSolidFillProperties(XDDFColor.from(colorArrRGB)); // XDDFColor.from(color.getRed(), color.getGreen(), color.getBlue()));
                XDDFShapeProperties properties = series.getShapeProperties();
                if (properties == null) {
                    properties = new XDDFShapeProperties();
                }
                properties.setFillProperties(fill);
                series.setShapeProperties(properties);
            }
        }

        chart.plot(data);
        
        return new ItemDimensions(rowStart, colStart, Math.max(tableDimensions.getRowEnd(), rowStart + rowSize) + rowPadding, chartColStart + colSize);
    }


    
}
