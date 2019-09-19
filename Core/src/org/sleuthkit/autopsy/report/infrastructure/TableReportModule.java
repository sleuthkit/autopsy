/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.infrastructure;

import org.sleuthkit.autopsy.report.ReportModule;
import java.util.List;

/**
 * ReportModule that implements a tabular style of reporting.
 *
 * All TableReportModules will be sent the exact data to report, and will only
 * have to determine how to write and display this data.
 *
 * The data sent consists of user-chosen fields such as Blackboard Artifacts and
 * File/Result Tags.
 */
public interface TableReportModule extends ReportModule {

    /**
     * Start the report. Open any output streams, initialize member variables,
     * write summary and navigation pages. Considered the "constructor" of the
     * report.
     *
     * @param baseReportDir Directory to save the report file into. Report
     *                      should go into baseReportDir +
     *                      getRelativeFilePath().
     */
    public void startReport(String baseReportDir);

    /**
     * End the report. Close all output streams and write any end-of-report
     * files.
     */
    public void endReport();

    /**
     * Start a new data type for the report. This is how the report will
     * differentiate between the start and end of a certain type of data, such
     * as a blackboard artifact Type. It is up to the report how the
     * differentiation is shown.
     *
     * @param title       String name of the data type
     * @param description Description of the data type
     */
    public void startDataType(String title, String description);

    /**
     * End the current data type and prepare for either the end of the report or
     * the start of a new data type.
     */
    public void endDataType();

    /**
     * Start a new set, or sub-category, for the current data type.
     *
     * @param setName String name of the set
     */
    public void startSet(String setName);

    /**
     * End the current set and prepare for either the end of the current data
     * type or the start of a new set.
     */
    public void endSet();

    /**
     * Add an index of all the sets to the report's current data type. This
     * method is guaranteed to be called before any sets are added to the data
     * type, and may be ignored.
     *
     * @param sets List of all the String set names
     */
    public void addSetIndex(List<String> sets);

    /**
     * Add an element to the current set. An element is considered the 'title'
     * of a table in a set, a sub-set in a sense.
     *
     * @param elementName String name of element
     */
    public void addSetElement(String elementName);

    /**
     * Create a table with the column names given.
     *
     * @param titles List of String column names
     */
    public void startTable(List<String> titles);

    /**
     * End the current table.
     */
    public void endTable();

    /**
     * Add a row with the cell values given to the current table.
     *
     * @param row List of String cell values
     */
    public void addRow(List<String> row);

    /**
     * Returns a String date, created by the module. All date values will query
     * the module for its interpretation of the date before sending it a row
     * with the date value.
     *
     * @param date long date as long
     *
     * @return String date as String
     */
    public String dateToString(long date);

}
