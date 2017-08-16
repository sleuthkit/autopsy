/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;

/**
 * A singleton EventType to represent the root type of all event types.
 */
public class RootEventType implements EventType {

    @Override
    public List<RootEventType> getSiblingTypes() {
        return Collections.singletonList(this);
    }

    @Override
    public EventTypeZoomLevel getZoomLevel() {
        return EventTypeZoomLevel.ROOT_TYPE;
    }

    private RootEventType() {
    }

    public static RootEventType getInstance() {
        return RootEventTypeHolder.INSTANCE;
    }

    @Override
    public EventType getSubType(String string) {
        return BaseTypes.valueOf(string);
    }

    @Override
    public int ordinal() {
        return 0;
    }

    private static class RootEventTypeHolder {

        private static final RootEventType INSTANCE = new RootEventType();

        private RootEventTypeHolder() {
        }
    }

    @Override
    public Color getColor() {
        return Color.hsb(359, .9, .9, 0);
    }

    @Override
    public RootEventType getSuperType() {
        return this;
    }

    @Override
    public List<BaseTypes> getSubTypes() {
        return Arrays.asList(BaseTypes.values());
    }

    @Override
    public String getIconBase() {
        throw new UnsupportedOperationException("Not supported yet."); // NON-NLS //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(this.getClass(), "RootEventType.eventTypes.name");
    }

    @Override
    public Image getFXImage() {
        return null;
    }
}
