/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * A Report Module that reports information on files in a case.
 *
 * @author jwallace
 */
abstract class FileReportModule extends ReportModule {

    /**
     * Initialize the report which will be stored at the given path.
     *
     * @param baseReportDir Base directory to store the report file in. Report
     *                      should go into baseReportDir +
     *                      getRelativeFilePath().
     */
    public abstract void startReport(String baseReportDir);

    /**
     * End the report. Will be called after the entire report has been written.
     */
    public abstract void endReport();

    /**
     * Start the file list table.
     *
     * @param headers The columns that should be included in the table.
     */
    public abstract void startTable(List<FileReportDataTypes> headers);

    /**
     * Add the given AbstractFile as a row in the table. Guaranteed to be called
     * between startTable and endTable.
     *
     * @param toAdd   the AbstractFile to be added.
     * @param columns the columns that should be included
     */
    public abstract void addRow(AbstractFile toAdd, List<FileReportDataTypes> columns);

    /**
     * Close the table.
     */
    public abstract void endTable();
}
