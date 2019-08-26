package org.sleuthkit.autopsy.keywordsearch;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MiniChunksTest {

  @Test
  public void isMiniChunkID() {
    assertTrue(MiniChunks.isMiniChunkID("1_1_mini"));
    assertFalse(MiniChunks.isMiniChunkID("1_1"));
    assertFalse(MiniChunks.isMiniChunkID("1"));
  }

  @Test
  public void getBaseChunkID() {
    Assert.assertEquals("1_1", MiniChunks.getBaseChunkID("1_1_mini"));
    Assert.assertEquals("1_1", MiniChunks.getBaseChunkID("1_1"));
    Assert.assertEquals("1", MiniChunks.getBaseChunkID("1"));
  }

}