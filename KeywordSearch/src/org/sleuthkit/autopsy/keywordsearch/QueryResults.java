/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2015 Basis Technology Corp.
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
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.aggregate.ProgressContributor;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;

/**
 * Stores the results from running a Solr query (which could contain multiple
 * keywords).
 *
 */
class QueryResults {

    private static final Logger logger = Logger.getLogger(QueryResults.class.getName());
    private static final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();
    /**
     * The query that generated the results.
     */
    private final KeywordSearchQuery keywordSearchQuery;

    /**
     * A map of keywords to keyword hits.
     */
    private final Map<Keyword, List<KeywordHit>> results = new HashMap<>();

    /**
     * The list of keywords
     */
    // TODO: This is redundant. The keyword list is in the query. 
    private final KeywordList keywordList;

    QueryResults(KeywordSearchQuery query, KeywordList keywordList) {
        this.keywordSearchQuery = query;
        this.keywordList = keywordList;
    }

    void addResult(Keyword keyword, List<KeywordHit> hits) {
        results.put(keyword, hits);
    }

    // TODO: This is redundant. The keyword list is in the query.  
    KeywordList getKeywordList() {
        return keywordList;
    }

    KeywordSearchQuery getQuery() {
        return keywordSearchQuery;
    }

    List<KeywordHit> getResults(Keyword keyword) {
        return results.get(keyword);
    }

    Set<Keyword> getKeywords() {
        return results.keySet();
    }

    /**
     * Writes the keyword hits encapsulated in this query result to the
     * blackboard. Makes one artifact per keyword per searched object (file or
     * artifact), i.e., if a keyword is found several times in the object, only
     * one artifact is created.
     *
     * @param progress    Can be null.
     * @param subProgress Can be null.
     * @param worker      The Swing worker that is writing the hits, needed to
     *                    support cancellation.
     * @param notifyInbox Whether or not write a message to the ingest messages
     *                    inbox.
     *
     * @return The artifacts that were created.
     */
    Collection<BlackboardArtifact> writeAllHitsToBlackBoard(ProgressHandle progress, ProgressContributor subProgress, SwingWorker<Object, Void> worker, boolean notifyInbox) {
        final Collection<BlackboardArtifact> newArtifacts = new ArrayList<>();
        if (progress != null) {
            progress.start(getKeywords().size());
        }
        int unitProgress = 0;

        for (final Keyword keyword : getKeywords()) {
            if (worker.isCancelled()) {
                logger.log(Level.INFO, "Cancel detected, bailing before new keyword processed: {0}", keyword.getSearchTerm()); //NON-NLS
                break;
            }

            // Update progress object(s), if any
            if (progress != null) {
                progress.progress(keyword.toString(), unitProgress);
            }
            if (subProgress != null) {
                String hitDisplayStr = keyword.getSearchTerm();
                if (hitDisplayStr.length() > 50) {
                    hitDisplayStr = hitDisplayStr.substring(0, 49) + "...";
                }
                subProgress.progress(keywordList.getName() + ": " + hitDisplayStr, unitProgress);
            }

            for (KeywordHit hit : getOneHitPerObject(keyword)) {
                String termString = keyword.getSearchTerm();
                final String snippetQuery = KeywordSearchUtil.escapeLuceneQuery(termString);
                String snippet;
                try {
                    snippet = LuceneQuery.querySnippet(snippetQuery, hit.getSolrObjectId(), hit.getChunkId(), !keywordSearchQuery.isLiteral(), true);
                } catch (NoOpenCoreException e) {
                    logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e); //NON-NLS
                    //no reason to continue
                    break;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e); //NON-NLS
                    continue;
                }
                if (snippet != null) {
                    KeywordCachedArtifact writeResult = keywordSearchQuery.writeSingleFileHitsToBlackBoard(termString, hit, snippet, keywordList.getName());
                    if (writeResult != null) {
                        newArtifacts.add(writeResult.getArtifact());
                        if (notifyInbox) {
                            writeSingleFileInboxMessage(writeResult, hit.getContent());
                        }
                    } else {
                        logger.log(Level.WARNING, "BB artifact for keyword hit not written, file: {0}, hit: {1}", new Object[]{hit.getContent(), keyword.toString()}); //NON-NLS
                    }
                }
            }
            ++unitProgress;
        }

        // Update artifact browser
        if (!newArtifacts.isEmpty()) {
            newArtifacts.stream()
                    //group artifacts by type
                    .collect(Collectors.groupingBy(BlackboardArtifact::getArtifactTypeID))
                    //for each type send an event
                    .forEach((typeID, artifacts) ->
                            IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.fromID(typeID), artifacts)));

        }

        return newArtifacts;
    }

    /**
     * Gets the first hit of the keyword.
     *
     * @param keyword
     *
     * @return Collection<KeywordHit> containing KeywordHits with lowest
     *         SolrObjectID-ChunkID pairs.
     */
    private Collection<KeywordHit> getOneHitPerObject(Keyword keyword) {

        HashMap<Long, KeywordHit> hits = new HashMap<>();

        // create a list of KeywordHits. KeywordHits with lowest chunkID is added the the list.
        for (KeywordHit hit : getResults(keyword)) {
            if (!hits.containsKey(hit.getSolrObjectId())) {
                hits.put(hit.getSolrObjectId(), hit);
            } else if (hit.getChunkId() < hits.get(hit.getSolrObjectId()).getChunkId()) {
                hits.put(hit.getSolrObjectId(), hit);
            }
        }
        return hits.values();
    }

    /**
     * Generate an ingest inbox message for given keyword in given file
     *
     * @param written
     * @param hitFile
     */
    private void writeSingleFileInboxMessage(KeywordCachedArtifact written, Content hitContent) {
        StringBuilder subjectSb = new StringBuilder();
        StringBuilder detailsSb = new StringBuilder();

        if (!keywordSearchQuery.isLiteral()) {
            subjectSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.regExpHitLbl"));
        } else {
            subjectSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.kwHitLbl"));
        }
        String uniqueKey = null;
        BlackboardAttribute attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID());
        if (attr != null) {
            final String keyword = attr.getValueString();
            subjectSb.append(keyword);
            uniqueKey = keyword.toLowerCase();
        }

        //details
        detailsSb.append("<table border='0' cellpadding='4' width='280'>"); //NON-NLS
        //hit
        detailsSb.append("<tr>"); //NON-NLS
        detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.kwHitThLbl"));
        detailsSb.append("<td>").append(EscapeUtil.escapeHtml(attr.getValueString())).append("</td>"); //NON-NLS
        detailsSb.append("</tr>"); //NON-NLS

        //preview
        attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID());
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
        attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());
        if (attr != null) {
            detailsSb.append("<tr>"); //NON-NLS
            detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.listThLbl"));
            detailsSb.append("<td>").append(attr.getValueString()).append("</td>"); //NON-NLS
            detailsSb.append("</tr>"); //NON-NLS
        }
        //regex
        if (!keywordSearchQuery.isLiteral()) {
            attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID());
            if (attr != null) {
                detailsSb.append("<tr>"); //NON-NLS
                detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.regExThLbl"));
                detailsSb.append("<td>").append(attr.getValueString()).append("</td>"); //NON-NLS
                detailsSb.append("</tr>"); //NON-NLS
            }
        }
        detailsSb.append("</table>"); //NON-NLS

        IngestServices.getInstance().postMessage(IngestMessage.createDataMessage(MODULE_NAME, subjectSb.toString(), detailsSb.toString(), uniqueKey, written.getArtifact()));
    }

}
