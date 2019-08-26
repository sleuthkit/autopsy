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

class LanguageSpecificContentIndexingHelper {

  enum Language {
    JAPANESE
  }

  private final LanguageDetector languageDetector = new LanguageDetector();

  Optional<Language> detectLanguageIfNeeded(Chunker.Chunk chunk) throws NoOpenCoreException {
    double indexSchemaVersion = NumberUtils.toDouble(KeywordSearch.getServer().getIndexInfo().getSchemaVersion());
    if (2.2 <= indexSchemaVersion) {
      return languageDetector.detect(chunk.toString()).flatMap(lang -> Optional.ofNullable(toLanguage(lang)));
    } else {
      return Optional.empty();
    }
  }

  void updateLanguageSpecificFields(Map<String, Object> fields, Chunker.Chunk chunk, Language language) {
    List<String> values = new ArrayList<>();
    values.add(chunk.toString());
    if (fields.containsKey(Server.Schema.FILE_NAME.toString())) {
      values.add(fields.get(Server.Schema.FILE_NAME.toString()).toString());
    }

    // index the chunk to a language specific field
    fields.put(Server.Schema.CONTENT_JA.toString(), values);
    fields.put(Server.Schema.LANGUAGE.toString(), toFieldValue(language));
  }

  void indexMiniChunk(Chunker.Chunk chunk, String sourceName, Map<String, Object> fields, String baseChunkID, Language language)
      throws Ingester.IngesterException {
    //Make a SolrInputDocument out of the field map
    SolrInputDocument updateDoc = new SolrInputDocument();
    for (String key : fields.keySet()) {
      updateDoc.addField(key, fields.get(key));
    }

    try {
      updateDoc.setField(Server.Schema.ID.toString(), MiniChunks.getChunkIdString(baseChunkID));

      // index the chunk to a language specific field
      updateDoc.addField(Server.Schema.CONTENT_JA.toString(), chunk.toString().substring(chunk.getBaseChunkLength()));
      updateDoc.addField(Server.Schema.LANGUAGE.toString(), toFieldValue(language));

      TimingMetric metric = HealthMonitor.getTimingMetric("Solr: Index chunk");

      KeywordSearch.getServer().addDocument(updateDoc);
      HealthMonitor.submitTimingMetric(metric);

    } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
      throw new Ingester.IngesterException(
          NbBundle.getMessage(Ingester.class, "Ingester.ingest.exception.err.msg", sourceName), ex);
    }
  }

  private static String toFieldValue(Language language) {
    if (language == null) {
      return null;
    }
    switch (language) {
      case JAPANESE: return "ja";
      default:
        throw new IllegalStateException("Unknown language: " + language);
    }
  }

  private Language toLanguage(LanguageDetector.Language language) {
    if (language == null) {
      return null;
    }
    switch (language) {
      case JAPANESE: return Language.JAPANESE;
      default:
        return null;
    }
  }
}
