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
package org.sleuthkit.autopsy.casemodule.events;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An application event generic used as a superclass for events published when
 * something changes in the Sleuth Kit Data Model for a case.
 *
 * @param <T> A Sleuth Kit Data Model object type.
 */
public abstract class TskDataModelChangeEvent<T> extends AutopsyEvent {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(TskDataModelChangeEvent.class.getName());
    private final List<Long> dataModelObjectIds;
    private transient List<T> dataModelObjects;

    /**
     * Constructs an application event generic used as a superclass for events
     * published when something changes in the Sleuth Kit Data Model for a case.
     *
     * @param eventName          The event name.
     * @param dataModelObjectIds The unique numeric IDs (TSK object IDs, case
     *                           database row IDs, etc.) of the Sleuth Kit Data
     *                           Model objects associated with this application
     *                           event.
     * @param dataModelObjects   The Sleuth Kit Data Model objects associated
     *                           with this application event
     */
    protected TskDataModelChangeEvent(String eventName, List<Long> dataModelObjectIds, List<T> dataModelObjects) {
        super(eventName, null, null);
        this.dataModelObjectIds = dataModelObjectIds;
        this.dataModelObjects = dataModelObjects;
        if (eventName == null) {
            throw new IllegalArgumentException("eventName is null");
        }
        if (dataModelObjectIds == null) {
            throw new IllegalArgumentException("dataModelObjectIds is null");
        }
        if (dataModelObjects == null) {
            throw new IllegalArgumentException("dataModelObjects is null");
        }
    }

    /**
     * Gets the unique numeric IDs (TSK object IDs, case database row IDs, etc.)
     * of the Sleuth Kit Data Model objects associated with this application
     * event.
     *
     * @return The unique IDs.
     */
    public final List<Long> getDataModelObjectIds() {
        return Collections.unmodifiableList(dataModelObjectIds);
    }

    /**
     * Gets the Sleuth Kit Data Model objects associated with this application
     * event.
     *
     * @return The objects.
     */
    @Override
    public List<T> getNewValue() {
        /*
         * If this event came from another host collaborating on a multi-user
         * case, the transient list of Sleuth Kit Data Model objects will be
         * null and will need to be reconstructed on the current host.
         */
        if (dataModelObjects == null) {
            try {
                Case currentCase = Case.getCurrentCaseThrows();
                SleuthkitCase caseDb = currentCase.getSleuthkitCase();
                dataModelObjects = getDataModelObjects(caseDb, dataModelObjectIds);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Error geting TSK Data Model objects for %s event (%s)", getPropertyName(), getSourceType()), ex);
                return Collections.emptyList();
            }
        }
        return Collections.unmodifiableList(dataModelObjects);
    }

    /**
     * Gets the Sleuth Kit Data Model objects associated with this application
     * event.
     *
     * @param caseDb The case database.
     * @param ids    The unique, numeric IDs (TSK object IDs, case database row
     *               IDs, etc.) of the Sleuth Kit Data Model objects.
     *
     * @return The objects.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException If there is an error
     *                                                  getting the Sleuth Kit
     *                                                  Data Model objects.
     */
    abstract protected List<T> getDataModelObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException;

}
