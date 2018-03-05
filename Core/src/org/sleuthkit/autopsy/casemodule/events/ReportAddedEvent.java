/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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

import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Event published when a report is added to a case.
 */
public final class ReportAddedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DataSourceAddedEvent.class.getName());
    private transient Report report;

    /**
     * Constructs an event published when a report is added to a case.
     *
     * @param report The data source that was added.
     */
    public ReportAddedEvent(Report report) {
        /**
         * Putting the object id of the report into newValue to allow for lazy
         * loading of the Report object.
         */
        super(Case.Events.REPORT_ADDED.toString(), null, report.getId());
        this.report = report;
    }

    /**
     * Gets the data source that was added.
     *
     * @return The data source.
     */
    @Override
    public Object getNewValue() {
        /**
         * The report field is set in the constructor, but it is transient so it
         * will become null when the event is serialized for publication over a
         * network. Doing a lazy load of the Report object may save database
         * round trips from other nodes since subscribers to this event are
         * often not interested in the event data.
         */
        if (null != report) {
            return report;
        }
        try {
            long id = (Long) super.getNewValue();
            List<Report> reports = Case.getOpenCase().getSleuthkitCase().getAllReports();
            for (Report thisReport : reports) {
                if (thisReport.getId() == id) {
                    report = thisReport;
                    break;
                }
            }
            return report;
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Error doing lazy load for remote event", ex); //NON-NLS
            return null;
        }
    }

}
