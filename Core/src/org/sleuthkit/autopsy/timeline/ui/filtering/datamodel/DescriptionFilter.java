/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import java.util.Objects;
import org.sleuthkit.datamodel.TimelineEvent;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;

/**
 * Ui level filter for events that have the given description.
 */
public final class DescriptionFilter implements UIFilter {

    private final TimelineLevelOfDetail descriptionLoD;
    private final String description;

    public DescriptionFilter(TimelineLevelOfDetail descriptionLoD, String description) {
        super();
        this.descriptionLoD = descriptionLoD;
        this.description = description;
    }

    public TimelineLevelOfDetail getDescriptionLevel() {
        return descriptionLoD;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.descriptionLoD);
        hash = 23 * hash + Objects.hashCode(this.description);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DescriptionFilter other = (DescriptionFilter) obj;
        if (!Objects.equals(this.description, other.description)) {
            return false;
        }
        return this.descriptionLoD == other.descriptionLoD;
    }

    @Override
    public boolean test(TimelineEvent event) {
        return event.getDescription(descriptionLoD).equalsIgnoreCase(description);
    }
}
