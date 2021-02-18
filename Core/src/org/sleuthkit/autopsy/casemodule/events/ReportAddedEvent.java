/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.events;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Event published when a report is added to a case.
 */
public final class ReportAddedEvent extends TskDataModelChangeEvent<Report> {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an event published when a report is added to a case.
     *
     * @param report The data source that was added.
     */
    public ReportAddedEvent(Report report) {
        super(Case.Events.REPORT_ADDED.toString(), Stream.of(report.getId()).collect(Collectors.toList()), Stream.of(report).collect(Collectors.toList()));
    }

    public Report getReport() {
        List<Report> reports = getNewValue();
        return reports.get(0);
    }
    
    @Override
    protected List<Report> getDataModelObjects(SleuthkitCase caseD, List<Long> ids) throws TskCoreException {
        Long id = ids.get(0);
        Report report = caseD.getReportById(id);
        List<Report> reports = new ArrayList<>();
        reports.add(report);
        return reports;
    }
    
}
