/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.events;

import java.beans.PropertyChangeEvent;
import java.io.Serializable;
import javax.annotation.concurrent.Immutable;

/**
 * A base class for events that can be published locally as well as to other
 * Autopsy nodes when a multi-user case is open. It extends PropertyChangeEvent
 * to integrate with the legacy use of JavaBeans PropertyChangeEvents and
 * PropertyChangeListeners as a local event system.
 */
@Immutable
public class AutopsyEvent extends PropertyChangeEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private SourceType sourceType;

    /**
     * Events have a source field set to local or remote to allow event
     * subscribers to filter events by source type.
     */
    public enum SourceType {

        LOCAL,
        REMOTE
    };

    /**
     * Constructs an event that can be published locally and to other Autopsy
     * nodes when a multi-user case is open.
     *
     * @param sourceType The source type of the event, local or remote.
     * @param eventName The event name.
     * @param oldValue The "old" value to associate with the event. May be null.
     * @param newValue The "new" value to associate with the event. May be null.
     */
    public AutopsyEvent(SourceType sourceType, String eventName, Object oldValue, Object newValue) {
        super(sourceType.toString(), eventName, oldValue, newValue);
        this.sourceType = sourceType;
    }

    /**
     * Gets the source type (local or remote).
     *
     * @param sourceType The source type of the event, local or remote.
     */
    public SourceType getSourceType() {
        return sourceType;
    }

    /**
     * Sets the source type (local or remote). This field is mutable in this way
     * to allow an event to be published both locally and remotely without
     * requiring the construction of two separate objects.
     *
     * @param sourceType The source type of the event, local or remote.
     */
    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

}
