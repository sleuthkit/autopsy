/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.ui;

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
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ColumnModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport.ExcelSheetExport;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelTableExport;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestModuleInfo;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author gregd
 */
@Messages({
    "IngestJobExcelExport_startTimeColumn=Start Time",
    "IngestJobExcelExport_endTimeColumn=End Time",
    "IngestJobExcelExport_ingestStatusTimeColumn=Ingest Status",
    "IngestJobExcelExport_moduleNameTimeColumn=Module Name",
    "IngestJobExcelExport_versionColumn=Module Version",
    "IngestJobExcelExport_sheetName=Ingest History"
})
class IngestJobExcelExport {

    private static class IngestJobEntry {

        private final Date startTime;
        private final Date endTime;
        private final String status;
        private final String ingestModule;
        private final String ingestModuleVersion;

        IngestJobEntry(Date startTime, Date endTime, String status, String ingestModule, String ingestModuleVersion) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.status = status;
            this.ingestModule = ingestModule;
            this.ingestModuleVersion = ingestModuleVersion;
        }

        Date getStartTime() {
            return startTime;
        }

        Date getEndTime() {
            return endTime;
        }

        String getStatus() {
            return status;
        }

        String getIngestModule() {
            return ingestModule;
        }

        String getIngestModuleVersion() {
            return ingestModuleVersion;
        }
    }

    private static final Logger logger = Logger.getLogger(IngestJobExcelExport.class.getName());
    private static final String DATETIME_FORMAT_STR = "yyyy/MM/dd HH:mm:ss";
    private static final DateFormat DATETIME_FORMAT = new SimpleDateFormat(DATETIME_FORMAT_STR, Locale.getDefault());

    private static final List<ColumnModel<IngestJobEntry, DefaultCellModel<?>>> COLUMNS = Arrays.asList(
            new ColumnModel<>(
                    Bundle.IngestJobExcelExport_startTimeColumn(),
                    (entry) -> getDateCell(entry.getStartTime())),
            new ColumnModel<>(
                    Bundle.IngestJobExcelExport_endTimeColumn(),
                    (entry) -> getDateCell(entry.getEndTime())),
            new ColumnModel<>(
                    Bundle.IngestJobExcelExport_ingestStatusTimeColumn(),
                    (entry) -> new DefaultCellModel<>(entry.getStatus())),
            new ColumnModel<>(
                    Bundle.IngestJobExcelExport_moduleNameTimeColumn(),
                    (entry) -> new DefaultCellModel<>(entry.getIngestModule())),
            new ColumnModel<>(
                    Bundle.IngestJobExcelExport_versionColumn(),
                    (entry) -> new DefaultCellModel<>(entry.getIngestModuleVersion()))
    );

    private static DefaultCellModel getDateCell(Date date) {
        Function<Date, String> dateParser = (dt) -> dt == null ? "" : DATETIME_FORMAT.format(dt);
        return new DefaultCellModel<>(date, dateParser, DATETIME_FORMAT_STR);
    }

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
                    .sorted((a,b) -> StringUtils.compareIgnoreCase(a.getIngestModule(), b.getIngestModule()))
                    .collect(Collectors.toList());
        }
    }

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
                .sorted((a,b) -> {
                    boolean aIsNull = a.getStartDateTime() == null;
                    boolean bIsNull = b.getStartDateTime() == null;
                    if (aIsNull || bIsNull) {
                        return Boolean.compare(bIsNull, aIsNull);
                    } else {
                        return a.getStartDateTime().compareTo(b.getStartDateTime());
                    }
                })
                .map((job) -> getEntries(job))
                .filter(lst -> lst != null)
                .flatMap((lst) -> showFirstRowOnly(lst))
                .filter(item -> item != null)
                .collect(Collectors.toList());
                        
        return Arrays.asList(new ExcelTableExport(Bundle.IngestJobExcelExport_sheetName(), COLUMNS, toDisplay));
    }
}
