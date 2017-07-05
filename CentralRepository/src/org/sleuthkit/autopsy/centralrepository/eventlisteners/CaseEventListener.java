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
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceAddedEvent;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifact;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamOrganization;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
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
        EamDb dbManager = EamDb.getInstance();
        switch (Case.Events.valueOf(evt.getPropertyName())) {
            case CONTENT_TAG_ADDED: {
                if (!EamDb.isEnabled()) {
                    return;
                }

                final ContentTagAddedEvent tagAddedEvent = (ContentTagAddedEvent) evt;
                final ContentTag tagAdded = tagAddedEvent.getAddedTag();
                // TODO: detect failed cast and break if so.
                final AbstractFile af = (AbstractFile) tagAdded.getContent();
                final TagName tagName = tagAdded.getName();

                if ((af.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                        || (af.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)
                        || (af.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.SLACK)
                        || (af.getKnown() == TskData.FileKnown.KNOWN)
                        || (af.isDir() == true)
                        || (!af.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC))) {
                    break;
                }

                String dsName;
                try {
                    dsName = af.getDataSource().getName();
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Error, unable to get name of data source from abstract file during CONTENT_TAG_ADDED event.", ex);
                    return;
                }

                if (dbManager.getBadTags().contains(tagName.getDisplayName())) {
                    String md5 = af.getMd5Hash();
                    if (md5 == null || md5.isEmpty()) {
                        return;
                    }
                    String deviceId = "";
                    try {
                        deviceId = Case.getCurrentCase().getSleuthkitCase().getDataSource(af.getDataSource().getId()).getDeviceId();
                    } catch (TskCoreException | TskDataException ex) {
                        LOGGER.log(Level.SEVERE, "Error, failed to get deviceID or data source from current case.", ex);
                    }

                    EamArtifact eamArtifact;
                    try {
                        EamArtifact.Type filesType = dbManager.getCorrelationTypeById(EamArtifact.FILES_TYPE_ID);
                        eamArtifact = new EamArtifact(filesType, af.getMd5Hash());
                        EamArtifactInstance cei = new EamArtifactInstance(
                                new EamCase(Case.getCurrentCase().getName(), Case.getCurrentCase().getDisplayName()),
                                new EamDataSource(deviceId, dsName),
                                af.getParentPath() + af.getName(),
                                tagAdded.getComment(),
                                TskData.FileKnown.BAD,
                                EamArtifactInstance.GlobalStatus.LOCAL
                        );
                        eamArtifact.addInstance(cei);
                        // send update to Central Repository db
                        Runnable r = new BadFileTagRunner(eamArtifact);
                        // TODO: send r into a thread pool instead
                        Thread t = new Thread(r);
                        t.start();
                    } catch (EamDbException ex) {
                        LOGGER.log(Level.SEVERE, "Error, unable to get FILES correlation type during CONTENT_TAG_ADDED event.", ex);
                    }
                }
            } // CONTENT_TAG_ADDED
            break;

            case BLACKBOARD_ARTIFACT_TAG_ADDED: {
                if (!EamDb.isEnabled()) {
                    return;
                }

                final BlackBoardArtifactTagAddedEvent bbTagAddedEvent = (BlackBoardArtifactTagAddedEvent) evt;
                final BlackboardArtifactTag bbTagAdded = bbTagAddedEvent.getAddedTag();
                final AbstractFile af = (AbstractFile) bbTagAdded.getContent();
                final BlackboardArtifact bbArtifact = bbTagAdded.getArtifact();
                final TagName tagName = bbTagAdded.getName();

                if (af.getKnown() == TskData.FileKnown.KNOWN) {
                    break;
                }

                if (dbManager.getBadTags().contains(tagName.getDisplayName())) {
                    try {
                        EamArtifact eamArtifact = EamArtifactUtil.fromBlackboardArtifact(bbArtifact, true, dbManager.getCorrelationTypes(), true);
                        if (null != eamArtifact) {
                            eamArtifact.getInstances().get(0).setComment(bbTagAdded.getComment());
                            Runnable r = new BadFileTagRunner(eamArtifact);
                            // TODO: send r into a thread pool instead
                            Thread t = new Thread(r);
                            t.start();
                        }
                    } catch (EamDbException ex) {
                        LOGGER.log(Level.SEVERE, "Error, unable to get artifact types during BLACKBOARD_ARTIFACT_TAG_ADDED event.", ex);
                    }
                }
            } // BLACKBOARD_ARTIFACT_TAG_ADDED
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
                        dbManager.newDataSource(new EamDataSource(deviceId, newDataSource.getName()));
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

                    EamCase curCeCase = new EamCase(
                            -1,
                            curCase.getName(), // unique case ID
                            EamOrganization.getDefault(),
                            curCase.getDisplayName(),
                            curCase.getCreatedDate(),
                            curCase.getNumber(),
                            curCase.getExaminer(),
                            "",
                            "",
                            "");

                    if (!EamDb.isEnabled()) {
                        break;
                    }

                    try {
                        // NOTE: Cannot determine if the opened case is a new case or a reopened case,
                        //  so check for existing name in DB and insert if missing.
                        EamCase existingCase = dbManager.getCaseDetails(curCeCase.getCaseUUID());

                        if (null == existingCase) {
                            dbManager.newCase(curCeCase);
                        }
                    } catch (EamDbException ex) {
                        LOGGER.log(Level.SEVERE, "Error connecting to Central Repository database.", ex); //NON-NLS
                    }
                }
            } // CURRENT_CASE
            break;
        }
    }
}
