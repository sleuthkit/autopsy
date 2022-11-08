/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.swing.Action;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.openide.nodes.Children;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.autopsy.timeline.ui.EventTypeUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineEvent;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;

/**
 * * Explorer Node for a TimelineEvent.
 */
public class EventNode extends DisplayableItemNode {

    private static final Logger logger = Logger.getLogger(EventNode.class.getName());

    private final TimelineEvent event;

    /**
     * Construct an EvetNode for an event with a Content and a
     * BlackboardArtifact in its lookup.
     *
     * @param event    The event this node is for.
     * @param file     The Content the artifact for this event is derived form.
     *                 Not Null.
     * @param artifact The artifact this event is derived from. Not Null.
     */
    EventNode(@Nonnull TimelineEvent event, @Nonnull Content file, @Nonnull BlackboardArtifact artifact) {
        super(Children.LEAF, Lookups.fixed(event, file, artifact));
        this.event = event;
        TimelineEventType evenType = event.getEventType();
        this.setIconBaseWithExtension(EventTypeUtils.getImagePath(evenType));
    }

    /**
     * Construct an EvetNode for an event with a Content in its lookup.
     *
     * @param event The event this node is for.
     * @param file  The Content this event is derived directly from. Not Null.
     */
    EventNode(@Nonnull TimelineEvent event, @Nonnull Content file) {
        super(Children.LEAF, Lookups.fixed(event, file));
        this.event = event;
        TimelineEventType evenType = event.getEventType();
        this.setIconBaseWithExtension(EventTypeUtils.getImagePath(evenType));
    }

    @Override
    @NbBundle.Messages({
        "NodeProperty.displayName.icon=Icon",
        "NodeProperty.displayName.description=Description",
        "NodeProperty.displayName.eventType=Event Type",
        "NodeProperty.displayName.known=Known",
        "NodeProperty.displayName.dateTime=Date/Time"})
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set properties = sheet.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            sheet.put(properties);
        }

        properties.put(new NodeProperty<>("icon", Bundle.NodeProperty_displayName_icon(), "icon", true)); // NON-NLS //gets overridden with icon
        properties.put(new TimeProperty("time", Bundle.NodeProperty_displayName_dateTime(), "time ", getDateTimeString()));// NON-NLS
        properties.put(new NodeProperty<>("description", Bundle.NodeProperty_displayName_description(), "description", event.getDescription(TimelineLevelOfDetail.HIGH))); // NON-NLS
        properties.put(new NodeProperty<>("eventType", Bundle.NodeProperty_displayName_eventType(), "event type", event.getEventType().getDisplayName())); // NON-NLS

        return sheet;
    }

    /**
     * Get the time of this event as a String formated according to the
     * controller's time zone setting.
     *
     * @return The time of this event as a String formated according to the
     *         controller's time zone setting.
     */
    private String getDateTimeString() {
        return new DateTime(event.getEventTimeInMs(), DateTimeZone.UTC).toString(TimeLineController.getZonedFormatter());
    }

    @Override
    @NbBundle.Messages({
        "EventNode.getAction.errorTitle=Error getting actions",
        "EventNode.getAction.linkedFileMessage=There was a problem getting actions for the selected result. "
        + " The 'View File in Timeline' action will not be available."})
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<>();
        Collections.addAll(actionsList, super.getActions(context));
        /*
         * If this event is derived from an artifact, add actions to view the
         * source file and a "linked" file, if present.
         */
        final BlackboardArtifact artifact = getLookup().lookup(BlackboardArtifact.class);
        final Content sourceFile = getLookup().lookup(Content.class);
        if (artifact != null) {
            try {
                //find a linked file such as a downloaded file.
                AbstractFile linkedfile = findLinked(artifact);
                if (linkedfile != null) {
                    actionsList.add(ViewFileInTimelineAction.createViewFileAction(linkedfile));
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, MessageFormat.format("Error getting linked file from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
                MessageNotifyUtil.Notify.error(Bundle.EventNode_getAction_errorTitle(), Bundle.EventNode_getAction_linkedFileMessage());
            }

            //add the action to view the content in the timeline, only for abstract files ( ie with times)
            if (sourceFile instanceof AbstractFile) {
                actionsList.add(ViewFileInTimelineAction.createViewSourceFileAction((AbstractFile) sourceFile));
            }
        }

        //get default actions for the source file
        List<Action> factoryActions = DataModelActionsFactory.getActions(sourceFile, artifact != null);
        actionsList.addAll(factoryActions);
        if (factoryActions.isEmpty()) { // if there were no factory supplied actions, at least add the tagging actions.
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
            if (isExactlyOneArtifactSelected()) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
            actionsList.addAll(ContextMenuExtensionPoint.getActions());
        }
        return actionsList.toArray(new Action[actionsList.size()]);
    }

    /**
     * Gets the file, if any, linked to an artifact via a TSK_PATH_ID attribute
     *
     * @param artifact The artifact.
     *
     * @return An AbstractFile or null.
     *
     * @throws TskCoreException
     */
    private static AbstractFile findLinked(BlackboardArtifact artifact) throws TskCoreException {
        BlackboardAttribute pathIDAttribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID));
        if (pathIDAttribute != null) {
            long contentID = pathIDAttribute.getValueLong();
            if (contentID != -1) {
                return artifact.getSleuthkitCase().getAbstractFileById(contentID);
            }
        }
        return null;
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
            TimeLineController.timeZoneProperty().addListener(timeZone -> {
                try {
                    setValue(getDateTimeString());
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    logger.log(Level.SEVERE, "Unexpected error setting date/time property on EventNode explorer node", ex); //NON-NLS
                }
            });

        }

        @Override
        public String getValue() throws IllegalAccessException, InvocationTargetException {
            return value;
        }

        @Override
        public void setValue(String newValue) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            String oldValue = getValue();
            value = newValue;
            firePropertyChange("time", oldValue, newValue); // NON-NLS
        }
    }

    /**
     * Factory method to create an EventNode from the event ID and the events
     * model.
     *
     * @param eventID     The ID of the event this node is for.
     * @param eventsModel The model that provides access to the events DB.
     *
     * @return An EventNode with the content (and possible artifact) backing
     *         this event in its lookup.
     */
    public static EventNode createEventNode(final Long eventID, EventsModel eventsModel) throws TskCoreException {

        SleuthkitCase sleuthkitCase = eventsModel.getSleuthkitCase();

        /*
         * Look up the event by id and creata an EventNode with the appropriate
         * data in the lookup.
         */
            final TimelineEvent eventById = eventsModel.getEventById(eventID);
        Content file = sleuthkitCase.getContentById(eventById.getContentObjID());

        if (eventById.getArtifactID().isPresent()) {
            BlackboardArtifact blackboardArtifact = sleuthkitCase.getBlackboardArtifact(eventById.getArtifactID().get());
            return new EventNode(eventById, file, blackboardArtifact);
        } else {
            return new EventNode(eventById, file);
        }
    }

    private static boolean isExactlyOneArtifactSelected() {
        final Collection<BlackboardArtifact> selectedArtifactsList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
        return selectedArtifactsList.size() == 1;
    }
}
