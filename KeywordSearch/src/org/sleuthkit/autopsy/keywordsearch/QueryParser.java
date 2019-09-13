/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parse query using Solr
 */
class QueryParser {

  static class Result {
    /**
     * field name -> [term]
     */
    Map<String, List<String>> fieldTermsMap;
  }

  /**
   * Parse the given query string on Solr and return the result
   */
  static Result parse(String query, List<Server.Schema> fields) throws KeywordSearchModuleException, NoOpenCoreException {
    SolrQuery q = new SolrQuery();
    q.setShowDebugInfo(true);
    q.setQuery(fields.stream().map(f -> String.format("%s:%s", f, KeywordSearchUtil.escapeLuceneQuery(query))).collect(Collectors.joining(" OR ")));
    q.setRows(0);

    QueryResponse response = KeywordSearch.getServer().query(q, SolrRequest.METHOD.POST);
    Map<String, Object> debugMap = response.getDebugMap();
    String parsedQuery = debugMap.getOrDefault("parsedquery", "").toString();

    Result result = new Result();
    result.fieldTermsMap = getFieldTermsMap(parsedQuery);
    return result;
  }

  static Map<String, List<String>> getFieldTermsMap(String parsedQuery) {
    Map<String, List<String>> map = new HashMap<>();

    for (String fieldTermStr : parsedQuery.split(" ")) {
      String[] fieldTerm = fieldTermStr.split(":");
      if (fieldTerm.length != 2) {
        continue;
      }

      String field = fieldTerm[0];
      String term = fieldTerm[1];

      List<String> terms = map.getOrDefault(field, new ArrayList<>());
      terms.add(term);

      map.put(field, terms);
    }

    return map;
  }
}
