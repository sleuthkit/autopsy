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
package org.sleuthkit.autopsy.textextractors.textextractorconfigs;

/**
 * Allows for configuration of OCR on image files. Extractors that use ImageConfig
 * can be obtained through TextExtractoryFactory.getExtractor().
 *
 * @see org.openide.util.Lookup
 */
public class ImageConfig {

    private Boolean OCREnabled;

    /**
     * Enables OCR to be run on the text reader responsible for handling
     * image files.
     *
     * @param enabled Flag indicating if OCR is enabled.
     */
    public void setOCREnabled(boolean enabled) {
        this.OCREnabled = enabled;
    }

    /**
     * Gets the OCR flag that has been set. By default this flag is turned off.
     *
     * @return Flag indicating if OCR is enabled.
     */
    public boolean getOCREnabled() {
        return this.OCREnabled;
    }
}
