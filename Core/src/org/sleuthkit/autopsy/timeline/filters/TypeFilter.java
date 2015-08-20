/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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

import java.util.Objects;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.events.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.events.type.EventType;
import org.sleuthkit.autopsy.timeline.events.type.RootEventType;

/**
 * Event Type Filter. An instance of TypeFilter is usually a tree that parallels
 * the event type hierarchy with one filter/node for each event type.
 */
public class TypeFilter extends UnionFilter<TypeFilter> {

    /**
     * the event type this filter passes
     */
    private final EventType eventType;

    /**
     * private constructor that enables non recursive/tree construction of the
     * filter hierarchy for use in {@link TypeFilter#copyOf()}.
     *
     * @param et        the event type this filter passes
     * @param recursive true if subfilters should be added for each subtype.
     *                  False if no subfilters should be added.
     */
    private TypeFilter(EventType et, boolean recursive) {
        super(FXCollections.observableArrayList());
        this.eventType = et;

        if (recursive) { // add subfilters for each subtype
            for (EventType subType : et.getSubTypes()) {
                this.getSubFilters().add(new TypeFilter(subType));
            }
        }
    }

    /**
     * public constructor. creates a subfilter for each subtype of the given
     * event type
     *
     * @param et the event type this filter will pass
     */
    public TypeFilter(EventType et) {
        this(et, true);
    }

    public EventType getEventType() {
        return eventType;
    }

    @Override
    @NbBundle.Messages("TypeFilter.displayName.text=Event Type Filter")
    public String getDisplayName() {
        return (eventType == RootEventType.getInstance())
                ? Bundle.TypeFilter_displayName_text()
                : eventType.getDisplayName();
    }

    /**
     * @return a color to use in GUI components representing this filter
     */
    public Color getColor() {
        return eventType.getColor();
    }

    /**
     * @return an image to use in GUI components representing this filter
     */
    public Image getFXImage() {
        return eventType.getFXImage();
    }

    @Override
    public TypeFilter copyOf() {
        //make a nonrecursive copy of this filter
        final TypeFilter typeFilter = new TypeFilter(eventType, false);
        typeFilter.setSelected(isSelected());
        typeFilter.setDisabled(isDisabled());
        //add a copy of each subfilter
        this.getSubFilters().forEach((TypeFilter t) -> {
            typeFilter.getSubFilters().add(t.copyOf());
        });

        return typeFilter;
    }

    @Override
    public String getHTMLReportString() {
        String string = getEventType().getDisplayName() + getStringCheckBox();
        if (getSubFilters().isEmpty() == false) {
            string = string + " : " + getSubFilters().stream().filter(Filter::isSelected).map(Filter::getHTMLReportString).collect(Collectors.joining("</li><li>", "<ul><li>", "</li></ul>")); // NON-NLS
        }
        return string;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TypeFilter other = (TypeFilter) obj;

        if (isSelected() != other.isSelected()) {
            return false;
        }

        if (this.eventType != other.eventType) {
            return false;
        }
        return areSubFiltersEqual(this, other);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.eventType);
        return hash;
    }

    @Override
    public boolean test(TimeLineEvent t) {
        boolean name = !isSelected() || isDisabled() || eventType.equals(t.getType())
                || getSubFilters().stream().anyMatch((TypeFilter f) ->  f.test(t));
             System.out.println(t.toString() + (name ? " passed " : " failed ") + getDisplayName() + "filter ");
        return name;
    }
}
