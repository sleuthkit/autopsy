/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Event published when new data is posted to the blackboard of a case. The
 * "old" value is a legacy ModuleDataEvent object. The "new" value is null.
 */
public final class BlackboardPostEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(BlackboardPostEvent.class.getName());
    private transient ModuleDataEvent eventData;

    /**
     * Constructs an event to be published when new content is added to a case
     * or there is a change a recorded attribute of existing content.
     *
     * @param eventData A ModuleDataEvent object containing the data associated
     *                  with the blackboard post.
     */
    public BlackboardPostEvent(ModuleDataEvent eventData) {
        /**
         * Putting a serializable data holding object into oldValue to allow for
         * lazy loading of the ModuleDataEvent object for remote events. This
         * bypasses the issues related to the serialization and de-serialization
         * of BlackboardArtifact objects when the event is published over a
         * network.
         */
        super(
                IngestManager.IngestModuleEvent.DATA_ADDED.toString(),
                new SerializableEventData(eventData.getModuleName(), eventData.getBlackboardArtifactType(), eventData.getArtifacts() != null
                        ? eventData.getArtifacts()
                        .stream()
                        .map(BlackboardArtifact::getArtifactID)
                        .collect(Collectors.toList()) : Collections.emptyList()),
                null
        );
        this.eventData = eventData;
    }

    /**
     * Gets the legacy ModuleDataEvent object associated with this event.
     *
     * @return The ModuleDataEvent.
     */
    @Override
    public Object getOldValue() {
        /**
         * The eventData field is set in the constructor, but it is transient so
         * it will become null when the event is serialized for publication over
         * a network. Doing a lazy load of the ModuleDataEvent object bypasses
         * the issues related to the serialization and de-serialization of
         * BlackboardArtifact objects and may also save database round trips
         * from other nodes since subscribers to this event are often not
         * interested in the event data.
         */
        if (null != eventData) {
            return eventData;
        }
        try {
            SerializableEventData data = (SerializableEventData) super.getOldValue();
            Collection<BlackboardArtifact> artifacts = new ArrayList<>();
            for (Long id : data.artifactIds) {
                artifacts.add(Case.getOpenCase().getSleuthkitCase().getBlackboardArtifact(id));
            }
            eventData = new ModuleDataEvent(data.moduleName, data.artifactTypeId, !artifacts.isEmpty() ? artifacts : null);
            return eventData;
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Error doing lazy load for remote event", ex); //NON-NLS
            return null;
        }
    }

    /**
     * Data holder class.
     */
    @Immutable
    private static final class SerializableEventData implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String moduleName;
        private BlackboardArtifact.Type artifactTypeId;
        private Collection<Long> artifactIds;

        private SerializableEventData(String moduleName, BlackboardArtifact.Type artifactTypeId, Collection<Long> artifactIds) {
            this.moduleName = moduleName;
            this.artifactTypeId = artifactTypeId;
            this.artifactIds = new ArrayList<>(artifactIds);
        }

    }

}
