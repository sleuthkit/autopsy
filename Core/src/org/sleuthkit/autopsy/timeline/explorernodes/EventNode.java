/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.openide.nodes.Children;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * * Explorer Node for a SingleEvent.
 */
public class EventNode extends DisplayableItemNode {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(EventNode.class.getName());

    private final SingleEvent event;

    EventNode(SingleEvent event, AbstractFile file, BlackboardArtifact artifact) {
        super(Children.LEAF, Lookups.fixed(event, file, artifact));
        this.event = event;
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/timeline/images/" + event.getEventType().getIconBase()); // NON-NLS
    }

    EventNode(SingleEvent event, AbstractFile file) {
        super(Children.LEAF, Lookups.fixed(event, file));
        this.event = event;
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/timeline/images/" + event.getEventType().getIconBase()); // NON-NLS
    }

    @Override
    @NbBundle.Messages({
        "NodeProperty.displayName.icon=Icon",
        "NodeProperty.displayName.description=Description",
        "NodeProperty.displayName.baseType=Base Type",
        "NodeProperty.displayName.subType=Sub Type",
        "NodeProperty.displayName.known=Known",
        "NodeProperty.displayName.dateTime=Date/Time"})
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set properties = s.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            s.put(properties);
        }

        properties.put(new NodeProperty<>("icon", Bundle.NodeProperty_displayName_icon(), "icon", true)); // NON-NLS //gets overridden with icon
        properties.put(new TimeProperty("time", Bundle.NodeProperty_displayName_dateTime(), "time ", getDateTimeString()));// NON-NLS
        properties.put(new NodeProperty<>("description", Bundle.NodeProperty_displayName_description(), "description", event.getFullDescription())); // NON-NLS
        properties.put(new NodeProperty<>("eventBaseType", Bundle.NodeProperty_displayName_baseType(), "base type", event.getEventType().getSuperType().getDisplayName())); // NON-NLS
        properties.put(new NodeProperty<>("eventSubType", Bundle.NodeProperty_displayName_subType(), "sub type", event.getEventType().getDisplayName())); // NON-NLS
        properties.put(new NodeProperty<>("Known", Bundle.NodeProperty_displayName_known(), "known", event.getKnown().toString())); // NON-NLS

        return s;
    }

    /**
     * Get the time of this event as a String formated according to the
     * controller's time zone setting.
     *
     * @return The time of this event as a String formated according to the
     *         controller's time zone setting.
     */
    private String getDateTimeString() {
        return new DateTime(event.getStartMillis(), DateTimeZone.UTC).toString(TimeLineController.getZonedFormatter());
    }

    @Override
    @NbBundle.Messages({
        "EventNode.getAction.errorTitle=Error getting actions",
        "EventNode.getAction.linkedFileMessage=There was a problem getting actions for the selected result. "
        + " The 'View File in Timeline' action will not be available."})
    public Action[] getActions(boolean context) {
        Action[] superActions = super.getActions(context);
        List<Action> actionsList = new ArrayList<>();
        actionsList.addAll(Arrays.asList(superActions));

        final AbstractFile sourceFile = getLookup().lookup(AbstractFile.class);

        /*
         * if this event is derived from an artifact, add actions to view the
         * source file and a "linked" file, if present.
         */
        final BlackboardArtifact artifact = getLookup().lookup(BlackboardArtifact.class);
        if (artifact != null) {
            try {
                AbstractFile linkedfile = findLinked(artifact);
                if (linkedfile != null) {
                    actionsList.add(ViewFileInTimelineAction.createViewFileAction(linkedfile));
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error getting linked file from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
                MessageNotifyUtil.Notify.error(Bundle.EventNode_getAction_errorTitle(), Bundle.EventNode_getAction_linkedFileMessage());
            }

            //if this event  has associated content, add the action to view the content in the timeline
            if (null != sourceFile) {
                actionsList.add(ViewFileInTimelineAction.createViewSourceFileAction(sourceFile));
            }
        }

        //get default actions for the source file
        final List<Action> factoryActions = DataModelActionsFactory.getActions(sourceFile, artifact != null);

        actionsList.addAll(factoryActions);
        return actionsList.toArray(new Action[actionsList.size()]);
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> dinv) {
        throw new UnsupportedOperationException("Not supported yet."); // NON-NLS 
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    /**
     * We use TimeProperty instead of a normal NodeProperty to correctly display
     * the date/time when the user changes the timezone setting.
     */
    final private class TimeProperty extends PropertySupport.ReadWrite<String> {

        private String value;

        @Override
        public boolean canWrite() {
            return false;
        }

        TimeProperty(String name, String displayName, String shortDescription, String value) {
            super(name, String.class, displayName, shortDescription);
            setValue("suppressCustomEditor", Boolean.TRUE); // remove the "..." (editing) button NON-NLS
            this.value = value;
            TimeLineController.getTimeZone().addListener(timeZone -> {
                try {
                    setValue(getDateTimeString());
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    LOGGER.log(Level.SEVERE, "Unexpected error setting date/time property on EventNode explorer node", ex); //NON-NLS
                }
            });

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

    /**
     * Factory method to create an EventNode from the event ID and the events
     * model.
     *
     * @param eventID     The ID of the event this node is for.
     * @param eventsModel The model that provides access to the events DB.
     *
     * @return An EventNode with the file (and artifact) backing this event in
     *         its lookup.
     */
    public static EventNode createEventNode(final Long eventID, FilteredEventsModel eventsModel) throws TskCoreException, NoCurrentCaseException {
        /*
         * Look up the event by id and creata an EventNode with the appropriate
         * data in the lookup.
         */
        final SingleEvent eventById = eventsModel.getEventById(eventID);

        SleuthkitCase sleuthkitCase = Case.getOpenCase().getSleuthkitCase();
        AbstractFile file = sleuthkitCase.getAbstractFileById(eventById.getFileID());

        if (eventById.getArtifactID().isPresent()) {
            BlackboardArtifact blackboardArtifact = sleuthkitCase.getBlackboardArtifact(eventById.getArtifactID().get());
            return new EventNode(eventById, file, blackboardArtifact);
        } else {
            return new EventNode(eventById, file);
        }
    }

    /**
     * this code started as a cut and past of
     * DataResultFilterNode.GetPopupActionsDisplayableItemNodeVisitor.findLinked(BlackboardArtifactNode
     * ba)
     *
     * It is now in DisplayableItemNode too, but is not accesible across
     * packages
     *
     * @param artifact
     *
     * @return
     */
    static AbstractFile findLinked(BlackboardArtifact artifact) throws TskCoreException {

        BlackboardAttribute pathIDAttribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID));

        if (pathIDAttribute != null) {
            long contentID = pathIDAttribute.getValueLong();
            if (contentID != -1) {
                return artifact.getSleuthkitCase().getAbstractFileById(contentID);
            }
        }

        return null;
    }
}
