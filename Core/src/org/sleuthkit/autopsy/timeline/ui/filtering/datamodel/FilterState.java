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

import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;

/**
 * An interface for filter state classes. Filter state classes are wrappers that
 * adapt a timeline data model filtering object for display by the timeline GUI
 * by providing selected, disabled, and active properties for the wrapped
 * filter. A filter that is selected and not disabled is active.
 *
 * @param <FilterType> The type of the wrapped filter.
 */
public interface FilterState<FilterType> {

    /**
     * Gets the display name that will be used to identify the wrapped filter in
     * the timeline GUI.
     *
     * @return The display name of the wrapped filter.
     */
    String getDisplayName();

    /**
     * Gets the wrapped filter.
     *
     * @return The wrapped filter.
     */
    FilterType getFilter();

    /**
     * Gets the wrapped filter only if it is active. A filter that is selected
     * and not disabled is active. If the wrapped filter is not active, null is
     * returned.
     *
     * @return The wrapped filter or null.
     */
    FilterType getActiveFilter();

    /**
     * Makes a deep copy of this filter state object.
     *
     * @return The copy.
     */
    FilterState<FilterType> copyOf();

    /**
     * Gets the active property of this filter state object. A filter that is
     * selected and not disabled is active.
     *
     * @return The active property.
     */
    BooleanExpression activeProperty();

    /**
     * Gets the value of the active property of this filter state object. A
     * filter that is selected and not disabled is active.
     *
     * @return True or false.
     */
    boolean isActive();

    /**
     * Gets the disabled property of this filter state object.
     *
     * @return The disabled property.
     */
    BooleanProperty disabledProperty();

    /**
     * Gets the value of the disabled property of this filter state object.
     *
     * @return True or false.
     */
    boolean isDisabled();

    /**
     * Sets the value of the disabled property of this filter state object.
     *
     * @param value True or false.
     */
    void setDisabled(Boolean value);

    /**
     * Gets the selected property of this filter state object.
     *
     * @return The selected property.
     */
    BooleanProperty selectedProperty();

    /**
     * Gets the value of the selected property of this filter state object.
     *
     * @return True or false.
     */
    boolean isSelected();

    /**
     * Sets the value of the selected property of this filter state object.
     *
     * @param value True or false.
     */
    void setSelected(Boolean value);

}
