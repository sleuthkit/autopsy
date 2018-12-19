/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.textextractors.configs;

import java.util.List;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;

/**
 * Allows for configuration of the TextExtractor obtained from
 * TextExtractorFactory.getStringsExtractor().
 *
 * The Strings extractor will extract strings from the Content instance. This
 * class allows for the configuration of the encoding and language scripts that
 * will be used.
 *
 * @see org.sleuthkit.autopsy.textextractors.TextExtractorFactory
 * @see
 * org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT
 * @see org.openide.util.Lookup
 */
public class StringsConfig {

    private Boolean extractUTF8;
    private Boolean extractUTF16;
    private List<SCRIPT> languageScripts;

    /**
     * Enables UTF-8 encoding to be used during reading.
     *
     * @param enabled Flag indicating if UTF-8 should be turned on
     */
    public void setExtractUTF8(boolean enabled) {
        this.extractUTF8 = enabled;
    }

    /**
     * Enables UTF-16 encoding to be used during extraction.
     *
     * @param enabled Flag indicating if UTF-16 should be turned on
     */
    public void setExtractUTF16(boolean enabled) {
        this.extractUTF16 = enabled;
    }

    /**
     * Returns whether extraction with UTF-8 encoding should be done.
     *
     * @return Flag indicating if UTF-8 has been turned on/off
     */
    public Boolean getExtractUTF8() {
        return extractUTF8;
    }

    /**
     * Return whether extraction with UTF-16 encoding should be done.
     *
     * @return Flag indicating if UTF-16 has been turned on/off
     */
    public Boolean getExtractUTF16() {
        return extractUTF16;
    }

    /**
     * Sets the type of language scripts that will be used during extraction.
     * See
     * {@link org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT}
     * for more information about available scripts.
     *
     * @param scripts Desired set of scripts to be used during extraction
     */
    public void setLanguageScripts(List<SCRIPT> scripts) {
        this.languageScripts = scripts;
    }

    /**
     * Gets the desired set of scripts to be used during extraction.
     *
     * @return Set of language scripts to be used
     */
    public List<SCRIPT> getLanguageScripts() {
        return this.languageScripts;
    }
}
