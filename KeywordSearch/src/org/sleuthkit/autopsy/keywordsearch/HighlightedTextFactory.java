package org.sleuthkit.autopsy.keywordsearch;

import org.apache.commons.lang3.math.NumberUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

class HighlightedTextFactory {

  static IndexedText create(long solrObjectId, QueryResults hits) throws NoOpenCoreException {
    double indexSchemaVersion = NumberUtils.toDouble(KeywordSearch.getServer().getIndexInfo().getSchemaVersion());
    if (indexSchemaVersion >= 2.2) {
      return new HighlightedText(solrObjectId, hits);
    } else {
      return new HighlightedText_2_1(solrObjectId, hits);
    }
  }

  static IndexedText create(BlackboardArtifact artifact) throws TskCoreException, NoOpenCoreException {
    double indexSchemaVersion = NumberUtils.toDouble(KeywordSearch.getServer().getIndexInfo().getSchemaVersion());
    if (indexSchemaVersion >= 2.2) {
      return new HighlightedText(artifact);
    } else {
      return new HighlightedText_2_1(artifact);
    }
  }
}
