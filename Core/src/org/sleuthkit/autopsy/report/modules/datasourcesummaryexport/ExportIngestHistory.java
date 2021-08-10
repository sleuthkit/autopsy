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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelSheetExport;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestModuleInfo;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Class that handles exporting ingest job information to excel.
 */
@Messages({
    "ExportIngestHistory_startTimeColumn=Start Time",
    "ExportIngestHistory_endTimeColumn=End Time",
    "ExportIngestHistory_ingestStatusTimeColumn=Ingest Status",
    "ExportIngestHistory_moduleNameTimeColumn=Module Name",
    "ExportIngestHistory_versionColumn=Module Version",
    "ExportIngestHistory_sheetName=Ingest History"
})
class ExportIngestHistory {

    /**
     * An entry to display in an excel export.
     */
    private static class IngestJobEntry {

        private final Date startTime;
        private final Date endTime;
        private final String status;
        private final String ingestModule;
        private final String ingestModuleVersion;

        /**
         * Main constructor.
         *
         * @param startTime           The ingest start time.
         * @param endTime             The ingest stop time.
         * @param status              The ingest status.
         * @param ingestModule        The ingest module.
         * @param ingestModuleVersion The ingest module version.
         */
        IngestJobEntry(Date startTime, Date endTime, String status, String ingestModule, String ingestModuleVersion) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.status = status;
            this.ingestModule = ingestModule;
            this.ingestModuleVersion = ingestModuleVersion;
        }

        /**
         * @return The ingest start time.
         */
        Date getStartTime() {
            return startTime;
        }

        /**
         * @return The ingest stop time.
         */
        Date getEndTime() {
            return endTime;
        }

        /**
         * @return The ingest status.
         */
        String getStatus() {
            return status;
        }

        /**
         * @return The ingest module.
         */
        String getIngestModule() {
            return ingestModule;
        }

        /**
         * @return The ingest module version.
         */
        String getIngestModuleVersion() {
            return ingestModuleVersion;
        }
    }

    private static final Logger logger = Logger.getLogger(ExportIngestHistory.class.getName());
    private static final String DATETIME_FORMAT_STR = "yyyy/MM/dd HH:mm:ss";
    private static final DateFormat DATETIME_FORMAT = new SimpleDateFormat(DATETIME_FORMAT_STR, Locale.getDefault());

    // columns in the excel export table to be created.
    private static final List<ColumnModel<IngestJobEntry, DefaultCellModel<?>>> COLUMNS = Arrays.asList(
            new ColumnModel<>(
                    Bundle.ExportIngestHistory_startTimeColumn(),
                    (entry) -> getDateCell(entry.getStartTime())),
            new ColumnModel<>(
                    Bundle.ExportIngestHistory_endTimeColumn(),
                    (entry) -> getDateCell(entry.getEndTime())),
            new ColumnModel<>(
                    Bundle.ExportIngestHistory_ingestStatusTimeColumn(),
                    (entry) -> new DefaultCellModel<>(entry.getStatus())),
            new ColumnModel<>(
                    Bundle.ExportIngestHistory_moduleNameTimeColumn(),
                    (entry) -> new DefaultCellModel<>(entry.getIngestModule())),
            new ColumnModel<>(
                    Bundle.ExportIngestHistory_versionColumn(),
                    (entry) -> new DefaultCellModel<>(entry.getIngestModuleVersion()))
    );

    /**
     * Retrieves data for a date cell.
     *
     * @param date The date.
     *
     * @return The data cell to be used in the excel export.
     */
    private static DefaultCellModel<?> getDateCell(Date date) {
        Function<Date, String> dateParser = (dt) -> dt == null ? "" : DATETIME_FORMAT.format(dt);
        return new DefaultCellModel<>(date, dateParser, DATETIME_FORMAT_STR);
    }

    /**
     * Retrieves all the ingest job modules and versions for a job.
     *
     * @param job The ingest job.
     *
     * @return All of the corresponding entries sorted by module name.
     */
    private static List<IngestJobEntry> getEntries(IngestJobInfo job) {
        List<IngestModuleInfo> infoList = job.getIngestModuleInfo();
        if (infoList == null) {
            return Collections.emptyList();
        } else {
            Date startTime = job.getStartDateTime();
            Date endTime = job.getEndDateTime();
            String status = job.getStatus().getDisplayName();

            return infoList.stream()
                    .filter(info -> info != null)
                    .map(info -> new IngestJobEntry(startTime, endTime, status, info.getDisplayName(), info.getVersion()))
                    .sorted((a, b) -> {
                        boolean aIsNull = a == null || a.getIngestModule() == null;
                        boolean bIsNull = b == null || b.getIngestModule() == null;
                        if (aIsNull || bIsNull) {
                            return Boolean.compare(aIsNull, bIsNull);
                        } else {
                            return a.getIngestModule().compareTo(b.getIngestModule());
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    /**
     * For output, show ingest job details in first row present. Otherwise, set
     * to null.
     *
     * @param list The list of entries for an ingest job.
     *
     * @return The stream of entries to be displayed.
     */
    private static Stream<IngestJobEntry> showFirstRowOnly(List<IngestJobEntry> list) {
        return IntStream.range(0, list.size())
                .mapToObj(idx -> {
                    IngestJobEntry entry = list.get(idx);
                    if (entry == null || idx == 0) {
                        return entry;
                    } else {
                        return new IngestJobEntry(null, null, null, entry.getIngestModule(), entry.getIngestModuleVersion());
                    }
                });

    }

    /**
     * Returns a list of sheets to be exported for the Ingest History tab.
     *
     * @param dataSource The data source.
     *
     * @return The list of sheets to be included in an export.
     */
    static List<ExcelSheetExport> getExports(DataSource dataSource) {
        if (dataSource == null) {
            return Collections.emptyList();
        }

        List<IngestJobInfo> info = null;
        try {
            info = Case.getCurrentCaseThrows().getSleuthkitCase().getIngestJobs();
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "There was an error fetching ingest jobs", ex);
        }

        if (info == null) {
            info = Collections.emptyList();
        }

        List<IngestJobEntry> toDisplay = info.stream()
                .filter(job -> job != null && dataSource.getId() == job.getObjectId())
                .sorted((a, b) -> {
                    // sort ingest jobs by time.
                    boolean aIsNull = a.getStartDateTime() == null;
                    boolean bIsNull = b.getStartDateTime() == null;
                    if (aIsNull || bIsNull) {
                        return Boolean.compare(aIsNull, bIsNull);
                    } else {
                        return a.getStartDateTime().compareTo(b.getStartDateTime());
                    }
                })
                .map((job) -> getEntries(job))
                .filter(lst -> lst != null)
                .flatMap((lst) -> showFirstRowOnly(lst))
                .filter(item -> item != null)
                .collect(Collectors.toList());

        return Arrays.asList(new ExcelTableExport<>(Bundle.ExportIngestHistory_sheetName(), COLUMNS, toDisplay));
    }

    private ExportIngestHistory() {
    }
}
