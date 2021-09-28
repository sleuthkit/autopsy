/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;

/**
 * Background task to get Score, Comment and Occurrences values for an Abstract
 * content node.
 *
 */
class GetSCOTask implements Runnable {

    private final WeakReference<AbstractContentNode<?>> weakNodeRef;
    private final PropertyChangeListener listener;

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
        Pair<Score, String> scoreAndDescription;  
        DataResultViewerTable.HasCommentStatus comment;
        Pair<Long, String> countAndDescription = null;

        scoreAndDescription = contentNode.getScorePropertyAndDescription(tags);
        //getting the correlation attribute and setting the comment column is done before the eamdb isEnabled check
        //because the Comment column will reflect the presence of comments in the CR when the CR is enabled, but reflect tag comments regardless
        String description = Bundle.GetSCOTask_occurrences_defaultDescription();
        List<CorrelationAttributeInstance> listOfPossibleAttributes = new ArrayList<>();
        Content contentFromNode = contentNode.getContent();
        if (contentFromNode instanceof AbstractFile) {
            listOfPossibleAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((AbstractFile) contentFromNode));
        } else if (contentFromNode instanceof AnalysisResult) {
            listOfPossibleAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((AnalysisResult) contentFromNode));
        } else if (contentFromNode instanceof DataArtifact) {
            listOfPossibleAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((DataArtifact) contentFromNode));
        } else {
            //JIRA-TODO : add code for Jira-7938 OsAccounts
        }
        comment = contentNode.getCommentProperty(tags, listOfPossibleAttributes);
        CorrelationAttributeInstance corInstance = null;
        if (CentralRepository.isEnabled()) {
            if (listOfPossibleAttributes.size() > 1) {
                //Don't display anything if there is more than 1 correlation property for an artifact but let the user know
                description = Bundle.GetSCOTask_occurrences_multipleProperties();
            } else if (!listOfPossibleAttributes.isEmpty()) {
                //there should only be one item in the list
                corInstance = listOfPossibleAttributes.get(0);
            }
            countAndDescription = contentNode.getCountPropertyAndDescription(corInstance, description);
        }

        if(Thread.currentThread().isInterrupted()) {
            return;
        }
        
        // signal SCO data is available.
        if (listener != null) {
            listener.propertyChange(new PropertyChangeEvent(
                    AutopsyEvent.SourceType.LOCAL.toString(),
                    AbstractAbstractFileNode.NodeSpecificEvents.SCO_AVAILABLE.toString(),
                    null, new SCOData(scoreAndDescription, comment, countAndDescription)));
        }
    }
}
