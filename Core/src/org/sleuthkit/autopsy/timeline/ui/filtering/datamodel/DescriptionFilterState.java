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

/**
 * A FilterState implementation for DescriptionFilters
 */
public class DescriptionFilterState extends AbstractFilterState<DescriptionFilter> {

    public DescriptionFilterState(DescriptionFilter filter) {
        this(filter, false);
    }

    public DescriptionFilterState(DescriptionFilter filter, boolean selected) {
        super(filter, selected);
    }

    @Override
    public String getDisplayName() {
        return filter.getDescription();
    }

    @Override
    public DescriptionFilter getFilter() {
        return filter;
    }

    @Override
    public DescriptionFilter getActiveFilter() {
        return isActive() ? getFilter() : null;
    }

    @Override
    public DescriptionFilterState copyOf() {
        DescriptionFilterState copy = new DescriptionFilterState(filter);
        copy.setSelected(isSelected());
        copy.setDisabled(isDisabled());
        return copy;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.filter);
        hash = 37 * hash + Objects.hashCode(this.selected);
        hash = 37 * hash + Objects.hashCode(this.disabled);
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
        final DescriptionFilterState other = (DescriptionFilterState) obj;
        if (!Objects.equals(this.filter, other.filter)) {
            return false;
        }
        if (!Objects.equals(this.isSelected(), other.isSelected())) {
            return false;
        }
        if (!Objects.equals(this.isDisabled(), other.isDisabled())) {
            return false;
        }
        return true;
    }

}
