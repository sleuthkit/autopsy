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
package org.sleuthkit.autopsy.mainui.datamodel;

import com.google.common.collect.ImmutableList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Report;

/**
 * Row of credit card information for a file.
 */
@Messages({
    "ReportsRowDTO_sourceModuleName_displayName=Source Module Name",
    "ReportsRowDTO_reportName_displayName=Report Name",
    "ReportsRowDTO_createTime_displayName=Created Time",
    "ReportsRowDTO_reportFilePath_displayName=Report File Path"
})
public class ReportsRowDTO extends BaseRowDTO {

    private static ColumnKey getColumnKey(String displayName) {
        return new ColumnKey(displayName.toUpperCase().replaceAll("\\s", "_"), displayName, "");
    }

    static List<ColumnKey> COLUMNS = ImmutableList.of(
            getColumnKey(Bundle.ReportsRowDTO_sourceModuleName_displayName()),
            getColumnKey(Bundle.ReportsRowDTO_reportName_displayName()),
            getColumnKey(Bundle.ReportsRowDTO_createTime_displayName()),
            getColumnKey(Bundle.ReportsRowDTO_reportFilePath_displayName())
    );

    private static final String TYPE_ID = "REPORTS";

    /**
     * @return The type identifier of this class.
     */
    public static String getTypeIdForClass() {
        return TYPE_ID;
    }
    
    private final String sourceModuleName;
    private final String reportName;
    private final Date createdTime;
    private final String reportFilePath;
    private final Report report;

    /**
     * Main constructor.
     * @param report The report.
     * @param id The report id.
     * @param sourceModuleName The source module name.
     * @param reportName The report name.
     * @param createdTime The created time.
     * @param reportFilePath The report file path.
     */
    public ReportsRowDTO(Report report, long id, String sourceModuleName, String reportName, Date createdTime, String reportFilePath) {
        super(ImmutableList.of(sourceModuleName, reportName, createdTime, reportFilePath), TYPE_ID, id);
        this.sourceModuleName = sourceModuleName;
        this.reportName = reportName;
        this.createdTime = createdTime;
        this.reportFilePath = reportFilePath;
        this.report = report;
    }

    /**
     * @return The source module name.
     */
    public String getSourceModuleName() {
        return sourceModuleName;
    }

    /**
     * @return The report name.
     */
    public String getReportName() {
        return reportName;
    }

    /**
     * @return The created time.
     */
    public Date getCreatedTime() {
        return createdTime;
    }

    /**
     * @return The report file path.
     */
    public String getReportFilePath() {
        return reportFilePath;
    }

    /**
     * @return The report.
     */
    public Report getReport() {
        return report;
    }
}
