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
import java.util.List;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

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
        SCOData scoData = new SCOData();
        scoData.setScoreAndDescription(contentNode.getScorePropertyAndDescription(tags));
        //getting the correlation attribute and setting the comment column is done before the eamdb isEnabled check
        //because the Comment column will reflect the presence of comments in the CR when the CR is enabled, but reflect tag comments regardless
        CorrelationAttributeInstance corInstance = contentNode.getFirstCorrelationAttributeInstance();
        scoData.setComment(contentNode.getCommentProperty(tags, corInstance));
        if (CentralRepository.isEnabled()) {
            String description = Bundle.GetSCOTask_occurrences_defaultDescription();
            scoData.setCountAndDescription(contentNode.getCountPropertyAndDescription(corInstance, description));
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
