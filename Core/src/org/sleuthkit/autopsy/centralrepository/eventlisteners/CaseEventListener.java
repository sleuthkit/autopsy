/*
 * Central Repository
 *
 * Copyright 2017-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.eventlisteners;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceNameChangedEvent;
import org.sleuthkit.autopsy.casemodule.events.OsAcctInstancesAddedEvent;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.ThreadUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_OTHER_CASES;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CORRELATION_TYPE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CORRELATION_VALUE;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountInstance;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Listen for case events and update entries in the Central Repository database
 * accordingly
 */
@Messages({"caseeventlistener.evidencetag=Evidence"})
public final class CaseEventListener implements PropertyChangeListener {

    private static final Logger LOGGER = Logger.getLogger(CaseEventListener.class.getName());
    private final ExecutorService jobProcessingExecutor;
    private static final String CASE_EVENT_THREAD_NAME = "Case-Event-Listener-%d";

    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(
            Case.Events.CONTENT_TAG_ADDED, Case.Events.CONTENT_TAG_DELETED,
            Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED, Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED,
            Case.Events.CONTENT_TAG_ADDED, Case.Events.CONTENT_TAG_DELETED,
            Case.Events.DATA_SOURCE_ADDED,
            Case.Events.TAG_DEFINITION_CHANGED,
            Case.Events.CURRENT_CASE,
            Case.Events.DATA_SOURCE_NAME_CHANGED,
            Case.Events.OS_ACCT_INSTANCES_ADDED);

    public CaseEventListener() {
        jobProcessingExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(CASE_EVENT_THREAD_NAME).build());
    }

    public void shutdown() {
        ThreadUtils.shutDownTaskExecutor(jobProcessingExecutor);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!(evt instanceof AutopsyEvent) || (((AutopsyEvent) evt).getSourceType() != AutopsyEvent.SourceType.LOCAL)) {
            return;
        }

        CentralRepository dbManager;
        try {
            dbManager = CentralRepository.getInstance();
        } catch (CentralRepoException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get instance of db manager.", ex);
            return;
        }

        // If any changes are made to which event types are handled the change 
        // must also be made to CASE_EVENTS_OF_INTEREST.
        switch (Case.Events.valueOf(evt.getPropertyName())) {
            case CONTENT_TAG_ADDED:
            case CONTENT_TAG_DELETED: {
                jobProcessingExecutor.submit(new ContentTagTask(dbManager, evt));
            }
            break;

            case BLACKBOARD_ARTIFACT_TAG_DELETED:
            case BLACKBOARD_ARTIFACT_TAG_ADDED: {
                jobProcessingExecutor.submit(new BlackboardTagTask(dbManager, evt));
            }
            break;

            case DATA_SOURCE_ADDED: {
                jobProcessingExecutor.submit(new DataSourceAddedTask(dbManager, evt));
            }
            break;
            case TAG_DEFINITION_CHANGED: {
                jobProcessingExecutor.submit(new TagDefinitionChangeTask(evt));
            }
            break;
            case CURRENT_CASE: {
                jobProcessingExecutor.submit(new CurrentCaseTask(dbManager, evt));
            }
            break;
            case DATA_SOURCE_NAME_CHANGED: {
                jobProcessingExecutor.submit(new DataSourceNameChangedTask(dbManager, evt));
            }
            break;
            case OS_ACCT_INSTANCES_ADDED: {
                if (((AutopsyEvent) evt).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                    jobProcessingExecutor.submit(new OsAccountInstancesAddedTask(dbManager, evt));
                }
            }
            break;
        }
    }

    /*
     * Add all of our Case Event Listeners to the case.
     */
    public void installListeners() {
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, this);
    }

    /*
     * Remove all of our Case Event Listeners from the case.
     */
    public void uninstallListeners() {
        Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, this);
    }

    /**
     * Returns true if the tag has a notable status.
     *
     * @param t The tag to use in determination.
     *
     * @return Whether or not it is a notable tag.
     */
    private static boolean isNotableTag(Tag t) {
        return (t != null && isNotableTagName(t.getName()));
    }

    /**
     * Returns true if the tag name has a notable status.
     *
     * @param t The tag name to use in determination.
     *
     * @return Whether or not it is a notable tag name.
     */
    private static boolean isNotableTagName(TagName t) {
        return (t != null && TagsManager.getNotableTagDisplayNames().contains(t.getDisplayName()));
    }

    /**
     * Searches a list of tags for a tag with a notable status.
     *
     * @param tags The tags to search.
     *
     * @return Whether or not the list contains a notable tag.
     */
    private static boolean hasNotableTag(List<? extends Tag> tags) {
        if (tags == null) {
            return false;
        }

        return tags.stream()
                .filter(CaseEventListener::isNotableTag)
                .findFirst()
                .isPresent();
    }

    /**
     * Sets the known status of a blackboard artifact in the central repository.
     *
     * @param dbManager   The central repo database.
     * @param bbArtifact  The blackboard artifact to set known status.
     * @param knownStatus The new known status.
     */
    private static void setArtifactKnownStatus(CentralRepository dbManager, BlackboardArtifact bbArtifact, TskData.FileKnown knownStatus) {
        List<CorrelationAttributeInstance> convertedArtifacts = new ArrayList<>();
        if (bbArtifact instanceof DataArtifact) {
            convertedArtifacts.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((DataArtifact) bbArtifact));
        } else if (bbArtifact instanceof AnalysisResult) {
            convertedArtifacts.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((AnalysisResult) bbArtifact));
        }
        for (CorrelationAttributeInstance eamArtifact : convertedArtifacts) {
            try {
                dbManager.setAttributeInstanceKnownStatus(eamArtifact, knownStatus);
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, "Error connecting to Central Repository database while setting artifact known status.", ex); //NON-NLS
            }
        }
    }

    private final class ContentTagTask implements Runnable {

        private final CentralRepository dbManager;
        private final PropertyChangeEvent event;

        private ContentTagTask(CentralRepository db, PropertyChangeEvent evt) {
            dbManager = db;
            event = evt;
        }

        @Override
        public void run() {
            if (!CentralRepository.isEnabled()) {
                return;
            }

            Case.Events curEventType = Case.Events.valueOf(event.getPropertyName());
            if (curEventType == Case.Events.CONTENT_TAG_ADDED && event instanceof ContentTagAddedEvent) {
                handleTagAdded((ContentTagAddedEvent) event);
            } else if (curEventType == Case.Events.CONTENT_TAG_DELETED && event instanceof ContentTagDeletedEvent) {
                handleTagDeleted((ContentTagDeletedEvent) event);
            } else {
                LOGGER.log(Level.SEVERE,
                        String.format("Received an event %s of type %s and was expecting either CONTENT_TAG_ADDED or CONTENT_TAG_DELETED.",
                                event, curEventType));
            }
        }

        private void handleTagDeleted(ContentTagDeletedEvent evt) {
            // ensure tag deleted event has a valid content id
            if (evt.getDeletedTagInfo() == null) {
                LOGGER.log(Level.SEVERE, "ContentTagDeletedEvent did not have valid content to provide a content id.");
                return;
            }

            try {
                // obtain content
                Content content = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(evt.getDeletedTagInfo().getContentID());
                if (content == null) {
                    LOGGER.log(Level.WARNING,
                            String.format("Unable to get content for item with content id: %d.", evt.getDeletedTagInfo().getContentID()));
                    return;
                }

                // then handle the event 
                handleTagChange(content);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                LOGGER.log(Level.WARNING, "Error updating non-file object: " + evt.getDeletedTagInfo().getContentID(), ex);
            }
        }

        private void handleTagAdded(ContentTagAddedEvent evt) {
            // ensure tag added event has a valid content id
            if (evt.getAddedTag() == null || evt.getAddedTag().getContent() == null) {
                LOGGER.log(Level.SEVERE, "ContentTagAddedEvent did not have valid content to provide a content id.");
                return;
            }

            // then handle the event
            handleTagChange(evt.getAddedTag().getContent());
        }

        /**
         * When a tag is added or deleted, check if there are other notable tags
         * for the item. If there are, set known status as notable. If not set
         * status as unknown.
         *
         * @param content The content for the tag that was added or deleted.
         */
        private void handleTagChange(Content content) {
            AbstractFile af = null;
            try {
                af = Case.getCurrentCaseThrows().getSleuthkitCase().getAbstractFileById(content.getId());
            } catch (NoCurrentCaseException | TskCoreException ex) {
                Long contentID = (content != null) ? content.getId() : null;
                LOGGER.log(Level.WARNING, "Error updating non-file object: " + contentID, ex);
            }

            if (af == null) {
                return;
            }

            try {
                // Get the tags on the content object
                TagsManager tagsManager = Case.getCurrentCaseThrows().getServices().getTagsManager();

                if (hasNotableTag(tagsManager.getContentTagsByContent(content))) {
                    // if there is a notable tag on the object, set content known status to bad
                    setContentKnownStatus(af, TskData.FileKnown.BAD);
                } else {
                    // otherwise, set to unknown
                    setContentKnownStatus(af, TskData.FileKnown.UNKNOWN);
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                LOGGER.log(Level.SEVERE, "Failed to obtain tags manager for case.", ex);
            }
        }

        /**
         * Sets the known status for the correlation attribute instance for the
         * given abstract file.
         *
         * @param af          The abstract file for which to set the correlation
         *                    attribute instance.
         * @param knownStatus The new known status for the correlation attribute
         *                    instance.
         */
        private void setContentKnownStatus(AbstractFile af, TskData.FileKnown knownStatus) {
            final List<CorrelationAttributeInstance> md5CorrelationAttr = CorrelationAttributeUtil.makeCorrAttrsForSearch(af);
            if (!md5CorrelationAttr.isEmpty()) {
                //for an abstract file the 'list' of attributes will be a single attribute or empty and is returning a list for consistency with other makeCorrAttrsForSearch methods per 7852 
                // send update to Central Repository db
                try {
                    dbManager.setAttributeInstanceKnownStatus(md5CorrelationAttr.get(0), knownStatus);
                } catch (CentralRepoException ex) {
                    LOGGER.log(Level.SEVERE, "Error connecting to Central Repository database while setting artifact known status.", ex); //NON-NLS
                }
            }
        }
    }

    private final class BlackboardTagTask implements Runnable {

        private final CentralRepository dbManager;
        private final PropertyChangeEvent event;

        private BlackboardTagTask(CentralRepository db, PropertyChangeEvent evt) {
            dbManager = db;
            event = evt;
        }

        @Override
        public void run() {
            if (!CentralRepository.isEnabled()) {
                return;
            }

            Case.Events curEventType = Case.Events.valueOf(event.getPropertyName());
            if (curEventType == Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED && event instanceof BlackBoardArtifactTagAddedEvent) {
                handleTagAdded((BlackBoardArtifactTagAddedEvent) event);
            } else if (curEventType == Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED && event instanceof BlackBoardArtifactTagDeletedEvent) {
                handleTagDeleted((BlackBoardArtifactTagDeletedEvent) event);
            } else {
                LOGGER.log(Level.WARNING,
                        String.format("Received an event %s of type %s and was expecting either CONTENT_TAG_ADDED or CONTENT_TAG_DELETED.",
                                event, curEventType));
            }
        }

        private void handleTagDeleted(BlackBoardArtifactTagDeletedEvent evt) {
            // ensure tag deleted event has a valid content id
            if (evt.getDeletedTagInfo() == null) {
                LOGGER.log(Level.SEVERE, "BlackBoardArtifactTagDeletedEvent did not have valid content to provide a content id.");
                return;
            }

            try {
                Case openCase = Case.getCurrentCaseThrows();

                // obtain content
                Content content = openCase.getSleuthkitCase().getContentById(evt.getDeletedTagInfo().getContentID());
                if (content == null) {
                    LOGGER.log(Level.WARNING,
                            String.format("Unable to get content for item with content id: %d.", evt.getDeletedTagInfo().getContentID()));
                    return;
                }

                // obtain blackboard artifact
                BlackboardArtifact bbArtifact = openCase.getSleuthkitCase().getBlackboardArtifact(evt.getDeletedTagInfo().getArtifactID());
                if (bbArtifact == null) {
                    LOGGER.log(Level.WARNING,
                            String.format("Unable to get blackboard artifact for item with artifact id: %d.", evt.getDeletedTagInfo().getArtifactID()));
                    return;
                }

                // then handle the event 
                handleTagChange(content, bbArtifact);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                LOGGER.log(Level.WARNING, "Error updating non-file object.", ex);
            }
        }

        private void handleTagAdded(BlackBoardArtifactTagAddedEvent evt) {
            // ensure tag added event has a valid content id
            if (evt.getAddedTag() == null || evt.getAddedTag().getContent() == null || evt.getAddedTag().getArtifact() == null) {
                LOGGER.log(Level.SEVERE, "BlackBoardArtifactTagAddedEvent did not have valid content to provide a content id.");
                return;
            }

            // then handle the event
            handleTagChange(evt.getAddedTag().getContent(), evt.getAddedTag().getArtifact());
        }

        /**
         * When a tag is added or deleted, check if there are other notable tags
         * for the item. If there are, set known status as notable. If not set
         * status as unknown.
         *
         * @param content    The content for the tag that was added or deleted.
         * @param bbArtifact The artifact for the tag that was added or deleted.
         */
        private void handleTagChange(Content content, BlackboardArtifact bbArtifact) {
            Case openCase;
            try {
                openCase = Case.getCurrentCaseThrows();
            } catch (NoCurrentCaseException ex) {
                LOGGER.log(Level.SEVERE, "Exception while getting open case.", ex);
                return;
            }

            try {
                if (isKnownFile(content)) {
                    return;
                }

                TagsManager tagsManager = openCase.getServices().getTagsManager();
                List<BlackboardArtifactTag> tags = tagsManager.getBlackboardArtifactTagsByArtifact(bbArtifact);
                if (hasNotableTag(tags)) {
                    setArtifactKnownStatus(dbManager, bbArtifact, TskData.FileKnown.BAD);
                } else {
                    setArtifactKnownStatus(dbManager, bbArtifact, TskData.FileKnown.UNKNOWN);
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Failed to obtain tags manager for case.", ex);
            }
        }

        /**
         * Determines if the content is an abstract file and is a known file.
         *
         * @param content The content to assess.
         *
         * @return True if an abstract file and a known file.
         */
        private boolean isKnownFile(Content content) {
            return ((content instanceof AbstractFile) && (((AbstractFile) content).getKnown() == TskData.FileKnown.KNOWN));
        }

    }

    private final class TagDefinitionChangeTask implements Runnable {

        private final PropertyChangeEvent event;

        private TagDefinitionChangeTask(PropertyChangeEvent evt) {
            event = evt;
        }

        @Override
        public void run() {
            if (!CentralRepository.isEnabled()) {
                return;
            }
            //get the display name of the tag that has had it's definition modified
            String modifiedTagName = (String) event.getOldValue();

            /*
             * Set knownBad status for all files/artifacts in the given case
             * that are tagged with the given tag name.
             */
            try {
                TagName tagName = Case.getCurrentCaseThrows().getServices().getTagsManager().getDisplayNamesToTagNamesMap().get(modifiedTagName);
                //First update the artifacts
                //Get all BlackboardArtifactTags with this tag name
                List<BlackboardArtifactTag> artifactTags = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifactTagsByTagName(tagName);
                for (BlackboardArtifactTag bbTag : artifactTags) {
                    //start with assumption that none of the other tags applied to this Correlation Attribute will prevent it's status from being changed
                    boolean hasTagWithConflictingKnownStatus = false;
                    // if the status of the tag has been changed to TskData.FileKnown.UNKNOWN
                    // we need to check the status of all other tags on this correlation attribute before changing
                    // the status of the correlation attribute in the central repository
                    if (tagName.getKnownStatus() == TskData.FileKnown.UNKNOWN) {
                        Content content = bbTag.getContent();
                        // If the content which this Blackboard Artifact Tag is linked to is an AbstractFile with KNOWN status then 
                        // it's status in the central reporsitory should not be changed to UNKNOWN
                        if ((content instanceof AbstractFile) && (((AbstractFile) content).getKnown() == TskData.FileKnown.KNOWN)) {
                            continue;
                        }
                        //Get the BlackboardArtifact which this BlackboardArtifactTag has been applied to.
                        BlackboardArtifact bbArtifact = bbTag.getArtifact();
                        TagsManager tagsManager = Case.getCurrentCaseThrows().getServices().getTagsManager();
                        List<BlackboardArtifactTag> tags = tagsManager.getBlackboardArtifactTagsByArtifact(bbArtifact);
                        //get all tags which are on this blackboard artifact
                        for (BlackboardArtifactTag t : tags) {
                            //All instances of the modified tag name will be changed, they can not conflict with each other
                            if (t.getName().equals(tagName)) {
                                continue;
                            }
                            //if any other tags on this artifact are Notable in status then this artifact can not have its status changed 
                            if (TskData.FileKnown.BAD == t.getName().getKnownStatus()) {
                                //a tag with a conflicting status has been found, the status of this correlation attribute can not be modified
                                hasTagWithConflictingKnownStatus = true;
                                break;
                            }
                        }
                    }
                    //if the Correlation Attribute will have no tags with a status which would prevent the current status from being changed 
                    if (!hasTagWithConflictingKnownStatus) {
                        setArtifactKnownStatus(CentralRepository.getInstance(), bbTag.getArtifact(), tagName.getKnownStatus());
                    }
                }
                // Next update the files

                List<ContentTag> fileTags = Case.getCurrentCaseThrows().getSleuthkitCase().getContentTagsByTagName(tagName);
                //Get all ContentTags with this tag name
                for (ContentTag contentTag : fileTags) {
                    //start with assumption that none of the other tags applied to this ContentTag will prevent it's status from being changed
                    boolean hasTagWithConflictingKnownStatus = false;
                    // if the status of the tag has been changed to TskData.FileKnown.UNKNOWN
                    // we need to check the status of all other tags on this file before changing
                    // the status of the file in the central repository
                    if (tagName.getKnownStatus() == TskData.FileKnown.UNKNOWN) {
                        Content content = contentTag.getContent();
                        TagsManager tagsManager = Case.getCurrentCaseThrows().getServices().getTagsManager();
                        List<ContentTag> tags = tagsManager.getContentTagsByContent(content);
                        //get all tags which are on this file
                        for (ContentTag t : tags) {
                            //All instances of the modified tag name will be changed, they can not conflict with each other
                            if (t.getName().equals(tagName)) {
                                continue;
                            }
                            //if any other tags on this file are Notable in status then this file can not have its status changed 
                            if (TskData.FileKnown.BAD == t.getName().getKnownStatus()) {
                                //a tag with a conflicting status has been found, the status of this file can not be modified
                                hasTagWithConflictingKnownStatus = true;
                                break;
                            }
                        }
                    }
                    //if the file will have no tags with a status which would prevent the current status from being changed 
                    if (!hasTagWithConflictingKnownStatus) {
                        Content taggedContent = contentTag.getContent();
                        if (taggedContent instanceof AbstractFile) {
                            final List<CorrelationAttributeInstance> eamArtifact = CorrelationAttributeUtil.makeCorrAttrsForSearch((AbstractFile) taggedContent);
                            if (!eamArtifact.isEmpty()) {
                                //for an abstract file the 'list' of attributes will be a single attribute or empty and is returning a list for consistency with other makeCorrAttrsForSearch methods per 7852 
                                CentralRepository.getInstance().setAttributeInstanceKnownStatus(eamArtifact.get(0), tagName.getKnownStatus());
                            }
                        }
                    }
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Cannot update known status in central repository for tag: " + modifiedTagName, ex);  //NON-NLS
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, "Cannot get central repository for tag: " + modifiedTagName, ex);  //NON-NLS
            } catch (NoCurrentCaseException ex) {
                LOGGER.log(Level.SEVERE, "Exception while getting open case.", ex);  //NON-NLS
            }
        } //TAG_STATUS_CHANGED
    }

    private final class DataSourceAddedTask implements Runnable {

        private final CentralRepository dbManager;
        private final PropertyChangeEvent event;

        private DataSourceAddedTask(CentralRepository db, PropertyChangeEvent evt) {
            dbManager = db;
            event = evt;
        }

        @Override
        public void run() {
            if (!CentralRepository.isEnabled()) {
                return;
            }
            Case openCase;
            try {
                openCase = Case.getCurrentCaseThrows();
            } catch (NoCurrentCaseException ex) {
                LOGGER.log(Level.SEVERE, "Exception while getting open case.", ex);
                return;
            }

            final DataSourceAddedEvent dataSourceAddedEvent = (DataSourceAddedEvent) event;
            Content newDataSource = dataSourceAddedEvent.getDataSource();

            try {
                CorrelationCase correlationCase = dbManager.getCase(openCase);
                if (null == dbManager.getDataSource(correlationCase, newDataSource.getId())) {
                    CorrelationDataSource.fromTSKDataSource(correlationCase, newDataSource);
                }
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, "Error adding new data source to the central repository", ex); //NON-NLS
            }
        } // DATA_SOURCE_ADDED
    }

    private final class CurrentCaseTask implements Runnable {

        private final CentralRepository dbManager;
        private final PropertyChangeEvent event;

        private CurrentCaseTask(CentralRepository db, PropertyChangeEvent evt) {
            dbManager = db;
            event = evt;
        }

        @Override
        public void run() {
            /*
             * A case has been opened if evt.getOldValue() is null and
             * evt.getNewValue() is a valid Case.
             */
            if ((null == event.getOldValue()) && (event.getNewValue() instanceof Case)) {
                Case curCase = (Case) event.getNewValue();

                if (!CentralRepository.isEnabled()) {
                    return;
                }

                try {
                    // NOTE: Cannot determine if the opened case is a new case or a reopened case,
                    //  so check for existing name in DB and insert if missing.
                    if (dbManager.getCase(curCase) == null) {
                        dbManager.newCase(curCase);
                    }
                } catch (CentralRepoException ex) {
                    LOGGER.log(Level.SEVERE, "Error connecting to Central Repository database.", ex); //NON-NLS
                }
            }
        } // CURRENT_CASE
    }

    @NbBundle.Messages({"CaseEventsListener.module.name=Central Repository",
        "CaseEventsListener.prevCaseComment.text=Users seen in previous cases",
        "CaseEventsListener.prevExists.text=Previously Seen Users (Central Repository)"})
    /**
     * Add OsAccount Instance to CR and find interesting items based on the
     * OsAccount
     */
    private final class OsAccountInstancesAddedTask implements Runnable {

        private final CentralRepository dbManager;
        private final PropertyChangeEvent event;
        private final String MODULE_NAME = Bundle.CaseEventsListener_module_name();

        private OsAccountInstancesAddedTask(CentralRepository db, PropertyChangeEvent evt) {
            dbManager = db;
            event = evt;
        }

        @Override
        public void run() {
            //Nothing to do here if the central repo is not enabled or if ingest is running but is set to not save data/make artifacts
            if (!CentralRepository.isEnabled()
                    || (IngestManager.getInstance().isIngestRunning() && !(IngestEventsListener.isFlagSeenDevices() || IngestEventsListener.shouldCreateCrProperties()))) {
                return;
            }

            final OsAcctInstancesAddedEvent osAcctInstancesAddedEvent = (OsAcctInstancesAddedEvent) event;
            List<OsAccountInstance> addedOsAccountNew = osAcctInstancesAddedEvent.getOsAccountInstances();
            for (OsAccountInstance osAccountInstance : addedOsAccountNew) {
                try {
                    OsAccount osAccount = osAccountInstance.getOsAccount();
                    List<CorrelationAttributeInstance> correlationAttributeInstances = CorrelationAttributeUtil.makeCorrAttrsToSave(osAccountInstance);
                    if (correlationAttributeInstances.isEmpty()) {
                        return;
                    }

                    Optional<String> accountAddr = osAccount.getAddr();
                    try {
                        // Save to the database if requested
                        if (IngestEventsListener.shouldCreateCrProperties()) {
                            for (CorrelationAttributeInstance correlationAttributeInstance : correlationAttributeInstances) {
                                dbManager.addArtifactInstance(correlationAttributeInstance);
                            }
                        }

                        // Look up and create artifacts for previously seen accounts if requested
                        if (IngestEventsListener.isFlagSeenDevices()) {

                            CorrelationAttributeInstance instanceWithTypeValue = null;
                            for (CorrelationAttributeInstance instance : correlationAttributeInstances) {
                                if (instance.getCorrelationType().getId() == CorrelationAttributeInstance.OSACCOUNT_TYPE_ID) {
                                    instanceWithTypeValue = instance;
                                    break;
                                }
                            }

                            if (instanceWithTypeValue != null) {
                                List<CorrelationAttributeInstance> previousOccurences = dbManager.getArtifactInstancesByTypeValue(instanceWithTypeValue.getCorrelationType(), instanceWithTypeValue.getCorrelationValue());

                                for (CorrelationAttributeInstance instance : previousOccurences) {
                                    //we can get the first instance here since the case for all attributes will be the same
                                    if (!instance.getCorrelationCase().getCaseUUID().equals(instanceWithTypeValue.getCorrelationCase().getCaseUUID())) {
                                        SleuthkitCase tskCase = osAccount.getSleuthkitCase();
                                        Blackboard blackboard = tskCase.getBlackboard();

                                        List<String> caseDisplayNames = dbManager.getListCasesHavingArtifactInstances(instanceWithTypeValue.getCorrelationType(), instanceWithTypeValue.getCorrelationValue());

                                        // calculate score
                                        Score score;
                                        int numCases = caseDisplayNames.size();
                                        if (numCases <= IngestEventsListener.MAX_NUM_PREVIOUS_CASES_FOR_LIKELY_NOTABLE_SCORE) {
                                            score = Score.SCORE_LIKELY_NOTABLE;
                                        } else if (numCases > IngestEventsListener.MAX_NUM_PREVIOUS_CASES_FOR_LIKELY_NOTABLE_SCORE && numCases <= IngestEventsListener.MAX_NUM_PREVIOUS_CASES_FOR_PREV_SEEN_ARTIFACT_CREATION) {
                                            score = Score.SCORE_NONE;
                                        } else {
                                            // don't make an Analysis Result, the artifact is too common.
                                            continue;
                                        }

                                        String prevCases = caseDisplayNames.stream().distinct().collect(Collectors.joining(","));
                                        String justification = "Previously seen in cases " + prevCases;
                                        Collection<BlackboardAttribute> attributesForNewArtifact = Arrays.asList(
                                                new BlackboardAttribute(
                                                        TSK_SET_NAME, MODULE_NAME,
                                                        Bundle.CaseEventsListener_prevExists_text()),
                                                new BlackboardAttribute(
                                                        TSK_CORRELATION_TYPE, MODULE_NAME,
                                                        instance.getCorrelationType().getDisplayName()),
                                                new BlackboardAttribute(
                                                        TSK_CORRELATION_VALUE, MODULE_NAME,
                                                        instanceWithTypeValue.getCorrelationValue()),
                                                new BlackboardAttribute(
                                                        TSK_OTHER_CASES, MODULE_NAME,
                                                        prevCases));
                                        BlackboardArtifact newAnalysisResult = osAccount.newAnalysisResult(
                                                BlackboardArtifact.Type.TSK_PREVIOUSLY_SEEN, score,
                                                null, Bundle.CaseEventsListener_prevExists_text(), justification, attributesForNewArtifact, osAccountInstance.getDataSource().getId()).getAnalysisResult();
                                        try {
                                            blackboard.postArtifact(newAnalysisResult, MODULE_NAME, null);
                                            break;
                                        } catch (Blackboard.BlackboardException ex) {
                                            LOGGER.log(Level.SEVERE, "Unable to index blackboard artifact " + newAnalysisResult.getArtifactID(), ex); //NON-NLS
                                        }
                                    }
                                }
                            }
                        }

                    } catch (CorrelationAttributeNormalizationException ex) {
                        LOGGER.log(Level.SEVERE, "Exception with Correlation Attribute Normalization.", ex);  //NON-NLS
                    } catch (CentralRepoException ex) {
                        LOGGER.log(Level.SEVERE, String.format("Cannot get central repository for OsAccount: %s.", accountAddr.get()), ex);  //NON-NLS
                    }
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Cannot get central repository for OsAccount: " + "OsAccount", ex);
                }
            }
        }
    }

    private final class DataSourceNameChangedTask implements Runnable {

        private final CentralRepository dbManager;
        private final PropertyChangeEvent event;

        private DataSourceNameChangedTask(CentralRepository db, PropertyChangeEvent evt) {
            dbManager = db;
            event = evt;
        }

        @Override
        public void run() {

            final DataSourceNameChangedEvent dataSourceNameChangedEvent = (DataSourceNameChangedEvent) event;
            Content dataSource = dataSourceNameChangedEvent.getDataSource();
            String newName = (String) event.getNewValue();

            if (!StringUtils.isEmpty(newName)) {

                if (!CentralRepository.isEnabled()) {
                    return;
                }

                try {
                    CorrelationCase correlationCase = dbManager.getCase(Case.getCurrentCaseThrows());
                    CorrelationDataSource existingEamDataSource = dbManager.getDataSource(correlationCase, dataSource.getId());
                    dbManager.updateDataSourceName(existingEamDataSource, newName);
                } catch (CentralRepoException ex) {
                    LOGGER.log(Level.SEVERE, "Error updating data source with ID " + dataSource.getId() + " to " + newName, ex); //NON-NLS
                } catch (NoCurrentCaseException ex) {
                    LOGGER.log(Level.SEVERE, "No open case", ex);
                }
            }
        } // DATA_SOURCE_NAME_CHANGED
    }
}
