/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org *

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest job settings for the keywords search module.
 */
final class KeywordSearchJobSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private HashSet<String> namesOfEnabledKeywordLists;
    private HashSet<String> namesOfDisabledKeywordLists; // Added in version 1.1

    /**
     * Constructs ingest job settings for the keywords search module.
     *
     * @param namesOfEnabledKeywordLists A list of enabled keywords lists.
     */
    KeywordSearchJobSettings(List<String> namesOfEnabledKeywordLists) {
        this(namesOfEnabledKeywordLists, new ArrayList<String>());
    }

    /**
     * Constructs ingest job settings for the keywords search module.
     *
     * @param namesOfEnabledKeywordLists  A list of enabled keywords lists.
     * @param namesOfDisabledKeywordLists A list of disabled keywords lists.
     */
    KeywordSearchJobSettings(List<String> namesOfEnabledKeywordLists, List<String> namesOfDisabledKeywordLists) {
        this.namesOfEnabledKeywordLists = new HashSet<>(namesOfEnabledKeywordLists);
        this.namesOfDisabledKeywordLists = new HashSet<>(namesOfDisabledKeywordLists);
    }

    /**
     * @inheritDoc
     */
    @Override
    public long getVersionNumber() {
        this.upgradeFromOlderVersions();
        return serialVersionUID;
    }

    /**
     * Checks whether or not a keywords list is enabled. If there is no setting
     * for the requested list, it is deemed to be enabled.
     *
     * @param keywordListName The name of the keywords list to check.
     *
     * @return True if the keywords list is enabled, false otherwise.
     */
    boolean keywordListIsEnabled(String keywordListName) {
        this.upgradeFromOlderVersions();
        return namesOfEnabledKeywordLists.contains(keywordListName);
    }

    /**
     * Get the names of all explicitly enabled keywords lists.
     *
     * @return The list of names.
     */
    List<String> getNamesOfEnabledKeyWordLists() {
        this.upgradeFromOlderVersions();
        return new ArrayList<>(namesOfEnabledKeywordLists);
    }

    /**
     * Get the names of all explicitly disabled keywords lists.
     *
     * @return The list of names.
     */
    List<String> getNamesOfDisabledKeyWordLists() {
        this.upgradeFromOlderVersions();
        return new ArrayList<>(namesOfDisabledKeywordLists);
    }

    /**
     * Initialize fields set to null when an instance of a previous, but still
     * compatible, version of this class is de-serialized.
     */
    private void upgradeFromOlderVersions() {
        if (null == this.namesOfDisabledKeywordLists) {
            this.namesOfDisabledKeywordLists = new HashSet<>();
        }
    }

}
