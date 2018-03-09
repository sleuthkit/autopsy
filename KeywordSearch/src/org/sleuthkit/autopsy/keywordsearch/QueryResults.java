/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.SwingWorker;
import org.apache.commons.lang.StringUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.aggregate.ProgressContributor;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Stores and processes the results of a keyword search query. Processing
 * includes posting keyword hit artifacts to the blackboard, sending messages
 * about the search hits to the ingest inbox, and publishing an event to notify
 * subscribers of the blackboard posts.
 */
class QueryResults {

    private static final Logger logger = Logger.getLogger(QueryResults.class.getName());
    private static final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();
    private final KeywordSearchQuery query;
    private final Map<Keyword, List<KeywordHit>> results = new HashMap<>();

    /**
     * Constructs a object that stores and processes the results of a keyword
     * search query. Processing includes posting keyword hit artifacts to the
     * blackboard, sending messages about the search hits to the ingest inbox,
     * and publishing an event to notify subscribers of the blackboard posts.
     *
     * The KeywordSearchQuery is used to do the blackboard posts.
     * 
     * @param query The query.
     */
    QueryResults(KeywordSearchQuery query) {
        this.query = query;
    }

    /**
     * Gets the keyword search query that generated the results stored in this
     * object.
     *
     * @return The query.
     */
    KeywordSearchQuery getQuery() {
        return query;
    }

    /**
     * Adds the keyword hits for a keyword to the hits that are stored in this
     * object. All calls to this method MUST be completed before calling the
     * process method.
     *
     * @param keyword The keyword,
     * @param hits    The hits.
     */
    void addResult(Keyword keyword, List<KeywordHit> hits) {
        results.put(keyword, hits);
    }

    /**
     * Gets the keyword hits stored in this object for a given keyword.
     *
     * @param keyword The keyword.
     *
     * @return The keyword hits.
     */
    List<KeywordHit> getResults(Keyword keyword) {
        return results.get(keyword);
    }

    /**
     * Gets the set of unique keywords for which keyword hits have been stored
     * in this object.
     *
     * @return
     */
    Set<Keyword> getKeywords() {
        return results.keySet();
    }

    /**
     * Processes the keyword hits stored in this object by osting keyword hit
     * artifacts to the blackboard, sending messages about the search hits to
     * the ingest inbox, and publishing an event to notify subscribers of the
     * blackboard posts.
     *
     * Makes one artifact per keyword per searched text source object (file or
     * artifact), i.e., if a keyword is found several times in the text
     * extracted from the source object, only one artifact is created.
     *
     * This method ASSUMES that the processing is being done using a SwingWorker
     * that should be checked for task cancellation.
     *
     * All calls to the addResult method MUST be completed before calling this
     * method.
     *
     * @param progress    A progress indicator that reports the number of
     *                    keywords processed. Can be null.
     * @param subProgress A progress contributor that reports the keyword
     *                    currently being processed. Can be null.
     * @param worker      The SwingWorker that is being used to do the
     *                    processing, will be checked for task cancellation
     *                    before processing each keyword.
     * @param notifyInbox Whether or not to write a message to the ingest
     *                    messages inbox if there is a keyword hit in the text
     *                    exrtacted from the text source object.
     *
     */
    void process(ProgressHandle progress, ProgressContributor subProgress, SwingWorker<?, ?> worker, boolean notifyInbox) {
        /*
         * Initialize the progress indicator to the number of keywords that will
         * be processed.
         */
        if (null != progress) {
            progress.start(getKeywords().size());
        }

        /*
         * Process the keyword hits for each keyword.
         */
        int keywordsProcessed = 0;
        final Collection<BlackboardArtifact> hitArtifacts = new ArrayList<>();
        for (final Keyword keyword : getKeywords()) {
            /*
             * Cancellation check.
             */
            if (worker.isCancelled()) {
                logger.log(Level.INFO, "Processing cancelled, exiting before processing search term {0}", keyword.getSearchTerm()); //NON-NLS
                break;
            }

            /*
             * Update the progress indicator and the show the current keyword
             * via the progress contributor.
             */
            if (progress != null) {
                progress.progress(keyword.toString(), keywordsProcessed);
            }
            if (subProgress != null) {
                String hitDisplayStr = keyword.getSearchTerm();
                if (hitDisplayStr.length() > 50) {
                    hitDisplayStr = hitDisplayStr.substring(0, 49) + "...";
                }
                subProgress.progress(query.getKeywordList().getName() + ": " + hitDisplayStr, keywordsProcessed);
            }

            /*
             * Reduce the hits for this keyword to one hit per text source
             * object so that only one hit artifact is generated per text source
             * object, no matter how many times the keyword was actually found.
             */
            for (KeywordHit hit : getOneHitPerTextSourceObject(keyword)) {
                /*
                 * Get a snippet (preview) for the hit. Regex queries always
                 * have snippets made from the content_str pulled back from Solr
                 * for executing the search. Other types of queries may or may
                 * not have snippets yet.
                 */
                String snippet = hit.getSnippet();
                if (StringUtils.isBlank(snippet)) {
                    final String snippetQuery = KeywordSearchUtil.escapeLuceneQuery(keyword.getSearchTerm());
                    try {
                        snippet = LuceneQuery.querySnippet(snippetQuery, hit.getSolrObjectId(), hit.getChunkId(), !query.isLiteral(), true);
                    } catch (NoOpenCoreException e) {
                        logger.log(Level.SEVERE, "Solr core closed while executing snippet query " + snippetQuery, e); //NON-NLS
                        break; // Stop processing.
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Error executing snippet query " + snippetQuery, e); //NON-NLS
                        continue; // Try processing the next hit.
                    }
                }

                /*
                 * Get the content (file or artifact) that is the text source
                 * for the hit.
                 */
                Content content = null;
                try {
                    SleuthkitCase tskCase = Case.getOpenCase().getSleuthkitCase();
                    content = tskCase.getContentById(hit.getContentID());
                } catch (TskCoreException | NoCurrentCaseException tskCoreException) {
                    logger.log(Level.SEVERE, "Failed to get text source object for ", tskCoreException); //NON-NLS
                }

                /*
                 * Post an artifact for the hit to the blackboard.
                 */
                BlackboardArtifact artifact = query.postKeywordHitToBlackboard(content, keyword, hit, snippet, query.getKeywordList().getName());

                /*
                 * Send an ingest inbox message for the hit.
                 */
                if (null != artifact) {
                    hitArtifacts.add(artifact);
                    if (notifyInbox) {
                        try {
                            writeSingleFileInboxMessage(artifact, content);
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Error sending message to ingest messages inbox", ex); //NON-NLS
                        }
                    }
                }
            }

            ++keywordsProcessed;
        }

        /*
         * Publish an event to notify subscribers of the blackboard posts. The
         * artifacts are grouped by type, since they may contain both
         * TSK_KEYWORD_HIT artifacts and TSK_ACCOUNT artifacts (for credit card
         * account number hits).
         */
        if (!hitArtifacts.isEmpty()) {
            hitArtifacts.stream()
                    // Group artifacts by type
                    .collect(Collectors.groupingBy(BlackboardArtifact::getArtifactTypeID))
                    // For each type send an event
                    .forEach((typeID, artifacts)
                            -> IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.fromID(typeID), artifacts)));

        }
    }

    /**
     * Reduce the hits for a given keyword to one hit per text source object so
     * that only one hit artifact is generated per text source object, no matter
     * how many times the keyword was actually found.
     *
     * @param keyword The keyword.
     *
     * @return Collection<KeywordHit> The reduced set of keyword hits.
     */
    private Collection<KeywordHit> getOneHitPerTextSourceObject(Keyword keyword) {
        /*
         * For each Solr document (chunk) for a text source object, return only
         * a single keyword hit from the first chunk of text (the one with the
         * lowest chunk id).
         */
        HashMap< Long, KeywordHit> hits = new HashMap<>();
        getResults(keyword).forEach((hit) -> {
            if (!hits.containsKey(hit.getSolrObjectId())) {
                hits.put(hit.getSolrObjectId(), hit);
            } else if (hit.getChunkId() < hits.get(hit.getSolrObjectId()).getChunkId()) {
                hits.put(hit.getSolrObjectId(), hit);
            }
        });
        return hits.values();
    }

    /**
     * Send an ingest inbox message indicating that there was a keyword hit in
     * the given text source object.
     *
     * @param artifact   The keyword hit artifact for the hit.
     * @param hitContent The text source object.
     *
     * @throws TskCoreException If there is a problem generating or send the
     *                          inbox message.
     */
    private void writeSingleFileInboxMessage(BlackboardArtifact artifact, Content hitContent) throws TskCoreException {
        StringBuilder subjectSb = new StringBuilder(1024);
        if (!query.isLiteral()) {
            subjectSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.regExpHitLbl"));
        } else {
            subjectSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.kwHitLbl"));
        }

        StringBuilder detailsSb = new StringBuilder(1024);
        String uniqueKey = null;
        BlackboardAttribute attr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD));
        if (attr != null) {
            final String keyword = attr.getValueString();
            subjectSb.append(keyword);
            uniqueKey = keyword.toLowerCase();
            detailsSb.append("<table border='0' cellpadding='4' width='280'>"); //NON-NLS
            detailsSb.append("<tr>"); //NON-NLS
            detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.kwHitThLbl"));
            detailsSb.append("<td>").append(EscapeUtil.escapeHtml(keyword)).append("</td>"); //NON-NLS
            detailsSb.append("</tr>"); //NON-NLS
        }

        //preview
        attr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW));
        if (attr != null) {
            detailsSb.append("<tr>"); //NON-NLS
            detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.previewThLbl"));
            detailsSb.append("<td>").append(EscapeUtil.escapeHtml(attr.getValueString())).append("</td>"); //NON-NLS
            detailsSb.append("</tr>"); //NON-NLS
        }

        //file
        detailsSb.append("<tr>"); //NON-NLS
        detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.fileThLbl"));
        if (hitContent instanceof AbstractFile) {
            AbstractFile hitFile = (AbstractFile) hitContent;
            detailsSb.append("<td>").append(hitFile.getParentPath()).append(hitFile.getName()).append("</td>"); //NON-NLS
        } else {
            detailsSb.append("<td>").append(hitContent.getName()).append("</td>"); //NON-NLS
        }
        detailsSb.append("</tr>"); //NON-NLS

        //list
        attr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
        if (attr != null) {
            detailsSb.append("<tr>"); //NON-NLS
            detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.listThLbl"));
            detailsSb.append("<td>").append(attr.getValueString()).append("</td>"); //NON-NLS
            detailsSb.append("</tr>"); //NON-NLS
        }

        //regex
        if (!query.isLiteral()) {
            attr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP));
            if (attr != null) {
                detailsSb.append("<tr>"); //NON-NLS
                detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.regExThLbl"));
                detailsSb.append("<td>").append(attr.getValueString()).append("</td>"); //NON-NLS
                detailsSb.append("</tr>"); //NON-NLS
            }
        }
        detailsSb.append("</table>"); //NON-NLS

        IngestServices.getInstance().postMessage(IngestMessage.createDataMessage(MODULE_NAME, subjectSb.toString(), detailsSb.toString(), uniqueKey, artifact));
    }
}
