/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications.snapshot;

import java.util.List;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import javax.imageio.ImageIO;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.report.uisnapshot.UiSnapShotReportWriter;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsFilter.AccountTypeFilter;
import org.sleuthkit.datamodel.CommunicationsFilter.DateRangeFilter;
import org.sleuthkit.datamodel.CommunicationsFilter.DeviceFilter;
import org.sleuthkit.datamodel.CommunicationsFilter.SubFilter;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Generate and write the Communication snapshot report to disk.
 */
public class CommSnapShotReportWriter extends UiSnapShotReportWriter {

    private final BufferedImage image;
    private final CommunicationsFilter filter;

    /**
     * Constructor
     *
     * @param currentCase      The Case to write a report for.
     * @param reportFolderPath The Path to the folder that will contain the
     *                         report.
     * @param reportName       The name of the report.
     * @param generationDate   The generation Date of the report.
     * @param snapshot         A snapshot of the view to include in the report.
     * @param filter           The communications filter
     */
    public CommSnapShotReportWriter(Case currentCase, Path reportFolderPath, String reportName, Date generationDate, BufferedImage snapshot, CommunicationsFilter filter) {

        super(currentCase, reportFolderPath, reportName, generationDate);

        this.image = snapshot;
        this.filter = filter;

    }

    /**
     * Generate and write the html page that shows the snapshot and the state of
     * the CommunicationFilters
     *
     * @throws IOException If there is a problem writing the html file to disk.
     */
    @Override
    protected void writeSnapShotHTMLFile() throws IOException {
        SimpleDateFormat formatter = new SimpleDateFormat("MMMMM dd, yyyy"); //NON-NLS

        ImageIO.write(image, "png", getReportFolderPath().resolve("snapshot.png").toFile()); //NON-NLS

        //make a map of context objects to resolve template paramaters against
        HashMap<String, Object> snapShotContext = new HashMap<>();
        snapShotContext.put("reportTitle", getReportName()); //NON-NLS

        List<SubFilter> filters = filter.getAndFilters();

        for (SubFilter filter : filters) {
            if (filter instanceof DateRangeFilter) {
                long startDate = ((DateRangeFilter) filter).getStartDate();
                long endDate = ((DateRangeFilter) filter).getEndDate();

                if (startDate > 0) {

                    snapShotContext.put("startTime", formatter.format(new Date((Instant.ofEpochSecond(startDate)).toEpochMilli()))); //NON-NLS
                }

                if (endDate > 0) {
                    snapShotContext.put("endTime", formatter.format(new Date((Instant.ofEpochSecond(endDate)).toEpochMilli()))); //NON-NLS
                }
            } else if (filter instanceof AccountTypeFilter) {

                Set<Account.Type> selectedAccounts = ((AccountTypeFilter) filter).getAccountTypes();
                ArrayList<ReportWriterHelper> fullAccountList = new ArrayList<>();
                for (Account.Type type : Account.Type.PREDEFINED_ACCOUNT_TYPES) {
                    if (type == Account.Type.CREDIT_CARD) {
                        continue;
                    }

                    fullAccountList.add(new ReportWriterHelper(type.getDisplayName(), selectedAccounts.contains(type)));
                }

                snapShotContext.put("accounts", fullAccountList);
            } else if (filter instanceof DeviceFilter) {
                Collection<String> ids = ((DeviceFilter) filter).getDevices();
                ArrayList<ReportWriterHelper> list = new ArrayList<>();
                try {
                    final SleuthkitCase sleuthkitCase = getCurrentCase().getSleuthkitCase();
                    for (DataSource dataSource : sleuthkitCase.getDataSources()) {
                        boolean selected = ids.contains(dataSource.getDeviceId());
                        String dsName = sleuthkitCase.getContentById(dataSource.getId()).getName();
                        list.add(new ReportWriterHelper(dsName, selected));
                    }
                } catch (TskCoreException ex) {

                }

                snapShotContext.put("devices", list);
            }
        }

        fillTemplateAndWrite("/org/sleuthkit/autopsy/communications/snapshot/comm_snapshot_template.html", "Snapshot", snapShotContext, getReportFolderPath().resolve("snapshot.html")); //NON-NLS
    }

    /**
     * Helper class for use with the html template
     */
    private final class ReportWriterHelper {

        private final String label;
        private final boolean selected;

        /**
         * Helper class for use with the html template.
         *
         * @param label    Display label
         * @param selected Boolean selected state
         */
        ReportWriterHelper(String label, boolean selected) {
            this.label = label;
            this.selected = selected;
        }
        
        /**
         * Returns the display label
         * 
         * @return The display label
         */
        public String getLabel(){
            return label;
        }
        
        /**
         * Returns the selection state
         * 
         * @return The selection state
         */
        public boolean isSelected(){
            return selected;
        }
    }

}
