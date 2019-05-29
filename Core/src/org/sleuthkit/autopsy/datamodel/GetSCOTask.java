/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.ContentTag;

/**
 * Background task to get Score, Comment and Occurrences values for a Abstract file node.
 * 
 */
class GetSCOTask implements Runnable {

    private final WeakReference<AbstractAbstractFileNode<?>> weakNodeRef;
    private final PropertyChangeListener listener;

    public GetSCOTask(WeakReference<AbstractAbstractFileNode<?>> weakContentRef, PropertyChangeListener listener) {
        this.weakNodeRef = weakContentRef;
        this.listener = listener;
    }

    @Override
    public void run() {
        AbstractAbstractFileNode<?> fileNode = weakNodeRef.get();
        
        //Check for stale reference
        if (fileNode == null) {
            return;
        }

        // get the SCO  column values
        List<ContentTag> tags = fileNode.getContentTagsFromDatabase();
        CorrelationAttributeInstance attribute = fileNode.getCorrelationAttributeInstance();

        SCOData scoData = new SCOData();
        scoData.setScoreAndDescription(fileNode.getScorePropertyAndDescription(tags));
        scoData.setComment(fileNode.getCommentProperty(tags, attribute));
        if (!UserPreferences.hideCentralRepoCommentsAndOccurrences()) {
            scoData.setCountAndDescription(fileNode.getCountPropertyAndDescription(attribute));
        }
        
        if  (listener != null) {
            listener.propertyChange(new PropertyChangeEvent(
                    AutopsyEvent.SourceType.LOCAL.toString(),
                    AbstractAbstractFileNode.NodeSpecificEvents.SCO_AVAILABLE.toString(),
                    null, scoData));
        }
    }
}
