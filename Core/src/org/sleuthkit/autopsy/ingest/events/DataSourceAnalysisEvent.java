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
package org.sleuthkit.autopsy.ingest.events;

import java.io.Serializable;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A base class for events published in connection with the analysis (ingest) of
 * a data source.
 */
public abstract class DataSourceAnalysisEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DataSourceAnalysisEvent.class.getName());
    private final long ingestJobId;
    private final long dataSourceIngestJobId;
    private transient Content dataSource;

    /**
     * Constructs an instance of the base class for events published in
     * connection with the analysis (ingest) of a data source.
     *
     * @param eventType             The event string for the subtype.
     * @param ingestJobId           The identifier of the ingest job, specific
     *                              to this node.
     * @param dataSourceIngestJobId The identifier of the data source ingest
     *                              job,specific to this node.
     * @param dataSource            The data source.
     */
    public DataSourceAnalysisEvent(IngestManager.IngestJobEvent eventType, long ingestJobId, long dataSourceIngestJobId, Content dataSource) {
        super(eventType.toString(), null, null);
        this.ingestJobId = ingestJobId;
        this.dataSourceIngestJobId = dataSourceIngestJobId;
        this.dataSource = dataSource;
    }

    /**
     * Gets the id of the ingest job of which the analysis of this data source
     * is a part.
     *
     * @return The id.
     */
    public long getIngestJobId() {
        return ingestJobId;
    }

    /**
     * Gets the id of the data source ingest job of which the analysis of this
     * data source is a part.
     *
     * @return The id.
     */
    public long getDataSourceIngestJobId() {
        return dataSourceIngestJobId;
    }

    /**
     * Gets the data source associated with this event.
     *
     * @return The data source.
     */
    public Content getDataSource() {
        /**
         * The dataSource field is set in the constructor, but it is transient
         * so it will become null when the event is serialized for publication
         * over a network. Doing a lazy load of the Content object bypasses the
         * issues related to the serialization and de-serialization of Content
         * objects and may also save database round trips from other nodes since
         * subscribers to this event are often not interested in the event data.
         */
        if (null != dataSource) {
            return dataSource;
        }
        try {
            long id = (Long) super.getNewValue();
            dataSource = Case.getOpenCase().getSleuthkitCase().getContentById(id);
            return dataSource;
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Error doing lazy load for remote event", ex); //NON-NLS
            return null;
        }
    }

}
