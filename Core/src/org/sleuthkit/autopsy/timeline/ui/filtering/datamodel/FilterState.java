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
 * The state of a filter: selected, disabled, active, etc.
 *
 * @param <FilterType> The type of filter this is the state for.
 */
public interface FilterState<FilterType> {

    String getDisplayName();

    FilterType getFilter();

    FilterType getActiveFilter();

    FilterState<FilterType> copyOf();

    BooleanExpression activeProperty();

    boolean isActive();

    BooleanProperty disabledProperty();

    boolean isDisabled();

    void setDisabled(Boolean act);

    BooleanProperty selectedProperty();

    boolean isSelected();

    void setSelected(Boolean act);

}
