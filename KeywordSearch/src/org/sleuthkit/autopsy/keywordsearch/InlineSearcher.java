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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
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
import org.sleuthkit.datamodel.TskException;

final class InlineSearcher {

    private final List<KeywordList> keywordList;
    private static final int MIN_EMAIL_ADDR_LENGTH = 8;
    private static final Logger logger = Logger.getLogger(InlineSearcher.class.getName());

    private final IngestJobContext context;

    static final Map<Long, List<UniqueKeywordHit>> uniqueHitMap = new ConcurrentHashMap<>();

    static final Map<Long, Map<Long, Map<Keyword, Map<Keyword, List<UniqueKeywordHit>>>>> uniqueHitMap2 = new ConcurrentHashMap<>();

    // Uses mostly native java and the lucene api to search the a given chuck
    // for Keywords. Create unique KeywordHits for any unique hit.
    InlineSearcher(List<String> keywordListNames, IngestJobContext context) {
        this.keywordList = new ArrayList<>();
        this.context = context;

        if (keywordListNames != null) {
            XmlKeywordSearchList loader = XmlKeywordSearchList.getCurrent();
            for (String name : keywordListNames) {
                keywordList.add(loader.getList(name));
            }
        }
    }

    /**
     * Search the chunk for the currently selected keywords.
     *
     * @param chunk
     * @param sourceID
     *
     * @throws TskCoreException
     */
    boolean searchChunk(Chunk chunk, long sourceID, int chunkId) throws TskCoreException {
        return searchString(chunk.getLowerCasedChunk(), sourceID, chunkId);
    }

    /**
     * Search a string for the currently selected keywords.
     *
     * @param text
     * @param sourceID
     *
     * @throws TskCoreException
     */
    boolean searchString(String text, long sourceID, int chunkId) throws TskCoreException {
        boolean hitFound = false;
        Map<Keyword, Map<Keyword, List<UniqueKeywordHit>>> hitByKeyword = getMap(context.getJobId(), sourceID);
        for (KeywordList list : keywordList) {
            List<Keyword> keywords = list.getKeywords();
            for (Keyword originalKeyword : keywords) {
                Map<Keyword, List<UniqueKeywordHit>> hitMap = hitByKeyword.get(originalKeyword);
                if (hitMap == null) {
                    hitMap = new HashMap<>();
                    hitByKeyword.put(originalKeyword, hitMap);
                }

                List<UniqueKeywordHit> keywordHits = new ArrayList<>();
                if (originalKeyword.searchTermIsLiteral()) {
                    if (StringUtil.containsIgnoreCase(text, originalKeyword.getSearchTerm())) {
                        keywordHits.addAll(createKeywordHits(text, originalKeyword, sourceID, chunkId, list.getName()));
                    }
                } else {
                    String regex = originalKeyword.getSearchTerm();

                    try {
                        // validate the regex
                        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                        Matcher matcher = pattern.matcher(text);

                        if (matcher.find()) {
                            keywordHits.addAll(createKeywordHits(text, originalKeyword, sourceID, chunkId, list.getName()));
                        }
                    } catch (IllegalArgumentException ex) {
                        //TODO What should we do here? Log and continue?
                    }
                }

                if (!keywordHits.isEmpty()) {
                    hitFound = true;
                    for (UniqueKeywordHit hit : keywordHits) {
                        Keyword keywordCopy = new Keyword(hit.getHit(),
                                originalKeyword.searchTermIsLiteral(),
                                originalKeyword.searchTermIsWholeWord(),
                                list.getName(),
                                originalKeyword.getOriginalTerm());

                        List<UniqueKeywordHit> mapHitList = hitMap.get(keywordCopy);
                        if (mapHitList == null) {
                            mapHitList = new ArrayList<>();
                            hitMap.put(keywordCopy, mapHitList);
                        }

                        if (!mapHitList.contains(hit)) {
                            mapHitList.add(hit);
                        }
                    }
                }

                if (context.fileIngestIsCancelled()) {
                    return hitFound;
                }
            }
        }
        return hitFound;
    }

    /**
     * This method very similar to RegexQuery createKeywordHits, with the
     * knowledge of solr removed.
     *
     * @param text
     * @param originalKeyword
     *
     * @return A list of KeywordHit objects.
     *
     * @throws TskCoreException
     */
    private List<UniqueKeywordHit> createKeywordHits(String text, Keyword originalKeyword, long sourceID, int chunkId, String keywordListName) throws TskCoreException {

        if (originalKeyword.searchTermIsLiteral() && originalKeyword.searchTermIsWholeWord()) {
            try {
                return getExactMatchHits(text, originalKeyword, sourceID, chunkId, keywordListName);
            } catch (IOException ex) {
                throw new TskCoreException("Failed to create exactMatch hits", ex);
            }
        }

        final HashMap<String, String> keywordsFoundInThisDocument = new HashMap<>();

        List<UniqueKeywordHit> hits = new ArrayList<>();
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

        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(searchPattern, Pattern.CASE_INSENSITIVE);

        try {
            String content = text;
            Matcher hitMatcher = pattern.matcher(content);
            int offset = 0;

            while (hitMatcher.find(offset)) {

                String hit = hitMatcher.group().toLowerCase();

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
                    hits.add(new UniqueKeywordHit(chunkId, sourceID, KeywordSearchUtil.makeSnippet(content, hitMatcher, hit), hit, keywordListName, originalKeyword.searchTermIsWholeWord(), originalKeyword.searchTermIsLiteral(), originalKeyword.getArtifactAttributeType(), originalKeyword.getSearchTerm()));
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
                                hits.add(new UniqueKeywordHit(chunkId, sourceID, KeywordSearchUtil.makeSnippet(content, hitMatcher, hit), hit, keywordListName, originalKeyword.searchTermIsWholeWord(), originalKeyword.searchTermIsLiteral(), originalKeyword.getArtifactAttributeType(), originalKeyword.getSearchTerm()));
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
                                        hits.add(new UniqueKeywordHit(chunkId, sourceID, KeywordSearchUtil.makeSnippet(content, hitMatcher, hit), hit, keywordListName, originalKeyword.searchTermIsWholeWord(), originalKeyword.searchTermIsLiteral(), originalKeyword.getArtifactAttributeType(), originalKeyword.getSearchTerm()));
                                    }
                                }
                            }

                            break;
                        default:
                            hits.add(new UniqueKeywordHit(chunkId, sourceID, KeywordSearchUtil.makeSnippet(content, hitMatcher, hit), hit, keywordListName, originalKeyword.searchTermIsWholeWord(), originalKeyword.searchTermIsLiteral(), originalKeyword.getArtifactAttributeType(), originalKeyword.getSearchTerm()));
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
     * Clean up the memory that is being used for the given job.
     *
     * @param context
     */
    static void cleanup(IngestJobContext context) {
        Map<Long, Map<Keyword, Map<Keyword, List<UniqueKeywordHit>>>> jobMap = uniqueHitMap2.get(context.getJobId());
        if (jobMap != null) {
            jobMap.clear();
        }
    }

    /**
     * Generates the artifacts for the found KeywordHits. This method should be
     * called once per content object.
     *
     * @param context
     */
    static void makeArtifacts(IngestJobContext context) throws TskException {

        Map<Long, Map<Keyword, Map<Keyword, List<UniqueKeywordHit>>>> jobMap = uniqueHitMap2.get(context.getJobId());
        if (jobMap == null) {
            return;
        }

        for (Map.Entry<Long, Map<Keyword, Map<Keyword, List<UniqueKeywordHit>>>> mapBySource : jobMap.entrySet()) {
            Long sourceId = mapBySource.getKey();
            Map<Keyword, Map<Keyword, List<UniqueKeywordHit>>> mapByKeyword = mapBySource.getValue();

            for (Map.Entry<Keyword, Map<Keyword, List<UniqueKeywordHit>>> item : mapByKeyword.entrySet()) {
                Keyword originalKeyword = item.getKey();
                Map<Keyword, List<UniqueKeywordHit>> map = item.getValue();

                List<BlackboardArtifact> hitArtifacts = new ArrayList<>();
                if (!map.isEmpty()) {
                    for (Map.Entry<Keyword, List<UniqueKeywordHit>> entry : map.entrySet()) {
                        Keyword hitKeyword = entry.getKey();
                        List<UniqueKeywordHit> hitList = entry.getValue();
                        // Only create one hit for the document. 
                        // The first hit in the list should be the first one that
                        // was found.
                        if (!hitList.isEmpty()) {
                            UniqueKeywordHit hit = hitList.get(0);
                            SleuthkitCase tskCase = Case.getCurrentCase().getSleuthkitCase();
                            Content content = tskCase.getContentById(hit.getContentID());
                            BlackboardArtifact artifact;
                            if (hit.isLiteral() && hit.isWholeWord()) {
                                artifact = LuceneQuery.createKeywordHitArtifact(content, originalKeyword, hitKeyword, hit, hit.getSnippet(), hitKeyword.getListName(), sourceId);
                            } else {
                                artifact = RegexQuery.createKeywordHitArtifact(content, originalKeyword, hitKeyword, hit, hit.getSnippet(), hitKeyword.getListName(), sourceId);
                            }
                            // createKeywordHitArtifact has the potential to return null
                            // when a CCN account is created.
                            if (artifact != null) {
                                hitArtifacts.add(artifact);

                            }

                        }
                    }

                    if (!hitArtifacts.isEmpty()) {
                        try {
                            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                            Blackboard blackboard = tskCase.getBlackboard();

                            blackboard.postArtifacts(hitArtifacts, "KeywordSearch", context.getJobId());
                            hitArtifacts.clear();
                        } catch (NoCurrentCaseException | Blackboard.BlackboardException ex) {
                            logger.log(Level.SEVERE, "Failed to post KWH artifact to blackboard.", ex); //NON-NLS
                        }
                    }

                    if (context.fileIngestIsCancelled()) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * Searches the chunk for exact matches and creates the appropriate keyword
     * hits.
     *
     * @param text
     * @param originalKeyword
     * @param sourceID
     *
     * @return
     *
     * @throws IOException
     */
    public List<UniqueKeywordHit> getExactMatchHits(String text, Keyword originalKeyword, long sourceID, int chunkId, String keywordListName) throws IOException {
        final HashMap<String, String> keywordsFoundInThisDocument = new HashMap<>();

        List<UniqueKeywordHit> hits = new ArrayList<>();
        Analyzer analyzer = new StandardAnalyzer();

        //Get the tokens of the keyword
        List<String> keywordTokens = new ArrayList<>();
        try (TokenStream keywordstream = analyzer.tokenStream("field", originalKeyword.getSearchTerm())) {
            CharTermAttribute attr = keywordstream.addAttribute(CharTermAttribute.class);
            keywordstream.reset();
            while (keywordstream.incrementToken()) {
                keywordTokens.add(attr.toString());
            }
        }

        try (TokenStream stream = analyzer.tokenStream("field", text)) {
            CharTermAttribute attr = stream.addAttribute(CharTermAttribute.class);
            OffsetAttribute offset = stream.addAttribute(OffsetAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                if (!attr.toString().equals(keywordTokens.get(0))) {
                    continue;
                }

                int startOffset = offset.startOffset();
                int endOffset = offset.endOffset();
                boolean match = true;

                for (int index = 1; index < keywordTokens.size(); index++) {
                    if (stream.incrementToken()) {
                        if (!attr.toString().equals(keywordTokens.get(index))) {
                            match = false;
                            break;
                        } else {
                            endOffset = offset.endOffset();
                        }
                    }
                }

                if (match) {
                    String hit = text.subSequence(startOffset, endOffset).toString();

                    // We will only create one KeywordHit instance per document for
                    // a given hit.
                    if (keywordsFoundInThisDocument.containsKey(hit)) {
                        continue;
                    }
                    keywordsFoundInThisDocument.put(hit, hit);

                    hits.add(new UniqueKeywordHit(chunkId, sourceID, KeywordSearchUtil.makeSnippet(text, startOffset, endOffset, hit), hit, keywordListName, originalKeyword.searchTermIsWholeWord(), originalKeyword.searchTermIsLiteral(), originalKeyword.getArtifactAttributeType(), originalKeyword.getOriginalTerm()));
                }
            }
        }

        return hits;
    }

    /**
     * Get the keyword map for the given job and source.
     *
     * @param jobId
     * @param sourceID
     *
     * @return
     */
    static private Map<Keyword, Map<Keyword, List<UniqueKeywordHit>>> getMap(long jobId, long sourceID) {
        Map<Long, Map<Keyword, Map<Keyword, List<UniqueKeywordHit>>>> jobMap = uniqueHitMap2.get(jobId);
        if (jobMap == null) {
            jobMap = new ConcurrentHashMap<>();
            uniqueHitMap2.put(jobId, jobMap);
        }

        Map<Keyword, Map<Keyword, List<UniqueKeywordHit>>> sourceMap = jobMap.get(sourceID);
        if (sourceMap == null) {
            sourceMap = new ConcurrentHashMap<>();
            jobMap.put(sourceID, sourceMap);
        }

        return sourceMap;
    }

    // KeywordHit is not unique enough for finding duplicates, this class 
    // extends the KeywordHit class to make truely unique hits.
    static class UniqueKeywordHit extends KeywordHit {

        private final String listName;
        private final boolean isLiteral;
        private final boolean isWholeWord;
        private final BlackboardAttribute.ATTRIBUTE_TYPE artifactAtrributeType;
        private final String originalSearchTerm;

        UniqueKeywordHit(int chunkId, long sourceID, String snippet, String hit, String listName, boolean isWholeWord, boolean isLiteral, BlackboardAttribute.ATTRIBUTE_TYPE artifactAtrributeType, String originalSearchTerm) {
            super(chunkId, sourceID, snippet, hit);

            this.listName = listName;
            this.isWholeWord = isWholeWord;
            this.isLiteral = isLiteral;
            this.artifactAtrributeType = artifactAtrributeType;
            this.originalSearchTerm = originalSearchTerm;
        }

        @Override
        public int compareTo(KeywordHit other) {
            return compare((UniqueKeywordHit) other);
        }

        private int compare(UniqueKeywordHit other) {
            return Comparator.comparing(UniqueKeywordHit::getSolrObjectId)
                    .thenComparing(UniqueKeywordHit::getChunkId)
                    .thenComparing(UniqueKeywordHit::getHit)
                    .thenComparing(UniqueKeywordHit::getSnippet)
                    .thenComparing(UniqueKeywordHit::isWholeWord)
                    .thenComparing(UniqueKeywordHit::isLiteral)
                    .thenComparing(UniqueKeywordHit::getArtifactAtrributeType)
                    .thenComparing(UniqueKeywordHit::getOriginalSearchTerm)
                    .thenComparing(UniqueKeywordHit::getListName)
                    .compare(this, other);
        }

        @Override
        public boolean equals(Object obj) {

            if (null == obj) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final UniqueKeywordHit other = (UniqueKeywordHit) obj;

            return getSnippet().equalsIgnoreCase(other.getSnippet())
                    && getSolrObjectId().equals(other.getSolrObjectId())
                    && getChunkId().equals(other.getChunkId())
                    && getHit().equalsIgnoreCase(other.getHit())
                    && listName.equalsIgnoreCase(other.getListName())
                    && isLiteral == other.isLiteral()
                    && isWholeWord == other.isWholeWord()
                    && originalSearchTerm.equalsIgnoreCase(other.getOriginalSearchTerm())
                    && (artifactAtrributeType != null ? artifactAtrributeType.equals(other.getArtifactAtrributeType()) : true);
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 67 * hash + super.hashCode();
            hash = 67 * hash + Objects.hashCode(this.listName);
            hash = 67 * hash + (this.isLiteral ? 1 : 0);
            hash = 67 * hash + (this.isWholeWord ? 1 : 0);
            hash = 67 * hash + Objects.hashCode(this.artifactAtrributeType);
            hash = 67 * hash + Objects.hashCode(this.originalSearchTerm);
            return hash;
        }

        String getListName() {
            return listName;
        }

        Boolean isLiteral() {
            return isLiteral;
        }

        Boolean isWholeWord() {
            return isWholeWord;
        }

        BlackboardAttribute.ATTRIBUTE_TYPE getArtifactAtrributeType() {
            return artifactAtrributeType;
        }

        String getOriginalSearchTerm() {
            return originalSearchTerm;
        }

    }
}
