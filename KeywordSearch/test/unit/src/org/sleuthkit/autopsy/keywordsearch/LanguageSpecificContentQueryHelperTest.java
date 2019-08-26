package org.sleuthkit.autopsy.keywordsearch;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class LanguageSpecificContentQueryHelperTest {

  @Test
  public void makeQueryString() {
    assertEquals("text:query OR content_ja:query", LanguageSpecificContentQueryHelper.expandQueryString("query"));
  }

  @Test
  public void findNthIndexOf() {
    assertEquals(-1, LanguageSpecificContentQueryHelper.findNthIndexOf("A1AA45", "_", 0));
    assertEquals(0, LanguageSpecificContentQueryHelper.findNthIndexOf("A1AA45", "A", 0));
    assertEquals(2, LanguageSpecificContentQueryHelper.findNthIndexOf("A1AA45", "A", 1));
    assertEquals(3, LanguageSpecificContentQueryHelper.findNthIndexOf("A1AA45", "A", 2));
    assertEquals(-1, LanguageSpecificContentQueryHelper.findNthIndexOf("A1AA45", "A", 3));
    assertEquals(0, LanguageSpecificContentQueryHelper.findNthIndexOf("A1AA45", "", 0));
    assertEquals(-1, LanguageSpecificContentQueryHelper.findNthIndexOf("", "A", 0));
    assertEquals(-1, LanguageSpecificContentQueryHelper.findNthIndexOf("A1AA45", "A", -1));
    assertEquals(-1, LanguageSpecificContentQueryHelper.findNthIndexOf("A1AA45", "A", 999));
  }

  @Test
  public void buildTermfreqQuery() {
    QueryParser.Result result = new QueryParser.Result();
    result.fieldTermsMap = new HashMap<>();
    result.fieldTermsMap.put("field1", Arrays.asList("term1"));
    result.fieldTermsMap.put("field2", Arrays.asList("term1", "term2"));
    assertEquals(
        "termfreq:sum(termfreq(\"field1\",\"term1\"),termfreq(\"field1\",\"term1\"),termfreq(\"field1\",\"term1\"))",
        LanguageSpecificContentQueryHelper.buildTermfreqQuery("query", result));
  }
}
