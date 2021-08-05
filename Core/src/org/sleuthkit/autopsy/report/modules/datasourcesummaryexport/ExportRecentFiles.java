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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.contentutils.RecentFilesSummary;
import org.sleuthkit.autopsy.contentutils.RecentFilesSummary.RecentAttachmentDetails;
import org.sleuthkit.autopsy.contentutils.RecentFilesSummary.RecentDownloadDetails;
import org.sleuthkit.autopsy.contentutils.RecentFilesSummary.RecentFileDetails;
import org.sleuthkit.datamodel.DataSource;

/**
 * Data Source Summary recent files panel.
 */
@Messages({
    "RecentFilesPanel_docsTable_tabName=Recently Opened Documents",
    "RecentFilesPanel_downloadsTable_tabName=Recently Downloads",
    "RecentFilesPanel_attachmentsTable_tabName=Recent Attachments",})
final class ExportRecentFiles {

    private static final String DATETIME_FORMAT_STR = "yyyy/MM/dd HH:mm:ss";
    private static final DateFormat DATETIME_FORMAT = new SimpleDateFormat(DATETIME_FORMAT_STR, Locale.getDefault());

    private static final List<ColumnModel<RecentFileDetails, DefaultCellModel<?>>> docsTemplate = Arrays.asList(
            new ColumnModel<>(Bundle.RecentFilePanel_col_header_path(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getPath());
                    }, 250),
            new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                    getDateFunct(),
                    80));

    private static final List<ColumnModel<RecentDownloadDetails, DefaultCellModel<?>>> downloadsTemplate = Arrays.asList(
            new ColumnModel<>(Bundle.RecentFilePanel_col_header_domain(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getWebDomain());
                    }, 100),
            new ColumnModel<>(Bundle.RecentFilePanel_col_header_path(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getPath());
                    }, 250),
            new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                    getDateFunct(),
                    80));

    private static final List<ColumnModel<RecentAttachmentDetails, DefaultCellModel<?>>> attachmentsTemplate = Arrays.asList(
            new ColumnModel<>(Bundle.RecentFilePanel_col_header_path(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getPath());
                    }, 250),
            new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                    getDateFunct(),
                    80),
            new ColumnModel<>(Bundle.RecentFilePanel_col_header_sender(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getSender());
                    }, 150));

    /**
     * Default constructor.
     */
    @Messages({
        "RecentFilesPanel_col_head_date=Date",
        "RecentFilePanel_col_header_domain=Domain",
        "RecentFilePanel_col_header_path=Path",
        "RecentFilePanel_col_header_sender=Sender",
        "RecentFilePanel_emailParserModuleName=Email Parser"
    })

    private ExportRecentFiles() {
    }

    /**
     * Returns a function that gets the date from the RecentFileDetails object and
     * converts into a DefaultCellModel to be displayed in a table.
     *
     * @return The function that determines the date cell from a RecentFileDetails object.
     */
    private static <T extends RecentFileDetails> Function<T, DefaultCellModel<?>> getDateFunct() {
        return (T lastAccessed) -> {
            Function<Date, String> dateParser = (dt) -> dt == null ? "" : DATETIME_FORMAT.format(dt);
            return new DefaultCellModel<>(new Date(lastAccessed.getDateAsLong() * 1000), dateParser, DATETIME_FORMAT_STR);
        };
    }

    static List<ExcelExport.ExcelSheetExport> getExports(DataSource dataSource) {

        DataFetcher<DataSource, List<RecentFileDetails>> docsFetcher = (ds) -> RecentFilesSummary.getRecentlyOpenedDocuments(ds, 10);
        DataFetcher<DataSource, List<RecentDownloadDetails>> downloadsFetcher = (ds) -> RecentFilesSummary.getRecentDownloads(ds, 10);
        DataFetcher<DataSource, List<RecentAttachmentDetails>> attachmentsFetcher = (ds) -> RecentFilesSummary.getRecentAttachments(ds, 10);

        return Stream.of(
                ExcelExportAction.getTableExport(docsFetcher, docsTemplate, Bundle.RecentFilesPanel_docsTable_tabName(), dataSource),
                ExcelExportAction.getTableExport(downloadsFetcher, downloadsTemplate, Bundle.RecentFilesPanel_downloadsTable_tabName(), dataSource),
                ExcelExportAction.getTableExport(attachmentsFetcher, attachmentsTemplate, Bundle.RecentFilesPanel_attachmentsTable_tabName(), dataSource))
                .filter(sheet -> sheet != null)
                .collect(Collectors.toList());
    }
}
