/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.sco;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.mainui.sco.SCOFetcher.SCOData;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountInstance;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A Swingworker for fetching the SCO data for the given supporter.
 *
 * The SwingWorkers can be executed as a normal SwingWorkers or passed a
 * separate ExecutorService. Nodes should use the ExecutorService in BaseNode to
 * avoid interrupting other SwingWorkers.
 */
public class SCOFetcher<T extends Content> implements Runnable {
    
    private final WeakReference<SCOSupporter> weakSupporterRef;
    private static final Logger logger = Logger.getLogger(SCOFetcher.class.getName());

    /**
     * Construct a new SCOFetcher.
     *
     * @param weakSupporterRef A weak reference to a SCOSupporter.
     */
    public SCOFetcher(WeakReference<SCOSupporter> weakSupporterRef) {
        this.weakSupporterRef = weakSupporterRef;
    }
    
    @Override
    public void run() {
        try {
            SCOData data = doInBackground();
            
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    SCOFetcher.done(data, weakSupporterRef.get());
                }
            });
            
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "An exception occurred while trying to update the the SCO data", ex);
        }
    }
    
    @NbBundle.Messages({"SCOFetcher_occurrences_defaultDescription=No correlation properties found",
        "SCOFetcher_occurrences_multipleProperties=Multiple different correlation properties exist for this result"
    })
    private SCOData doInBackground() throws Exception {
        SCOSupporter scoSupporter = weakSupporterRef.get();
        Content content = scoSupporter.getContent().get();
        //Check for stale reference or if columns are disabled
        if (content == null || UserPreferences.getHideSCOColumns()) {
            return null;
        }
        // get the SCO  column values
        Pair<Score, String> scoreAndDescription;
        Pair<Long, String> countAndDescription = null;
        scoreAndDescription = scoSupporter.getScorePropertyAndDescription();
        
        String description = Bundle.SCOFetcher_occurrences_defaultDescription();
        List<CorrelationAttributeInstance> listOfPossibleAttributes = new ArrayList<>();
        //the lists returned will be empty if the CR is not enabled
        if (content instanceof AbstractFile) {
            listOfPossibleAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((AbstractFile) content));
        } else if (content instanceof AnalysisResult) {
            listOfPossibleAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((AnalysisResult) content));
        } else if (content instanceof DataArtifact) {
            listOfPossibleAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((DataArtifact) content));
        } else if (content instanceof OsAccount) {
            try {
                List<OsAccountInstance> osAccountInstances = ((OsAccount) content).getOsAccountInstances();

                /*
                 * In the most common use cases it will not matter which
                 * OsAccountInstance is selected, so choosing the first one is
                 * the most efficient solution.
                 */
                OsAccountInstance osAccountInstance = osAccountInstances.isEmpty() ? null : osAccountInstances.get(0);
                /*
                 * If we have a Case whith both data sources in the CR and data
                 * sources not in the CR, some of the OsAccountInstances for
                 * this OsAccount have not been processed into the CR. In this
                 * situation the counts may not always be accurate or
                 * consistent.
                 *
                 * In order to ensure conistency in all use cases we would need
                 * to ensure we always had an OsAccountInstance whose data
                 * source was in the CR when such an OsAccountInstance was
                 * available.
                 *
                 * The following block of code has been commented out because it
                 * reduces efficiency in what are believed to be the most common
                 * use cases. It would serve the purpose of providing
                 * consistency in edge cases where users are putting some but
                 * not all the data concerning OS Accounts, which is present in
                 * a single Case, into the CR. See TODO-JIRA-8031 for a similar
                 * issue in the OO viewer.
                 */

//                if (CentralRepository.isEnabled() && !osAccountInstances.isEmpty()) {
//                    try {
//                        CentralRepository centralRepo = CentralRepository.getInstance();
//                        //Correlation Cases are cached when we get them so this shouldn't involve a round trip for every node.
//                        CorrelationCase crCase = centralRepo.getCase(Case.getCurrentCaseThrows());
//                        for (OsAccountInstance caseOsAccountInstance : osAccountInstances) {
//                            //correlation data sources are also cached so once should not involve round trips every time.
//                            CorrelationDataSource correlationDataSource = centralRepo.getDataSource(crCase, caseOsAccountInstance.getDataSource().getId());
//                            if (correlationDataSource != null) {
//                                //we have found a data source which exists in the CR we will use it instead of the arbitrary first instance
//                                osAccountInstance = caseOsAccountInstance;
//                                break;
//                            }
//                        }
//                    } catch (CentralRepoException ex) {
//                        logger.log(Level.SEVERE, "Error checking CR for data sources which exist in it", ex);
//                    } catch (NoCurrentCaseException ex) {
//                        logger.log(Level.WARNING, "The current case was closed while attempting to find a data source in the central repository", ex);
//                    }
//                }
                listOfPossibleAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch(osAccountInstance));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Unable to get the DataSource or OsAccountInstances from an OsAccount with ID: " + content.getId(), ex);
            }
        }
        
        Optional<List<Tag>> optionalList = scoSupporter.getAllTagsFromDatabase();
        
        DataResultViewerTable.HasCommentStatus commentStatus = DataResultViewerTable.HasCommentStatus.NO_COMMENT;
        
        if (optionalList.isPresent()) {
            commentStatus = scoSupporter.getCommentProperty(optionalList.get(), listOfPossibleAttributes);
        }
        
        CorrelationAttributeInstance corInstance = null;
        if (CentralRepository.isEnabled()) {
            if (listOfPossibleAttributes.size() > 1) {
                //Don't display anything if there is more than 1 correlation property for an artifact but let the user know
                description = Bundle.SCOFetcher_occurrences_multipleProperties();
            } else if (!listOfPossibleAttributes.isEmpty()) {
                //there should only be one item in the list
                corInstance = listOfPossibleAttributes.get(0);
            }
            countAndDescription = scoSupporter.getCountPropertyAndDescription(corInstance, description);
        }
        
        return new SCOData(scoreAndDescription, commentStatus, countAndDescription, content.getId());
    }
    
    @Messages({
        "SCOFetcher_nodescription_text=No description"
    })
    private static void done(SCOData data, SCOSupporter scoSupporter) {
        if (data == null || UserPreferences.getHideSCOColumns()) {
            return;
        }

        if (scoSupporter == null) {
            return;
        }
        
        List<NodeProperty<?>> props = new ArrayList<>();
        
        if (data.getScoreAndDescription() != null) {
            props.add(new NodeProperty<>(
                    SCOUtils.SCORE_COLUMN_NAME,
                    SCOUtils.SCORE_COLUMN_NAME,
                    data.getScoreAndDescription().getRight(),
                    data.getScoreAndDescription().getLeft()));
        }
        
        if (data.getComment() != null) {
            props.add(new NodeProperty<>(
                    SCOUtils.COMMENT_COLUMN_NAME,
                    SCOUtils.COMMENT_COLUMN_NAME,
                    Bundle.SCOFetcher_nodescription_text(),
                    data.getComment()));
        }
        
        if (data.getCountAndDescription() != null) {
            props.add(new NodeProperty<>(
                    SCOUtils.OCCURANCES_COLUMN_NAME,
                    SCOUtils.OCCURANCES_COLUMN_NAME,
                    data.getCountAndDescription().getRight(),
                    data.getCountAndDescription().getLeft()));
        }
        
        if (!props.isEmpty()) {
            scoSupporter.updateSheet(props);
        }
    }

    /**
     * Class for passing the SCO data.
     */
    public static class SCOData {
        
        private final Pair<Score, String> scoreAndDescription;
        private final DataResultViewerTable.HasCommentStatus comment;
        private final Pair<Long, String> countAndDescription;
        private final Long contentId;

        /**
         * Construct a new SCOData object.
         *
         * @param scoreAndDescription
         * @param comment
         * @param countAndDescription
         */
        SCOData(Pair<Score, String> scoreAndDescription, DataResultViewerTable.HasCommentStatus comment, Pair<Long, String> countAndDescription, Long contentId) {
            this.scoreAndDescription = scoreAndDescription;
            this.comment = comment;
            this.countAndDescription = countAndDescription;
            this.contentId = contentId;
        }
        
        Pair<Score, String> getScoreAndDescription() {
            return scoreAndDescription;
        }
        
        DataResultViewerTable.HasCommentStatus getComment() {
            return comment;
        }
        
        Pair<Long, String> getCountAndDescription() {
            return countAndDescription;
        }
        
        Long getContentId() {
            return contentId;
        }
    } 
}
