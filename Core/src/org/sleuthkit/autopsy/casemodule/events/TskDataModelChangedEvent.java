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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An abstract base class for application events published when Sleuth Kit Data
 * Model objects for a case are added, updated, or deleted.
 *
 * @param <T> A Sleuth Kit Data Model object type.
 */
public abstract class TskDataModelChangedEvent<T> extends AutopsyEvent {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(TskDataModelChangedEvent.class.getName());
    private final boolean isDeletionEvent;
    private final List<Long> dataModelObjectIds;
    private transient List<T> dataModelObjects;

    /**
     * Constructs an instance of an abstract base class for application events
     * published when Sleuth Kit Data Model objects for a case are added or
     * updated. The getNewValue() method of this event will return the objects
     * and the getOldValue() method will return an empty list.
     *
     * @param eventName        The event name.
     * @param dataModelObjects The Sleuth Kit Data Model objects that have been
     *                         added or updated.
     * @param getIdMethod      A method that can be applied to the data model
     *                         objects to get their unique numeric IDs (TSK
     *                         object IDs, case database row IDs, etc.).
     */
    protected TskDataModelChangedEvent(String eventName, List<T> dataModelObjects, Function<T, Long> getIdMethod) {
        super(eventName, null, null);
        isDeletionEvent = false;
        this.dataModelObjectIds = new ArrayList<>();
        this.dataModelObjectIds.addAll(dataModelObjects.stream().map(o -> getIdMethod.apply(o)).collect(Collectors.toList()));
        this.dataModelObjects = new ArrayList<>();
        this.dataModelObjects.addAll(dataModelObjects);
    }

    /**
     * Constructs an instance of an abstract base class for application events
     * published when Sleuth Kit Data Model objects for a case are added or
     * updated. The getOldValue() method of this event will return the object
     * IDs and the getNewValue() method will return an empty list.
     *
     * @param eventName          The event name.
     * @param dataModelObjectIds The unique numeric IDs (TSK object IDs, case
     *                           database row IDs, etc.) of the Sleuth Kit Data
     *                           Model objects that have been deleted.
     */
    protected TskDataModelChangedEvent(String eventName, List<Long> dataModelObjectIds) {
        super(eventName, null, null);
        isDeletionEvent = true;
        this.dataModelObjectIds = new ArrayList<>();
        this.dataModelObjectIds.addAll(dataModelObjectIds);
        dataModelObjects = Collections.emptyList();
    }

    /**
     * Gets the the unique numeric IDs (TSK object IDs, case database row IDs,
     * etc.) of the Sleuth Kit Data Model objects that were deleted.
     *
     * @return The unique IDs.
     */
    @Override
    public List<Long> getOldValue() {
        if (isDeletionEvent) {
            return getDataModelObjectIds();
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Gets the Sleuth Kit Data Model objects that were added or updated. If
     * this event came from another host collaborating on a multi-user case, the
     * Sleuth Kit Data Model objects will be reconstructed on the current host.
     *
     * @return The objects.
     */
    @Override
    public List<T> getNewValue() {
        if (!isDeletionEvent) {
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
        } else {
            return Collections.emptyList();
        }
    }


    /**
     * Gets the unique numeric IDs (TSK object IDs, case database row IDs, etc.)
     * of the Sleuth Kit Data Model objects associated with this application
     * event.
     *
     * This method is provided as an optimization that allows handling of an
     * event that came from another host collaborating on a multi-user case
     * without reconstructing the data model objects that are the subject s of
     * the event.
     *
     * @return The unique IDs.
     */
    public final List<Long> getDataModelObjectIds() {
        return Collections.unmodifiableList(dataModelObjectIds);
    }    
    
    /**
     * Gets the Sleuth Kit Data Model objects associated with this application
     * event. If this event came from another host collaborating on a multi-user
     * case, the Sleuth Kit Data Model objects, this method will be called to
     * reconstruct the objects on the curartifactExists(), I think we should continue to use what we have and suppress the deprecation warnings.Bent host.
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
