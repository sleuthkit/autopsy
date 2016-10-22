/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.util.Date;
import java.util.List;

/**
 * A list of keywords for which to search. Includes list creation and
 * modification metadata and a setting that indicates whether messages should be
 * sent to the ingest messages inbox when a keyword in the list is found.
 */
public class KeywordList {

    private String name;
    private Date created;
    private Date modified;
    private Boolean useForIngest;
    private Boolean postIngestMessages;
    private List<Keyword> keywords;
    private Boolean locked;

    KeywordList(String name, Date created, Date modified, Boolean useForIngest, Boolean postIngestMessages, List<Keyword> keywords, boolean locked) {
        this.name = name;
        this.created = created;
        this.modified = modified;
        this.useForIngest = useForIngest;
        this.postIngestMessages = postIngestMessages;
        this.keywords = keywords;
        this.locked = locked;
    }

    KeywordList(String name, Date created, Date modified, Boolean useForIngest, Boolean ingestMessages, List<Keyword> keywords) {
        this(name, created, modified, useForIngest, ingestMessages, keywords, false);
    }

    /**
     * Create an unnamed list. Usually used for ad-hoc searches
     *
     * @param keywords
     */
    KeywordList(List<Keyword> keywords) {
        this("", new Date(0), new Date(0), false, false, keywords, false);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final KeywordList other = (KeywordList) obj;
        if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        return hash;
    }

    String getName() {
        return name;
    }

    Date getDateCreated() {
        return created;
    }

    Date getDateModified() {
        return modified;
    }

    Boolean getUseForIngest() {
        return useForIngest;
    }

    void setUseForIngest(boolean use) {
        this.useForIngest = use;
    }

    Boolean getIngestMessages() {
        return postIngestMessages;
    }

    void setIngestMessages(boolean ingestMessages) {
        this.postIngestMessages = ingestMessages;
    }

    List<Keyword> getKeywords() {
        return keywords;
    }

    boolean hasKeyword(Keyword keyword) {
        return keywords.contains(keyword);
    }

    boolean hasKeyword(String keyword) {
        //note, this ignores isLiteral
        for (Keyword k : keywords) {
            if (k.getSearchTerm().equals(keyword)) {
                return true;
            }
        }
        return false;
    }

    Boolean isLocked() {
        return locked;
    }
}
