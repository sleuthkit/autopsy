/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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

import org.sleuthkit.datamodel.TimelineFilter;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;

/**
 * A wrapper for a TimelineFilter.TextFilter object that allows it to be
 * displayed by the timeline GUI via the filter panel by providing selected,
 * disabled, and active properties for the TextFilter. The description substring
 * of the TextFilter is also exposed as a JFX property so that it can be bound
 * to the text filter text field of the filter panel.
 */
public class TextFilterState extends SqlFilterState<TimelineFilter.TextFilter> {

    private final SimpleStringProperty descriptionSubstring;

    /**
     * Constructs a wrapper for a TimelineFilter.TextFilter object that allows
     * it to be displayed by the timeline GUI via filter panel by providing
     * selected, disabled, and active properties for the TextFilter. The
     * description substring of the TextFilter is also exposed as a JFX property
     * so that it can be bound to the text filter text field of the filter
     * panel.
     *
     * @param textFilter A TimelineFilter.TextFilter object.
     */
    public TextFilterState(TimelineFilter.TextFilter textFilter) {
        super(textFilter);
        this.descriptionSubstring = new SimpleStringProperty(textFilter.getDescriptionSubstring());
    }

    /**
     * "Copy constructs" a wrapper for a TimelineFilter.TextFilter object
     * that allows it to be displayed by the timeline GUI via the filter panel
     * by providing selected, disabled, and active properties for the
     * TextFilter.
     *
     * @param other A HashHitsFilterState object.
     */
    public TextFilterState(TextFilterState other) {
        super(other.getFilter().copyOf());
        setSelected(other.isSelected());
        setDisabled(other.isDisabled());
        this.descriptionSubstring = new SimpleStringProperty(other.getFilter().getDescriptionSubstring());
    }     
    
    @Override
    public TimelineFilter.TextFilter getFilter() {
        TimelineFilter.TextFilter textFilter = super.getFilter();
        textFilter.setDescriptionSubstring(descriptionSubstringProperty().getValue());
        return textFilter;
    }

    /**
     * Gets the substring that must be present in one or more of the
     * descriptions of each event that passes the filter. The property is
     * intended to be bound to the text filter text field of the filter panel in
     * the timeline GUI.
     *
     * @return The required substring as a Property.
     */
    public Property<String> descriptionSubstringProperty() {
        return descriptionSubstring;
    }
       
}
