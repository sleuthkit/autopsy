/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.HasCommentStatus;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.Score;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;


/**
 * Completes the tasks needed to populate the Score, Comment, Occurrences and Translation
 * columns in the background so that the UI is not blocked while waiting for responses from the database or 
 * translation service. Once these events are done, it fires a PropertyChangeEvent
 * to let the AbstractAbstractFileNode know it's time to update!
 */
class SCOAndTranslationTask implements Runnable {
    
    private final WeakReference<AbstractFile> weakContentRef;
    private final PropertyChangeListener listener;
    
    public SCOAndTranslationTask(WeakReference<AbstractFile> weakContentRef, PropertyChangeListener listener) {
        this.weakContentRef = weakContentRef;
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            AbstractFile content = weakContentRef.get();
            
            //Long DB queries
            List<ContentTag> tags = PropertyUtil.getContentTagsFromDatabase(content);
            CorrelationAttributeInstance attribute = 
                    PropertyUtil.getCorrelationAttributeInstance(content);

            Pair<DataResultViewerTable.Score, String> scoreAndDescription = 
                    PropertyUtil.getScorePropertyAndDescription(content, tags);
            DataResultViewerTable.HasCommentStatus comment = 
                    PropertyUtil.getCommentProperty(tags, attribute);
            Pair<Long, String> countAndDescription = 
                    PropertyUtil.getCountPropertyAndDescription(attribute);

            //Load the results from the SCO column operations into a wrapper object to be passed
            //back to the listener so that the node can internally update it's propertySheet.
            SCOResults results = new SCOResults(
                    scoreAndDescription.getLeft(),
                    scoreAndDescription.getRight(),
                    comment,
                    countAndDescription.getLeft(),
                    countAndDescription.getRight()
            );

            listener.propertyChange(new PropertyChangeEvent(
                AutopsyEvent.SourceType.LOCAL.toString(),
                AbstractAbstractFileNode.NodeSpecificEvents.DABABASE_CONTENT_AVAILABLE.toString(),
                null,
                results));

            //Once we've got the SCO columns, then lets fire the translation result.
            //Updating of this column is significantly lower priority than 
            //getting results to the SCO columns!
            listener.propertyChange(new PropertyChangeEvent(
                    AutopsyEvent.SourceType.LOCAL.toString(),
                    AbstractAbstractFileNode.NodeSpecificEvents.TRANSLATION_AVAILABLE.toString(),
                    null,
                    PropertyUtil.getTranslatedFileName(content)));
        } catch (NullPointerException ex) {
           //If we are here, that means our weakPcl or content pointer has gone stale (aka
           //has been garbage collected). There's no recovery. Netbeans has 
           //GC'd the node because its not useful to the user anymore. No need 
           //to log. Fail out fast to keep the thread pool rollin!
        }
    }
    
    /**
     * Wrapper around data obtained from doing the Score, Comment, and Occurrences
     * tasks. This object will be accessed by the AAFN to update it's state.
     */
    final class SCOResults {

        private final Score score;
        private final String scoreDescription;

        private final HasCommentStatus comment;

        private final Long count;
        private final String countDescription;

        public SCOResults(Score score, String scoreDescription,
                HasCommentStatus comment, Long count,
                String countDescription) {
            this.score = score;
            this.scoreDescription = scoreDescription;
            this.comment = comment;
            this.count = count;
            this.countDescription = countDescription;
        }

        public Score getScore() {
            return score;
        }

        public String getScoreDescription() {
            return scoreDescription;
        }

        public HasCommentStatus getComment() {
            return comment;
        }

        public Long getCount() {
            return count;
        }

        public String getCountDescription() {
            return countDescription;
        }
    }
}
