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
package org.sleuthkit.autopsy.timeline.explorernodes;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.beans.Observable;
import javax.swing.Action;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.openide.nodes.Children;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.events.TimeLineEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;

/** * Explorer Node for {@link TimeLineEvent}s. */
class EventNode extends DisplayableItemNode {

    private final TimeLineEvent e;

    EventNode(TimeLineEvent eventById, AbstractFile file, BlackboardArtifact artifact) {
        super(Children.LEAF, Lookups.fixed(eventById, file, artifact));
        this.e = eventById;
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/timeline/images/" + e.getType().getIconBase()); // NON-NLS
    }

    EventNode(TimeLineEvent eventById, AbstractFile file) {
        super(Children.LEAF, Lookups.fixed(eventById, file));
        this.e = eventById;
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/timeline/images/" + e.getType().getIconBase()); // NON-NLS
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set properties = s.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            s.put(properties);
        }

        final TimeProperty timePropery = new TimeProperty("time", "Date/Time", "time ", getDateTimeString()); // NON-NLS

        TimeLineController.getTimeZone().addListener((Observable observable) -> {
            try {
                timePropery.setValue(getDateTimeString());
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Exceptions.printStackTrace(ex);
            }
        });

        properties.put(new NodeProperty<>("icon", "Icon", "icon", true)); // NON-NLS //gets overridden with icon
        properties.put(timePropery);
        properties.put(new NodeProperty<>("description", "Description", "description", e.getFullDescription())); // NON-NLS
        properties.put(new NodeProperty<>("eventBaseType", "Base Type", "base type", e.getType().getSuperType().getDisplayName())); // NON-NLS
        properties.put(new NodeProperty<>("eventSubType", "Sub Type", "sub type", e.getType().getDisplayName())); // NON-NLS
        properties.put(new NodeProperty<>("Known", "Known", "known", e.getKnown().toString())); // NON-NLS

        return s;
    }

    private String getDateTimeString() {
        return new DateTime(e.getTime() * 1000, DateTimeZone.UTC).toString(TimeLineController.getZonedFormatter());
    }

    @Override
    public Action[] getActions(boolean context) {
        Action[] superActions = super.getActions(context);
        List<Action> actionsList = new ArrayList<>();
        actionsList.addAll(Arrays.asList(superActions));

        final Content content = getLookup().lookup(Content.class);
        final BlackboardArtifact artifact = getLookup().lookup(BlackboardArtifact.class);

        final List<Action> factoryActions = DataModelActionsFactory.getActions(content, artifact != null);

        actionsList.addAll(factoryActions);
        return actionsList.toArray(new Action[0]);
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> dinv) {
        throw new UnsupportedOperationException("Not supported yet."); // NON-NLS //To change body of generated methods, choose Tools | Templates.
    }

    class TimeProperty extends PropertySupport.ReadWrite<String> {

        private String value;

        @Override
        public boolean canWrite() {
            return false;
        }

        public TimeProperty(String name, String displayName, String shortDescription, String value) {
            super(name, String.class, displayName, shortDescription);
            setValue("suppressCustomEditor", Boolean.TRUE); // remove the "..." (editing) button NON-NLS
            this.value = value;
        }

        @Override
        public String getValue() throws IllegalAccessException, InvocationTargetException {
            return value;
        }

        @Override
        public void setValue(String t) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            String oldValue = getValue();
            value = t;
            firePropertyChange("time", oldValue, t); // NON-NLS
        }
    }
}
