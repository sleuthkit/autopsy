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
 * An abstract base class for application events published when one or more
 * Sleuth Kit Data Model objects for a case change in some way.
 *
 * This class extends AutopsyEvent. The AutopsyEvent class extends
 * PropertyChangeEvent to integrate with legacy use of JavaBeans
 * PropertyChangeEvents and PropertyChangeListeners as an application event
 * publisher-subcriber mechanism. Subclasses need to decide what constitutes
 * "old" and "new" objects for them and are encouraged to provide getters for
 * these values that do not require clients to cast the return values.
 *
 * The AutopsyEvent class implements Serializable to allow local event instances
 * to be published to other Autopsy nodes over a network in serialized form. TSK
 * Data Model objects are generally not serializable because they encapsulate a
 * reference to a SleuthkitCase object that represents the case database and
 * which has local JDBC Connection objects. For this reason, this class supports
 * serialization of the unique numeric IDs (TSK object IDs, case database row
 * IDs, etc.) of the subject TSK Data Model objects and the "reconstruction" of
 * those objects on other Autopsy nodes by querying the case database by unique
 * ID.
 *
 * @param <T> The Sleuth Kit Data Model object type of the "old" data model
 *            objects.
 * @param <U> The Sleuth Kit Data Model object type of the "new" data model
 *            objects.
 */
public abstract class TskDataModelChangedEvent<T, U> extends AutopsyEvent {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(TskDataModelChangedEvent.class.getName());
    private final boolean hasOldValue;
    private final List<Long> oldValueIds;
    private transient List<T> oldValueObjects;
    private final boolean hasNewValue;
    private final List<Long> newValueIds;
    private transient List<U> newValueObjects;

    /**
     * Constructs the base class part for application events published when one
     * or more Sleuth Kit Data Model objects for a case change in some way.
     *
     * @param eventName           The event name.
     * @param oldValueObjects     A list of he Data Model objects that have been
     *                            designated as the "old" objects in the event.
     *                            May be null.
     * @param oldValueGetIdMethod A method that can be applied to the "old" data
     *                            model objects to get their unique numeric IDs
     *                            (TSK object IDs, case database row IDs, etc.).
     *                            May be null if there are no "old" objects.
     * @param newValueObjects     A list of he Data Model objects that have been
     *                            designated as the "new" objects in the event.
     *                            May be null.
     * @param newValueGetIdMethod A method that can be applied to the "new" data
     *                            model objects to get their unique numeric IDs
     *                            (TSK object IDs, case database row IDs, etc.).
     *                            May be null if there are no "new" objects.
     */
    protected TskDataModelChangedEvent(String eventName, List<T> oldValueObjects, Function<T, Long> oldValueGetIdMethod, List<U> newValueObjects, Function<U, Long> newValueGetIdMethod) {
        super(eventName, null, null);
        oldValueIds = new ArrayList<>();
        this.oldValueObjects = new ArrayList<>();
        if (oldValueObjects != null) {
            hasOldValue = true;
            oldValueIds.addAll(oldValueObjects.stream()
                    .map(o -> oldValueGetIdMethod.apply(o))
                    .collect(Collectors.toList()));
            this.oldValueObjects.addAll(oldValueObjects);
        } else {
            hasOldValue = false;
        }
        newValueIds = new ArrayList<>();
        this.newValueObjects = new ArrayList<>();
        if (newValueObjects != null) {
            hasNewValue = true;
            newValueIds.addAll(newValueObjects.stream()
                    .map(o -> newValueGetIdMethod.apply(o))
                    .collect(Collectors.toList()));
            this.newValueObjects.addAll(newValueObjects);
        } else {
            hasNewValue = false;
        }
    }

    /**
     * Gets a list of the Data Model objects that have been designated as the
     * "old" objects in the event.
     *
     * @return The list of the "old" data model objects. May be empty.
     */
    @Override
    public List<T> getOldValue() {
        if (hasOldValue) {
            if (oldValueObjects == null) {
                try {
                    Case currentCase = Case.getCurrentCaseThrows();
                    SleuthkitCase caseDb = currentCase.getSleuthkitCase();
                    oldValueObjects = getOldValueObjects(caseDb, oldValueIds);
                } catch (NoCurrentCaseException | TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error getting oldValue() TSK Data Model objects for %s event (%s)", getPropertyName(), getSourceType()), ex);
                    return Collections.emptyList();
                }
            }
            return Collections.unmodifiableList(oldValueObjects);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Gets a list of the Data Model objects that have been designated as the
     * "new" objects in the event.
     *
     * @return The list of the "new" data model objects. May be empty.
     */
    @Override
    public List<U> getNewValue() {
        if (hasNewValue) {
            if (newValueObjects == null) {
                try {
                    Case currentCase = Case.getCurrentCaseThrows();
                    SleuthkitCase caseDb = currentCase.getSleuthkitCase();
                    newValueObjects = getNewValueObjects(caseDb, newValueIds);
                } catch (NoCurrentCaseException | TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error getting newValue() TSK Data Model objects for %s event (%s)", getPropertyName(), getSourceType()), ex);
                    return Collections.emptyList();
                }
            }
            return Collections.unmodifiableList(newValueObjects);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Reconstructs the "old" Sleuth Kit Data Model objects associated with this
     * application event, if any, using the given unique numeric IDs (TSK object
     * IDs, case database row IDs, etc.) to query the given case database.
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
    protected List<T> getOldValueObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        return Collections.emptyList();
    }

    /**
     * Reconstructs the "new" Sleuth Kit Data Model objects associated with this
     * application event, if any, using the given unique numeric IDs (TSK object
     * IDs, case database row IDs, etc.) to query the given case database.
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
    protected List<U> getNewValueObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        return Collections.emptyList();
    }

}
