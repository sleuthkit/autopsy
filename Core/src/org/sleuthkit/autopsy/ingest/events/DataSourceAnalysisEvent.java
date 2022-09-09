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
    private final long dataSourceIngestJobId; // Obsolete, same as ingestJobId. Do not remove for the sake of serialization compatibility.
    private transient Content dataSource;
    private final long dataSourceObjectId;

    /**
     * Constructs an instance of the base class for events published in
     * connection with the analysis (ingest) of a data source.
     *
     * @param eventType   The event type string.
     * @param ingestJobId The identifier of the ingest job. For a multi-user
     *                    case, this ID is only unique on the host where the
     *                    ingest job is running.
     * @param dataSource  The data source.
     */
    public DataSourceAnalysisEvent(IngestManager.IngestJobEvent eventType, long ingestJobId, Content dataSource) {
        super(eventType.toString(), null, null);
        this.ingestJobId = ingestJobId;
        this.dataSourceIngestJobId = ingestJobId;
        this.dataSource = dataSource;
        this.dataSourceObjectId = dataSource.getId();
    }

    /**
     * Constructs an instance of the base class for events published in
     * connection with the analysis (ingest) of a data source by an ingest job.
     *
     * @param eventType   The event type string.
     * @param ingestJobId The identifier of the ingest job. For a multi-user
     *                    case, this ID is only unique on the host where the
     *                    ingest job is running.
     * @param unused      Unused.
     * @param dataSource  The data source.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public DataSourceAnalysisEvent(IngestManager.IngestJobEvent eventType, long ingestJobId, long unused, Content dataSource) {
        this(eventType, unused, dataSource);
    }

    /**
     * Gets the ID of the ingest job. For a multi-user case, this ID is only
     * unique on the host where the ingest job is running.
     *
     * @return The ingest job ID.
     */
    public long getIngestJobId() {
        return ingestJobId;
    }

    /**
     * Gets the ID of the ingest job. For a multi-user case, this ID is only
     * unique on the host where the ingest job is running.
     *
     * @return The ingest job ID.
     *
     * @deprecated Use getIngestJobId() instead.
     */
    @Deprecated
    public long getDataSourceIngestJobId() {
        return dataSourceIngestJobId;
    }

    /**
     * Gets the data source associated with this event.
     *
     * @return The data source or null if there is an error getting the data
     *         source from an event published by a remote host.
     */
    public Content getDataSource() {
        /**
         * The dataSource field is set in the constructor, but it is transient
         * so it will become null when the event is serialized for publication
         * over a network. Doing a lazy load of the Content object bypasses the
         * issues related to the serialization and de-serialization of Content
         * objects and may also save database round trips from other hosts since
         * subscribers to this event are often not interested in the event data.
         */
        if (null != dataSource) {
            return dataSource;
        }
        try {
            dataSource = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(dataSourceObjectId);
            return dataSource;
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error doing lazy load of data source (objId=%d) for remote event", this.dataSourceObjectId), ex); //NON-NLS
            return null;
        }
    }

}
