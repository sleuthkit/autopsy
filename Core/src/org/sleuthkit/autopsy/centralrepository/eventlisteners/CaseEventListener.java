/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceAddedEvent;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamOrganization;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskDataException;

/**
 * Listen for case events and update entries in the Central Repository database
 * accordingly
 */
@Messages({"caseeventlistener.evidencetag=Evidence"})
public class CaseEventListener implements PropertyChangeListener {

    private static final Logger LOGGER = Logger.getLogger(CaseEventListener.class.getName());

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        EamDb dbManager;
        try {
            dbManager = EamDb.getInstance();
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get instance of db manager.", ex);
            return;
        }
        switch (Case.Events.valueOf(evt.getPropertyName())) {
            case CONTENT_TAG_ADDED:
            case CONTENT_TAG_DELETED: {
                if (!EamDb.isEnabled()) {
                    return;
                }

                AbstractFile af;
                TskData.FileKnown knownStatus;
                String comment;
                if(Case.Events.valueOf(evt.getPropertyName()) == Case.Events.CONTENT_TAG_ADDED){
                    // For added tags, we want to change the known status to BAD if the 
                    // tag that was just added is in the list of central repo tags.
                    final ContentTagAddedEvent tagAddedEvent = (ContentTagAddedEvent) evt;
                    final ContentTag tagAdded = tagAddedEvent.getAddedTag();
                    
                    if(dbManager.getBadTags().contains(tagAdded.getName().getDisplayName())){
                        if(tagAdded.getContent() instanceof AbstractFile){
                            af = (AbstractFile) tagAdded.getContent();
                            knownStatus = TskData.FileKnown.BAD;
                            comment = tagAdded.getComment();
                        } else {
                            LOGGER.log(Level.WARNING, "Error updating non-file object");
                            return;
                        }
                    } else {
                        // The added tag isn't flagged as bad in central repo, so do nothing
                        return;
                    }
                } else { // CONTENT_TAG_DELETED
                    // For deleted tags, we want to set the file status to UNKNOWN if:
                    //   - The tag that was just removed is notable in central repo
                    //   - There are no remaining tags that are notable 
                    final ContentTagDeletedEvent tagDeletedEvent = (ContentTagDeletedEvent) evt;
                    long contentID = tagDeletedEvent.getDeletedTagInfo().getContentID();

                    String tagName = tagDeletedEvent.getDeletedTagInfo().getName().getDisplayName();
                    if(! dbManager.getBadTags().contains(tagName)){
                        // If the tag that got removed isn't on the list of central repo tags, do nothing
                        return;
                    }        
                    
                    try{
                        // Get the remaining tags on the content object
                        Content content = Case.getCurrentCase().getSleuthkitCase().getContentById(contentID);
                        TagsManager tagsManager = Case.getCurrentCase().getServices().getTagsManager();
                        List<ContentTag> tags = tagsManager.getContentTagsByContent(content);
                        
                        if(tags.stream()
                            .map(tag -> tag.getName().getDisplayName())
                            .filter(dbManager.getBadTags()::contains)
                            .collect(Collectors.toList())
                            .isEmpty()){
                            
                                // There are no more bad tags on the object
                                if(content instanceof AbstractFile){
                                    af = (AbstractFile) content;
                                    knownStatus = TskData.FileKnown.UNKNOWN;
                                    comment = "";
                                } else {
                                    LOGGER.log(Level.WARNING, "Error updating non-file object");
                                    return;
                                }
                        } else {
                            // There's still at least one bad tag, so leave the known status as is
                            return;
                        }
                    } catch (TskCoreException ex){
                        LOGGER.log(Level.SEVERE, "Failed to find content", ex);
                        return;
                    }
                }

                final CorrelationAttribute eamArtifact = EamArtifactUtil.getEamArtifactFromContent(af, 
                        knownStatus, comment);

                // send update to Central Repository db
                Runnable r = new KnownStatusChangeRunner(eamArtifact, knownStatus);
                // TODO: send r into a thread pool instead
                Thread t = new Thread(r);
                t.start();
            } // CONTENT_TAG_ADDED, CONTENT_TAG_DELETED
            break;

            case BLACKBOARD_ARTIFACT_TAG_DELETED:
            case BLACKBOARD_ARTIFACT_TAG_ADDED: {
                if (!EamDb.isEnabled()) {
                    return;
                }
                
                Content content;
                BlackboardArtifact bbArtifact;
                TskData.FileKnown knownStatus;
                String comment;
                if(Case.Events.valueOf(evt.getPropertyName()) == Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED){
                    // For added tags, we want to change the known status to BAD if the 
                    // tag that was just added is in the list of central repo tags.
                    final BlackBoardArtifactTagAddedEvent tagAddedEvent = (BlackBoardArtifactTagAddedEvent) evt;
                    final BlackboardArtifactTag tagAdded = tagAddedEvent.getAddedTag();
                    
                    if(dbManager.getBadTags().contains(tagAdded.getName().getDisplayName())){
                        content = tagAdded.getContent();
                        bbArtifact = tagAdded.getArtifact();
                        knownStatus = TskData.FileKnown.BAD;
                        comment = tagAdded.getComment();
                    } else {
                        // The added tag isn't flagged as bad in central repo, so do nothing
                        return;
                    }
                } else { //BLACKBOARD_ARTIFACT_TAG_DELETED
                    // For deleted tags, we want to set the file status to UNKNOWN if:
                    //   - The tag that was just removed is notable in central repo
                    //   - There are no remaining tags that are notable 
                    final BlackBoardArtifactTagDeletedEvent tagDeletedEvent = (BlackBoardArtifactTagDeletedEvent) evt;
                    long contentID = tagDeletedEvent.getDeletedTagInfo().getContentID();
                    long artifactID = tagDeletedEvent.getDeletedTagInfo().getArtifactID();

                    String tagName = tagDeletedEvent.getDeletedTagInfo().getName().getDisplayName();
                    if(! dbManager.getBadTags().contains(tagName)){
                        // If the tag that got removed isn't on the list of central repo tags, do nothing
                        return;
                    }        
                    
                    try{
                        // Get the remaining tags on the artifact
                        content = Case.getCurrentCase().getSleuthkitCase().getContentById(contentID);
                        bbArtifact = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifact(artifactID);
                        TagsManager tagsManager = Case.getCurrentCase().getServices().getTagsManager();                        
                        List<BlackboardArtifactTag> tags = tagsManager.getBlackboardArtifactTagsByArtifact(bbArtifact);
                        
                        if(tags.stream()
                            .map(tag -> tag.getName().getDisplayName())
                            .filter(dbManager.getBadTags()::contains)
                            .collect(Collectors.toList())
                            .isEmpty()){
                            
                                // There are no more bad tags on the object
                                knownStatus = TskData.FileKnown.UNKNOWN;
                                comment = "";

                        } else {
                            // There's still at least one bad tag, so leave the known status as is
                            return;
                        }
                    } catch (TskCoreException ex){
                        LOGGER.log(Level.SEVERE, "Failed to find content", ex);
                        return;
                    }
                }
                
                if((content instanceof AbstractFile) && (((AbstractFile)content).getKnown() == TskData.FileKnown.KNOWN)){
                    return;
                }

                List<CorrelationAttribute> convertedArtifacts = EamArtifactUtil.getCorrelationAttributeFromBlackboardArtifact(bbArtifact, true, true);
                for (CorrelationAttribute eamArtifact : convertedArtifacts) {
                    eamArtifact.getInstances().get(0).setComment(comment);
                    Runnable r = new KnownStatusChangeRunner(eamArtifact, knownStatus);
                    // TODO: send r into a thread pool instead
                    Thread t = new Thread(r);
                    t.start();
                }
                
            } // BLACKBOARD_ARTIFACT_TAG_ADDED, BLACKBOARD_ARTIFACT_TAG_DELETED
            break;

            case DATA_SOURCE_ADDED: {
                if (!EamDb.isEnabled()) {
                    break;
                }

                final DataSourceAddedEvent dataSourceAddedEvent = (DataSourceAddedEvent) evt;
                Content newDataSource = dataSourceAddedEvent.getDataSource();

                try {
                    String deviceId = Case.getCurrentCase().getSleuthkitCase().getDataSource(newDataSource.getId()).getDeviceId();
                    if (null == dbManager.getDataSourceDetails(deviceId)) {
                        dbManager.newDataSource(CorrelationDataSource.fromTSKDataSource(newDataSource));
                    }
                } catch (EamDbException ex) {
                    LOGGER.log(Level.SEVERE, "Error connecting to Central Repository database.", ex); //NON-NLS
                } catch (TskCoreException | TskDataException ex) {
                    LOGGER.log(Level.SEVERE, "Error getting data source from DATA_SOURCE_ADDED event content.", ex); //NON-NLS
                }
            } // DATA_SOURCE_ADDED
            break;

            case CURRENT_CASE: {
                /*
                 * A case has been opened if evt.getOldValue() is null and
                 * evt.getNewValue() is a valid Case.
                 */
                if ((null == evt.getOldValue()) && (evt.getNewValue() instanceof Case)) {
                    Case curCase = (Case) evt.getNewValue();
                    IngestEventsListener.resetCeModuleInstanceCount();
                    try {
                        // only add default evidence tag if case is open and it doesn't already exist in the tags list.
                        if (Case.isCaseOpen()
                                && Case.getCurrentCase().getServices().getTagsManager().getAllTagNames().stream()
                                        .map(tag -> tag.getDisplayName())
                                        .filter(tagName -> Bundle.caseeventlistener_evidencetag().equals(tagName))
                                        .collect(Collectors.toList())
                                        .isEmpty()) {
                            curCase.getServices().getTagsManager().addTagName(Bundle.caseeventlistener_evidencetag());
                        }
                    } catch (TagsManager.TagNameAlreadyExistsException ex) {
                        LOGGER.info("Evidence tag already exists"); // NON-NLS
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Error adding tag.", ex); // NON-NLS
                    }

                    CorrelationCase curCeCase = new CorrelationCase(
                            -1,
                            curCase.getName(), // unique case ID
                            EamOrganization.getDefault(),
                            curCase.getDisplayName(),
                            curCase.getCreatedDate(),
                            curCase.getNumber(),
                            curCase.getExaminer(),
                            null,
                            null,
                            null);

                    if (!EamDb.isEnabled()) {
                        break;
                    }

                    try {
                        // NOTE: Cannot determine if the opened case is a new case or a reopened case,
                        //  so check for existing name in DB and insert if missing.
                        CorrelationCase existingCase = dbManager.getCaseByUUID(curCeCase.getCaseUUID());

                        if (null == existingCase) {
                            dbManager.newCase(curCeCase);
                        }
                    } catch (EamDbException ex) {
                        LOGGER.log(Level.SEVERE, "Error connecting to Central Repository database.", ex); //NON-NLS
                    }
                }
            } // CURRENT_CASE
            break;
            
            case NAME: {
                // The display name of the case has been changed
                
                if (!EamDb.isEnabled()) {
                    break;
                }
                
                if(evt.getNewValue() instanceof String){
                    String newName = (String)evt.getNewValue();
                    try {
                        // See if the case is in the database. If it is, update the display name.
                        CorrelationCase existingCase = dbManager.getCaseByUUID(Case.getCurrentCase().getName());

                        if (null != existingCase) {
                            existingCase.setDisplayName(newName);
                            dbManager.updateCase(existingCase);
                        }
                    } catch (EamDbException ex) {
                        LOGGER.log(Level.SEVERE, "Error connecting to Central Repository database.", ex); //NON-NLS
                    }
                }
            } // NAME
            break;
        }
    }
}
