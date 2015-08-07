/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
 * A base class for events to be published to registered subscribers on both
 * this Autopsy node and other Autopsy nodes. The class extends
 * PropertyChangeEvent to integrate with legacy use of JavaBeans
 * PropertyChangeEvents and PropertyChangeListeners as an application event
 * system, and implements Serializable to allow it to be published over a
 * network in serialized form.
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
     * Constructs an event that can be published to registered subscribers on
     * both this Autopsy node and other Autopsy nodes.
     *
     * @param eventName The event name.
     * @param oldValue  The "old" value to associate with the event. May be
     *                  null.
     * @param newValue  The "new" value to associate with the event. May be
     *                  null.
     */
    public AutopsyEvent(String eventName, Object oldValue, Object newValue) {
        super(SourceType.LOCAL.toString(), eventName, oldValue, newValue);
        this.sourceType = SourceType.LOCAL;
    }

    /**
     * Gets the source type (local or remote).
     *
     * @return SourceType The source type of the event, local or remote.
     */
    public SourceType getSourceType() {
        return sourceType;
    }

    /**
     * Gets the source type (local or remote) as a string. This is for clients
     * that do not have access to the AutopsyEvent type, and is necessary
     * because the events package is not currently a public package within the
     * Autopsy-Core NetBeans Module (NBM).
     *
     * @return A string, either "LOCAL" or "REMOTE", as an Object.
     */
    @Override
    public Object getSource() {
        return sourceType.toString();
    }

    /**
     * Sets the source type (local or remote). This field is mutable in this way
     * to allow an event to be published both locally and remotely without
     * requiring the construction of two separate objects. It is for use by the
     * event publishing classes within this package only.
     *
     * @param sourceType The source type of the event, local or remote.
     */
    void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

}
