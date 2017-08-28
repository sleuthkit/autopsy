/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.datamodel.eventtype;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;

/**
 * An Event Type represents a distinct kind of event ie file system or web
 * activity. An EventType may have an optional super-type and 0 or more
 * subtypes, allowing events to be organized in a type hierarchy.
 */
public interface EventType {

    final static List<? extends EventType> allTypes = RootEventType.getInstance().getSubTypesRecusive();

    static Comparator<EventType> getComparator() {
        return Comparator.comparing(EventType.allTypes::indexOf);

    }

    default BaseTypes getBaseType() {
        if (this instanceof BaseTypes) {
            return (BaseTypes) this;
        } else {
            return getSuperType().getBaseType();
        }
    }

    default List<? extends EventType> getSubTypesRecusive() {
        ArrayList<EventType> flatList = new ArrayList<>();

        for (EventType et : getSubTypes()) {
            flatList.add(et);
            flatList.addAll(et.getSubTypesRecusive());
        }
        return flatList;
    }

    /**
     * @return the color used to represent this event type visually
     */
    default Color getColor() {

        Color baseColor = this.getSuperType().getColor();
        int siblings = getSuperType().getSiblingTypes().stream().max((
                EventType t, EventType t1)
                -> Integer.compare(t.getSubTypes().size(), t1.getSubTypes().size()))
                .get().getSubTypes().size() + 1;
        int superSiblings = this.getSuperType().getSiblingTypes().size();

        double offset = (360.0 / superSiblings) / siblings;
        final Color deriveColor = baseColor.deriveColor(ordinal() * offset, 1, 1, 1);

        return Color.hsb(deriveColor.getHue(), deriveColor.getSaturation(), deriveColor.getBrightness());

    }

    default List<? extends EventType> getSiblingTypes() {
        return this.getSuperType().getSubTypes();
    }

    /**
     * @return the super type of this event
     */
    public EventType getSuperType();

    public EventTypeZoomLevel getZoomLevel();

    /**
     * @return a list of event types, one for each subtype of this eventype, or
     *         an empty list if this event type has no subtypes
     */
    public List<? extends EventType> getSubTypes();

    /*
     * return the name of the icon file for this type, it will be resolved in
     * the org/sleuthkit/autopsy/timeline/images
     */
    public String getIconBase();

    public String getDisplayName();

    public EventType getSubType(String string);

    public Image getFXImage();

    public int ordinal();

}
