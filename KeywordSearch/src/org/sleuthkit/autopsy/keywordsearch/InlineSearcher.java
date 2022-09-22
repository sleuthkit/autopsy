/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import com.twelvemonkeys.lang.StringUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.validator.routines.DomainValidator;
import org.sleuthkit.autopsy.keywordsearch.Chunker.Chunk;
import static org.sleuthkit.autopsy.keywordsearch.RegexQuery.CREDIT_CARD_NUM_PATTERN;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;


final class InlineSearcher {
    private final List<KeywordList> keywordList;
    private static final int MIN_EMAIL_ADDR_LENGTH = 8;
    private static final String SNIPPET_DELIMITER = String.valueOf(Character.toChars(171));
    
    private Map<Keyword, List<KeywordHit>> hitMap = new HashMap<>();
    
    InlineSearcher(List<String> keywordListNames) {
        this.keywordList = new ArrayList<>();
        
        if(keywordListNames != null) {
            XmlKeywordSearchList loader = XmlKeywordSearchList.getCurrent();
            for(String name: keywordListNames) {
                keywordList.add(loader.getList(name));
            }
        }
    }
    
    Map<Keyword, List<KeywordHit>> getHitMap() {
        return Collections.unmodifiableMap(hitMap);
    }
    
    List<KeywordHit> getHits(Keyword word) {
        return hitMap.get(word);
    }
    
    void searchChunk(Chunk chunk) throws TskCoreException {
        for(KeywordList list: keywordList) {
            List<Keyword> keywords = list.getKeywords();
            for(Keyword originalKeyword: keywords) {
                List<KeywordHit> keywordHits = new ArrayList<>();
                if(originalKeyword.searchTermIsLiteral()) {
                    if(!originalKeyword.searchTermIsWholeWord()) {
                        if(StringUtil.containsIgnoreCase(chunk.geLowerCasedChunk(), originalKeyword.getSearchTerm())) {
                            keywordHits.addAll(createKeywordHits(chunk, originalKeyword));
                        }
                    } else {
                        String regex = ".*\\b" + Pattern.quote(originalKeyword.getSearchTerm().toLowerCase()) + "\\b.*";
                        Pattern pattern = Pattern.compile(regex);
                        Matcher matcher = pattern.matcher(chunk.geLowerCasedChunk());
                        
                        if(matcher.find()) {
                           keywordHits.addAll(createKeywordHits(chunk, originalKeyword));
                        } 
                    }
                } else {
                    String regex = originalKeyword.getSearchTerm();
                    
                    try{
                        // validate the regex
                        Pattern pattern = Pattern.compile(regex, java.util.regex.Pattern.UNICODE_CHARACTER_CLASS);
                        Matcher matcher = pattern.matcher(chunk.geLowerCasedChunk());
                        
                        if(matcher.find()) {
                            keywordHits.addAll(createKeywordHits(chunk, originalKeyword));
                        }
                    } catch(IllegalArgumentException ex) {
                        //TODO What should we do here? Log and continue?
                    }
                }
                
                if(!keywordHits.isEmpty()) {
                    List<KeywordHit> mapHitList = hitMap.get(originalKeyword);
                    if(mapHitList == null) {
                        mapHitList = new ArrayList<>();
                        hitMap.put(originalKeyword, mapHitList);
                    }
                    
                    mapHitList.addAll(keywordHits);
                }
            }
        }
    }
    
    private List<KeywordHit> createKeywordHits(Chunk chunk, Keyword originalKeyword) throws TskCoreException {

        final HashMap<String, String> keywordsFoundInThisDocument = new HashMap<>();

        List<KeywordHit> hits = new ArrayList<>();
        final String docId = "";
        String keywordString = originalKeyword.getSearchTerm();
        
        boolean queryStringContainsWildcardSuffix = originalKeyword.getSearchTerm().endsWith(".*");

        //final Collection<Object> content_str = chunk.geLowerCasedChunk(); //solrDoc.getFieldValues(Server.Schema.CONTENT_STR.toString());

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
//            for (Object content_obj : content_str) {
                String content = chunk.geLowerCasedChunk();
                Matcher hitMatcher = pattern.matcher(content);
                int offset = 0;

                while (hitMatcher.find(offset)) {

                    String hit = hitMatcher.group();

                    /**
                     * No need to continue on if the the string is "" nothing to find or do.
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
                             * boundary characters immediately before and after
                             * the keyword hit. After a match, Java pattern
                             * mather re-starts at the first character not
                             * matched by the previous match. This basically
                             * requires two boundary characters to be present
                             * between each pattern match. To mitigate this we
                             * are resetting the offest one character back.
                             */
                            offset--;
                        }
                    }

                    /**
                     * Boundary characters are removed from the start and end of
                     * the hit to normalize the hits. This is being done for
                     * substring searches only at this point. We don't do it for
                     * real regular expression searches because the user may
                     * have explicitly included boundary characters in their
                     * regular expression.
                     */
                    if (originalKeyword.searchTermIsLiteral()) {
                        hit = hit.replaceAll("^" + KeywordSearchList.BOUNDARY_CHARACTERS + "*", "");
                        hit = hit.replaceAll(KeywordSearchList.BOUNDARY_CHARACTERS + "*$", "");
                    }

                    /**
                     * The use of String interning is an optimization to ensure
                     * that we reuse the same keyword hit String object across
                     * all hits. Even though we benefit from G1GC String
                     * deduplication, the overhead associated with creating a
                     * new String object for every KeywordHit can be significant
                     * when the number of hits gets large.
                     */
                    hit = hit.intern();

                    // We will only create one KeywordHit instance per document for
                    // a given hit.
                    if (keywordsFoundInThisDocument.containsKey(hit)) {
                        continue;
                    }
                    keywordsFoundInThisDocument.put(hit, hit);

                    if (artifactAttributeType == null) {
                        hits.add(new KeywordHit(0, makeSnippet(content, hitMatcher, hit), hit));
                    } else {
                        switch (artifactAttributeType) {
                            case TSK_EMAIL:
                                /*
                                 * Reduce false positives by eliminating email
                                 * address hits that are either too short or are
                                 * not for valid top level domains.
                                 */
                                if (hit.length() >= MIN_EMAIL_ADDR_LENGTH
                                        && DomainValidator.getInstance(true).isValidTld(hit.substring(hit.lastIndexOf('.')))) {
                                    hits.add(new KeywordHit(docId, makeSnippet(content, hitMatcher, hit), hit));
                                }

                                break;
                            case TSK_CARD_NUMBER:
                                /*
                                 * If searching for credit card account numbers,
                                 * do extra validation on the term and discard
                                 * it if it does not pass.
                                 */
                                Matcher ccnMatcher = CREDIT_CARD_NUM_PATTERN.matcher(hit);

                                for (int rLength = hit.length(); rLength >= 12; rLength--) {
                                    ccnMatcher.region(0, rLength);
                                    if (ccnMatcher.find()) {
                                        final String group = ccnMatcher.group("ccn");
                                        if (CreditCardValidator.isValidCCN(group)) {
                                            hits.add(new KeywordHit(docId, makeSnippet(content, hitMatcher, hit), hit));
                                        }
                                    }
                                }

                                break;
                            default:
                                hits.add(new KeywordHit(docId, makeSnippet(content, hitMatcher, hit), hit));
                                break;
                        }
                    }
                }
//            }
        } catch (Throwable error) {
            /*
             * NOTE: Matcher.find() is known to throw StackOverflowError in rare
             * cases (see JIRA-2700). StackOverflowError is an error, not an
             * exception, and therefore needs to be caught as a Throwable. When
             * this occurs we should re-throw the error as TskCoreException so
             * that it is logged by the calling method and move on to the next
             * Solr document.
             */
            throw new TskCoreException("Failed to create keyword hits for Solr document id " + docId + " due to " + error.getMessage());
        }
        return hits;
    }
    
    /**
     * Make a snippet from the given content that has the given hit plus some
     * surrounding context.
     *
     * @param content    The content to extract the snippet from.
     *
     * @param hitMatcher The Matcher that has the start/end info for where the
     *                   hit is in the content.
     * @param hit        The actual hit in the content.
     *
     * @return A snippet extracted from content that contains hit plus some
     *         surrounding context.
     */
    private String makeSnippet(String content, Matcher hitMatcher, String hit) {
        // Get the snippet from the document.
        int maxIndex = content.length() - 1;
        final int end = hitMatcher.end();
        final int start = hitMatcher.start();

        return content.substring(Integer.max(0, start - 20), Integer.max(0, start))
                + SNIPPET_DELIMITER + hit + SNIPPET_DELIMITER
                + content.substring(Integer.min(maxIndex, end), Integer.min(maxIndex, end + 20));
    }
}
