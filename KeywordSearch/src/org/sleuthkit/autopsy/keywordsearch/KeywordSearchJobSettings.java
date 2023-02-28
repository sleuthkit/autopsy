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
public final class KeywordSearchJobSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;

    private HashSet<String> namesOfEnabledKeywordLists;
    private HashSet<String> namesOfDisabledKeywordLists; // Added in version 1.1

    /**
     * These are nullable so that if no serialized setting, the setting can
     * defer to legacy KeywordSearchSettings.
     */
    private Boolean ocrEnabled;
    private Boolean limitedOCREnabled;

    private boolean ocrOnly;

    /**
     * Constructs ingest job settings for the keywords search module.
     *
     * @param namesOfEnabledKeywordLists A list of enabled keywords lists.
     */
    KeywordSearchJobSettings(List<String> namesOfEnabledKeywordLists) {
        this.namesOfEnabledKeywordLists = new HashSet<>(namesOfEnabledKeywordLists);
        this.namesOfDisabledKeywordLists = new HashSet<>();
        
        // explicitly set to default value
        this.ocrEnabled = null;
        this.limitedOCREnabled = null;
        this.ocrOnly = false;
    }

    /**
     * Constructs ingest job settings for the keywords search module.
     *
     * @param namesOfEnabledKeywordLists  A list of enabled keywords lists.
     * @param namesOfDisabledKeywordLists A list of disabled keywords lists.
     * @param ocrEnabled                  Whether or not OCR is enabled for
     *                                    keyword search.
     * @param limitedOCREnabled           If true, OCR is to be performed only
     *                                    on images larger than 100KB.
     * @param ocrOnly                     True if keyword search ingest should
     *                                    be solely limited to OCR.
     */
    KeywordSearchJobSettings(List<String> namesOfEnabledKeywordLists, List<String> namesOfDisabledKeywordLists, boolean ocrEnabled, boolean limitedOCREnabled, boolean ocrOnly) {
        this.namesOfEnabledKeywordLists = new HashSet<>(namesOfEnabledKeywordLists);
        this.namesOfDisabledKeywordLists = new HashSet<>(namesOfDisabledKeywordLists);
        this.ocrEnabled = ocrEnabled;
        this.limitedOCREnabled = limitedOCREnabled;
        this.ocrOnly = ocrOnly;
    }

    /**
     * Whether or not OCR is enabled for keyword search.
     *
     * @return Whether or not OCR is enabled for keyword search.
     */
    @SuppressWarnings("deprecation")
    public boolean isOCREnabled() {
        if (ocrEnabled == null) {
            ocrEnabled = KeywordSearchSettings.getOcrOption();
        }
        
        return ocrEnabled;
    }

    /**
     * Sets whether or not OCR is enabled for keyword search.
     *
     * @param ocrEnabled Whether or not OCR is enabled for keyword search.
     */
    public void setOCREnabled(boolean ocrEnabled) {
        this.ocrEnabled = ocrEnabled;
    }

    /**
     * Returns true if OCR is to be performed only on images larger than 100KB.
     * May defer to KeywordSearchSettings if no setting serialized.
     *
     * @return If true, OCR is to be performed only on images larger than 100KB.
     */
    @SuppressWarnings("deprecation")
    boolean isLimitedOCREnabled() {
        if (limitedOCREnabled == null) {
            limitedOCREnabled = KeywordSearchSettings.getLimitedOcrOption();
        }
        
        return limitedOCREnabled;
    }

    /**
     * Sets whether or not OCR should be performed only on images larger than
     * 100KB.
     *
     * @param limitedOCREnabled Whether or not OCR should be performed only on
     *                          images larger than 100KB.
     */
    void setLimitedOCREnabled(boolean limitedOCREnabled) {
        this.limitedOCREnabled = limitedOCREnabled;
    }

    /**
     * Returns true if keyword search ingest should be solely limited to OCR.
     *
     * @return True if keyword search ingest should be solely limited to OCR.
     */
    boolean isOCROnly() {
        return ocrOnly;
    }

    /**
     * Sets whether or not keyword search ingest should be solely limited to
     * OCR.
     *
     * @param ocrOnly Whether or not keyword search ingest should be solely
     *                limited to OCR.
     */
    void setOCROnly(boolean ocrOnly) {
        this.ocrOnly = ocrOnly;
    }

    /**
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
