/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.filters;

import java.util.function.Predicate;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;

/**
 * Interface for Filters
 */
public interface Filter extends Predicate<EventBundle> {

    /**
     * @param filters a set of filters to intersect
     *
     * @return a filter that is the intersection of the given filters
     */
    public static IntersectionFilter<Filter> intersect(ObservableList<Filter> filters) {
        return new IntersectionFilter<>(filters);
    }

    /**
     * @param filters a set of filters to intersect
     *
     * @return a filter that is the intersection of the given filters
     */
    public static IntersectionFilter<Filter> intersect(Filter[] filters) {
        return new IntersectionFilter<>(FXCollections.observableArrayList(filters));
    }

    /**
     * since filters have mutable state (active) and are observed in various
     * places, we need a mechanism to copy the current state to keep in history.
     *
     * Concrete subtasks should implement this in a way that preserves the
     * active state and any subfilters.
     *
     * @return a copy of this filter.
     */
    Filter copyOf();

    String getDisplayName();

    String getHTMLReportString();

    String getStringCheckBox();

    boolean isSelected();

    void setSelected(Boolean act);

    SimpleBooleanProperty getSelectedProperty();

    /*
     * TODO: disabled state only affects the state of the checkboxes in the ui
     * and not the actual filters and shouldn't be implemented here, but it was
     * too hard to figure out how it should be implemented without intruding on
     * the ui-ignorant filters
     */
    void setDisabled(Boolean act);

    SimpleBooleanProperty getDisabledProperty();

    boolean isDisabled();
    
    
    
}
