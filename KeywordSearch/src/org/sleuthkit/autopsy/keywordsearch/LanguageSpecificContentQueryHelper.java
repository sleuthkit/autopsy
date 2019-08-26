package org.sleuthkit.autopsy.keywordsearch;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.TskException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class LanguageSpecificContentQueryHelper {

  private static final List<Server.Schema> QUERY_FIELDS = new ArrayList<>();
  private static final List<Server.Schema> LANGUAGE_SPECIFIC_CONTENT_FIELDS
      = Collections.singletonList(Server.Schema.CONTENT_JA);
  private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);

  static {
    QUERY_FIELDS.add(Server.Schema.TEXT);
    QUERY_FIELDS.addAll(LANGUAGE_SPECIFIC_CONTENT_FIELDS);
  }

  static class QueryResults {
    List<SolrDocument> chunks = new ArrayList<>();
    Map</* ID */ String, SolrDocument> miniChunks = new HashMap<>();
    // objectId_chunk -> "text" -> List of previews
    Map<String, Map<String, List<String>>> highlighting = new HashMap<>();
  }

  /**
   * Make a query string from the given one by applying it to the multiple query fields
   * @param queryStr escaped query string
   * @return query string
   */
  static String expandQueryString(final String queryStr) {
    List<String> fieldQueries = new ArrayList<>();
    fieldQueries.add(Server.Schema.TEXT.toString() + ":" + KeywordSearchUtil.quoteQuery(queryStr));
    fieldQueries.addAll(LANGUAGE_SPECIFIC_CONTENT_FIELDS.stream().map(field -> field.toString() + ":" + queryStr).collect(Collectors.toList()));
    return String.join(" OR ", fieldQueries);
  }

  static List<Server.Schema> getQueryFields() {
    return QUERY_FIELDS;
  }

  static void updateQueryResults(QueryResults results, SolrDocument document) {
    String id = (String) document.getFieldValue(Server.Schema.ID.toString());
    if (MiniChunks.isMiniChunkID(id)) {
      results.miniChunks.put(MiniChunks.getBaseChunkID(id), document);
    } else {
      results.chunks.add(document);
    }
  }

  /**
   * Get snippets
   *
   * @param highlight field ID -> snippets
   * @return snippets of appropriate fields.
   *   Note that this method returns {@code Optional.empty} if the result is empty for convenience to interact with the existing code.
   */
  static Optional<List<String>> getHighlights(Map<String, List<String>> highlight) {
    for (Server.Schema field : LANGUAGE_SPECIFIC_CONTENT_FIELDS) {
      if (highlight.containsKey(field.toString())) {
        return Optional.of(highlight.get(field.toString()));
      }
    }
    return Optional.empty();
  }

  /**
   * Merge KeywordHits from TEXT field and a language specific field
   *
   * Replace KeywordHits in the given {@code matches} if its chunk ID is same.
   */
  static List<KeywordHit> mergeKeywordHits(List<KeywordHit> matches, Keyword originalKeyword, QueryResults queryResults) throws KeywordSearchModuleException {
    Map<String, KeywordHit> map = findMatches(originalKeyword, queryResults).stream().collect(Collectors.toMap(KeywordHit::getSolrDocumentId, x -> x));
    List<KeywordHit> merged = new ArrayList<>();

    // first, replace KeywordHit in matches
    for (KeywordHit match : matches) {
      String key = match.getSolrDocumentId();
      if (map.containsKey(key)) {
        merged.add(map.get(key));
        map.remove(key);
      } else {
        merged.add(match);
      }
    }
    // second, add rest of KeywordHits from queryResults
    merged.addAll(map.values());

    return merged;
  }

  static void configureTermfreqQuery(SolrQuery query, String keyword) throws KeywordSearchModuleException, NoOpenCoreException {
    // make a request to Solr to parse query.
    QueryParser.Result queryParserResult = QueryParser.parse(keyword, LANGUAGE_SPECIFIC_CONTENT_FIELDS);
    query.addField(buildTermfreqQuery(keyword, queryParserResult));
  }

  static String buildTermfreqQuery(String keyword, QueryParser.Result result) {
    List<String> termfreqs = new ArrayList<>();
    for (Map.Entry<String, List<String>> e : result.fieldTermsMap.entrySet()) {
      String field = e.getKey();
      for (String term : e.getValue()) {
        termfreqs.add(String.format("termfreq(\"%s\",\"%s\")", field, KeywordSearchUtil.escapeLuceneQuery(term)));
      }
    }

    // sum of all language specific query fields.
    // only one of these fields could be non-zero.
    return String.format("termfreq:sum(%s)", String.join(",", termfreqs));
  }

  static int queryChunkTermfreq(Set<String> keywords, String contentID) throws KeywordSearchModuleException, NoOpenCoreException {
    SolrQuery q = new SolrQuery();
    q.setShowDebugInfo(DEBUG);

    final String filterQuery = Server.Schema.ID.toString() + ":" + KeywordSearchUtil.escapeLuceneQuery(contentID);
    final String highlightQuery = keywords.stream()
        .map(s -> LanguageSpecificContentQueryHelper.expandQueryString(KeywordSearchUtil.escapeLuceneQuery(s)))
        .collect(Collectors.joining(" "));

    q.addFilterQuery(filterQuery);
    q.setQuery(highlightQuery);
    LanguageSpecificContentQueryHelper.configureTermfreqQuery(q, keywords.iterator().next());

    QueryResponse response = KeywordSearch.getServer().query(q, SolrRequest.METHOD.POST);
    SolrDocumentList results = response.getResults();
    if (results.isEmpty()) {
      return 0;
    }

    SolrDocument document = results.get(0);
    return ((Float) document.getFieldValue(Server.Schema.TERMFREQ.toString())).intValue();
  }

  static int findNthIndexOf(String s, String pattern, int n) {
    int found = 0;
    int idx = -1;
    int len = s.length();
    while (idx < len && found <= n) {
      idx = s.indexOf(pattern, idx + 1);
      if (idx == -1) {
        break;
      }
      found++;
    }

    return idx;
  }

  private static List<KeywordHit> findMatches(Keyword originalKeyword, QueryResults queryResults) throws KeywordSearchModuleException {
    List<KeywordHit> matches = new ArrayList<>();
    for (SolrDocument document : queryResults.chunks) {
      String docId = (String) document.getFieldValue(Server.Schema.ID.toString());

      try {
        int hitCountInChunk = ((Float) document.getFieldValue(Server.Schema.TERMFREQ.toString())).intValue();
        SolrDocument miniChunk = queryResults.miniChunks.get(docId);
        if (miniChunk == null) {
          // last chunk does not have mini chunk because there's no overlapped region with next one
          matches.add(createKeywordHit(originalKeyword, queryResults.highlighting, docId));
        } else {
          int hitCountInMiniChunk = ((Float) miniChunk.getFieldValue(Server.Schema.TERMFREQ.toString())).intValue();
          if (hitCountInMiniChunk < hitCountInChunk) {
            // there are at least one hit in base chunk
            matches.add(createKeywordHit(originalKeyword, queryResults.highlighting, docId));
          }
        }
      } catch (TskException ex) {
        throw new KeywordSearchModuleException(ex);
      }
    }
    return matches;
  }

  /** copied from LuceneQuery and modified to use getHighlightFieldValue */
  private static KeywordHit createKeywordHit(Keyword originalKeyword, Map<String, Map<String, List<String>>> highlightResponse, String docId) throws TskException {
    /**
     * Get the first snippet from the document if keyword search is
     * configured to use snippets.
     */
    String snippet = "";
    if (KeywordSearchSettings.getShowSnippets()) {
      List<String> snippetList = getHighlightFieldValue(highlightResponse.get(docId)).orElse(null);
      // list is null if there wasn't a snippet
      if (snippetList != null) {
        snippet = EscapeUtil.unEscapeHtml(snippetList.get(0)).trim();
      }
    }

    return new KeywordHit(docId, snippet, originalKeyword.getSearchTerm());
  }

  /**
   * @return Optional.empty if empty
   */
  private static Optional<List<String>> getHighlightFieldValue(Map<String, List<String>> highlight) {
    for (Server.Schema field : LANGUAGE_SPECIFIC_CONTENT_FIELDS) {
      if (highlight.containsKey(field.toString())) {
        return Optional.of(highlight.get(field.toString()));
      }
    }
    return Optional.empty();
  }
}
