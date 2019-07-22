package org.sleuthkit.autopsy.keywordsearch;

class MiniChunks {

  static String SUFFIX = "_mini";

  static String getChunkIdString(String baseChunkID) {
    return baseChunkID + SUFFIX;
  }

  static boolean isMiniChunkID(String chunkID) {
    return chunkID.endsWith(SUFFIX);
  }

  static String getBaseChunkID(String miniChunkID) {
    return miniChunkID.replaceFirst(SUFFIX + "$", "");
  }
}
