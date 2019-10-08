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

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * tests for LanguageSpecificContentQueryHelper
 */
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
        QueryTermHelper.Result result = new QueryTermHelper.Result();
        result.fieldTermsMap.put("field1", Arrays.asList("term1"));
        result.fieldTermsMap.put("field2", Arrays.asList("term1", "term2"));
        assertEquals(
            "termfreq:sum(termfreq(\"field1\",\"term1\"),termfreq(\"field2\",\"term1\"),termfreq(\"field2\",\"term2\"))",
            LanguageSpecificContentQueryHelper.buildTermfreqQuery("query", result));
    }
}
