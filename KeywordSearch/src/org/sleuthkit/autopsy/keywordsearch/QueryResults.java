/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
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

/**
 * Stores the results from running a SOLR query. 
 * 
 */
class QueryResults {
    private static final Logger logger = Logger.getLogger(QueryResults.class.getName());
   
    // maps Keyword object to its hits -> This is the long-term idea
    private Map<Keyword, List<ContentHit>> resultsK = new HashMap<>();
    
    void addResult(Keyword keyword, List<ContentHit> hits) {
        resultsK.put(keyword, hits);
    }
    
    List<ContentHit> getResults(Keyword keyword) {
        return resultsK.get(keyword);
    }
    
    Set<Keyword> getKeywords() {
        return resultsK.keySet();        
    }
    
    /**
     * Get the unique set of files across all keywords in the results
     * @param results
     * @return 
     */
    LinkedHashMap<AbstractFile, ContentHit> getUniqueFiles() {
        LinkedHashMap<AbstractFile, ContentHit> flattened = new LinkedHashMap<>();

        for (Keyword keyWord : getKeywords()) {
            for (ContentHit hit : getResults(keyWord)) {
                AbstractFile abstractFile = hit.getContent();
                //flatten, record first chunk encountered
                if (!flattened.containsKey(abstractFile)) {
                    flattened.put(abstractFile, hit);
                }
            }
        }
        return flattened;
    }
    

    /**
     * Get the unique set of files for a specific keyword
     * @param keyword
     * @return 
     */
    Map<AbstractFile, Integer> getUniqueFiles(Keyword keyword) {
        Map<AbstractFile, Integer> ret = new LinkedHashMap<>();
        for (ContentHit h : getResults(keyword)) {
            AbstractFile f = h.getContent();
            if (!ret.containsKey(f)) {
                ret.put(f, h.getChunkId());
            }
        }

        return ret;
    }
     
    /**
     * Creates a blackboard artifact for each keyword hit
     * @param query
     * @param listName
     * @param progress    can be null
     * @param subProgress can be null
     * @param notifyInbox flag indicating whether or not to call writeInboxMessage() for each hit
     * @return list of new artifacts
     */
    public Collection<BlackboardArtifact> writeAllHitsToBlackBoard(KeywordSearchQuery query, String listName, ProgressHandle progress, ProgressContributor subProgress, SwingWorker<Object, Void> worker, boolean notifyInbox, boolean fullHitInfo) {
        final Collection<BlackboardArtifact> newArtifacts = new ArrayList<>();
        if (progress != null) {
            progress.start(getKeywords().size());
        }
        int unitProgress = 0;
        
        for (final Keyword hitTerm : getKeywords()) {           
            if (worker.isCancelled()) {
                logger.log(Level.INFO, "Cancel detected, bailing before new keyword processed: {0}", hitTerm.getQuery()); //NON-NLS
                break;
            }
            
            // Update progress object(s), if any
            if (progress != null) {
                progress.progress(hitTerm.toString(), unitProgress);
            }                                  
            if (subProgress != null) {
                String hitDisplayStr = hitTerm.getQuery();
                if (hitDisplayStr.length() > 50) {
                    hitDisplayStr = hitDisplayStr.substring(0, 49) + "...";
                }
                subProgress.progress(listName + ": " + hitDisplayStr, unitProgress);
            }
            
            // this returns the unique files in the set with the first chunk that has a hit
            Map<AbstractFile, Integer> flattened = getUniqueFiles(hitTerm);
            
            for (AbstractFile hitFile : flattened.keySet()) {
                String termHit = fullHitInfo ? hitTerm.toString() : hitTerm.getQuery();
                int chunkId = flattened.get(hitFile);
                final String snippetQuery = KeywordSearchUtil.escapeLuceneQuery(termHit);
                String snippet;
                try {
                    snippet = LuceneQuery.querySnippet(snippetQuery, hitFile.getId(), chunkId, !query.isLiteral(), true);
                } catch (NoOpenCoreException e) {
                    logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e); //NON-NLS
                    //no reason to continue
                    break;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e); //NON-NLS
                    continue;
                }
                if (snippet != null) {
                    KeywordWriteResult written = query.writeToBlackBoard(termHit, hitFile, snippet, listName);
                    
                    if (written != null) {
                        newArtifacts.add(written.getArtifact());
                        if (notifyInbox) {
                            writeInboxMessage(query, written, hitFile);
                        }
                    } else {
                        logger.log(Level.WARNING, "BB artifact for keyword hit not written, file: {0}, hit: {1}", new Object[]{hitFile, hitTerm.toString()}); //NON-NLS
                    }
                }
            }
            ++unitProgress;
        }
        
        // Update artifact browser
        if (!newArtifacts.isEmpty()) {
            IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(KeywordSearchModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT, newArtifacts));
        }        
        
        return newArtifacts;
    }    
    
    /**
     * Generate an ingest inbox message for given keyword in given file
     * @param query
     * @param written
     * @param hitFile 
     */
    public void writeInboxMessage(KeywordSearchQuery query, KeywordWriteResult written, AbstractFile hitFile) {
        StringBuilder subjectSb = new StringBuilder();
        StringBuilder detailsSb = new StringBuilder();

        if (!query.isLiteral()) {
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
        detailsSb.append("<td>").append(hitFile.getParentPath()).append(hitFile.getName()).append("</td>"); //NON-NLS
        detailsSb.append("</tr>"); //NON-NLS

        //list
        attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());
        detailsSb.append("<tr>"); //NON-NLS
        detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.listThLbl"));
        detailsSb.append("<td>").append(attr.getValueString()).append("</td>"); //NON-NLS
        detailsSb.append("</tr>"); //NON-NLS

        //regex
        if (!query.isLiteral()) {
            attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID());
            if (attr != null) {
                detailsSb.append("<tr>"); //NON-NLS
                detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.regExThLbl"));
                detailsSb.append("<td>").append(attr.getValueString()).append("</td>"); //NON-NLS
                detailsSb.append("</tr>"); //NON-NLS
            }
        }
        detailsSb.append("</table>"); //NON-NLS

        IngestServices.getInstance().postMessage(IngestMessage.createDataMessage(KeywordSearchModuleFactory.getModuleName(), subjectSb.toString(), detailsSb.toString(), uniqueKey, written.getArtifact()));
    }
    
}
