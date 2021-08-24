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

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartSeries;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelExportException;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelSheetExport;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.ExcelItemExportable;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.ItemDimensions;

/**
 * Class that creates an excel stacked bar chart along with data table.
 */
class BarChartExport implements ExcelItemExportable, ExcelSheetExport {

    /**
     * Creates an excel table model to be written to an excel sheet and used as
     * a datasource for the chart.
     *
     * @param categories The categories with their data.
     * @param keyColumnHeader The header column name for the table descriptions
     * (i.e. types: file types / artifact types).
     * @param chartTitle The title for the chart.
     * @return An excel table export to be used as the data source for the chart
     * in the excel document.
     */
    private static ExcelTableExport<Pair<Object, List<Double>>, ? extends CellModel> getTableModel(
            List<BarChartSeries> categories, String keyColumnHeader, String chartTitle) {

        // get the row keys by finding the series with the largest set of bar items 
        // (they should all be equal, but just in case)
        List<? extends Object> rowKeys = categories.stream()
                .filter(cat -> cat != null && cat.getItems() != null)
                .map(cat -> cat.getItems())
                .max((items1, items2) -> Integer.compare(items1.size(), items2.size()))
                .orElse(Collections.emptyList())
                .stream()
                .map((barChartItem) -> barChartItem.getKey())
                .collect(Collectors.toList());

        // map of (bar chart category index, bar chart item index) -> value
        Map<Pair<Integer, Integer>, Double> valueMap = IntStream.range(0, categories.size())
                .mapToObj(idx -> Pair.of(idx, categories.get(idx)))
                .filter(pair -> pair.getValue() != null && pair.getValue().getItems() != null)
                .flatMap(categoryPair -> {
                    return IntStream.range(0, categoryPair.getValue().getItems().size())
                            .mapToObj(idx -> Pair.of(idx, categoryPair.getValue().getItems().get(idx)))
                            .map(itemPair -> Pair.of(
                            Pair.of(categoryPair.getKey(), itemPair.getKey()),
                            itemPair.getValue() == null ? null : itemPair.getValue().getValue()));
                })
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue(), (v1, v2) -> v1));

        // Create rows of data to be displayed where each row is a tuple of the bar chart item 
        // key and the list of values in category order.
        List<Pair<Object, List<Double>>> values = IntStream.range(0, rowKeys.size())
                .mapToObj(idx -> Pair.of(idx, rowKeys.get(idx)))
                .map((rowPair) -> {
                    List<Double> items = IntStream.range(0, categories.size())
                            .mapToObj(idx -> valueMap.get(Pair.of(idx, rowPair.getKey())))
                            .collect(Collectors.toList());

                    return Pair.of(rowPair.getValue(), items);
                })
                .collect(Collectors.toList());

        // Create the model for the category column
        ColumnModel<Pair<Object, List<Double>>, DefaultCellModel<?>> categoryColumn
                = new ColumnModel<>(keyColumnHeader, (row) -> new DefaultCellModel<>(row.getKey()));

        // create the models for each category of data to be displayed
        Stream<ColumnModel<Pair<Object, List<Double>>, DefaultCellModel<?>>> dataColumns = IntStream.range(0, categories.size())
                .mapToObj(idx -> new ColumnModel<>(
                categories.get(idx).getKey().toString(),
                (row) -> new DefaultCellModel<>(row.getValue().get(idx))));

        // create table
        return new ExcelTableExport<Pair<Object, List<Double>>, DefaultCellModel<?>>(
                chartTitle,
                Stream.concat(Stream.of(categoryColumn), dataColumns)
                        .collect(Collectors.toList()),
                values
        );
    }

    private static final int DEFAULT_ROW_SIZE = 15;
    private static final int DEFAULT_COL_SIZE = 10;
    private static final int DEFAULT_ROW_PADDING = 1;
    private static final int DEFAULT_COL_OFFSET = 1;

    private final ExcelTableExport<Pair<Object, List<Double>>, ? extends CellModel> tableExport;
    private final int colOffset;
    private final int rowPadding;
    private final int colSize;
    private final int rowSize;
    private final String chartTitle;
    private final String sheetName;
    private final List<BarChartSeries> categories;
    private final String keyColumnHeader;

    /**
     * Main constructor that assumes some defaults (i.e. chart size follows
     * defaults and sheet name is chart title).
     *
     * @param keyColumnHeader The header column name for the table descriptions
     * (i.e. types: file types / artifact types).
     * @param valueFormatString The excel format string to use for values.
     * @param chartTitle The title for the chart.
     * @param categories The categories along with data.
     */
    BarChartExport(String keyColumnHeader,
            String valueFormatString,
            String chartTitle,
            List<BarChartSeries> categories) {
        this(keyColumnHeader, valueFormatString, chartTitle, chartTitle, categories,
                DEFAULT_COL_OFFSET, DEFAULT_ROW_PADDING, DEFAULT_COL_SIZE, DEFAULT_ROW_SIZE);
    }

    /**
     * Main constructor.
     *
     * @param keyColumnHeader The header column name for the table descriptions
     * (i.e. types: file types / artifact types).
     * @param valueFormatString The excel format string to use for values.
     * @param chartTitle The title for the chart.
     * @param sheetName The sheet name if used as a sheet export.
     * @param categories The categories along with data.
     * @param colOffset The column spacing between the table and the chart.
     * @param rowPadding The padding between this and data above or below (if
     * used as an ExcelItemExportable).
     * @param colSize The column size of the chart.
     * @param rowSize The row size of the chart.
     */
    BarChartExport(String keyColumnHeader, String valueFormatString,
            String chartTitle, String sheetName,
            List<BarChartSeries> categories,
            int colOffset, int rowPadding, int colSize, int rowSize) {

        this.keyColumnHeader = keyColumnHeader;
        this.tableExport = getTableModel(categories, keyColumnHeader, chartTitle);
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

        int chartColStart = colStart + categories.size() + 1 + colOffset;

        //createAnchor has arguments of (int dx1, int dy1, int dx2, int dy2, int col1, int row1, int col2, int row2);
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, chartColStart, rowStart + rowPadding, chartColStart + colSize + 1, rowStart + rowSize + 1);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        // Use a category axis for the bottom axis.
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle(keyColumnHeader);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        leftAxis.setVisible(false);

        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        data.setBarGrouping(BarGrouping.STACKED);

        XDDFDataSource<String> headerSource = XDDFDataSourcesFactory.fromStringCellRange(xssfSheet,
                new CellRangeAddress(tableDimensions.getRowStart() + 1, tableDimensions.getRowEnd(),
                        tableDimensions.getColStart(), tableDimensions.getColStart()));

        data.setBarDirection(BarDirection.COL);

        // set data for each series and set color if applicable
        for (int i = 0; i < categories.size(); i++) {
            XDDFChartData.Series series = data.addSeries(headerSource,
                    XDDFDataSourcesFactory.fromNumericCellRange(xssfSheet,
                            new CellRangeAddress(tableDimensions.getRowStart() + 1, tableDimensions.getRowEnd(),
                                    tableDimensions.getColStart() + 1 + i, tableDimensions.getColStart() + 1 + i)));

            series.setTitle(categories.size() > i && categories.get(i).getKey() != null ? categories.get(i).getKey().toString() : "", null);
            if (categories.get(i).getColor() != null) {
                Color color = categories.get(i).getColor();
                byte[] colorArrARGB = ByteBuffer.allocate(4).putInt(color.getRGB()).array();
                byte[] colorArrRGB = new byte[]{colorArrARGB[1], colorArrARGB[2], colorArrARGB[3]};
                XDDFSolidFillProperties fill = new XDDFSolidFillProperties(XDDFColor.from(colorArrRGB));
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
