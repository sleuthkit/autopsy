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
 * modification metadata and settings that determine how the list is to be used
 * when ingesting a data source. Standard lists provided by Autopsy may be
 * marked as not editable.
 */
public class KeywordList {

    private String name;
    private Date created;
    private Date modified;
    private Boolean useForIngest;
    private Boolean postIngestMessages;
    private List<Keyword> keywords;
    private Boolean isEditable;

    /**
     * Constructs a list of keywords for which to search. Includes list creation
     * and modification metadata and settings that determine how the list is to
     * be used when ingesting a data source. Standard lists provided by Autopsy
     * may be marked as not editable.
     *
     * @param name               The name to asociate with the list.
     * @param created            When the list was created.
     * @param modified           When the list was last modified.
     * @param useForIngest       Whether or not the list is to be used when
     *                           ingesting a data source.
     * @param postIngestMessages Whether or not to post ingest inbox messages
     *                           when a keyword within the list is found while
     *                           ingesting a data source.
     * @param keywords           The keywords that make up the list.
     * @param isEditable         Whether or not the list may be edited by a
     *                           user; standard lists provided by Autopsy should
     *                           not be edited.
     */
    KeywordList(String name, Date created, Date modified, Boolean useForIngest, Boolean postIngestMessages, List<Keyword> keywords, boolean isEditable) {
        this.name = name;
        this.created = created;
        this.modified = modified;
        this.useForIngest = useForIngest;
        this.postIngestMessages = postIngestMessages;
        this.keywords = keywords;
        this.isEditable = isEditable;
    }

    /**
     * Constructs a list of keywords for which to search. Includes list creation
     * and modification metadata and settings that determine how the list is to
     * be used when ingesting a data source. The list will be marked as a
     * standard lists provided by Autopsy that should not be treated as
     * editable.
     *
     * @param name               The name to asociate with the list.
     * @param created            When the list was created.
     * @param modified           When the list was last modified.
     * @param useForIngest       Whether or not the list is to be used when
     *                           ingesting a data source.
     * @param postIngestMessages Whether or not to post ingest inbox messages
     *                           when a keyword within the list is found while
     *                           ingesting a data source.
     * @param keywords           The keywords that make up the list.
     */
    KeywordList(String name, Date created, Date modified, Boolean useForIngest, Boolean ingestMessages, List<Keyword> keywords) {
        this(name, created, modified, useForIngest, ingestMessages, keywords, false);
    }

    /**
     * Constructs a temporary list of keywords to be used for ad hoc keyword
     * search and then discarded.
     *
     * @param keywords
     */
    KeywordList(List<Keyword> keywords) {
        this("", new Date(0), new Date(0), false, false, keywords, false);
    }

    /**
     * Get the name assigned to the keyword list.
     *
     * @return The list name.
     */
    String getName() {
        return name;
    }

    /**
     * Gets the date the keyword list was created.
     *
     * @return The date.
     */
    Date getDateCreated() {
        return created;
    }

    /**
     * Gets the date the keyword list was last modified.
     *
     * @return The date.
     */
    Date getDateModified() {
        return modified;
    }

    /**
     * Gets whether or not the list should be used when ingesting a data source.
     *
     * @return True or false.
     */
    Boolean getUseForIngest() {
        return useForIngest;
    }

    /**
     * Sets whether or not the list should be used when ingesting a data source.
     *
     * @param useForIngest True or false.
     */
    void setUseForIngest(boolean useForIngest) {
        this.useForIngest = useForIngest;
    }

    /**
     * Gets whether or not to post ingest inbox messages when a keyword within
     * the list is found while ingesting a data source.
     *
     * @return true or false
     */
    Boolean getIngestMessages() {
        return postIngestMessages;
    }

    /**
     * Sets whether or not to post ingest inbox messages when a keyword within
     * the list is found while ingesting a data source.
     *
     * @param postIngestMessages True or false.
     */
    void setIngestMessages(boolean postIngestMessages) {
        this.postIngestMessages = postIngestMessages;
    }

    /**
     * Gets the keywords included in the list
     *
     * @return A collection of Keyword objects.
     */
    public List<Keyword> getKeywords() {
        return keywords;
    }

    /**
     * Indicates whether or not a given keyword is included in the list.
     *
     * @param keyword The keyword of interest.
     *
     * @return
     */
    boolean hasKeyword(Keyword keyword) {
        return keywords.contains(keyword);
    }

    /**
     * Indicates whether or not a given search term is included in the list.
     *
     * @param searchTerm The search term.
     *
     * @return True or false.
     */
    boolean hasSearchTerm(String searchTerm) {
        for (Keyword word : keywords) {
            if (word.getSearchTerm().equals(searchTerm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indicates Whether or not the list should be editable by a user; standard
     * lists provided by Autopsy should be marked as not editable when they are
     * contructed.
     *
     * @return True or false.
     */
    Boolean isEditable() {
        return isEditable;
    }

}
