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

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.FieldAnalysisRequest;
import org.apache.solr.client.solrj.response.AnalysisResponseBase;
import org.apache.solr.client.solrj.response.FieldAnalysisResponse;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;

/**
 * Get terms from query using Solr.
 *
 * This class is used to find matched terms from query results.
 */
final class QueryTermHelper {

    private QueryTermHelper() {}

    /**
     * Result of {@link #parse} method
     */
    static class Result {
        /**
         * field name -> [term]
         */
        final Map<String, List<String>> fieldTermsMap = new HashMap<>();
    }

    /**
     * Parse the given query string on Solr and return the result
     *
     * @param query query to parse
     * @param fields field names to use for parsing
     */
    static Result parse(String query, List<Server.Schema> fields) throws KeywordSearchModuleException, NoOpenCoreException {
        Server server = KeywordSearch.getServer();

        FieldAnalysisRequest request = new FieldAnalysisRequest();
        for (Server.Schema field : fields) {
            request.addFieldName(field.toString());
        }
        // FieldAnalysisRequest requires to set its field value property,
        // while the corresponding analysis.fieldvalue parameter is not needed in the API.
        // Setting an empty value does not effect on the result.
        request.setFieldValue("");
        request.setQuery(query);

        FieldAnalysisResponse response = new FieldAnalysisResponse();
        try {
            response.setResponse(server.request(request));
        } catch (SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
            throw new KeywordSearchModuleException(e);
        }

        Result result = new Result();
        for (Map.Entry<String, FieldAnalysisResponse.Analysis> entry : response.getAllFieldNameAnalysis()) {
            Iterator<AnalysisResponseBase.AnalysisPhase> it = entry.getValue().getQueryPhases().iterator();

            // The last phase is the one which is used in the search process.
            AnalysisResponseBase.AnalysisPhase lastPhase = null;
            while (it.hasNext()) {
                lastPhase = it.next();
            }

            if (lastPhase != null) {
                List<String> tokens = lastPhase.getTokens().stream().map(AnalysisResponseBase.TokenInfo::getText).collect(Collectors.toList());
                result.fieldTermsMap.put(entry.getKey(), tokens);
            }
        }

        return result;
    }
}
