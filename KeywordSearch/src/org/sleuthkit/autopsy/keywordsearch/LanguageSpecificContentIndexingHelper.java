/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.solr.common.SolrInputDocument;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.healthmonitor.HealthMonitor;
import org.sleuthkit.autopsy.healthmonitor.TimingMetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A helper class to support indexing language-specific fields.
 */
class LanguageSpecificContentIndexingHelper {

    private final LanguageDetector languageDetector = new LanguageDetector();
    
    Optional<Language> detectLanguageIfNeeded(String text) throws NoOpenCoreException {
        double indexSchemaVersion = NumberUtils.toDouble(KeywordSearch.getServer().getIndexInfo().getSchemaVersion());
        if (2.2 <= indexSchemaVersion) {
            return languageDetector.detect(text);
        } else {
            return Optional.empty();
        }
    }    

    void updateLanguageSpecificFields(Map<String, Object> fields, Chunker.Chunk chunk, Language language) {
        List<String> values = new ArrayList<>();
        values.add(chunk.toString());
        if (fields.containsKey(Server.Schema.FILE_NAME.toString())) {
            values.add(Chunker.sanitize(fields.get(Server.Schema.FILE_NAME.toString()).toString()).toString());
        }

        // index the chunk to a language specific field
        fields.put(Server.Schema.CONTENT_JA.toString(), values);
        fields.put(Server.Schema.LANGUAGE.toString(), Chunker.sanitize(language.getValue()).toString());
    }

    void indexMiniChunk(Chunker.Chunk chunk, String sourceName, Map<String, Object> fields, String baseChunkID, Language language)
        throws Ingester.IngesterException {
        //Make a SolrInputDocument out of the field map
        SolrInputDocument updateDoc = new SolrInputDocument();
        for (String key : fields.keySet()) {
            if (fields.get(key).getClass() == String.class) {
                updateDoc.addField(key, Chunker.sanitize((String)fields.get(key)).toString());
            } else {
                updateDoc.addField(key, fields.get(key));
            }
        } 

        try {
            updateDoc.setField(Server.Schema.ID.toString(), Chunker.sanitize(MiniChunkHelper.getChunkIdString(baseChunkID)).toString());

            // index the chunk to a language specific field
            updateDoc.addField(Server.Schema.CONTENT_JA.toString(), Chunker.sanitize(chunk.toString().substring(chunk.getBaseChunkLength())).toString());
            updateDoc.addField(Server.Schema.LANGUAGE.toString(), Chunker.sanitize(language.getValue()).toString());

            TimingMetric metric = HealthMonitor.getTimingMetric("Solr: Index chunk");

            KeywordSearch.getServer().addDocument(updateDoc);
            HealthMonitor.submitTimingMetric(metric);

        } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
            throw new Ingester.IngesterException(
                NbBundle.getMessage(Ingester.class, "Ingester.ingest.exception.err.msg", sourceName), ex);
        }
    }
}
