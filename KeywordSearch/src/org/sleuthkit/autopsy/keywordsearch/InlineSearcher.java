/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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

import com.twelvemonkeys.lang.StringUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.validator.routines.DomainValidator;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.keywordsearch.Chunker.Chunk;
import static org.sleuthkit.autopsy.keywordsearch.RegexQuery.CREDIT_CARD_NUM_PATTERN;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

final class InlineSearcher {

    private final List<KeywordList> keywordList;
    private static final int MIN_EMAIL_ADDR_LENGTH = 8;
    private static final Logger logger = Logger.getLogger(InlineSearcher.class.getName());

    private final Map<Keyword, Map<Keyword, List<KeywordHit>>> hitByKeyword = new HashMap<>();

    InlineSearcher(List<String> keywordListNames) {
        this.keywordList = new ArrayList<>();

        if (keywordListNames != null) {
            XmlKeywordSearchList loader = XmlKeywordSearchList.getCurrent();
            for (String name : keywordListNames) {
                keywordList.add(loader.getList(name));
            }
        }
    }

    void searchChunk(Chunk chunk) throws TskCoreException {
        for (KeywordList list : keywordList) {
            List<Keyword> keywords = list.getKeywords();
            for (Keyword originalKeyword : keywords) {
                Map<Keyword, List<KeywordHit>> hitMap = hitByKeyword.get(originalKeyword);
                if (hitMap == null) {
                    hitMap = new HashMap<>();
                    hitByKeyword.put(originalKeyword, hitMap);
                }

                List<KeywordHit> keywordHits = new ArrayList<>();
                if (originalKeyword.searchTermIsLiteral()) {
                    if (!originalKeyword.searchTermIsWholeWord()) {
                        if (StringUtil.containsIgnoreCase(chunk.geLowerCasedChunk(), originalKeyword.getSearchTerm())) {

                            keywordHits.addAll(createKeywordHits(chunk, originalKeyword));
                        }
                    } else {
                        String REGEX_FIND_WORD="(?i).*?\\b%s\\b.*?";
                        String regex=String.format(REGEX_FIND_WORD, Pattern.quote(originalKeyword.getSearchTerm().toLowerCase()));
                        if(chunk.geLowerCasedChunk().matches(regex)) {
                            keywordHits.addAll(createKeywordHits(chunk, originalKeyword));
                        }     
                    }
                } else {
                    String regex = originalKeyword.getSearchTerm();

                    try {
                        // validate the regex
                        Pattern pattern = Pattern.compile(regex);
                        Matcher matcher = pattern.matcher(chunk.geLowerCasedChunk());

                        if (matcher.find()) {
                            keywordHits.addAll(createKeywordHits(chunk, originalKeyword));
                        }
                    } catch (IllegalArgumentException ex) {
                        //TODO What should we do here? Log and continue?
                    }
                }

                if (!keywordHits.isEmpty()) {
                    for (KeywordHit hit : keywordHits) {
                        Keyword keywordCopy = new Keyword(hit.getHit(),
                                true,
                                true,
                                list.getName(),
                                originalKeyword.getOriginalTerm());

                        List<KeywordHit> mapHitList = hitMap.get(keywordCopy);
                        if (mapHitList == null) {
                            mapHitList = new ArrayList<>();
                            hitMap.put(keywordCopy, mapHitList);
                        }
                        mapHitList.add(hit);
                    }
                }
            }
        }
    }

    /**
     * This method very similar to RegexQuery createKeywordHits, with the knowledge
     * of solr removed.
     * 
     * @param chunk
     * @param originalKeyword
     * @return
     * @throws TskCoreException 
     */
    private List<KeywordHit> createKeywordHits(Chunk chunk, Keyword originalKeyword) throws TskCoreException {

        final HashMap<String, String> keywordsFoundInThisDocument = new HashMap<>();

        List<KeywordHit> hits = new ArrayList<>();
        String keywordString = originalKeyword.getSearchTerm();

        boolean queryStringContainsWildcardSuffix = originalKeyword.getSearchTerm().endsWith(".*");

        String searchPattern;
        if (originalKeyword.searchTermIsLiteral()) {
            /**
             * For substring searches, the following pattern was arrived at
             * through trial and error in an attempt to reproduce the same hits
             * we were getting when we were using the TermComponent approach.
             * This basically looks for zero of more word characters followed
             * optionally by a dot or apostrophe, followed by the quoted
             * lowercase substring following by zero or more word characters
             * followed optionally by a dot or apostrophe. The reason that the
             * dot and apostrophe characters are being handled here is because
             * the old code used to find hits in domain names (e.g. hacks.ie)
             * and possessives (e.g. hacker's). This obviously works for English
             * but is probably not sufficient for other languages.
             */
            searchPattern = "[\\w[\\.']]*" + java.util.regex.Pattern.quote(keywordString.toLowerCase()) + "[\\w[\\.']]*";
        } else {
            searchPattern = keywordString;
        }

        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(searchPattern, java.util.regex.Pattern.CASE_INSENSITIVE);

        try {
            String content = chunk.geLowerCasedChunk();
            Matcher hitMatcher = pattern.matcher(content);
            int offset = 0;

            while (hitMatcher.find(offset)) {

                String hit = hitMatcher.group();

                /**
                 * No need to continue on if the the string is "" nothing to
                 * find or do.
                 */
                if ("".equals(hit)) {
                    break;
                }

                offset = hitMatcher.end();
                final BlackboardAttribute.ATTRIBUTE_TYPE artifactAttributeType = originalKeyword.getArtifactAttributeType();

                // We attempt to reduce false positives for phone numbers and IP address hits
                // by querying Solr for hits delimited by a set of known boundary characters.
                // See KeywordSearchList.PHONE_NUMBER_REGEX for an example.
                // Because of this the hits may contain an extra character at the beginning or end that
                // needs to be chopped off, unless the user has supplied their own wildcard suffix
                // as part of the regex.
                if (!queryStringContainsWildcardSuffix
                        && (artifactAttributeType == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER
                        || artifactAttributeType == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IP_ADDRESS)) {
                    if (artifactAttributeType == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER) {
                        // For phone numbers replace all non numeric characters (except "(") at the start of the hit.
                        hit = hit.replaceAll("^[^0-9\\(]", "");
                    } else {
                        // Replace all non numeric characters at the start of the hit.
                        hit = hit.replaceAll("^[^0-9]", "");
                    }
                    // Replace all non numeric at the end of the hit.
                    hit = hit.replaceAll("[^0-9]$", "");

                    if (offset > 1) {
                        /*
                         * NOTE: our IP and phone number regex patterns look for
                         * boundary characters immediately before and after the
                         * keyword hit. After a match, Java pattern mather
                         * re-starts at the first character not matched by the
                         * previous match. This basically requires two boundary
                         * characters to be present between each pattern match.
                         * To mitigate this we are resetting the offest one
                         * character back.
                         */
                        offset--;
                    }
                }

                /**
                 * Boundary characters are removed from the start and end of the
                 * hit to normalize the hits. This is being done for substring
                 * searches only at this point. We don't do it for real regular
                 * expression searches because the user may have explicitly
                 * included boundary characters in their regular expression.
                 */
                if (originalKeyword.searchTermIsLiteral()) {
                    hit = hit.replaceAll("^" + KeywordSearchList.BOUNDARY_CHARACTERS + "*", "");
                    hit = hit.replaceAll(KeywordSearchList.BOUNDARY_CHARACTERS + "*$", "");
                }

                /**
                 * The use of String interning is an optimization to ensure that
                 * we reuse the same keyword hit String object across all hits.
                 * Even though we benefit from G1GC String deduplication, the
                 * overhead associated with creating a new String object for
                 * every KeywordHit can be significant when the number of hits
                 * gets large.
                 */
                hit = hit.intern();

                // We will only create one KeywordHit instance per document for
                // a given hit.
                if (keywordsFoundInThisDocument.containsKey(hit)) {
                    continue;
                }
                keywordsFoundInThisDocument.put(hit, hit);

                if (artifactAttributeType == null) {
                    hits.add(new KeywordHit(0, KeywordSearchUtil.makeSnippet(content, hitMatcher, hit), hit));
                } else {
                    switch (artifactAttributeType) {
                        case TSK_EMAIL:
                            /*
                             * Reduce false positives by eliminating email
                             * address hits that are either too short or are not
                             * for valid top level domains.
                             */
                            if (hit.length() >= MIN_EMAIL_ADDR_LENGTH
                                    && DomainValidator.getInstance(true).isValidTld(hit.substring(hit.lastIndexOf('.')))) {
                                hits.add(new KeywordHit(0, KeywordSearchUtil.makeSnippet(content, hitMatcher, hit), hit));
                            }

                            break;
                        case TSK_CARD_NUMBER:
                            /*
                             * If searching for credit card account numbers, do
                             * extra validation on the term and discard it if it
                             * does not pass.
                             */
                            Matcher ccnMatcher = CREDIT_CARD_NUM_PATTERN.matcher(hit);

                            for (int rLength = hit.length(); rLength >= 12; rLength--) {
                                ccnMatcher.region(0, rLength);
                                if (ccnMatcher.find()) {
                                    final String group = ccnMatcher.group("ccn");
                                    if (CreditCardValidator.isValidCCN(group)) {
                                        hits.add(new KeywordHit(0, KeywordSearchUtil.makeSnippet(content, hitMatcher, hit), hit));
                                    }
                                }
                            }

                            break;
                        default:
                            hits.add(new KeywordHit(0, KeywordSearchUtil.makeSnippet(content, hitMatcher, hit), hit));
                            break;
                    }
                }
            }

        } catch (Throwable error) {
            /*
             * NOTE: Matcher.find() is known to throw StackOverflowError in rare
             * cases (see JIRA-2700). StackOverflowError is an error, not an
             * exception, and therefore needs to be caught as a Throwable. When
             * this occurs we should re-throw the error as TskCoreException so
             * that it is logged by the calling method and move on to the next
             * Solr document.
             */
            throw new TskCoreException("Failed to create keyword hits for chunk due to " + error.getMessage());
        }
        return hits;
    }

    /**
     * Generates the artifacts for the found KeywordHits. This method should be
     * called once per content object.
     *
     * @param content
     * @param context
     * @param sourceID
     */
    void makeArtifacts(Content content, IngestJobContext context, long sourceID) {
        for (Map.Entry<Keyword, Map<Keyword, List<KeywordHit>>> item : hitByKeyword.entrySet()) {
            Keyword originalKeyword = item.getKey();
            Map<Keyword, List<KeywordHit>> map = item.getValue();

            List<BlackboardArtifact> hitArtifacts = new ArrayList<>();
            if (!map.isEmpty()) {
                for (Map.Entry<Keyword, List<KeywordHit>> entry : map.entrySet()) {
                    Keyword hitKeyword = entry.getKey();
                    List<KeywordHit> hitList = entry.getValue();
                    // Only create one hit for the document. 
                    // The first hit in the list should be the first one that
                    // was found.
                    if (!hitList.isEmpty()) {
                        KeywordHit hit = hitList.get(0);
                        hitArtifacts.add(RegexQuery.createKeywordHitArtifact(content, originalKeyword, hitKeyword, hit, hit.getSnippet(), hitKeyword.getListName(), sourceID));
                    }
                }

                if (!hitArtifacts.isEmpty()) {
                    try {
                        SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                        Blackboard blackboard = tskCase.getBlackboard();

                        blackboard.postArtifacts(hitArtifacts, "KeywordSearch", context.getJobId());
                    } catch (NoCurrentCaseException | Blackboard.BlackboardException ex) {
                        logger.log(Level.SEVERE, "Failed to post KWH artifact to blackboard.", ex); //NON-NLS
                    }
                }
            }

            // Just in case someone calls this method a second time for the given
            // content object the map will be cleared. 
            map.clear();
        }
    }
}
