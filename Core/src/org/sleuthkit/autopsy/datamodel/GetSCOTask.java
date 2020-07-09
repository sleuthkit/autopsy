/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance.Type;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * Background task to get Score, Comment and Occurrences values for an Abstract
 * content node.
 *
 */
class GetSCOTask implements Runnable {

    private final WeakReference<AbstractContentNode<?>> weakNodeRef;
    private final PropertyChangeListener listener;
    private static final Logger logger = Logger.getLogger(GetSCOTask.class.getName());

    GetSCOTask(WeakReference<AbstractContentNode<?>> weakContentRef, PropertyChangeListener listener) {
        this.weakNodeRef = weakContentRef;
        this.listener = listener;
    }

    @Messages({"GetSCOTask.occurrences.defaultDescription=No correlation properties found",
        "GetSCOTask.occurrences.multipleProperties=Multiple different correlation properties exist for this result"})
    @Override
    public void run() {
        AbstractContentNode<?> contentNode = weakNodeRef.get();

         //Check for stale reference or if columns are disabled
        if (contentNode == null || UserPreferences.getHideSCOColumns()) {
            return;
        }
        // get the SCO  column values
        List<Tag> tags = contentNode.getAllTagsFromDatabase();

        SCOData scoData = new SCOData();
        scoData.setScoreAndDescription(contentNode.getScorePropertyAndDescription(tags));
        //getting the correlation attribute and setting the comment column is done before the eamdb isEnabled check
        //because the Comment column will reflect the presence of comments in the CR when the CR is enabled, but reflect tag comments regardless 
        CorrelationAttributeInstance fileAttribute = contentNode.getCorrelationAttributeInstance();
        scoData.setComment(contentNode.getCommentProperty(tags, fileAttribute));

        if (CentralRepository.isEnabled()) {
            Type type = null;
            String value = null;
            String description = Bundle.GetSCOTask_occurrences_defaultDescription();
            if (contentNode instanceof BlackboardArtifactNode) {
                BlackboardArtifact bbArtifact = ((BlackboardArtifactNode) contentNode).getArtifact();
                //for specific artifact types we still want to display information for the file instance correlation attribute
                if (bbArtifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID()
                        || bbArtifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED.getTypeID()
                        || bbArtifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID()
                        || bbArtifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()
                        || bbArtifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()
                        || bbArtifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED.getTypeID()
                        || bbArtifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID()
                        || bbArtifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                    try {
                        if (bbArtifact.getParent() instanceof AbstractFile) {
                            type = CorrelationAttributeInstance.getDefaultCorrelationTypes().get(CorrelationAttributeInstance.FILES_TYPE_ID);
                            value = ((AbstractFile) bbArtifact.getParent()).getMd5Hash();
                        }
                    } catch (TskCoreException | CentralRepoException ex) {
                        logger.log(Level.WARNING, "Unable to get correlation type or value to determine value for O column for artifact", ex);
                    }
                } else {
                    List<CorrelationAttributeInstance> listOfPossibleAttributes = CorrelationAttributeUtil.makeCorrAttrsForCorrelation(bbArtifact);
                    if (listOfPossibleAttributes.size() > 1) {
                        //Don't display anything if there is more than 1 correlation property for an artifact but let the user know
                        description = Bundle.GetSCOTask_occurrences_multipleProperties();
                    } else if (!listOfPossibleAttributes.isEmpty()) {
                        //there should only be one item in the list
                        type = listOfPossibleAttributes.get(0).getCorrelationType();
                        value = listOfPossibleAttributes.get(0).getCorrelationValue();
                    }
                }
            } else if (contentNode.getContent() instanceof AbstractFile) {
                //use the file instance correlation attribute if the node is not a BlackboardArtifactNode    
                try {
                    type = CorrelationAttributeInstance.getDefaultCorrelationTypes().get(CorrelationAttributeInstance.FILES_TYPE_ID);
                    value = ((AbstractFile) contentNode.getContent()).getMd5Hash();
                } catch (CentralRepoException ex) {
                    logger.log(Level.WARNING, "Unable to get correlation type to determine value for O column for file", ex);
                }
            }
            scoData.setCountAndDescription(contentNode.getCountPropertyAndDescription(type, value, description));
        }

        // signal SCO data is available.
        if (listener
                != null) {
            listener.propertyChange(new PropertyChangeEvent(
                    AutopsyEvent.SourceType.LOCAL.toString(),
                    AbstractAbstractFileNode.NodeSpecificEvents.SCO_AVAILABLE.toString(),
                    null, scoData));
        }
    }
}
