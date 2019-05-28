/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.imagetagging;

import java.util.EventObject;
import javafx.event.Event;
import javafx.event.EventType;

/**
 *
 * @author dsmyda
 */
public final class FocusChangeEvent extends EventObject{
    
    private final EventType<Event> type;
    private final StoredTag focused;
    
    public FocusChangeEvent(Object source, EventType<Event> type, StoredTag focused) {
        super(source);
        this.type = type;
        this.focused = focused;
    }
    
    public EventType<Event> getType() {
        return type;
    }
    
    public StoredTag getNode() {
        return focused;
    }
    
}
