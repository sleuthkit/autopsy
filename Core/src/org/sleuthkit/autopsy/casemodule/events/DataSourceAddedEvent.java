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
import java.util.UUID;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Event published when a data source is added to a case.
 */
public final class DataSourceAddedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DataSourceAddedEvent.class.getName());
    private transient Content dataSource;
    private final UUID dataSourceId;

    /**
     * Constructs an event published when a data source is added to a case.
     *
     * @param dataSource   The data source that was added.
     * @param dataSourceId A unique identifier associated with the data source.
     *                     Used to pair this DataSourceAddedEvent with a
     *                     AddindDataSourceEvent.
     */
    public DataSourceAddedEvent(Content dataSource, UUID dataSourceId) {
        /**
         * Putting the object id of the data source into newValue to allow for
         * lazy loading of the Content object. This bypasses the issues related
         * to the serialization and de-serialization of Content objects when the
         * event is published over a network.
         */
        super(Case.Events.DATA_SOURCE_ADDED.toString(), null, dataSource.getId());
        this.dataSource = dataSource;
        this.dataSourceId = dataSourceId;
    }

    /**
     * Gets the data source that was added.
     *
     * @return The data source or null if there is an error retrieving the data
     *         source.
     */
    @Override
    public Object getNewValue() {
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

    /**
     * Gets the data source that was added.
     *
     * @return The data source.
     */
    public Content getDataSource() {
        return (Content) getNewValue();
    }

    /**
     * Gets the unique id used to pair this DataSourceAddedEvent with the
     * corresponding AddingDataSourceEvent.
     *
     * @return The unique event id.
     */
    public UUID getAddingDataSourceEventId() {
        return dataSourceId;
    }

    /**
     * Gets the unique id used to pair this DataSourceAddedEvent with the
     * corresponding AddingDataSourceEvent.
     *
     * @return The unique event id.
     *
     * @deprecated Use getAddingDataSourceEventId instead.
     */
    @Deprecated
    public UUID getDataSourceId() {
        return dataSourceId;
    }

}
