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
import java.util.logging.Level;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountInstance;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Background task to get Score, Comment and Occurrences values for an Abstract
 * content node.
 *
 */
class GetSCOTask implements Runnable {

    private final WeakReference<AbstractContentNode<?>> weakNodeRef;
    private static final Logger logger = Logger.getLogger(GetSCOTask.class.getName());
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
        Pair<Score, String> scoreAndDescription;
        Pair<Long, String> countAndDescription = null;
        scoreAndDescription = contentNode.getScorePropertyAndDescription();

        String description = Bundle.GetSCOTask_occurrences_defaultDescription();
        List<CorrelationAttributeInstance> listOfPossibleAttributes = new ArrayList<>();
        Content contentFromNode = contentNode.getContent();
        //the lists returned will be empty if the CR is not enabled
        if (contentFromNode instanceof AbstractFile) {
            listOfPossibleAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((AbstractFile) contentFromNode));
        } else if (contentFromNode instanceof AnalysisResult) {
            listOfPossibleAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((AnalysisResult) contentFromNode));
        } else if (contentFromNode instanceof DataArtifact) {
            listOfPossibleAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((DataArtifact) contentFromNode));
        } else if (contentFromNode instanceof OsAccount) {
            try {
                List<OsAccountInstance> osAccountInstances = ((OsAccount) contentFromNode).getOsAccountInstances();

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
                logger.log(Level.SEVERE, "Unable to get the DataSource or OsAccountInstances from an OsAccount with ID: " + contentFromNode.getId(), ex);
            }
        }
        DataResultViewerTable.HasCommentStatus commentStatus = contentNode.getCommentProperty(contentNode.getAllTagsFromDatabase(), listOfPossibleAttributes);
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
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        // signal SCO data is available.
        if (listener != null) {
            listener.propertyChange(new PropertyChangeEvent(
                    AutopsyEvent.SourceType.LOCAL.toString(),
                    AbstractAbstractFileNode.NodeSpecificEvents.SCO_AVAILABLE.toString(),
                    null, new SCOData(scoreAndDescription, commentStatus, countAndDescription)));
        }
    }
}
