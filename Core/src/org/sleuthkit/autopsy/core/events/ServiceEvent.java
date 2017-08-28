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
package org.sleuthkit.autopsy.core.events;

import java.io.Serializable;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * A class for events to be published to registered subscribers of Service
 * Monitor on this Autopsy node. The class extends PropertyChangeEvent (via
 * AutopsyEvent) to integrate with legacy use of JavaBeans PropertyChangeEvents
 * and PropertyChangeListeners as an application event system, and implements
 * Serializable to allow it to be published over a network in serialized form.
 */
public final class ServiceEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String details;

    public ServiceEvent(String serviceName, String status, String details) {
        super(serviceName, null, status);
        this.details = details;
    }

    /**
     * Gets details string passed as input to ServiceEvent constructor.
     *
     * @return String Details of the event.
     */
    public String getDetails() {
        return details;
    }
}
