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
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Tag;

/**
 * Base Class for events that are fired when a Tag is deleted
 */
@Immutable
abstract class TagDeletedEvent<T extends Tag> extends PropertyChangeEvent {

    protected TagDeletedEvent(String propertyName, T oldValue) {
        super(Case.class, propertyName, oldValue, null);
    }

    /**
     * get the Tag that was deleted
     *
     * @return the Tag
     */
    @SuppressWarnings("unchecked")
    public T getDeletedTag() {
        return (T) getOldValue();
    }
}
