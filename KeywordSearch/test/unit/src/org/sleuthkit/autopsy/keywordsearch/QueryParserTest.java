package org.sleuthkit.autopsy.keywordsearch;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class QueryParserTest {

  @Test
  public void getFieldTermsMap() {
    Map<String, List<String>> map = QueryParser.getFieldTermsMap("content_ja:雨 content_ja:降る");
    List<String> terms = map.get("content_ja");
    assertEquals(2, terms.size());
    assertEquals("雨", terms.get(0));
    assertEquals("降る", terms.get(1));
  }
}
